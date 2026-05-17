package dev.everydaythings.graph.network;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.Binding;
import static dev.everydaythings.graph.Seed.Frame;

/**
 * Network vocabulary — sememes used by {@link dev.everydaythings.graph.value.Endpoint
 * Endpoint} and the transport / bridge layer.
 *
 * <p>Two groups live here:
 * <ul>
 *   <li><b>Endpoint binding-role sememes</b> — {@link Protocol}, {@link Host},
 *       {@link Port}, {@link Path}, {@link Identity}, {@link Url}. These are
 *       qualities used as the role of each binding inside an Endpoint body,
 *       analogous to Color's R/G/B/A channel sememes.</li>
 *   <li><b>Transport-protocol vocabulary</b> — the {@link TransportProtocol}
 *       archetype and its named instances ({@link Tcp}, {@link Unix},
 *       {@link Reticulum}, {@link Ws}, {@link Loopback}). Each instance is a
 *       sememe used as the target of an Endpoint's PROTOCOL binding, and as
 *       the discriminator a bridge implementation uses to declare which
 *       protocol it serves.</li>
 * </ul>
 *
 * <p>Endpoint itself ({@link dev.everydaythings.graph.value.Endpoint}) is a
 * Value class — the typed Java mirror of bodies whose head is the Endpoint
 * archetype. It references the sememes here by IID.
 */
public final class NetworkVocabulary {

    private NetworkVocabulary() {}

    // ==================================================================================
    // Endpoint binding-role sememes — used inside Endpoint bodies, analogous
    // to Color's R/G/B/A channel sememes.
    // ==================================================================================

    /** PROTOCOL role — points at a {@link TransportProtocol} instance. */
    @Seed.Item(key = Protocol.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Protocol {
        public static final String KEY = "cg.endpoint:protocol";
        private Protocol() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the transport protocol an endpoint speaks";
    }

    /** HOST role — an IP address ({@link IpAddress}) for IP-based protocols. */
    @Seed.Item(key = Host.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Host {
        public static final String KEY = "cg.endpoint:host";
        private Host() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an endpoint's host address (typically an IP)";
    }

    /** PORT role — an integer 0..65535 for protocols that use ports. */
    @Seed.Item(key = Port.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Port {
        public static final String KEY = "cg.endpoint:port";
        private Port() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an endpoint's port number (0..65535)";
    }

    /** PATH role — a filesystem path for Unix-socket endpoints. */
    @Seed.Item(key = Path.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Path {
        public static final String KEY = "cg.endpoint:path";
        private Path() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an endpoint's filesystem path (Unix domain sockets)";
    }

    /** IDENTITY role — destination-identity bytes (Reticulum, etc.). */
    @Seed.Item(key = Identity.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Identity {
        public static final String KEY = "cg.endpoint:identity";
        private Identity() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an endpoint's cryptographic destination identifier (Reticulum hash, etc.)";
    }

    /** URL role — a URL string for WebSocket / HTTP-style endpoints. */
    @Seed.Item(key = Url.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Url {
        public static final String KEY = "cg.endpoint:url";
        private Url() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an endpoint's URL (WebSocket, HTTP, ...)";
    }

    // ==================================================================================
    // Transport-protocol vocabulary — the TransportProtocol archetype and its
    // named instances. Each instance is a sememe used as the target of an
    // Endpoint's PROTOCOL binding.
    // ==================================================================================

    /** The archetype of transport protocols. */
    @Seed.Item(key = TransportProtocol.KEY, head = CoreVocabulary.Archetype.KEY)
    public static final class TransportProtocol {
        public static final String KEY = "cg.archetype:transport-protocol";
        private TransportProtocol() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of transport protocols — the kinds of byte-channel an "
                        + "endpoint may speak (TCP, Unix sockets, Reticulum, WebSocket, ...)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "transport protocol";
    }

    /** TCP — bytes over IP. */
    @Seed.Item(key = Tcp.KEY, head = TransportProtocol.KEY)
    public static final class Tcp {
        public static final String KEY = "cg.transport:tcp";
        private Tcp() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "TCP — ordered bytes over IP";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "TCP";
    }

    /** Unix domain socket — local IPC via filesystem path. */
    @Seed.Item(key = Unix.KEY, head = TransportProtocol.KEY)
    public static final class Unix {
        public static final String KEY = "cg.transport:unix";
        private Unix() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Unix domain socket — local IPC via filesystem path";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "Unix socket";
    }

    /** Reticulum — mesh routing with built-in authenticated encryption. */
    @Seed.Item(key = Reticulum.KEY, head = TransportProtocol.KEY)
    public static final class Reticulum {
        public static final String KEY = "cg.transport:reticulum";
        private Reticulum() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "Reticulum — mesh routing with built-in authenticated encryption";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "Reticulum";
    }

    /** WebSocket — framed bytes over an HTTP(S) upgrade handshake. */
    @Seed.Item(key = Ws.KEY, head = TransportProtocol.KEY)
    public static final class Ws {
        public static final String KEY = "cg.transport:ws";
        private Ws() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "WebSocket — framed bytes over an HTTP(S) upgrade handshake";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "WebSocket";
    }

    /** Loopback — in-process sentinel for paired tunnels with no real transport. */
    @Seed.Item(key = Loopback.KEY, head = TransportProtocol.KEY)
    public static final class Loopback {
        public static final String KEY = "cg.transport:loopback";
        private Loopback() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "Loopback — in-process sentinel for paired tunnels with no real transport";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "loopback";
    }
}
