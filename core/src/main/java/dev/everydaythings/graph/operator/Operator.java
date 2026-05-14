package dev.everydaythings.graph.operator;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.LexicalVocabulary;
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
    public static final ItemRef IID = ItemRef.fromString(KEY);

    protected Operator(ItemRef iid) {
        super(iid);
    }

    protected Operator(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * Evaluate this operator with the given operands. Implementations must validate
     * operand count against their declared {@link NotationVocabulary.Arity} and
     * coerce or reject operand types as appropriate.
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
        if (NotationVocabulary.Infix.IID.equals(form.fixity())
                && anchorIndices.size() > 1
                && (NotationVocabulary.Left.IID.equals(form.associativity())
                        || NotationVocabulary.Right.IID.equals(form.associativity()))) {
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
        if (NotationVocabulary.Infix.IID.equals(form.fixity())) {
            BindingMap themeBinding = makeBinding(left, ThematicRole.Theme.IID, bindingConfidence);
            if (themeBinding != null) bindings.add(themeBinding);
            BindingMap goalBinding = makeBinding(right, ThematicRole.Goal.IID, bindingConfidence);
            if (goalBinding != null) bindings.add(goalBinding);
        } else if (NotationVocabulary.Prefix.IID.equals(form.fixity())) {
            BindingMap operandBinding = makeBinding(right, ThematicRole.Theme.IID, bindingConfidence);
            if (operandBinding != null) bindings.add(operandBinding);
        } else if (NotationVocabulary.Postfix.IID.equals(form.fixity())) {
            BindingMap operandBinding = makeBinding(left, ThematicRole.Theme.IID, bindingConfidence);
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
        boolean leftAssoc = NotationVocabulary.Left.IID.equals(form.associativity());

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
        if (NotationVocabulary.Infix.IID.equals(fixity)) {
            // Infix wants both operands. Both present = full fit; one missing = half.
            int neighbors = (left != null ? 1 : 0) + (right != null ? 1 : 0);
            return neighbors / 2.0;
        }
        if (NotationVocabulary.Prefix.IID.equals(fixity)) {
            // Prefix needs a right operand. If a left operand is also present, the
            // input could equally be infix — back off slightly so a real infix
            // operator (better-fit) outranks us at the same precedence.
            if (right == null) return 0.0;
            return left == null ? 1.0 : 0.7;
        }
        if (NotationVocabulary.Postfix.IID.equals(fixity)) {
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
        return endorsedFramesByPredicate(LexicalVocabulary.Lexeme.IID)
                .map(Operator::readOperatorForm)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private static Optional<OperatorForm> readOperatorForm(Frame lexemeFrame) {
        for (ItemRef fixity : List.of(NotationVocabulary.Infix.IID,
                NotationVocabulary.Prefix.IID,
                NotationVocabulary.Postfix.IID)) {
            CompoundKey valueWithFixity = CompoundKey.of(ThematicRole.Value.IID, fixity);
            if (lexemeFrame.binding(valueWithFixity).isPresent()) {
                long precedence = readPrecedence(lexemeFrame).orElse(0L);
                ItemRef associativity = readAssociativity(lexemeFrame)
                        .orElse(NotationVocabulary.Left.IID);
                return Optional.of(new OperatorForm(fixity, precedence, associativity));
            }
        }
        return Optional.empty();
    }

    /** Read the integer precedence from an operator-form Lexeme's ATTRIBUTE[Precedence] binding. */
    private static Optional<Long> readPrecedence(Frame lexemeFrame) {
        CompoundKey attributePrecedence = CompoundKey.of(
                ThematicRole.Attribute.IID, NotationVocabulary.Precedence.IID);
        return lexemeFrame.binding(attributePrecedence)
                .map(Binding::target)
                .filter(t -> t instanceof Long)
                .map(t -> (Long) t);
    }

    /** Read the associativity sememe IID from an operator-form Lexeme's ATTRIBUTE[Associativity] binding. */
    private static Optional<ItemRef> readAssociativity(Frame lexemeFrame) {
        CompoundKey attributeAssociativity = CompoundKey.of(
                ThematicRole.Attribute.IID, NotationVocabulary.Associativity.IID);
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
                .anyMatch(p -> GroupVocabulary.OpenGroup.IID.equals(p.target()));
    }

    private static boolean isCloseGroup(TokenSpan token) {
        if (token == null) return false;
        return token.postings().stream()
                .anyMatch(p -> GroupVocabulary.CloseGroup.IID.equals(p.target()));
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
}
