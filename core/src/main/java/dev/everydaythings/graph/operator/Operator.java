package dev.everydaythings.graph.operator;


import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.text.AnchorTable.TokenAnchor;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import dev.everydaythings.graph.text.FrameMapTarget;
import dev.everydaythings.graph.text.GroupVocabulary;
import dev.everydaythings.graph.text.ParseContext;
import dev.everydaythings.graph.text.ParseEngine;
import dev.everydaythings.graph.text.ParseParams;
import dev.everydaythings.graph.text.TextSpan;
import dev.everydaythings.graph.text.TokenLattice;
import dev.everydaythings.graph.text.TokenLattice.TokenSpan;
import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base for notation-bearing predicates that evaluate to a value.
 *
 * <p>Operators (Add, Subtract, And, Equal, …) and arithmetic-style functions share
 * a uniform shape: they're predicates whose frame instances carry operand bindings
 * and whose runtime form computes a result from those operands. The split between
 * "operator" and "function" is purely notational — operators have a Fixity-qualified
 * lexeme (infix/prefix/postfix); functions have prefix-call form. Both subclass
 * {@code Operator} and implement {@link #execute}.
 *
 * <p><b>Metadata is data.</b> Concrete subclasses declare their notation and arity
 * through annotations:
 * <ul>
 *   <li>{@code @Seed.Item(bindings = {@Seed.Binding(role = Arity.KEY, integer = N)})}
 *       — semantic arity, lives on the predicate's manifest body.</li>
 *   <li>{@code @Seed.Frame(predicate = Lexeme.KEY, field = …, bindings = {…})} —
 *       the operator-form lexeme bundling the symbol, Fixity qualifier, and
 *       Precedence/Associativity ATTRIBUTE bindings.</li>
 * </ul>
 *
 * <p><b>Code is code.</b> Each subclass implements {@link #execute} with the actual
 * computation. The interface is uniformly n-ary — implementations validate operand
 * count and types. Future runtimes (GraalVM polyglot, etc.) may carry alternate
 * implementations of the same contract through IMPLEMENTS frames.
 */
@Seed.Item(key = Operator.KEY, head = CoreVocabulary.Predicate.KEY)
public abstract class Operator extends Item {

    /** Canonical key for the operator concept itself — the archetype of all operators. */
    public static final String KEY = "cg.sememe:operator";

    /** Deterministic IID for the operator concept. */

    protected Operator(ItemRef iid) {
        super(iid);
    }

    protected Operator(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * Evaluate this operator with the given operands. Implementations must validate
     * operand count against their declared {@link Arity} and coerce or reject
     * operand types as appropriate.
     */
    public abstract Object execute(Object... operands);

    // ==================================================================================
    // Generic parse contribution — inherited by all concrete operators.
    // ==================================================================================
    //
    // The Operator sememe's contribution to a parse round: find this operator's anchor
    // token, read its own fixity from its endorsed operator-form Lexeme, and claim
    // neighboring tokens as operand bindings.
    //
    // Concrete operators (Add, Subtract, …) inherit this without override. Per-operator
    // customization is possible by overriding parse(ctx), but should be rare — the
    // metadata-driven default handles the entire fixity family generically.

    @Override
    public FrameMap parse(ParseContext ctx) {
        Optional<TokenAnchor> selfAnchor = ctx.anchors().tokenAnchors().stream()
                .filter(ta -> ta.participant().iid().equals(this.iid()))
                .findFirst();
        if (selfAnchor.isEmpty() || selfAnchor.get().spans().isEmpty()) {
            return FrameMap.empty();
        }

        Optional<OperatorForm> formOpt = ownOperatorForm();
        if (formOpt.isEmpty()) return FrameMap.empty();
        OperatorForm form = formOpt.get();

        // Collect all anchor token-indices of this operator at the OUTER level —
        // anchors that fall inside a paren group are skipped (the recursive parse
        // call from {@link #parenGroupOperand} handles them in their own scope).
        List<Integer> anchorIndices = new ArrayList<>();
        for (TextSpan span : selfAnchor.get().spans()) {
            int i = indexOfTokenSpan(ctx.tokens(), span);
            if (i < 0) continue;
            if (isInsideParens(ctx.tokens(), i)) continue;
            anchorIndices.add(i);
        }
        if (anchorIndices.isEmpty()) return FrameMap.empty();
        Collections.sort(anchorIndices);

        // Chain handling for multi-anchor infix operators with associativity. For
        // {@code 5 - 3 - 2}, Subtract appears twice; left-associativity means the
        // structure is {@code Subtract{ THEME=Subtract{5,3}, GOAL=2 }} — outer is
        // the rightmost anchor, inner is the leftmost. Right-associative operators
        // mirror it (outer = leftmost, inner = rightmost). For non-associative or
        // unary fixities (prefix/postfix), only the first anchor is handled — chains
        // there are either ill-formed (non-assoc) or unusual (unary).
        if (ItemRef.iid(Infix.KEY).equals(form.fixity())
                && anchorIndices.size() > 1
                && (ItemRef.iid(Left.KEY).equals(form.associativity())
                        || ItemRef.iid(Right.KEY).equals(form.associativity()))) {
            return buildChainFrame(anchorIndices, ctx, form);
        }

        return buildAnchorFrame(anchorIndices.get(0), ctx, form);
    }

    /**
     * Build a single-anchor FrameMap for this operator at the given token index,
     * resolving its operands from context (paren-aware via the resolveOperand helpers).
     */
    private FrameMap buildAnchorFrame(int anchorIdx, ParseContext ctx, OperatorForm form) {
        Operand left = resolveLeftOperand(ctx, anchorIdx);
        Operand right = resolveRightOperand(ctx, anchorIdx);
        return buildFrameWithOperands(anchorIdx, left, right, ctx, form);
    }

    /**
     * Build a single-anchor FrameMap with operands provided externally — used by
     * {@link #buildChainFrame} to splice prior chain-segments in as operands.
     */
    private FrameMap buildFrameWithOperands(int anchorIdx, Operand left, Operand right,
                                            ParseContext ctx, OperatorForm form) {
        TextSpan anchorSpan = ctx.tokens().get(anchorIdx).span();

        double fitness = contextFitness(form.fixity(), left, right);
        if (fitness <= 0.0) return FrameMap.empty();

        double predConf = pickConfidence(fitness, form.precedence());
        double bindConf = predConf * 0.95;

        BigDecimal predicateConfidence = new BigDecimal(formatConfidence(predConf));
        BigDecimal bindingConfidence = new BigDecimal(formatConfidence(bindConf));

        List<BindingMap> bindings = new ArrayList<>();
        if (ItemRef.iid(Infix.KEY).equals(form.fixity())) {
            BindingMap themeBinding = makeBinding(left, ItemRef.iid(ThematicRole.Theme.KEY), bindingConfidence);
            if (themeBinding != null) bindings.add(themeBinding);
            BindingMap goalBinding = makeBinding(right, ItemRef.iid(ThematicRole.Goal.KEY), bindingConfidence);
            if (goalBinding != null) bindings.add(goalBinding);
        } else if (ItemRef.iid(Prefix.KEY).equals(form.fixity())) {
            BindingMap operandBinding = makeBinding(right, ItemRef.iid(ThematicRole.Theme.KEY), bindingConfidence);
            if (operandBinding != null) bindings.add(operandBinding);
        } else if (ItemRef.iid(Postfix.KEY).equals(form.fixity())) {
            BindingMap operandBinding = makeBinding(left, ItemRef.iid(ThematicRole.Theme.KEY), bindingConfidence);
            if (operandBinding != null) bindings.add(operandBinding);
        } else {
            return FrameMap.empty();
        }

        return new FrameMap(
                null,
                new Part<>(ItemRef.of(this.iid()), predicateConfidence, List.of(anchorSpan)),
                bindings,
                List.of());
    }

    /**
     * Build a multi-anchor chain frame for left- or right-associative infix
     * operators. For {@code 5 - 3 - 2} (left-associative), produces
     * {@code Subtract{ THEME=FrameMapTarget(Subtract{5,3}), GOAL=2 }} —
     * accumulates left-to-right, each step wrapping the prior chain segment as
     * the THEME of the next anchor. Right-associative mirrors right-to-left.
     */
    private FrameMap buildChainFrame(List<Integer> anchorIndices, ParseContext ctx, OperatorForm form) {
        boolean leftAssoc = ItemRef.iid(Left.KEY).equals(form.associativity());

        if (leftAssoc) {
            // Start with the leftmost anchor's natural frame (its own operands).
            FrameMap current = buildAnchorFrame(anchorIndices.get(0), ctx, form);
            if (current.predicate() == null) return current;
            // Each subsequent anchor wraps `current` as its THEME.
            for (int i = 1; i < anchorIndices.size(); i++) {
                int idx = anchorIndices.get(i);
                Operand right = resolveRightOperand(ctx, idx);
                Operand left = new Operand(new FrameMapTarget(current), claimSpan(current));
                current = buildFrameWithOperands(idx, left, right, ctx, form);
                if (current.predicate() == null) return current;
            }
            return current;
        } else {
            // Right-associative: start with the rightmost anchor; each earlier
            // anchor wraps `current` as its GOAL.
            FrameMap current = buildAnchorFrame(anchorIndices.get(anchorIndices.size() - 1), ctx, form);
            if (current.predicate() == null) return current;
            for (int i = anchorIndices.size() - 2; i >= 0; i--) {
                int idx = anchorIndices.get(i);
                Operand left = resolveLeftOperand(ctx, idx);
                Operand right = new Operand(new FrameMapTarget(current), claimSpan(current));
                current = buildFrameWithOperands(idx, left, right, ctx, form);
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
     * Score how well this operator's fixity fits the surrounding context. The
     * score multiplies into predicate confidence below — so an infix operator with
     * no left operand (a poor fit for infix) yields a weak bid that lets a
     * better-fitting prefix operator win the symbol-collision tie. Result in [0, 1].
     */
    private static double contextFitness(ItemRef fixity, Operand left, Operand right) {
        if (ItemRef.iid(Infix.KEY).equals(fixity)) {
            // Infix wants both operands. Both present = full fit; one missing = half.
            int neighbors = (left != null ? 1 : 0) + (right != null ? 1 : 0);
            return neighbors / 2.0;
        }
        if (ItemRef.iid(Prefix.KEY).equals(fixity)) {
            // Prefix needs a right operand. If a left operand is also present, the
            // input could equally be infix — back off slightly so a real infix
            // operator (better-fit) outranks us at the same precedence.
            if (right == null) return 0.0;
            return left == null ? 1.0 : 0.7;
        }
        if (ItemRef.iid(Postfix.KEY).equals(fixity)) {
            // Mirror of prefix.
            if (left == null) return 0.0;
            return right == null ? 1.0 : 0.7;
        }
        return 0.0;
    }

    /**
     * Combine context fitness and operator precedence into a confidence score in
     * {@code [0, 0.95]}. Lower-precedence operators get a higher base confidence —
     * they form the OUTER predicate of expressions like {@code 5 + 3 * 2}, where
     * the addition (precedence 10) wraps the multiplication (precedence 20). The
     * fitness factor multiplies in: a poor structural fit suppresses the bid.
     */
    private static double pickConfidence(double fitness, long precedence) {
        if (fitness <= 0.0) return 0.0;
        // Map precedence onto [0.5, 0.95]: precedence -20 → 0.95, precedence 50 → 0.5.
        double clamped = Math.max(-20.0, Math.min(50.0, (double) precedence));
        double precFactor = 0.95 - 0.45 * ((clamped + 20.0) / 70.0);
        return precFactor * fitness;
    }

    /** Format a [0, 1] confidence as a fixed-precision BigDecimal-parseable string. */
    private static String formatConfidence(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f",
                Math.max(0.0, Math.min(0.9999, value)));
    }

    /**
     * Find this operator's own endorsed operator-form Lexeme and extract its
     * surface metadata: fixity and precedence. The "operator-form" is the Lexeme
     * whose VALUE binding carries an Infix / Prefix / Postfix qualifier; precedence
     * comes from an ATTRIBUTE[Precedence] binding on the same frame (default 0).
     * Returns empty if no operator-form lexeme is endorsed.
     */
    private Optional<OperatorForm> ownOperatorForm() {
        return endorsedFramesByPredicate(ItemRef.iid(LexicalVocabulary.Lexeme.KEY))
                .map(Operator::readOperatorForm)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /** Fixity sememes recognized as operator-form markers on a Lexeme's VALUE binding. */
    private static final java.util.Set<ItemRef> RECOGNIZED_FIXITIES = java.util.Set.of(
            ItemRef.iid(Infix.KEY),
            ItemRef.iid(Prefix.KEY),
            ItemRef.iid(Postfix.KEY));

    /**
     * Scan a Lexeme frame's bindings for a VALUE binding whose qualifiers include any
     * recognized fixity sememe; on match, extract fixity + precedence + associativity.
     * Other qualifiers on the same binding (e.g., {@code OperatorNotation.KEY} as the
     * language tag) are ignored — we match by presence of a fixity qualifier, not by
     * exact compound-key equality.
     */
    private static Optional<OperatorForm> readOperatorForm(Frame lexemeFrame) {
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);
        return lexemeFrame.bindings()
                .filter(b -> valueRole.equals(b.role()))
                .map(b -> {
                    ItemRef fixity = fixityQualifier(b);
                    if (fixity == null) return null;
                    long precedence = readPrecedence(lexemeFrame).orElse(0L);
                    ItemRef associativity = readAssociativity(lexemeFrame).orElse(ItemRef.iid(Left.KEY));
                    return new OperatorForm(fixity, precedence, associativity);
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

    /** Read the integer precedence from an operator-form Lexeme's ATTRIBUTE[Precedence] binding. */
    private static Optional<Long> readPrecedence(Frame lexemeFrame) {
        CompoundKey attributePrecedence = CompoundKey.of(
                ItemRef.iid(ThematicRole.Attribute.KEY), ItemRef.iid(Precedence.KEY));
        return lexemeFrame.binding(attributePrecedence)
                .map(Binding::target)
                .filter(t -> t instanceof Long)
                .map(t -> (Long) t);
    }

    /** Read the associativity sememe IID from an operator-form Lexeme's ATTRIBUTE[Associativity] binding. */
    private static Optional<ItemRef> readAssociativity(Frame lexemeFrame) {
        CompoundKey attributeAssociativity = CompoundKey.of(
                ItemRef.iid(ThematicRole.Attribute.KEY), ItemRef.iid(Associativity.KEY));
        return lexemeFrame.binding(attributeAssociativity)
                .map(Binding::target)
                .filter(t -> t instanceof ItemRef ir && !ir.isPinned())
                .map(t -> (ItemRef) t);
    }

    private static int indexOfTokenSpan(List<TokenSpan> tokens, TextSpan span) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).span().equals(span)) return i;
        }
        return -1;
    }

    /** Build a role binding from a resolved operand, or null if no usable operand. */
    private static BindingMap makeBinding(Operand op, ItemRef role, BigDecimal confidence) {
        if (op == null) return null;
        return new BindingMap(
                new Part<>(ItemRef.of(role), confidence, List.of()),
                List.of(),
                new Part<>(op.target(), confidence, op.spans()));
    }

    // ==================================================================================
    // Paren-aware operand resolution
    // ==================================================================================
    //
    // When the immediate left or right token is an OpenGroup or CloseGroup marker,
    // walk to the matching paren and recursively parse the bracketed text. The
    // result becomes a FrameMapTarget operand whose span covers the whole paren
    // group — so the merger naturally treats parens as wider claims that win
    // outer-predicate position over inner operators.

    /** Either a literal/ref operand from a single token, or a parens-wrapped sub-FrameMap. */
    private record Operand(Object target, List<TextSpan> spans) {}

    private static Operand resolveLeftOperand(ParseContext ctx, int anchorIdx) {
        if (anchorIdx <= 0) return null;
        TokenSpan immediate = ctx.tokens().get(anchorIdx - 1);
        if (isCloseGroup(immediate)) {
            int openIdx = findMatchingOpen(ctx.tokens(), anchorIdx - 1);
            if (openIdx < 0) return null;
            TokenSpan openTok = ctx.tokens().get(openIdx);
            return parenGroupOperand(ctx, openTok, immediate);
        }
        Object target = tokenTarget(immediate);
        if (target == null) return null;
        return new Operand(target, List.of(immediate.span()));
    }

    private static Operand resolveRightOperand(ParseContext ctx, int anchorIdx) {
        if (anchorIdx >= ctx.tokens().size() - 1) return null;
        TokenSpan immediate = ctx.tokens().get(anchorIdx + 1);
        if (isOpenGroup(immediate)) {
            int closeIdx = findMatchingClose(ctx.tokens(), anchorIdx + 1);
            if (closeIdx < 0) return null;
            TokenSpan closeTok = ctx.tokens().get(closeIdx);
            return parenGroupOperand(ctx, immediate, closeTok);
        }
        Object target = tokenTarget(immediate);
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

    private static boolean isOpenGroup(TokenSpan token) {
        if (token == null) return false;
        return token.postings().stream()
                .anyMatch(p -> ItemRef.iid(GroupVocabulary.OpenGroup.KEY).equals(p.target()));
    }

    private static boolean isCloseGroup(TokenSpan token) {
        if (token == null) return false;
        return token.postings().stream()
                .anyMatch(p -> ItemRef.iid(GroupVocabulary.CloseGroup.KEY).equals(p.target()));
    }

    /**
     * True if the token at {@code idx} sits inside an unmatched OpenGroup span at
     * the outer level — i.e., walking from the start of the token list, the
     * paren depth at position {@code idx} is positive.
     */
    private static boolean isInsideParens(List<TokenSpan> tokens, int idx) {
        int depth = 0;
        for (int i = 0; i < idx; i++) {
            TokenSpan t = tokens.get(i);
            if (isOpenGroup(t)) depth++;
            else if (isCloseGroup(t)) depth--;
        }
        return depth > 0;
    }

    /** Walk back from {@code closeIdx} to the matching OpenGroup, balancing nesting. */
    private static int findMatchingOpen(List<TokenSpan> tokens, int closeIdx) {
        int depth = 1;
        for (int i = closeIdx - 1; i >= 0; i--) {
            TokenSpan t = tokens.get(i);
            if (isCloseGroup(t)) depth++;
            else if (isOpenGroup(t)) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Walk forward from {@code openIdx} to the matching CloseGroup, balancing nesting. */
    private static int findMatchingClose(List<TokenSpan> tokens, int openIdx) {
        int depth = 1;
        for (int i = openIdx + 1; i < tokens.size(); i++) {
            TokenSpan t = tokens.get(i);
            if (isOpenGroup(t)) depth++;
            else if (isCloseGroup(t)) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Convert a token to a BindingTarget. Integer literals → Literal.ofInteger; other → IID ref via posting. */
    private static Object tokenTarget(TokenSpan token) {
        if (token.kind() == TokenLattice.Kind.LITERAL) {
            try {
                return (long) (Long.parseLong(token.surfaceText().trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (!token.postings().isEmpty()) {
            return token.postings().get(0).target();
        }
        return null;
    }

    /** Internal carrier for parsed operator-form metadata: fixity, precedence, associativity. */
    private record OperatorForm(ItemRef fixity, long precedence, ItemRef associativity) {}

    // ==================================================================================
    // Operator-adjacent meta sememes — universal notation features riding on operator
    // declarations.  Each is a pure-data sememe used as a qualifier (Fixity / Precedence
    // / Associativity) or as a manifest-body binding role (Arity), plus the value
    // sememes those qualifiers point at (Infix / Prefix / Postfix / Mixfix / Circumfix;
    // Left / Right / NonAssociative).
    //
    // These are not operators themselves — they're the metadata vocabulary every
    // operator declaration uses to describe its surface form.  They live here as
    // inner classes so the operator and the words for talking about operators
    // travel together.
    //
    // Future notation languages (OperatorNotation, AssignmentNotation, FunctionNotation,
    // ...) are coherent syntaxes that compose these meta sememes with grouping and
    // other primitives.  They'll live in their own files when they arrive.
    // ==================================================================================

    /**
     * The fixity feature — qualifier identifying where an operator's symbol sits
     * relative to its operands.  As a qualifier on the symbol Lexeme's value-binding
     * it picks one of {@link Infix}, {@link Prefix}, {@link Postfix}, {@link Mixfix},
     * {@link Circumfix}.
     */
    @Seed.Item(key = Fixity.KEY)
    public static final class Fixity {
        public static final String KEY = "cg.notation:fixity";
        private Fixity() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the syntactic position of an operator: infix, prefix, postfix, mixfix, circumfix";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "fixity";
    }

    /**
     * The precedence feature — qualifier identifying an integer ATTRIBUTE binding
     * carrying an operator's binding tightness. Higher precedence binds tighter
     * (multiplication > addition; exponentiation > multiplication).
     */
    @Seed.Item(key = Precedence.KEY)
    public static final class Precedence {
        public static final String KEY = "cg.notation:precedence";
        private Precedence() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the binding tightness of an operator; higher binds tighter";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "precedence";
    }

    /**
     * The associativity feature — qualifier identifying an ATTRIBUTE binding whose
     * target is one of {@link Left}, {@link Right}, {@link NonAssociative}.
     * Determines how operators of equal precedence group: {@code 5-3-1} parses as
     * {@code (5-3)-1} under left-associativity, {@code 5-(3-1)} under right.
     */
    @Seed.Item(key = Associativity.KEY)
    public static final class Associativity {
        public static final String KEY = "cg.notation:associativity";
        private Associativity() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how operators of equal precedence group: left, right, or none";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "associativity";
    }

    /**
     * The arity feature — number of operands a predicate accepts.  Applied as a
     * binding directly on the predicate's manifest body via
     * {@code @Seed.Item.bindings}:
     *
     * <pre>{@code
     * @Seed.Item(key = Add.KEY,
     *            head = Item.Predicate.KEY,
     *            bindings = {@Seed.Binding(role = Arity.KEY, integer = 2)})
     * }</pre>
     *
     * <p>Unlike fixity/precedence/associativity (which describe a particular surface
     * lexeme), arity is a semantic fact about the predicate itself.
     */
    @Seed.Item(key = Arity.KEY)
    public static final class Arity {
        public static final String KEY = "cg.notation:arity";
        private Arity() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the number of operands a predicate accepts";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "arity";
    }

    // ----- Fixity values --------------------------------------------------------------

    /** Infix — operator appears between operands ({@code 5 + 3}). */
    @Seed.Item(key = Infix.KEY)
    public static final class Infix {
        public static final String KEY = "cg.fixity:infix";
        private Infix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator appears between operands";
    }

    /** Prefix — operator appears before operand ({@code -5}, {@code !x}). */
    @Seed.Item(key = Prefix.KEY)
    public static final class Prefix {
        public static final String KEY = "cg.fixity:prefix";
        private Prefix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator appears before its operand";
    }

    /** Postfix — operator appears after operand ({@code n!}, {@code x++}). */
    @Seed.Item(key = Postfix.KEY)
    public static final class Postfix {
        public static final String KEY = "cg.fixity:postfix";
        private Postfix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator appears after its operand";
    }

    /**
     * Mixfix — operator interleaves with operands at multiple positions
     * ({@code if … then … else …}; {@code a ? b : c}).  Placeholder; full mixfix
     * support requires a position template that's not yet specified.
     */
    @Seed.Item(key = Mixfix.KEY)
    public static final class Mixfix {
        public static final String KEY = "cg.fixity:mixfix";
        private Mixfix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator interleaves with operands at multiple positions (e.g. if/then/else, ternary)";
    }

    /**
     * Circumfix — operator brackets its operand with matching tokens on both sides
     * ({@code |x|}, {@code ⌊x⌋}, {@code (…)}).  Placeholder.
     */
    @Seed.Item(key = Circumfix.KEY)
    public static final class Circumfix {
        public static final String KEY = "cg.fixity:circumfix";
        private Circumfix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator brackets its operand with matching tokens on both sides (e.g. |x|, ⌊x⌋)";
    }

    // ----- Associativity values -------------------------------------------------------

    /** Left-associative: {@code 5-3-1} parses as {@code (5-3)-1}. Most arithmetic operators. */
    @Seed.Item(key = Left.KEY)
    public static final class Left {
        public static final String KEY = "cg.associativity:left";
        private Left() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "left-to-right grouping for equal-precedence operators";
    }

    /** Right-associative: {@code 2^3^2} parses as {@code 2^(3^2)}. Exponentiation, assignment. */
    @Seed.Item(key = Right.KEY)
    public static final class Right {
        public static final String KEY = "cg.associativity:right";
        private Right() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "right-to-left grouping for equal-precedence operators";
    }

    /** Non-associative: chaining requires explicit parentheses. */
    @Seed.Item(key = NonAssociative.KEY)
    public static final class NonAssociative {
        public static final String KEY = "cg.associativity:none";
        private NonAssociative() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "no grouping for equal-precedence operators; require parentheses";
    }
}
