package dev.everydaythings.graph.linguistics;

import com.ibm.icu.util.ULocale;
import dev.everydaythings.graph.Implements;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.ParseParams;
import dev.everydaythings.graph.value.NotationVocabulary;

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
     * <p>The base implementation handles universal pre-linguistic notation — operator
     * infix specifically. If the framemap's predicate has an endorsed
     * {@link NotationVocabulary.Symbol Symbol} frame and the framemap has exactly two
     * bindings, output is rendered as infix: {@code <left-target> <symbol> <right-target>}.
     *
     * <p>Concrete Languages (English, German, etc.) override to add prose forms for
     * predicates they have lexemes for; they call {@code super.render(...)} as a
     * fallback for predicates they don't have prose for, so math notation works in any
     * Language by default. Sub-Languages further override for regional variations.
     *
     * <p>v1 limitations: only handles binary infix; targets render as their literal
     * value or sememe key (no recursive sub-frame rendering yet); spans on the output
     * FrameMap are not populated (only {@code text} is).
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

        ItemID predicateIid = framemap.predicate().value().iid();
        Optional<Item> predItem = librarian().fetchItem(predicateIid);
        if (predItem.isEmpty()) return framemap;

        Optional<String> symbol = lookupSymbolText(predItem.get());
        if (symbol.isEmpty()) return framemap;

        List<FrameMap.BindingMap> bindings = framemap.bindings();
        if (bindings.size() != 2) return framemap;

        String left = renderTarget(bindings.get(0).target().value());
        String right = renderTarget(bindings.get(1).target().value());
        if (left == null || right == null) return framemap;

        String text = left + " " + symbol.get() + " " + right;
        return framemap.withText(text);
    }

    /**
     * Find the universal symbol text on the given item.
     *
     * <p>A "universal symbol" is a Lexeme with no Language qualifier — by convention
     * the language-neutral notational form (e.g. {@code "+"} for ADD). Operators
     * declare these via {@code @Bind} with no qualifiers; the auto-indexing pipeline
     * picks them up alongside language-scoped lexemes.
     *
     * <p>Returns the first Lexeme found whose VALUE binding has empty qualifiers. If
     * no universal lexeme exists, returns empty (rendering falls back to leaving the
     * frame unchanged).
     */
    private static Optional<String> lookupSymbolText(Item item) {
        return item.endorsedFramesByPredicate(Lexeme.IID)
                .map(frame -> frame.binding(CompoundKey.of(ThematicRole.Value.IID)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(b -> b.qualifiers().isEmpty())
                .findFirst()
                .map(Binding::target)
                .filter(t -> t instanceof Literal)
                .map(t -> (Literal) t)
                .filter(lit -> Literal.TYPE_TEXT.equals(lit.valueType()))
                .map(Literal::asText);
    }

    /**
     * Render a {@link BindingTarget} as text. Integer literals format as decimal;
     * text literals pass through; refs render as their canonical key (placeholder
     * — proper rendering requires recursive name lookup).
     */
    private static String renderTarget(BindingTarget target) {
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
