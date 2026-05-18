package dev.everydaythings.graph.language;


import com.ibm.icu.util.ULocale;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.ParseParams;

/**
 * Language items — both the meta-sememe identifying a language scope and the Java
 * base class for concrete language implementations.
 *
 * <p>"Language" here is the universal abstraction for anything that contributes to
 * parsing or rendering. It covers:
 * <ul>
 *   <li>Syntactic notations — {@code OperatorNotation}, {@code FunctionNotation},
 *       {@code SExprNotation}, {@code SqlNotation} — informally called "notations,"
 *       but architecturally just Languages.</li>
 *   <li>Natural languages — English, German, Japanese — implemented in their own
 *       modules under {@code :languages:*}.</li>
 *   <li>Domain-specific languages — chess notation, music notation — wherever
 *       sememe vocabulary brings them into scope.</li>
 * </ul>
 *
 * <p>All Languages are equal. They participate in the consensus parse round on the
 * same footing; they're brought into scope by the same mechanism — a token resolved
 * to a sememe whose Language tag pulls in that Language as an anchored participant.
 * Render output is composed by walking a stack of Languages, each contributing what
 * it has rules for and delegating the rest to the next in the stack.
 *
 * <p>The base class holds only the parse/render contract and a locale default;
 * everything else is delegated to specific Language subclasses. Even literal forms
 * (numbers, booleans, string quotation) are not universal — each Language defines
 * its own conventions ("3.14" in English vs "3,14" in German; "true" / "false" /
 * "真" / "偽" / {@code t} / {@code nil} for booleans; etc.).
 *
 * <p>Canonical-key prefix for specific languages: {@code cg.lang:} followed by the
 * ISO 639-3 three-letter code (e.g., {@code cg.lang:eng} for English). Sub-Language
 * codes follow BCP-47 ({@code cg.lang:en-US}, {@code cg.lang:de-CH}, etc.).
 * Syntactic notations use descriptive keys ({@code cg.lang:operator-notation},
 * {@code cg.lang:s-expr}, ...).
 */
@Seed.Item(key = Language.KEY)
public class Language extends Item {

    /** Canonical key for the language meta-sememe. */
    public static final String KEY = "cg.sememe:language";

    /** Seed/siloed constructor (no librarian). */
    public Language(ItemRef iid) {
        super(iid);
    }

    /** Runtime constructor — bound to a librarian. */
    public Language(ItemRef iid, Librarian librarian) {
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
     * <p>The base implementation has no rules of its own and returns the input
     * unchanged. Concrete Languages override to add their rendering rules:
     * {@link dev.everydaythings.graph.operator.OperatorNotation} for operator-form
     * frames (infix/prefix/postfix with precedence), natural-language Languages
     * (English, German, ...) for prose forms of predicates they have lexemes for,
     * domain Languages (chess notation, SQL, ...) for their domain shapes.
     *
     * <p>Languages typically delegate to {@code super.render(...)} or to another
     * Language in the stack when they don't have a rule for a given predicate, so
     * a frame rendered through a stack like {@code [English, OperatorNotation]}
     * picks up English prose where English has it and falls through to operator
     * notation for the rest.
     *
     * @param framemap the frame to render
     * @param params   render parameters (mode, verbosity, register, etc.)
     * @return a FrameMap with text populated; unchanged when this Language has no
     *     applicable rule
     */
    public FrameMap render(FrameMap framemap, ParseParams params) {
        return framemap;
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
        private English() {}
    }
}
