package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.network.IpAddress;
import dev.everydaythings.graph.network.NetworkVocabulary;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Endpoint — a network destination as a {@link Value}-shaped body.
 *
 * <p>An Endpoint body has head=Endpoint and bindings declaring a transport
 * protocol plus protocol-specific addressing (host/port for TCP, path for
 * Unix, identity for Reticulum, URL for WebSocket). The canonical shape:
 *
 * <pre>Body[head=Endpoint, PROTOCOL=&#64;tcp, HOST=&lt;IpAddress&gt;, PORT=8080]</pre>
 *
 * <p>Endpoint is the typed Java mirror of that body — same way {@link Color}
 * mirrors color bodies. Construction is via the static factories
 * ({@link #tcp(IpAddress,int)}, {@link #unix(String)},
 * {@link #reticulum(byte[])}, {@link #ws(String)}, {@link #loopback()}); the
 * binding-role and transport-protocol sememes used inside the body live in
 * {@link NetworkVocabulary}.
 *
 * <p>For IP-based protocols (TCP), the HOST target is an {@link IpAddress}
 * value carrying its own {@code @Encode}/{@code @Decode} round-trip — the
 * codec serializes it as a raw 4- or 16-byte CBOR byte string and rehydrates
 * it on decode. No string-form parsing is round-tripped on the wire.
 */
@Seed.Item(key = Endpoint.KEY, head = Value.KEY)
@Seed.Mints(key = Endpoint.KEY)
public final class Endpoint extends Value {

    public static final String KEY = "cg.archetype:endpoint";

    // ==================================================================================
    // Archetype-level lexical frames
    // ==================================================================================

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the archetype of network destinations — bodies whose head is Endpoint and "
                    + "whose bindings declare a transport protocol plus the protocol's "
                    + "addressable fields (host/port, path, identity, url)";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "endpoint";

    // ==================================================================================
    // Construction — every Endpoint IS a Body (head=Endpoint, PROTOCOL + per-protocol bindings).
    // ==================================================================================

    /**
     * Build an Endpoint from a protocol sememe and the protocol's specific
     * bindings. The PROTOCOL binding is prepended automatically; callers pass
     * only the protocol-specific bindings (HOST/PORT/PATH/IDENTITY/URL).
     */
    public Endpoint(ItemRef protocol, List<Binding> protocolSpecific) {
        super(ItemRef.iid(KEY), assemble(protocol, protocolSpecific));
    }

    private static List<Binding> assemble(ItemRef protocol, List<Binding> rest) {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(rest, "protocolSpecific");
        List<Binding> all = new ArrayList<>(rest.size() + 1);
        all.add(Binding.ref(ItemRef.iid(NetworkVocabulary.Protocol.KEY), protocol));
        all.addAll(rest);
        return all;
    }

    // ==================================================================================
    // Static factories — one per built-in transport protocol.
    // ==================================================================================

    /** TCP endpoint at the given IP address and port. */
    public static Endpoint tcp(IpAddress host, int port) {
        Objects.requireNonNull(host, "host");
        checkPort(port);
        return new Endpoint(ItemRef.iid(NetworkVocabulary.Tcp.KEY), List.of(
                Binding.literal(ItemRef.iid(NetworkVocabulary.Host.KEY), host),
                Binding.literal(ItemRef.iid(NetworkVocabulary.Port.KEY), (long) port)));
    }

    /** TCP endpoint at a host string (parsed to {@link IpAddress}) and port. */
    public static Endpoint tcp(String host, int port) {
        return tcp(IpAddress.parse(host), port);
    }

    /** Unix-domain-socket endpoint at the given filesystem path. */
    public static Endpoint unix(String path) {
        Objects.requireNonNull(path, "path");
        return new Endpoint(ItemRef.iid(NetworkVocabulary.Unix.KEY), List.of(
                Binding.literal(ItemRef.iid(NetworkVocabulary.Path.KEY), path)));
    }

    /**
     * Reticulum endpoint identified by a destination hash (the bytes Reticulum
     * uses to address a destination on its mesh).
     */
    public static Endpoint reticulum(byte[] identity) {
        Objects.requireNonNull(identity, "identity");
        return new Endpoint(ItemRef.iid(NetworkVocabulary.Reticulum.KEY), List.of(
                Binding.literal(ItemRef.iid(NetworkVocabulary.Identity.KEY), identity.clone())));
    }

    /** WebSocket endpoint at the given {@code ws://} or {@code wss://} URL. */
    public static Endpoint ws(String url) {
        Objects.requireNonNull(url, "url");
        return new Endpoint(ItemRef.iid(NetworkVocabulary.Ws.KEY), List.of(
                Binding.literal(ItemRef.iid(NetworkVocabulary.Url.KEY), url)));
    }

    /**
     * In-VM loopback endpoint — the sentinel for "same process, no real
     * transport." Used to wire LoopbackTunnel pairs without inventing an
     * address.
     */
    public static Endpoint loopback() {
        return new Endpoint(ItemRef.iid(NetworkVocabulary.Loopback.KEY), List.of());
    }

    /**
     * Typed view over an existing Body whose head is the Endpoint archetype.
     * Throws if the body has no PROTOCOL binding or the head isn't Endpoint.
     */
    public static Endpoint from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof Endpoint ep) return ep;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the Endpoint archetype: " + body.headRef());
        }
        ItemRef protocol = readRef(body, NetworkVocabulary.Protocol.KEY)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Endpoint body missing PROTOCOL binding"));
        ItemRef protocolRole = ItemRef.iid(NetworkVocabulary.Protocol.KEY);
        List<Binding> rest = body.bindings().stream()
                .filter(b -> !protocolRole.equals(b.role()))
                .toList();
        return new Endpoint(protocol, rest);
    }

    // ==================================================================================
    // Accessors — read from the underlying bindings list.
    // ==================================================================================

    /** The transport protocol sememe (always present). */
    public ItemRef protocol() {
        return readRef(this, NetworkVocabulary.Protocol.KEY)
                .orElseThrow(() -> new IllegalStateException("Endpoint missing PROTOCOL binding"));
    }

    /** The host IP, present for IP-based protocols (TCP). */
    public Optional<IpAddress> host() {
        return readTarget(this, NetworkVocabulary.Host.KEY, IpAddress.class);
    }

    /** The port number, present for protocols that use ports (TCP, optionally Ws). */
    public OptionalLong port() {
        return readTarget(this, NetworkVocabulary.Port.KEY, Long.class)
                .map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    /** The filesystem path, present for Unix-socket endpoints. */
    public Optional<String> path() {
        return readTarget(this, NetworkVocabulary.Path.KEY, String.class);
    }

    /** The destination identity bytes, present for Reticulum endpoints. */
    public Optional<byte[]> identity() {
        return readTarget(this, NetworkVocabulary.Identity.KEY, byte[].class).map(byte[]::clone);
    }

    /** The URL string, present for WebSocket / HTTP-style endpoints. */
    public Optional<String> url() {
        return readTarget(this, NetworkVocabulary.Url.KEY, String.class);
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        ItemRef p = protocol();
        if (ItemRef.iid(NetworkVocabulary.Tcp.KEY).equals(p)) {
            String h = host().map(IpAddress::toHostString).orElse("?");
            long pt = port().orElse(-1);
            return "tcp://" + h + ":" + pt;
        }
        if (ItemRef.iid(NetworkVocabulary.Unix.KEY).equals(p)) {
            return "unix:" + path().orElse("?");
        }
        if (ItemRef.iid(NetworkVocabulary.Reticulum.KEY).equals(p)) {
            return "reticulum:" + identity().map(HexFormat.of()::formatHex).orElse("?");
        }
        if (ItemRef.iid(NetworkVocabulary.Ws.KEY).equals(p)) {
            return url().orElse("ws://?");
        }
        if (ItemRef.iid(NetworkVocabulary.Loopback.KEY).equals(p)) {
            return "loopback:";
        }
        return "endpoint{" + p + "}";
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static Optional<ItemRef> readRef(Body body, String roleKey) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : body.bindings()) {
            if (role.equals(b.role()) && b.target() instanceof ItemRef ref) {
                return Optional.of(ref);
            }
        }
        return Optional.empty();
    }

    private static <T> Optional<T> readTarget(Body body, String roleKey, Class<T> type) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : body.bindings()) {
            if (role.equals(b.role()) && type.isInstance(b.target())) {
                return Optional.of(type.cast(b.target()));
            }
        }
        return Optional.empty();
    }

    private static void checkPort(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be 0..65535, got " + port);
        }
    }
}
