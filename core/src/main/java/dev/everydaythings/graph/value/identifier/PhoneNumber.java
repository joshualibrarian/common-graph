package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.canonical.Encode;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PhoneNumber — an addressable phone identifier in E.164 form.
 *
 * <p>Stored as an atomic Body whose head is the PhoneNumber archetype and
 * whose content is the canonical E.164 text (e.g. {@code "+15551234567"}).
 * Two PhoneNumbers with identical canonical text produce identical CIDs.
 *
 * <p><b>Canonical form:</b> E.164 — a leading {@code +} followed by 7 to 15
 * digits.  No spaces, hyphens, parentheses, or extensions in the canonical
 * form.  Common decorative characters in input (spaces, hyphens, parens,
 * dots) are stripped during parsing.
 *
 * <p><b>Validation scope (v1):</b> regex-based.  Accepts the realistic 99%
 * of international numbers but doesn't enforce country-specific subscriber
 * length rules.  Full validation against per-country dialing plans wants
 * libphonenumber; we can wire that as an option later without changing the
 * value-type shape.
 *
 * <p>Extensions ({@code x123}, {@code ext. 4567}) and per-call options
 * (CLIR, etc.) aren't part of the canonical form for v1.  If those matter,
 * use a separate frame binding alongside the PhoneNumber reference.
 */
@Seed.Item(key = PhoneNumber.KEY, head = Identifier.KEY)
public final class PhoneNumber extends Identifier {

    public static final String KEY = "cg.archetype:phone-number";

    /** E.164: leading + then 7–15 digits.  Tightest reasonable bound. */
    private static final Pattern E164 = Pattern.compile("^\\+\\d{7,15}$");

    /** Strip-on-parse: common decorative chars in user-typed input. */
    private static final Pattern DECORATIVE = Pattern.compile("[\\s\\-().]+");

    private PhoneNumber(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    /**
     * Parse a phone number text into a typed PhoneNumber.  Strips decorative
     * characters (spaces, hyphens, parens, dots); the remaining string must
     * match E.164 ({@code +<digits>}, 7–15 digits).
     *
     * @throws IllegalArgumentException if the input doesn't canonicalize to
     *         a valid E.164 string
     */
    @Decode
    public static PhoneNumber fromText(String text) {
        Objects.requireNonNull(text, "text");
        String canonical = canonicalize(text);
        if (!E164.matcher(canonical).matches()) {
            throw new IllegalArgumentException(
                    "Not a valid E.164 phone number: " + text);
        }
        return new PhoneNumber(canonical);
    }

    /**
     * Canonical E.164 form (leading {@code +}, then 7–15 digits, no
     * decoration).
     */
    @Override
    @Encode
    public String encodeText() {
        return (String) atomicContent().orElseThrow();
    }

    /**
     * The country-code prefix.  For an E.164 number, country codes are 1–3
     * digits, but the boundary is not encoded in the canonical form.  This
     * method returns up to the first 3 digits after the {@code +}; callers
     * needing the exact country code should consult libphonenumber.
     *
     * <p>Provided as a convenience accessor; not a substitute for real
     * country-code lookup.
     */
    public String countryCodeBestEffort() {
        String c = encodeText();
        int limit = Math.min(c.length() - 1, 3);
        return c.substring(1, 1 + limit);
    }

    private static String canonicalize(String text) {
        String trimmed = text.trim();
        Matcher m = DECORATIVE.matcher(trimmed);
        return m.replaceAll("");
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a telephone number in E.164 form — a leading '+' followed by 7 to 15 digits, "
                    + "identifying a subscriber on the global telephone network";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "phone number";
}
