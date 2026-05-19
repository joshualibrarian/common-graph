package dev.everydaythings.graph.operator;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.AnchorTable.TokenAnchor;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import dev.everydaythings.graph.text.FrameMapTarget;
import dev.everydaythings.graph.text.ParseContext;
import dev.everydaythings.graph.text.ParseEngine;
import dev.everydaythings.graph.text.ParseParams;
import dev.everydaythings.graph.text.TextSpan;
import dev.everydaythings.graph.text.TokenLattice.TokenSpan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Operator notation — the {@link Language} that owns rendering of
 * operator-form frames (infix, prefix, postfix) into expressions like
 * {@code 5 + 3 * 2} or {@code -5}.
 *
 * <p>Sibling of natural-language Languages (English, German, ...): all are
 * Languages, all participate equally in the parse/render pipeline; OperatorNotation
 * is the one that knows about precedence, associativity, fixity, and parens.
 *
 * <p>Parsing of operator expressions is distributed across the individual
 * {@link Operator} sememes (each anchored from a token resolution handles its own
 * piece in {@link Operator#parse}). OperatorNotation's contribution is on the
 * <i>render</i> side: given a frame whose predicate is an Operator, walk the
 * operator-form Lexeme metadata (symbol, fixity, precedence, associativity),
 * recurse into operands, and assemble the surface form with parens inserted
 * exactly where precedence and associativity demand.
 *
 * <p>Concrete operator sememes ({@link dev.everydaythings.graph.operator.math.Add Add},
 * {@link dev.everydaythings.graph.operator.math.Multiply Multiply}, etc.) supply the
 * data; this class supplies the assembly machinery. Languages further up the
 * stack (English prose, German prose) may override predicates they have prose
 * forms for and {@code super.render(...)} to fall back here for everything else,
 * so math notation works in any Language by default.
 */
@Seed.Item(key = OperatorNotation.KEY)
@Seed.Embodies(key = OperatorNotation.KEY)
public class OperatorNotation extends Language {

    /** Canonical key for the operator-notation Language. */
    public static final String KEY = "cg.lang:operator-notation";

    /** Seed/siloed constructor (no librarian). */
    public OperatorNotation() {
        super(ItemRef.iid(KEY));
    }

    /** Runtime constructor — bound to a librarian. */
    public OperatorNotation(Librarian librarian) {
        super(ItemRef.iid(KEY), librarian);
    }

    /** Runtime constructor invoked by {@link dev.everydaythings.graph.item.SeedProcessor}
     *  during bootstrap hydration; matches the {@code (ItemRef, Librarian)} contract
     *  enforced for any class carrying {@link Seed.Embodies}. */
    public OperatorNotation(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // ==================================================================================
    // Parse — entry point + helpers.
    //
    // {@link #parseAnchor} is called by {@link Operator#parse(ParseContext)} for each
    // anchored operator sememe. The operator sememes contribute the data (their
    // operator-form Lexeme metadata); this class owns the parsing CODE — the
    // syntactic recognition of operator expressions, paren grouping, chain handling
    // for associative operators, and confidence scoring by precedence and fixity fit.
    // ==================================================================================

    /**
     * Build one operator's parse contribution for a round. Locates the operator's
     * anchor spans at the outer level (anchors inside paren groups are deferred to
     * the recursive paren resolver), reads its operator-form metadata, and emits
     * either a single-anchor or chained FrameMap delta.
     *
     * <p>Instance method (rather than static) so the operand-resolution helpers can
     * call {@code this.recognizeOperand} for locale-aware literal recognition.
     * Callers (typically {@link Operator#parse}) fetch the OperatorNotation Language
     * item from the librarian and invoke this method on the instance.
     */
    public FrameMap parseAnchor(Operator self, ParseContext ctx) {
        Optional<TokenAnchor> selfAnchor = ctx.anchors().tokenAnchors().stream()
                .filter(ta -> ta.participant().iid().equals(self.iid()))
                .findFirst();
        if (selfAnchor.isEmpty() || selfAnchor.get().spans().isEmpty()) {
            return FrameMap.empty();
        }
        Optional<OperatorForm> formOpt = lookupOperatorForm(self);
        if (formOpt.isEmpty()) return FrameMap.empty();
        OperatorForm form = formOpt.get();

        List<Integer> anchorIndices = new ArrayList<>();
        for (TextSpan span : selfAnchor.get().spans()) {
            int i = indexOfTokenSpan(ctx.tokens(), span);
            if (i < 0) continue;
            if (isInsideParens(ctx.tokens(), i)) continue;
            // Only operate on anchors whose actual token text is this operator's
            // OperatorNotation symbol — skip anchors from other lexemes (English
            // verb-lemma "add", FunctionNotation name "sum", etc.), which other
            // Languages will handle.
            if (!form.symbol().equals(ctx.tokens().get(i).surfaceText())) continue;
            anchorIndices.add(i);
        }
        if (anchorIndices.isEmpty()) return FrameMap.empty();
        Collections.sort(anchorIndices);

        // Chain handling for multi-anchor associative infix operators. For
        // {@code 5 - 3 - 2}, Subtract appears twice; left-associativity means the
        // structure is {@code Subtract{ THEME=Subtract{5,3}, GOAL=2 }} — outer is
        // the rightmost anchor, inner is the leftmost. Right-associative operators
        // mirror it. Non-associative or unary fixities take only the first anchor.
        if (ItemRef.iid(Operator.Infix.KEY).equals(form.fixity())
                && anchorIndices.size() > 1
                && (ItemRef.iid(Operator.Left.KEY).equals(form.associativity())
                        || ItemRef.iid(Operator.Right.KEY).equals(form.associativity()))) {
            return buildChainFrame(self, anchorIndices, ctx, form);
        }
        return buildAnchorFrame(self, anchorIndices.get(0), ctx, form);
    }

    /** Build a single-anchor FrameMap, resolving operands from context. */
    private FrameMap buildAnchorFrame(Operator self, int anchorIdx,
                                      ParseContext ctx, OperatorForm form) {
        Operand left = resolveLeftOperand(ctx, anchorIdx);
        Operand right = resolveRightOperand(ctx, anchorIdx);
        return buildFrameWithOperands(self, anchorIdx, left, right, ctx, form);
    }

    /**
     * Build a single-anchor FrameMap with operands provided externally — used by
     * {@link #buildChainFrame} to splice prior chain segments in as operands.
     */
    private FrameMap buildFrameWithOperands(Operator self, int anchorIdx,
                                            Operand left, Operand right,
                                            ParseContext ctx, OperatorForm form) {
        TextSpan anchorSpan = ctx.tokens().get(anchorIdx).span();

        double fitness = contextFitness(form.fixity(), left, right);
        if (fitness <= 0.0) return FrameMap.empty();

        double predConf = pickConfidence(fitness, form.precedence());
        double bindConf = predConf * 0.95;
        BigDecimal predicateConfidence = new BigDecimal(formatConfidence(predConf));
        BigDecimal bindingConfidence = new BigDecimal(formatConfidence(bindConf));

        List<BindingMap> bindings = new ArrayList<>();
        if (ItemRef.iid(Operator.Infix.KEY).equals(form.fixity())) {
            BindingMap themeBinding = makeBinding(left, ItemRef.iid(ThematicRole.Theme.KEY), bindingConfidence);
            if (themeBinding != null) bindings.add(themeBinding);
            BindingMap goalBinding = makeBinding(right, ItemRef.iid(ThematicRole.Goal.KEY), bindingConfidence);
            if (goalBinding != null) bindings.add(goalBinding);
        } else if (ItemRef.iid(Operator.Prefix.KEY).equals(form.fixity())) {
            BindingMap operandBinding = makeBinding(right, ItemRef.iid(ThematicRole.Theme.KEY), bindingConfidence);
            if (operandBinding != null) bindings.add(operandBinding);
        } else if (ItemRef.iid(Operator.Postfix.KEY).equals(form.fixity())) {
            BindingMap operandBinding = makeBinding(left, ItemRef.iid(ThematicRole.Theme.KEY), bindingConfidence);
            if (operandBinding != null) bindings.add(operandBinding);
        } else {
            return FrameMap.empty();
        }

        return new FrameMap(
                null,
                new Part<>(ItemRef.of(self.iid()), predicateConfidence, List.of(anchorSpan)),
                bindings,
                List.of());
    }

    /** Build a chain frame for multi-anchor associative infix operators. */
    private FrameMap buildChainFrame(Operator self, List<Integer> anchorIndices,
                                     ParseContext ctx, OperatorForm form) {
        boolean leftAssoc = ItemRef.iid(Operator.Left.KEY).equals(form.associativity());
        if (leftAssoc) {
            FrameMap current = buildAnchorFrame(self, anchorIndices.get(0), ctx, form);
            if (current.predicate() == null) return current;
            for (int i = 1; i < anchorIndices.size(); i++) {
                int idx = anchorIndices.get(i);
                Operand right = resolveRightOperand(ctx, idx);
                Operand left = new Operand(new FrameMapTarget(current), claimSpan(current));
                current = buildFrameWithOperands(self, idx, left, right, ctx, form);
                if (current.predicate() == null) return current;
            }
            return current;
        } else {
            FrameMap current = buildAnchorFrame(self, anchorIndices.get(anchorIndices.size() - 1), ctx, form);
            if (current.predicate() == null) return current;
            for (int i = anchorIndices.size() - 2; i >= 0; i--) {
                int idx = anchorIndices.get(i);
                Operand left = resolveLeftOperand(ctx, idx);
                Operand right = new Operand(new FrameMapTarget(current), claimSpan(current));
                current = buildFrameWithOperands(self, idx, left, right, ctx, form);
                if (current.predicate() == null) return current;
            }
            return current;
        }
    }

    /** Bounding span of a frame's claim region (predicate anchor + binding-target spans). */
    private static List<TextSpan> claimSpan(FrameMap frame) {
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        if (frame.predicate() != null) {
            for (TextSpan s : frame.predicate().spans()) {
                if (s.start() < start) start = s.start();
                if (s.end() > end) end = s.end();
            }
        }
        for (BindingMap b : frame.bindings()) {
            if (b.target() == null) continue;
            for (TextSpan s : b.target().spans()) {
                if (s.start() < start) start = s.start();
                if (s.end() > end) end = s.end();
            }
        }
        if (start == Integer.MAX_VALUE) return List.of();
        return List.of(new TextSpan(start, end));
    }

    /**
     * Score how well an operator's fixity fits the surrounding context. Result in
     * [0, 1] multiplies into the predicate confidence: an infix operator with no
     * left operand yields a weak bid so a better-fitting prefix operator wins
     * at the same symbol position.
     */
    private static double contextFitness(ItemRef fixity, Operand left, Operand right) {
        if (ItemRef.iid(Operator.Infix.KEY).equals(fixity)) {
            int neighbors = (left != null ? 1 : 0) + (right != null ? 1 : 0);
            return neighbors / 2.0;
        }
        if (ItemRef.iid(Operator.Prefix.KEY).equals(fixity)) {
            if (right == null) return 0.0;
            return left == null ? 1.0 : 0.7;
        }
        if (ItemRef.iid(Operator.Postfix.KEY).equals(fixity)) {
            if (left == null) return 0.0;
            return right == null ? 1.0 : 0.7;
        }
        return 0.0;
    }

    /**
     * Combine fitness and precedence into a confidence in [0, 0.95]. Lower-
     * precedence operators get a higher base — they form the OUTER predicate of
     * expressions like {@code 5 + 3 * 2}, where Add (10) wraps Multiply (20).
     */
    private static double pickConfidence(double fitness, long precedence) {
        if (fitness <= 0.0) return 0.0;
        double clamped = Math.max(-20.0, Math.min(50.0, (double) precedence));
        double precFactor = 0.95 - 0.45 * ((clamped + 20.0) / 70.0);
        return precFactor * fitness;
    }

    /** Format a [0, 1] confidence as a fixed-precision BigDecimal-parseable string. */
    private static String formatConfidence(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f",
                Math.max(0.0, Math.min(0.9999, value)));
    }

    /** Build a role binding from a resolved operand, or null if no usable operand. */
    private static BindingMap makeBinding(Operand op, ItemRef role, BigDecimal confidence) {
        if (op == null) return null;
        return new BindingMap(
                new Part<>(ItemRef.of(role), confidence, List.of()),
                List.of(),
                new Part<>(op.target(), confidence, op.spans()));
    }

    // ----- Paren-aware operand resolution -----

    /** Either a literal/ref operand from a single token, or a parens-wrapped sub-FrameMap. */
    private record Operand(Object target, List<TextSpan> spans) {}

    private Operand resolveLeftOperand(ParseContext ctx, int anchorIdx) {
        if (anchorIdx <= 0) return null;
        TokenSpan immediate = ctx.tokens().get(anchorIdx - 1);
        if (isCloseGroup(immediate)) {
            int openIdx = findMatchingOpen(ctx.tokens(), anchorIdx - 1);
            if (openIdx < 0) return null;
            TokenSpan openTok = ctx.tokens().get(openIdx);
            return parenGroupOperand(ctx, openTok, immediate);
        }
        Object target = recognizeOperand(immediate).orElse(null);
        if (target == null) return null;
        return new Operand(target, List.of(immediate.span()));
    }

    private Operand resolveRightOperand(ParseContext ctx, int anchorIdx) {
        if (anchorIdx >= ctx.tokens().size() - 1) return null;
        TokenSpan immediate = ctx.tokens().get(anchorIdx + 1);
        if (isOpenGroup(immediate)) {
            int closeIdx = findMatchingClose(ctx.tokens(), anchorIdx + 1);
            if (closeIdx < 0) return null;
            TokenSpan closeTok = ctx.tokens().get(closeIdx);
            return parenGroupOperand(ctx, immediate, closeTok);
        }
        Object target = recognizeOperand(immediate).orElse(null);
        if (target == null) return null;
        return new Operand(target, List.of(immediate.span()));
    }

    /**
     * Build an Operand from a paren group: recursively parse the bracketed text
     * and return a FrameMapTarget whose span covers {@code openTok.start} to
     * {@code closeTok.end}.
     */
    private static Operand parenGroupOperand(ParseContext ctx, TokenSpan openTok, TokenSpan closeTok) {
        TextSpan groupSpan = new TextSpan(openTok.span().start(), closeTok.span().end());
        String text = ctx.draft() != null ? ctx.draft().text() : null;
        if (text == null) return null;
        int innerStart = openTok.span().end();
        int innerEnd = closeTok.span().start();
        if (innerStart < 0 || innerEnd > text.length() || innerStart > innerEnd) return null;
        String bracketed = text.substring(innerStart, innerEnd);
        FrameMap subFrame = ParseEngine.run(ctx.orchestrator(), bracketed, ParseParams.defaults());
        return new Operand(new FrameMapTarget(subFrame), List.of(groupSpan));
    }

    // ==================================================================================
    // Render entry point
    // ==================================================================================

    /**
     * Render a frame whose predicate carries an operator-form Lexeme. If the
     * predicate has no operator form, returns the input FrameMap unchanged so a
     * higher-level Language in the stack can take a turn (or the caller falls
     * back elsewhere).
     */
    @Override
    public FrameMap render(FrameMap framemap, ParseParams params) {
        if (framemap == null || framemap.predicate() == null
                || framemap.predicate().value() == null
                || librarian() == null) {
            return framemap;
        }
        List<Object> targets = operandTargetsByRole(framemap);
        Optional<Rendered> result = renderOperation(
                framemap.predicate().value().iid(), targets, params);
        return result.isPresent() ? framemap.withText(result.get().text()) : framemap;
    }

    // ==================================================================================
    // Render dispatch — by fixity
    //
    // The dispatcher resolves the predicate's operator-form Lexeme then hands off
    // to a fixity-specific assembler. Each assembler returns a Rendered carrier
    // (text + originating precedence) so its caller can decide whether to wrap
    // this rendering in parens when it appears inside a larger expression.
    // ==================================================================================

    private Optional<Rendered> renderOperation(ItemRef predicateIid,
                                               List<Object> targets,
                                               ParseParams params) {
        Optional<Item> predItem = librarian().fetchItem(predicateIid);
        if (predItem.isEmpty()) return Optional.empty();

        Optional<OperatorForm> formOpt = lookupOperatorForm(predItem.get());
        if (formOpt.isEmpty()) return Optional.empty();
        OperatorForm form = formOpt.get();

        ItemRef fixity = form.fixity();
        if (ItemRef.iid(Operator.Infix.KEY).equals(fixity))   return renderInfix(form, targets, params);
        if (ItemRef.iid(Operator.Prefix.KEY).equals(fixity))  return renderPrefix(form, targets, params);
        if (ItemRef.iid(Operator.Postfix.KEY).equals(fixity)) return renderPostfix(form, targets, params);
        return Optional.empty();
    }

    /** Infix: {@code <left> <symbol> <right>}. Requires two operand targets. */
    private Optional<Rendered> renderInfix(OperatorForm form, List<Object> targets, ParseParams params) {
        if (targets.size() != 2) return Optional.empty();
        String left  = renderOperand(targets.get(0), form.precedence(), form.associativity(), true, params);
        String right = renderOperand(targets.get(1), form.precedence(), form.associativity(), false, params);
        if (left == null || right == null) return Optional.empty();
        String separator = " ";
        return Optional.of(new Rendered(left + separator + form.symbol() + separator + right, form.precedence()));
    }

    /**
     * Prefix: {@code <symbol><operand>} (symbolic) or {@code <symbol> <operand>}
     * (word-form). The operand is treated as the "right side" of the operator so
     * right-associative same-precedence siblings ({@code --5}) render without
     * redundant parens.
     */
    private Optional<Rendered> renderPrefix(OperatorForm form, List<Object> targets, ParseParams params) {
        if (targets.size() != 1) return Optional.empty();
        String operand = renderOperand(targets.get(0), form.precedence(), form.associativity(), false, params);
        if (operand == null) return Optional.empty();
        String separator = isWordSymbol(form.symbol()) ? " " : "";
        return Optional.of(new Rendered(form.symbol() + separator + operand, form.precedence()));
    }

    /**
     * Postfix: {@code <operand><symbol>} (symbolic) or {@code <operand> <symbol>}
     * (word-form). The operand is treated as the "left side" of the operator so
     * left-associative same-precedence chains ({@code n!!}) render without
     * redundant parens.
     */
    private Optional<Rendered> renderPostfix(OperatorForm form, List<Object> targets, ParseParams params) {
        if (targets.size() != 1) return Optional.empty();
        String operand = renderOperand(targets.get(0), form.precedence(), form.associativity(), true, params);
        if (operand == null) return Optional.empty();
        String separator = isWordSymbol(form.symbol()) ? " " : "";
        return Optional.of(new Rendered(operand + separator + form.symbol(), form.precedence()));
    }

    // ==================================================================================
    // Operand resolution
    // ==================================================================================

    /**
     * Render one operand position. A {@link BindingTarget.RefTarget} pointing at a
     * stored frame body is fetched via the librarian and recursed via
     * {@link #renderOperation}, with parens decided by {@link #needsParens}. Other
     * targets (literals, IID refs, legacy inline FrameTarget) fall through to
     * {@link #renderOperandFallback}.
     */
    private String renderOperand(Object target, long outerPrecedence,
                                 ItemRef outerAssociativity, boolean isLeftOperand,
                                 ParseParams params) {
        if (!(target instanceof BindingTarget.RefTarget rt)) {
            return renderOperandFallback(target);
        }
        DatumRef cid = rt.asDatumId();
        Optional<Frame> innerFrame = librarian().fetchFrame(cid);
        if (innerFrame.isEmpty()) return renderOperandFallback(target);
        Body inner = innerFrame.get().body();
        if (!(inner.head() instanceof ItemRef ref)) return renderOperandFallback(target);

        List<Object> innerTargets = operandTargetsByRole(inner);
        Optional<Rendered> innerOpt = renderOperation(ref.iid(), innerTargets, params);
        if (innerOpt.isEmpty()) return renderOperandFallback(target);

        Rendered r = innerOpt.get();
        boolean wrap = needsParens(r.precedence(), outerPrecedence, outerAssociativity, isLeftOperand);
        return wrap ? "(" + r.text() + ")" : r.text();
    }

    /**
     * Bare-toString fallback for operand positions that couldn't be recursed into
     * (literals, ItemRefs, legacy inline FrameTargets).  Strings pass through
     * unquoted because they sit inside operator expressions where quotes would
     * be wrong ({@code 5 + "hello"} would already have been wrapped by the
     * caller if needed).  Distinct from {@link Language#renderLiteral(Object)},
     * which produces round-trippable, locale-formatted output.
     */
    private static String renderOperandFallback(Object target) {
        if (target == null) return null;
        if (target instanceof String s) return s;
        return target.toString();
    }

    // ==================================================================================
    // Operator-form discovery — extract symbol, precedence, associativity, fixity
    // from an item's operator-form Lexeme frame.
    // ==================================================================================

    /** Fixity sememes recognized as operator-form markers on a Lexeme's VALUE binding. */
    private static final java.util.Set<ItemRef> RECOGNIZED_FIXITIES = java.util.Set.of(
            ItemRef.iid(Operator.Infix.KEY),
            ItemRef.iid(Operator.Prefix.KEY),
            ItemRef.iid(Operator.Postfix.KEY));

    /**
     * Find the first endorsed operator-form Lexeme frame on the item and extract its
     * surface form. An "operator-form Lexeme" has a VALUE binding whose qualifiers
     * include a fixity sememe (Infix/Prefix/Postfix) — possibly alongside other
     * qualifiers (e.g., {@code OperatorNotation.KEY} as the language tag).
     */
    private static Optional<OperatorForm> lookupOperatorForm(Item item) {
        return item.endorsedFramesByPredicate(ItemRef.iid(LexicalVocabulary.Lexeme.KEY))
                .map(OperatorNotation::readOperatorForm)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Scan a Lexeme frame's bindings for a VALUE binding whose qualifiers include any
     * recognized fixity; on match, extract symbol + precedence + associativity. Other
     * qualifiers on the same binding (e.g., language tag) are ignored — we match by
     * presence of a fixity qualifier, not by exact compound-key equality.
     */
    private static Optional<OperatorForm> readOperatorForm(Frame lexemeFrame) {
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);
        return lexemeFrame.bindings()
                .filter(b -> valueRole.equals(b.role()))
                .map(b -> {
                    ItemRef fixity = fixityQualifier(b);
                    if (fixity == null) return null;
                    Optional<String> symbol = readTextLiteral(b.target());
                    if (symbol.isEmpty()) return null;
                    long precedence = readPrecedence(lexemeFrame).orElse(0L);
                    ItemRef associativity = readAssociativity(lexemeFrame).orElse(ItemRef.iid(Operator.Left.KEY));
                    return new OperatorForm(symbol.get(), precedence, associativity, fixity);
                })
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    /** Return the fixity sememe from a binding's qualifiers, or null if none present. */
    private static ItemRef fixityQualifier(Binding binding) {
        for (CompoundKey.Qualifier q : binding.qualifiers()) {
            if (q instanceof CompoundKey.Sememe s && RECOGNIZED_FIXITIES.contains(s.id())) {
                return s.id();
            }
        }
        return null;
    }

    /** Pull a text-literal value out of a binding target, if present. */
    private static Optional<String> readTextLiteral(Object target) {
        return target instanceof String s ? Optional.of(s) : Optional.empty();
    }

    /** Read the precedence integer from an operator-form Lexeme's ATTRIBUTE[Precedence] binding. */
    private static Optional<Long> readPrecedence(Frame lexemeFrame) {
        CompoundKey attributePrecedence = CompoundKey.of(
                ItemRef.iid(ThematicRole.Attribute.KEY), ItemRef.iid(Operator.Precedence.KEY));
        return lexemeFrame.binding(attributePrecedence)
                .map(Binding::target)
                .filter(t -> t instanceof Long)
                .map(t -> (Long) t);
    }

    /** Read the associativity sememe IID from an operator-form Lexeme's ATTRIBUTE[Associativity] binding. */
    private static Optional<ItemRef> readAssociativity(Frame lexemeFrame) {
        CompoundKey attributeAssociativity = CompoundKey.of(
                ItemRef.iid(ThematicRole.Attribute.KEY), ItemRef.iid(Operator.Associativity.KEY));
        return lexemeFrame.binding(attributeAssociativity)
                .map(Binding::target)
                .filter(t -> t instanceof ItemRef ir && !ir.isPinned())
                .map(t -> (ItemRef) t);
    }

    // ==================================================================================
    // Parenthesization rules
    // ==================================================================================

    /** Word symbol = leading code point is a letter (so {@code "not"} → space, {@code "-"} → no space). */
    private static boolean isWordSymbol(String symbol) {
        return !symbol.isEmpty() && Character.isLetter(symbol.codePointAt(0));
    }

    /**
     * Standard precedence-and-associativity parens rule:
     * <ul>
     *   <li>Inner precedence higher than outer → no parens (binds tighter, safe).</li>
     *   <li>Inner precedence lower than outer → parens (would otherwise rebind).</li>
     *   <li>Equal precedence: depends on which side and the outer's associativity.
     *       For left-associative, the right operand needs parens; for right-associative,
     *       the left operand needs parens; for non-associative, both sides need parens.</li>
     * </ul>
     */
    private static boolean needsParens(long innerPrecedence, long outerPrecedence,
                                       ItemRef outerAssociativity, boolean innerIsLeftOperand) {
        if (innerPrecedence > outerPrecedence) return false;
        if (innerPrecedence < outerPrecedence) return true;
        if (outerAssociativity.equals(ItemRef.iid(Operator.Left.KEY)))  return !innerIsLeftOperand;
        if (outerAssociativity.equals(ItemRef.iid(Operator.Right.KEY))) return  innerIsLeftOperand;
        return true;
    }

    // ==================================================================================
    // Operand ordering — extract operand targets in {left, right} positional order
    // from either a FrameMap or a Body. The shared collectByRole helper holds the
    // single canonical THEME → left, GOAL → right convention; the per-input methods
    // adapt their binding shape to that helper.
    // ==================================================================================

    /** Operands by role from a FrameMap's bindings. THEME first, then GOAL, then any others. */
    private static List<Object> operandTargetsByRole(FrameMap framemap) {
        return collectByRole(
                framemap.bindings(),
                bm -> bm.role().value() != null ? bm.role().value().iid() : null,
                bm -> bm.target().value());
    }

    /** Operands by role from a Body's bindings. Same THEME-first / GOAL-second ordering. */
    private static List<Object> operandTargetsByRole(Body body) {
        return collectByRole(body.bindings(), Binding::roleIid, Binding::target);
    }

    /**
     * Generic extractor: walk {@code items}, classify each by role into THEME / GOAL /
     * other buckets via the provided accessor functions, and return {@code [theme,
     * goal, others...]} with absent buckets skipped.
     *
     * <p>The hardcoded THEME-as-left / GOAL-as-right convention is centralized here.
     * A future refactor can replace this with operator-form Lexeme metadata that
     * declares the mapping per-predicate; until then, the rule is universal.
     */
    private static <T> List<Object> collectByRole(Iterable<T> items,
                                                  Function<T, ItemRef> roleOf,
                                                  Function<T, Object> targetOf) {
        Object theme = null;
        Object goal = null;
        List<Object> others = new ArrayList<>();
        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        ItemRef goalRole  = ItemRef.iid(ThematicRole.Goal.KEY);

        for (T item : items) {
            ItemRef role = roleOf.apply(item);
            Object target = targetOf.apply(item);
            if (role == null) {
                others.add(target);
            } else if (theme == null && themeRole.equals(role)) {
                theme = target;
            } else if (goal == null && goalRole.equals(role)) {
                goal = target;
            } else {
                others.add(target);
            }
        }

        List<Object> ordered = new ArrayList<>(2 + others.size());
        if (theme != null) ordered.add(theme);
        if (goal != null) ordered.add(goal);
        ordered.addAll(others);
        return ordered;
    }

    // ==================================================================================
    // Internal carriers
    // ==================================================================================

    /**
     * Internal carrier for a recursively-rendered sub-expression: the surface text
     * plus the precedence of the predicate that produced it (so the caller can
     * decide whether to wrap in parens).
     */
    private record Rendered(String text, long precedence) {}

    /**
     * Internal carrier for the surface metadata of an operator-form Lexeme:
     * its symbol text, precedence, associativity, and fixity (which drives whether
     * it renders as infix, prefix, or postfix).
     */
    private record OperatorForm(String symbol, long precedence,
                                ItemRef associativity, ItemRef fixity) {}
}
