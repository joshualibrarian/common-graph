package dev.everydaythings.graph.linguistics;

import com.ibm.icu.util.ULocale;
import dev.everydaythings.graph.Implements;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.ParseParams;
import dev.everydaythings.graph.operator.NotationVocabulary;

import java.util.List;
import java.util.Optional;

/**
 * Language items — both the meta-sememe identifying a language scope and the Java
 * base class for concrete language implementations (English, German, chess-notation,
 * sub-Languages like English-US/English-GB, etc.).
 *
 * <p>The outer class itself is the language meta-sememe ({@code cg.sememe:language}),
 * usable as a qualifier on EXPECTS bindings declaring "the target is a language."
 * Inner static classes (English, etc.) are seed declarations for specific languages.
 *
 * <p>Concrete Language subclasses live in their own modules (e.g., the {@code :english}
 * module hosts the English class with its grammar rules and irregular morphology).
 * Each subclass overrides {@link #locale()}, {@link #parse(dev.everydaythings.graph.text.ParseContext)},
 * and {@link #render(FrameMap, ParseParams)} as needed. Languages typically embody
 * singleton items via {@code @Embodies}.
 *
 * <p>Canonical-key prefix for specific languages: {@code cg.lang:} followed by the
 * ISO 639-3 three-letter code (e.g., {@code cg.lang:eng} for English). Sub-Language
 * codes follow BCP-47 ({@code cg.lang:en-US}, {@code cg.lang:de-CH}, etc.).
 */
@Seed.Item(key = Language.KEY)
@Implements(Language.KEY)
public class Language extends Item {

    /** Canonical key for the language meta-sememe. */
    public static final String KEY = "cg.sememe:language";

    /** The deterministic IID for the language meta-sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    /** Seed/siloed constructor (no librarian). */
    public Language(ItemID iid) {
        super(iid);
    }

    /** Runtime constructor — bound to a librarian. */
    public Language(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * The locale for this Language, used by ICU services (number formatting, date
     * formatting, plural rules, grapheme/word break iteration, collation).
     *
     * <p>Default returns {@link ULocale#ROOT} — concrete Languages and sub-Languages
     * override with their specific locale (e.g., English-US returns {@code "en-US"},
     * German-CH returns {@code "de-CH"}).
     */
    public ULocale locale() {
        return ULocale.ROOT;
    }

    /**
     * Render a frame to text.
     *
     * <p>The base implementation handles universal pre-linguistic notation. If the
     * framemap's predicate has an endorsed operator-form Lexeme frame, output is
     * assembled by fixity:
     * <ul>
     *   <li><b>Infix</b> — {@code <left> <symbol> <right>} (requires two bindings).</li>
     *   <li><b>Prefix</b> — {@code <symbol><operand>} for symbolic operators
     *       ({@code -5}, {@code !x}); {@code <symbol> <operand>} for word operators
     *       (one binding).</li>
     * </ul>
     *
     * <p>Sub-frames in operand position are rendered recursively, with parentheses
     * inserted whenever the inner predicate's precedence is lower than the outer's,
     * or equal at a position where the outer's associativity demands grouping.
     *
     * <p>Concrete Languages (English, German, etc.) override to add prose forms for
     * predicates they have lexemes for; they call {@code super.render(...)} as a
     * fallback for predicates they don't have prose for, so math notation works in any
     * Language by default. Sub-Languages further override for regional variations.
     *
     * <p>v1 limitations: postfix/mixfix/circumfix not yet handled; prefix-call form
     * (functions like {@code sqrt(5)}) not yet handled; leaf-target rendering for
     * refs is a placeholder; spans on the output FrameMap are not populated.
     *
     * @param framemap the frame to render
     * @param params   render parameters (mode, verbosity, register, etc.)
     * @return a FrameMap with text populated; unchanged if rendering rules don't apply
     */
    public FrameMap render(FrameMap framemap, ParseParams params) {
        if (framemap == null || framemap.predicate() == null
                || framemap.predicate().value() == null
                || librarian() == null) {
            return framemap;
        }

        List<BindingTarget> targets = framemap.bindings().stream()
                .map(b -> b.target().value())
                .toList();
        Optional<Rendered> result = renderOperation(
                framemap.predicate().value().iid(), targets, params);
        if (result.isEmpty()) return framemap;
        return framemap.withText(result.get().text);
    }

    /**
     * Internal operator render. Looks up the predicate's operator-form Lexeme frame,
     * dispatches by fixity (infix → binary, prefix → unary), recursively renders
     * each operand, and assembles the surface form. Returns the text plus the
     * predicate's own precedence so a caller can decide whether to wrap this
     * rendering in parens when it appears inside a larger expression.
     */
    private Optional<Rendered> renderOperation(ItemID predicateIid,
                                               List<BindingTarget> targets,
                                               ParseParams params) {
        Optional<Item> predItem = librarian().fetchItem(predicateIid);
        if (predItem.isEmpty()) return Optional.empty();

        Optional<OperatorForm> formOpt = lookupOperatorForm(predItem.get());
        if (formOpt.isEmpty()) return Optional.empty();
        OperatorForm form = formOpt.get();

        if (NotationVocabulary.Infix.IID.equals(form.fixity)) {
            if (targets.size() != 2) return Optional.empty();
            String left = renderOperand(targets.get(0), form.precedence, form.associativity, true, params);
            String right = renderOperand(targets.get(1), form.precedence, form.associativity, false, params);
            if (left == null || right == null) return Optional.empty();
            return Optional.of(new Rendered(
                    left + " " + form.symbol + " " + right, form.precedence));
        }
        if (NotationVocabulary.Prefix.IID.equals(form.fixity)) {
            if (targets.size() != 1) return Optional.empty();
            // Treat the single operand as the "right side" so right-associative
            // chains (e.g. --5) and same-precedence siblings render without parens.
            String operand = renderOperand(targets.get(0), form.precedence, form.associativity, false, params);
            if (operand == null) return Optional.empty();
            String separator = isWordSymbol(form.symbol) ? " " : "";
            return Optional.of(new Rendered(form.symbol + separator + operand, form.precedence));
        }
        if (NotationVocabulary.Postfix.IID.equals(form.fixity)) {
            if (targets.size() != 1) return Optional.empty();
            // Treat the single operand as the "left side" so left-associative
            // postfix chains (e.g. n!!) and same-precedence siblings render without parens.
            String operand = renderOperand(targets.get(0), form.precedence, form.associativity, true, params);
            if (operand == null) return Optional.empty();
            String separator = isWordSymbol(form.symbol) ? " " : "";
            return Optional.of(new Rendered(operand + separator + form.symbol, form.precedence));
        }
        return Optional.empty();
    }

    /**
     * Render one operand position. A {@link BindingTarget.RefTarget} pointing at a
     * stored frame body is fetched via the librarian and recursed via
     * {@link #renderOperation}, with parens decided by {@link #needsParens}. Other
     * targets (literals, IID refs, legacy inline FrameTarget) fall through to
     * {@link #renderLiteral}.
     */
    private String renderOperand(BindingTarget target, long outerPrecedence,
                                 ItemID outerAssociativity, boolean isLeftOperand,
                                 ParseParams params) {
        if (target instanceof BindingTarget.RefTarget rt) {
            ContentID cid = rt.asCid();
            Optional<Frame> innerFrame = librarian().fetchFrame(cid);
            if (innerFrame.isEmpty()) return renderLiteral(target);
            Body inner = innerFrame.get().body();
            if (!(inner.head() instanceof ItemRef ref)) return renderLiteral(target);
            List<BindingTarget> innerTargets = inner.bindings().stream()
                    .map(Binding::target).toList();
            Optional<Rendered> innerOpt = renderOperation(ref.iid(), innerTargets, params);
            if (innerOpt.isEmpty()) return renderLiteral(target);
            Rendered r = innerOpt.get();
            boolean wrap = needsParens(r.precedence, outerPrecedence,
                    outerAssociativity, isLeftOperand);
            return wrap ? "(" + r.text + ")" : r.text;
        }
        return renderLiteral(target);
    }

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
                                       ItemID outerAssociativity, boolean innerIsLeftOperand) {
        if (innerPrecedence > outerPrecedence) return false;
        if (innerPrecedence < outerPrecedence) return true;
        if (outerAssociativity.equals(NotationVocabulary.Left.IID)) {
            return !innerIsLeftOperand;
        }
        if (outerAssociativity.equals(NotationVocabulary.Right.IID)) {
            return innerIsLeftOperand;
        }
        return true;
    }

    /** Fixity sememes recognized by the operator-form lookup, in match-priority order. */
    private static final List<ItemID> RECOGNIZED_FIXITIES = List.of(
            NotationVocabulary.Infix.IID,
            NotationVocabulary.Prefix.IID,
            NotationVocabulary.Postfix.IID);

    /**
     * Find the first endorsed operator-form Lexeme frame on the item and extract its
     * surface form: symbol text, precedence, associativity, fixity. An "operator-form
     * Lexeme" has a VALUE binding qualified by one of {@link NotationVocabulary.Infix},
     * {@link NotationVocabulary.Prefix}, or {@link NotationVocabulary.Postfix}.
     */
    private static Optional<OperatorForm> lookupOperatorForm(Item item) {
        return item.endorsedFramesByPredicate(Lexeme.IID)
                .map(Language::readOperatorForm)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /** Try each recognized fixity in turn; return the first VALUE-binding match with metadata. */
    private static Optional<OperatorForm> readOperatorForm(Frame lexemeFrame) {
        for (ItemID fixity : RECOGNIZED_FIXITIES) {
            CompoundKey valueWithFixity = CompoundKey.of(ThematicRole.Value.IID, fixity);
            Optional<Binding> valueBinding = lexemeFrame.binding(valueWithFixity);
            if (valueBinding.isEmpty()) continue;
            Optional<String> symbol = readTextLiteral(valueBinding.get().target());
            if (symbol.isEmpty()) continue;
            long precedence = readPrecedence(lexemeFrame).orElse(0L);
            ItemID associativity = readAssociativity(lexemeFrame).orElse(NotationVocabulary.Left.IID);
            return Optional.of(new OperatorForm(symbol.get(), precedence, associativity, fixity));
        }
        return Optional.empty();
    }

    /** Pull a text-literal value out of a binding target, if present. */
    private static Optional<String> readTextLiteral(BindingTarget target) {
        if (target instanceof Literal lit && Literal.TYPE_TEXT.equals(lit.valueType())) {
            return Optional.of(lit.asText());
        }
        return Optional.empty();
    }

    /** Read the precedence integer from an operator-form Lexeme's ATTRIBUTE[Precedence] binding. */
    private static Optional<Long> readPrecedence(Frame lexemeFrame) {
        CompoundKey attributePrecedence = CompoundKey.of(
                ThematicRole.Attribute.IID, NotationVocabulary.Precedence.IID);
        return lexemeFrame.binding(attributePrecedence)
                .map(Binding::target)
                .filter(t -> t instanceof Literal)
                .map(t -> (Literal) t)
                .filter(lit -> Literal.TYPE_INTEGER.equals(lit.valueType()))
                .map(Literal::asInteger);
    }

    /** Read the associativity sememe IID from an operator-form Lexeme's ATTRIBUTE[Associativity] binding. */
    private static Optional<ItemID> readAssociativity(Frame lexemeFrame) {
        CompoundKey attributeAssociativity = CompoundKey.of(
                ThematicRole.Attribute.IID, NotationVocabulary.Associativity.IID);
        return lexemeFrame.binding(attributeAssociativity)
                .map(Binding::target)
                .filter(t -> t instanceof BindingTarget.IidTarget)
                .map(t -> ((BindingTarget.IidTarget) t).iid());
    }

    /**
     * Render a literal {@link BindingTarget} as text. Integer literals format as
     * decimal; text literals pass through; other targets fall back to
     * {@code toString()} (placeholder — proper rendering requires recursive name
     * lookup for ref targets).
     */
    private static String renderLiteral(BindingTarget target) {
        if (target instanceof Literal lit) {
            if (Literal.TYPE_INTEGER.equals(lit.valueType())) {
                return Long.toString(lit.asInteger());
            }
            if (Literal.TYPE_TEXT.equals(lit.valueType())) {
                return lit.asText();
            }
            return lit.toString();
        }
        return target == null ? null : target.toString();
    }

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
                                ItemID associativity, ItemID fixity) {}

    /**
     * English — ISO 639-3 code "eng". Static-key holder for {@code @Bind} references.
     *
     * <p>The seed declaration and Java implementation for English live in the
     * {@code :english} module's {@code English} class. This inner class exists only to
     * provide the {@code KEY}/{@code IID} constants for {@code @Bind} qualifiers used
     * across {@code :core} (which can't depend on {@code :english}).
     */
    public static final class English {
        public static final String KEY = "cg.lang:eng";
        public static final ItemID IID = ItemID.fromString(KEY);
        private English() {}
    }
}
