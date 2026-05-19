package dev.everydaythings.graph.operator;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.ParseParams;

import java.util.ArrayList;
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

    /**
     * Hydration constructor used by SeedProcessor.  The IID is fixed by the
     * @Embodies binding, but the (ItemRef, Librarian) signature is the contract
     * the seed pipeline calls.
     */
    public OperatorNotation(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
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

        Optional<Operator.OperatorForm> formOpt = lookupOperatorForm(predItem.get());
        if (formOpt.isEmpty()) return Optional.empty();
        Operator.OperatorForm form = formOpt.get();
        // Render requires a surface symbol; parse-side OperatorForms may not have one.
        if (form.symbol() == null) return Optional.empty();

        ItemRef fixity = form.fixity();
        if (ItemRef.iid(Operator.Infix.KEY).equals(fixity))   return renderInfix(form, targets, params);
        if (ItemRef.iid(Operator.Prefix.KEY).equals(fixity))  return renderPrefix(form, targets, params);
        if (ItemRef.iid(Operator.Postfix.KEY).equals(fixity)) return renderPostfix(form, targets, params);
        return Optional.empty();
    }

    /** Infix: {@code <left> <symbol> <right>}. Requires two operand targets. */
    private Optional<Rendered> renderInfix(Operator.OperatorForm form, List<Object> targets, ParseParams params) {
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
    private Optional<Rendered> renderPrefix(Operator.OperatorForm form, List<Object> targets, ParseParams params) {
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
    private Optional<Rendered> renderPostfix(Operator.OperatorForm form, List<Object> targets, ParseParams params) {
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
     * {@link #renderLiteral}.
     */
    private String renderOperand(Object target, long outerPrecedence,
                                 ItemRef outerAssociativity, boolean isLeftOperand,
                                 ParseParams params) {
        if (!(target instanceof BindingTarget.RefTarget rt)) {
            return renderLiteral(target);
        }
        DatumRef cid = rt.asDatumId();
        Optional<Frame> innerFrame = librarian().fetchFrame(cid);
        if (innerFrame.isEmpty()) return renderLiteral(target);
        Body inner = innerFrame.get().body();
        if (!(inner.head() instanceof ItemRef ref)) return renderLiteral(target);

        List<Object> innerTargets = operandTargetsByRole(inner);
        Optional<Rendered> innerOpt = renderOperation(ref.iid(), innerTargets, params);
        if (innerOpt.isEmpty()) return renderLiteral(target);

        Rendered r = innerOpt.get();
        boolean wrap = needsParens(r.precedence(), outerPrecedence, outerAssociativity, isLeftOperand);
        return wrap ? "(" + r.text() + ")" : r.text();
    }

    /**
     * Render a literal binding target as text. Numbers format via {@code toString},
     * text passes through; other targets fall back to {@code toString()}.
     */
    private static String renderLiteral(Object target) {
        if (target == null) return null;
        if (target instanceof String s) return s;
        return target.toString();
    }

    // ==================================================================================
    // Operator-form discovery — find the operator-form Lexeme on an item.  The
    // extraction itself (symbol, precedence, associativity, fixity) lives on
    // Operator since both parse-side and render-side use the same logic.
    // ==================================================================================

    /**
     * Find the first endorsed operator-form Lexeme frame on the item and extract its
     * surface form. An "operator-form Lexeme" has a VALUE binding whose qualifiers
     * include a fixity sememe (Infix/Prefix/Postfix) — possibly alongside other
     * qualifiers (e.g., {@code OperatorNotation.KEY} as the language tag).
     */
    private static Optional<Operator.OperatorForm> lookupOperatorForm(Item item) {
        return item.endorsedFramesByPredicate(ItemRef.iid(LexicalVocabulary.Lexeme.KEY))
                .map(Operator::readOperatorForm)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
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
}
