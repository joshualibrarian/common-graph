package dev.everydaythings.graph.language;


import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.ULocale;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.GroupVocabulary;
import dev.everydaythings.graph.text.ParseParams;
import dev.everydaythings.graph.text.TextSpan;
import dev.everydaythings.graph.text.TokenLattice;
import dev.everydaythings.graph.text.TokenLattice.TokenSpan;

import java.math.BigDecimal;
import java.text.ParsePosition;
import java.util.List;
import java.util.Optional;

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

    // ==================================================================================
    // Literal recognition — the universal fallback every Language inherits.
    //
    // A LITERAL-kind token is a value (a number, a string, a boolean) — every
    // Language can recognize it, but the surface conventions differ wildly:
    //   English  3.14 / "hello" / true
    //   German   3,14 / „hello" / wahr
    //   Lisp     3.14 / "hello" / #t
    //   Japanese 三 / 「hello」 / 真
    //
    // {@link #recognizeLiteral} is the entry point; it dispatches to the
    // per-shape overrides {@link #recognizeNumber}, {@link #recognizeString},
    // {@link #recognizeBoolean}.  Subclasses override only the shapes they
    // need to customize.  The default implementations use ICU's locale-aware
    // parsing keyed off {@link #locale()}, so most sub-Languages get correct
    // behavior just by setting their locale.
    //
    // {@link #recognizeOperand} pairs literal recognition with dictionary-
    // posting lookup — the convenience every parse helper actually wants:
    // "give me a value for this token, whatever it is."
    // ==================================================================================

    /**
     * Resolve a token to an operand value — a literal value or a referenced item.
     *
     * <p>Combines literal recognition with dictionary-posting lookup. LITERAL-kind
     * tokens go through {@link #recognizeLiteral}; WORD-kind tokens with postings
     * return the first posting's target ItemRef. Returns empty when the token
     * resolves to nothing useful (UNRESOLVED with no postings, malformed literal).
     *
     * <p>This is the universal fallback every parse helper needs: the parser walks
     * tokens, calls {@code recognizeOperand} on each, and gets back whatever the
     * Language thinks should be bound. Notations, natural languages, domain
     * languages — all use this.
     */
    public Optional<Object> recognizeOperand(TokenSpan token) {
        if (token == null) return Optional.empty();
        Optional<Object> literal = recognizeLiteral(token);
        if (literal.isPresent()) return literal;
        if (!token.postings().isEmpty()) {
            return Optional.ofNullable(token.postings().get(0).target());
        }
        return Optional.empty();
    }

    /**
     * Parse a LITERAL-kind token as a value. Dispatches by literal shape to
     * {@link #recognizeNumber}, {@link #recognizeString}, {@link #recognizeBoolean}.
     *
     * <p>Subclasses normally override the per-shape methods rather than this one —
     * overriding individual shapes leaves the others on the inherited defaults.
     * Override this method directly only when the dispatch logic itself needs to
     * change (e.g., a language with a single combined number-or-symbol shape).
     */
    public Optional<Object> recognizeLiteral(TokenSpan token) {
        if (token == null || token.kind() != TokenLattice.Kind.LITERAL) return Optional.empty();
        String text = token.surfaceText();
        if (text == null) return Optional.empty();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        Optional<Object> result;
        result = recognizeNumber(trimmed);   if (result.isPresent()) return result;
        result = recognizeString(trimmed);   if (result.isPresent()) return result;
        result = recognizeBoolean(trimmed);  if (result.isPresent()) return result;
        return Optional.empty();
    }

    /**
     * Parse a numeric literal in this Language's conventions.  Default uses
     * ICU's {@link NumberFormat} for {@link #locale()} — correctly handles
     * locale-specific decimal separators (English "3.14" vs German "3,14"),
     * thousands separators, minus signs, and scientific notation.
     *
     * <p>Returns {@link Long} for whole numbers, {@link BigDecimal} for
     * decimals (CG-CBOR forbids IEEE 754 floats, so we never emit double).
     *
     * <p>Subclasses override to add: hex/octal/binary prefixes ({@code 0x1f},
     * {@code 0o755}, {@code 0b1011}), language-specific number words
     * ("thirteen", "三", "vingt"), unit-bearing literals ("5kg"), rationals
     * ("1/3"), etc.
     */
    protected Optional<Object> recognizeNumber(String text) {
        // Step 1 — try locale-neutral BigDecimal parsing.  Handles "3.14",
        // "-5", "1.5e2", "1E10" exactly.  Locale-independent: always uses "."
        // for the decimal separator, which is the right call for the
        // "language-neutral default" base — sub-Languages override for their
        // locale (German "3,14", French "3,14", Japanese number-word forms).
        try {
            return Optional.of(promoteToIntegralOrKeep(new BigDecimal(text)));
        } catch (NumberFormatException ignored) {
            // Fall through to ICU for shapes BigDecimal won't accept.
        }
        // Step 2 — fall back to ICU's locale-aware parsing for thousands
        // separators and other locale conventions BigDecimal won't accept.
        NumberFormat fmt = NumberFormat.getInstance(locale());
        if (fmt instanceof DecimalFormat df) df.setParseBigDecimal(true);
        fmt.setParseStrict(true);
        ParsePosition pos = new ParsePosition(0);
        Number parsed;
        try {
            parsed = fmt.parse(text, pos);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (parsed == null || pos.getIndex() != text.length()) return Optional.empty();
        if (parsed instanceof BigDecimal bd) return Optional.of(promoteToIntegralOrKeep(bd));
        if (parsed instanceof Long l) return Optional.of(l);
        if (parsed instanceof Integer i) return Optional.of((long) i);
        if (parsed instanceof Double || parsed instanceof Float) {
            return Optional.of(promoteToIntegralOrKeep(new BigDecimal(parsed.toString())));
        }
        return Optional.of(parsed.longValue());
    }

    /**
     * Promote integer-valued BigDecimals to {@link Long}; keep fractional ones
     * as {@link BigDecimal}.  Called from both BigDecimal-direct and ICU paths
     * to land on a consistent return type — {@code "5"} and {@code "5.0"} both
     * give {@code Long(5)}; {@code "3.14"} gives {@link BigDecimal}.
     */
    private static Object promoteToIntegralOrKeep(BigDecimal bd) {
        BigDecimal stripped = bd.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            try {
                return stripped.longValueExact();
            } catch (ArithmeticException ignored) {
                return stripped;
            }
        }
        return bd;
    }

    /**
     * Parse a quoted string literal.  Default recognizes paired {@code "..."}
     * and {@code '...'}, strips the quotes, returns the inner text verbatim
     * (no escape-sequence handling yet — bare-string only).
     *
     * <p>Subclasses override for language-specific quote conventions
     * (German typographic „..." or »...«, French «...», Japanese 「...」,
     * raw strings, triple-quoted multi-line strings, escape-sequence
     * decoding).
     */
    protected Optional<Object> recognizeString(String text) {
        if (text.length() < 2) return Optional.empty();
        char first = text.charAt(0);
        char last = text.charAt(text.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return Optional.of(text.substring(1, text.length() - 1));
        }
        return Optional.empty();
    }

    /**
     * Parse a boolean literal.  Default returns empty — booleans don't have a
     * universal lexical form, and many Languages have no boolean literals at
     * all (operator notation, function notation, math-only domain languages).
     *
     * <p>Subclasses override to add their convention: English {@code true}/
     * {@code false}, Lisp {@code #t}/{@code #f}, German {@code wahr}/
     * {@code falsch}, Common Lisp {@code t}/{@code nil}, Japanese 真/偽, etc.
     */
    protected Optional<Object> recognizeBoolean(String text) {
        return Optional.empty();
    }

    /**
     * Render a value as text in this Language's conventions.  Symmetric with
     * {@link #recognizeLiteral} — what render emits, parse accepts.
     *
     * <p>Default: locale-aware number formatting via ICU's {@link NumberFormat},
     * double-quoted strings, English-style {@code true}/{@code false} for
     * booleans, {@code toString} fallback for anything else.  Subclasses
     * override to add their conventions.
     */
    public String renderLiteral(Object value) {
        if (value == null) return "";
        if (value instanceof String s) return "\"" + s + "\"";
        if (value instanceof Boolean b) return b ? "true" : "false";
        if (value instanceof Number n) {
            NumberFormat fmt = NumberFormat.getInstance(locale());
            return fmt.format(n);
        }
        return value.toString();
    }

    // ==================================================================================
    // Token-stream structural helpers — paren grouping and span lookup.
    //
    // Every notation that uses brackets for grouping (OperatorNotation parens,
    // FunctionNotation argument lists, S-expression lists, JSON-style data
    // syntax, ...) needs the same paren-depth tracking.  These walk the
    // universal {@link GroupVocabulary#OpenGroup} / {@link GroupVocabulary#CloseGroup}
    // sememes and stay agnostic about what the brackets *mean* semantically.
    //
    // Static utilities for the simple cases.  When a Language needs different
    // bracket semantics (e.g. square brackets vs parens), promote the relevant
    // method to an instance-level override.
    // ==================================================================================

    /**
     * True when this token's postings include the OpenGroup sememe (an opening
     * bracket of any flavor — {@code "("}, {@code "["}, {@code "{"}, etc.).
     */
    public static boolean isOpenGroup(TokenSpan token) {
        if (token == null) return false;
        return token.postings().stream()
                .anyMatch(p -> ItemRef.iid(GroupVocabulary.OpenGroup.KEY).equals(p.target()));
    }

    /** Mirror of {@link #isOpenGroup} for CloseGroup. */
    public static boolean isCloseGroup(TokenSpan token) {
        if (token == null) return false;
        return token.postings().stream()
                .anyMatch(p -> ItemRef.iid(GroupVocabulary.CloseGroup.KEY).equals(p.target()));
    }

    /**
     * Find the index of a token whose span matches {@code span}, or -1 if no
     * token has that exact span.  A small list-walk used by parse helpers to
     * locate anchors within the active token stream.
     */
    public static int indexOfTokenSpan(List<TokenSpan> tokens, TextSpan span) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).span().equals(span)) return i;
        }
        return -1;
    }

    /**
     * True when the token at {@code idx} sits inside an unmatched OpenGroup
     * at the outer level — the bracket depth at position {@code idx} is positive.
     */
    public static boolean isInsideParens(List<TokenSpan> tokens, int idx) {
        int depth = 0;
        for (int i = 0; i < idx; i++) {
            TokenSpan t = tokens.get(i);
            if (isOpenGroup(t)) depth++;
            else if (isCloseGroup(t)) depth--;
        }
        return depth > 0;
    }

    /**
     * Walk backwards from the CloseGroup at {@code closeIdx} and find the index
     * of the matching OpenGroup.  Handles nesting.  Returns -1 when no matching
     * opener exists.
     */
    public static int findMatchingOpen(List<TokenSpan> tokens, int closeIdx) {
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

    /**
     * Walk forwards from the OpenGroup at {@code openIdx} and find the index
     * of the matching CloseGroup.  Handles nesting.  Returns -1 when no
     * matching closer exists.
     */
    public static int findMatchingClose(List<TokenSpan> tokens, int openIdx) {
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
