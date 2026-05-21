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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/**
 * URL — an addressable web identifier (a URI in the absolute-with-scheme
 * sense; the type name is "URL" because that's how users think of it).
 *
 * <p>Stored as an atomic Body whose head is the URL archetype and whose
 * content is the canonical text.  Two URLs with identical canonical text
 * produce identical CIDs.
 *
 * <p><b>Canonical form</b> (v1, conservative):
 * <ul>
 *   <li>scheme lowercased ({@code HTTPS} → {@code https})</li>
 *   <li>host lowercased ({@code Example.COM} → {@code example.com})</li>
 *   <li>default ports stripped ({@code http://x:80/} → {@code http://x/},
 *       {@code https://x:443/} → {@code https://x/})</li>
 *   <li>everything else preserved (path is case-sensitive per RFC 3986;
 *       query and fragment likewise)</li>
 *   <li>trailing slash on bare-host URLs preserved as written</li>
 * </ul>
 *
 * <p><b>Validation:</b> parsed via {@link java.net.URI}; must be absolute
 * (have a scheme).  Relative references and bare hosts without scheme are
 * rejected.
 *
 * <p>Punycode for internationalized domain names (IDNA) is NOT performed
 * automatically in v1 — if you have a Unicode domain, encode it before
 * passing in.  Adding IDNA normalization is additive when needed.
 */
@Seed.Item(key = URL.KEY, head = Identifier.KEY)
public final class URL extends Identifier {

    public static final String KEY = "cg.archetype:url";

    private URL(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    /**
     * Parse, validate, and canonicalize a URL.
     *
     * @throws IllegalArgumentException if the input is not a syntactically
     *         valid absolute URI
     */
    @Decode
    public static URL fromText(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        URI parsed;
        try {
            parsed = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Not a valid URL: " + text, e);
        }
        if (parsed.getScheme() == null) {
            throw new IllegalArgumentException(
                    "URL must include a scheme (http, https, mailto, ...): " + text);
        }
        return new URL(canonicalize(parsed));
    }

    /**
     * The canonical URL text (scheme + host lowercased, default ports
     * stripped).
     */
    @Override
    @Encode
    public String encodeText() {
        return (String) atomicContent().orElseThrow();
    }

    /** The scheme component (lowercased), e.g. {@code "https"}. */
    public String scheme() {
        return uri().getScheme();
    }

    /** The host component (lowercased), or {@code null} for opaque URIs. */
    public String host() {
        return uri().getHost();
    }

    /** The path component, preserved as-written. */
    public String path() {
        return uri().getPath();
    }

    /** The parsed {@link URI} view, recomputed on demand from the canonical text. */
    public URI uri() {
        try {
            return new URI(encodeText());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Canonical URL no longer parses?", e);
        }
    }

    private static String canonicalize(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        int port = uri.getPort();
        int defaultPort = defaultPortFor(scheme);
        if (port == defaultPort) port = -1;

        // Reassemble.  Use the URI 7-arg constructor when host is present so
        // we get authority handling for free; otherwise fall back to the
        // raw forms.
        try {
            if (host != null) {
                URI canonical = new URI(
                        scheme,
                        uri.getUserInfo(),
                        host.toLowerCase(Locale.ROOT),
                        port,
                        uri.getPath(),
                        uri.getQuery(),
                        uri.getFragment());
                return canonical.toString();
            }
            // Opaque (mailto:, tel:, urn:, ...) — just lowercase the scheme.
            return scheme + ":" + uri.getRawSchemeSpecificPart()
                    + (uri.getFragment() == null ? "" : "#" + uri.getRawFragment());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("URL canonicalization failed", e);
        }
    }

    private static int defaultPortFor(String scheme) {
        return switch (scheme) {
            case "http", "ws"   -> 80;
            case "https", "wss" -> 443;
            case "ftp"          -> 21;
            case "ssh"          -> 22;
            case "telnet"       -> 23;
            case "smtp"         -> 25;
            case "dns"          -> 53;
            default -> -1;
        };
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a Uniform Resource Locator — a textual address identifying a resource on a "
                    + "network, comprising a scheme, an authority, and a path";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "URL";
}
