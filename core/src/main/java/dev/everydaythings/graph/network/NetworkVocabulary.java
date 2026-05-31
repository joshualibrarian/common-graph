package dev.everydaythings.graph.network;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;

import static dev.everydaythings.graph.Seed.Binding;
import static dev.everydaythings.graph.Seed.Frame;

/**
 * Network vocabulary — sememes for the network layer: transports, protocols,
 * and the binding roles used inside endpoint bodies.
 *
 * <p>The network stack separates four concerns; only Transport and Protocol
 * have vocabulary entries here (Tunnel is a runtime abstraction; Encoding has
 * its own home under {@link dev.everydaythings.graph.encoding.Encoding}):
 *
 * <table>
 *   <tr><th>Layer</th><th>What it is</th><th>Examples</th></tr>
 *   <tr><td>Protocol</td><td>application-level conversation language</td><td>Parley natively; HTTP, ActivityPub, SMTP via bridges</td></tr>
 *   <tr><td>Encoding</td><td>byte serialization (separate package)</td><td>CG-CBOR, JSON</td></tr>
 *   <tr><td>Tunnel</td><td>handshaken byte channel (runtime only)</td><td>LoopbackTunnel, NoiseTunnel</td></tr>
 *   <tr><td>Transport</td><td>raw byte-channel mechanism with its own addressing</td><td>TCP, Unix socket, Reticulum, in-VM loopback</td></tr>
 * </table>
 *
 * <p>The test for "is this a Transport in CG's sense" is whether it provides
 * raw byte-channel addressing at the OS or library level, with its own
 * addressing scheme.  TCP, Unix sockets, and Reticulum all qualify.
 * WebSocket does not (it's an HTTP-upgrade application protocol that happens
 * to produce a bidirectional byte channel as a side effect) and lives in the
 * bridges layer when needed.
 *
 * <p>Three groups live here:
 * <ul>
 *   <li><b>Endpoint binding-role sememes</b> — {@link Host}, {@link Port},
 *       {@link Path}, {@link Identity}.  Each is a Quality used as the role of
 *       a binding inside an Endpoint body, analogous to Color's R/G/B/A channel
 *       sememes.</li>
 *   <li><b>Transport vocabulary</b> — the {@link Transport} archetype and its
 *       four built-in instances ({@link Tcp}, {@link Unix}, {@link Reticulum},
 *       {@link Loopback}).  Each instance is the sememe a concrete transport
 *       declares it serves, and the link target of an Endpoint subarchetype's
 *       {@link Addresses} binding.</li>
 *   <li><b>Protocol vocabulary</b> — the {@link Protocol} archetype.  Instances
 *       live with their implementations in bridge modules (e.g., {@code Parley}
 *       in {@code :bridges:parley}; future bridges add HTTP, ActivityPub,
 *       SMTP, SIP, etc.).</li>
 * </ul>
 *
 * <p>The {@link Addresses} predicate threads the family together: each Endpoint
 * subarchetype's manifest carries a single {@code Addresses → @<transport>}
 * binding declaring which Transport its instances address.  Runtime dispatch
 * (a Transport accepting an Endpoint) reads this binding off the endpoint's
 * head archetype rather than relying on Java-class naming.
 */
public final class NetworkVocabulary {

    private NetworkVocabulary() {}

    // ==================================================================================
    // Endpoint binding-role sememes — used inside Endpoint bodies, analogous
    // to Color's R/G/B/A channel sememes.
    // ==================================================================================

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

    /**
     * NAME role — a human-meaningful string identifier for an endpoint.
     * Used by the loopback transport (where there's no real addressing
     * scheme but multiple paired tunnels need to coexist in-VM) and
     * available to any transport that wants friendly addressing.
     */
    @Seed.Item(key = Name.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Name {
        public static final String KEY = "cg.endpoint:name";
        private Name() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a human-meaningful string identifier for an endpoint";
    }

    // ==================================================================================
    // Transport vocabulary — the Transport archetype and its built-in instances.
    // Each instance is the sememe a concrete byte-channel mechanism declares
    // itself to serve, and the link target of an Endpoint subarchetype's
    // Addresses binding.
    // ==================================================================================

    /** The archetype of transports — raw byte-channel mechanisms. */
    @Seed.Item(key = Transport.KEY, head = CoreVocabulary.Archetype.KEY)
    public static final class Transport {
        public static final String KEY = "cg.archetype:transport";
        private Transport() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of transports — raw byte-channel mechanisms with their own "
                        + "addressing scheme (TCP, Unix sockets, Reticulum, in-VM loopback). "
                        + "Application-level conversation languages (HTTP, ActivityPub) are "
                        + "Protocols, not Transports.";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "transport";
    }

    /** TCP — bytes over IP. */
    @Seed.Item(key = Tcp.KEY, head = Transport.KEY)
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
    @Seed.Item(key = Unix.KEY, head = Transport.KEY)
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
    @Seed.Item(key = Reticulum.KEY, head = Transport.KEY)
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

    /** Loopback — in-VM sentinel for paired tunnels with no real transport. */
    @Seed.Item(key = Loopback.KEY, head = Transport.KEY)
    public static final class Loopback {
        public static final String KEY = "cg.transport:loopback";
        private Loopback() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "Loopback — in-VM sentinel for paired tunnels with no real transport";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "loopback";
    }

    // ==================================================================================
    // Protocol vocabulary — the Protocol archetype.  Instances are seeded with
    // their implementations in bridge modules (Parley in :bridges:parley;
    // future bridges add HTTP, ActivityPub, SMTP, SIP, etc.).
    // ==================================================================================

    /** The archetype of application-level protocols — conversation languages spoken over Tunnels. */
    @Seed.Item(key = Protocol.KEY, head = CoreVocabulary.Archetype.KEY)
    public static final class Protocol {
        public static final String KEY = "cg.archetype:protocol";
        private Protocol() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of application-level protocols — conversation languages spoken "
                        + "over Tunnels (Parley natively; HTTP, ActivityPub, SMTP, SIP via bridges). "
                        + "Distinct from Transport, which is the raw byte-channel mechanism.";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "protocol";
    }

    // ==================================================================================
    // Predicates relating Endpoints to Transports.
    // ==================================================================================

    /**
     * Relates an Endpoint subarchetype to the Transport it addresses.  Each
     * concrete endpoint subarchetype (TcpEndpoint, UnixEndpoint, ...) carries
     * a single {@code Addresses → @<transport>} binding on its manifest.
     * Runtime dispatch (a Transport accepting an Endpoint) reads this binding
     * off the endpoint's head archetype instead of relying on Java-class
     * naming.
     */
    @Seed.Item(key = Addresses.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Addresses {
        public static final String KEY = "cg.predicate:addresses";
        private Addresses() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the relation between an Endpoint subarchetype and the Transport its instances address";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "addresses";
    }
}
