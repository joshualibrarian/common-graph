package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.network.IpAddress;
import dev.everydaythings.graph.network.NetworkVocabulary;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TcpEndpoint — an {@link Endpoint} that addresses the {@link
 * NetworkVocabulary.Tcp Tcp} transport.
 *
 * <p>Body shape: {@code Body[head=TcpEndpoint, @Host=<IpAddress>, @Port=N]}.
 * The Host target is a 4- or 16-byte {@link IpAddress} carrying its own
 * {@code @Encode}/{@code @Decode} round-trip.  Port is a long in the 0..65535
 * range.
 *
 * <p>Schema validation (EXPECTS slots on the manifest) is deferred until a
 * byte-array matcher for IpAddress is in place — for now the construction
 * surface and Java-level checks (port range, non-null host) are the only
 * guards.
 */
@Seed.Item(
        key = TcpEndpoint.KEY,
        head = Endpoint.KEY,
        bindings = {@Seed.Binding(role = NetworkVocabulary.Addresses.KEY, ref = NetworkVocabulary.Tcp.KEY)})
public final class TcpEndpoint extends Endpoint {

    public static final String KEY = "cg.archetype:tcp-endpoint";

    // ==================================================================================
    // Archetype-level lexical frames
    // ==================================================================================

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a network destination addressing the TCP transport — bytes over IP, addressed "
                    + "by host (IP address) and port";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "TCP endpoint";

    // ==================================================================================
    // Construction
    // ==================================================================================

    public TcpEndpoint(IpAddress host, int port) {
        super(ItemRef.iid(KEY), assemble(host, port));
    }

    private static List<Binding> assemble(IpAddress host, int port) {
        Objects.requireNonNull(host, "host");
        checkPort(port);
        return List.of(
                Binding.literal(ItemRef.iid(NetworkVocabulary.Host.KEY), host),
                Binding.literal(ItemRef.iid(NetworkVocabulary.Port.KEY), (long) port));
    }

    /** Factory: TCP endpoint at the given IP address and port. */
    public static TcpEndpoint of(IpAddress host, int port) {
        return new TcpEndpoint(host, port);
    }

    /** Factory: TCP endpoint at a host string (parsed to {@link IpAddress}) and port. */
    public static TcpEndpoint of(String host, int port) {
        return of(IpAddress.parse(host), port);
    }

    /**
     * Typed view over an existing Body whose head is the TcpEndpoint
     * archetype.  Throws if the body's shape isn't a valid TCP endpoint.
     */
    public static TcpEndpoint from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof TcpEndpoint tcp) return tcp;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the TcpEndpoint archetype: " + body.headRef());
        }
        IpAddress host = readHost(body)
                .orElseThrow(() -> new IllegalArgumentException("TcpEndpoint missing @Host binding"));
        long port = readTarget(body, NetworkVocabulary.Port.KEY, Long.class)
                .orElseThrow(() -> new IllegalArgumentException("TcpEndpoint missing @Port binding"));
        return new TcpEndpoint(host, (int) port);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    @Override
    public ItemRef transport() {
        return ItemRef.iid(NetworkVocabulary.Tcp.KEY);
    }

    public IpAddress host() {
        return readHost(this)
                .orElseThrow(() -> new IllegalStateException("TcpEndpoint missing @Host binding"));
    }

    public int port() {
        return readTarget(this, NetworkVocabulary.Port.KEY, Long.class)
                .map(Long::intValue)
                .orElseThrow(() -> new IllegalStateException("TcpEndpoint missing @Port binding"));
    }

    @Override
    public String toString() {
        return "tcp://" + host().toHostString() + ":" + port();
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static <T> Optional<T> readTarget(Body body, String roleKey, Class<T> type) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : body.bindings()) {
            if (role.equals(b.role()) && type.isInstance(b.target())) {
                return Optional.of(type.cast(b.target()));
            }
        }
        return Optional.empty();
    }

    /**
     * Read the @Host binding as an {@link IpAddress}.  Accepts either an
     * already-typed {@code IpAddress} target (the common construction path) or
     * a raw {@code byte[]} target (post-CBOR-decode, before any binding-role
     * decoding has reified the bytes into a typed value).
     */
    private static Optional<IpAddress> readHost(Body body) {
        ItemRef role = ItemRef.iid(NetworkVocabulary.Host.KEY);
        for (Binding b : body.bindings()) {
            if (!role.equals(b.role())) continue;
            Object target = b.target();
            if (target instanceof IpAddress ip) return Optional.of(ip);
            if (target instanceof byte[] raw)   return Optional.of(new IpAddress(raw));
        }
        return Optional.empty();
    }

    private static void checkPort(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be 0..65535, got " + port);
        }
    }
}
