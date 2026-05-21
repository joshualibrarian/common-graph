package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.canonical.Encode;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * EmailAddress — an addressable email-style identifier {@code local@domain}.
 *
 * <p>Stored as an atomic Body whose head is the EmailAddress archetype and
 * whose content is the canonical text (e.g., {@code "alice@example.com"}).
 * The {@link #local()} and {@link #domain()} parts are derived on demand from
 * the canonical form — they aren't stored as separate bindings.  Two emails
 * with identical canonical text produce identical CIDs, so reference-by-CID
 * dedup happens naturally.
 *
 * <p>Canonicalization rules (v1, intentionally conservative):
 * <ul>
 *   <li>Domain is lowercased (DNS is case-insensitive).</li>
 *   <li>Local-part is preserved as written (RFC 5321 says it CAN be
 *       case-sensitive; common SMTP servers ignore case, but we don't
 *       change letters in case some implementation does care).</li>
 *   <li>Surrounding whitespace stripped.</li>
 * </ul>
 *
 * <p>Validation uses a simple regex that accepts the realistic 99% of
 * addresses.  Full RFC 5322 compliance (quoted local parts, comments,
 * IP-literal domains) is out of scope for v1.  If callers need stricter
 * validation, they should run the input through their own RFC-grade parser
 * first and pass the canonicalized text in.
 */
@Seed.Item(key = EmailAddress.KEY, head = Identifier.KEY)
public final class EmailAddress extends Identifier {

    public static final String KEY = "cg.archetype:email-address";

    /**
     * Regex for the realistic 99% case.  Does NOT accept quoted local parts,
     * comments, or IP-literal domains — those need a full RFC 5322 parser.
     */
    private static final Pattern PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private EmailAddress(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    /**
     * Parse, validate, and canonicalize an email address.
     *
     * @throws IllegalArgumentException if {@code text} doesn't parse as a
     *         well-formed email address
     */
    @Decode
    public static EmailAddress fromText(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (!PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Not a valid email address: " + text);
        }
        int at = trimmed.lastIndexOf('@');
        String localPart = trimmed.substring(0, at);
        String domainPart = trimmed.substring(at + 1).toLowerCase();
        return new EmailAddress(localPart + "@" + domainPart);
    }

    /**
     * The canonical {@code local@domain} text.  The {@code @Encode}
     * annotation makes this the leaf value the codec serializes for
     * round-trips, and the token dictionary picks it up automatically as the
     * user-typeable form.
     */
    @Override
    @Encode
    public String encodeText() {
        return (String) atomicContent().orElseThrow();
    }

    /** The local part (everything before the {@code @}). */
    public String local() {
        String text = encodeText();
        return text.substring(0, text.lastIndexOf('@'));
    }

    /** The domain part (everything after the {@code @}), lowercased. */
    public String domain() {
        String text = encodeText();
        return text.substring(text.lastIndexOf('@') + 1);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "an email address — a handle of the form local@domain identifying a "
                    + "recipient on the Internet email system";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "email address";
}
