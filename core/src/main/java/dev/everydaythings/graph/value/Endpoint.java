package dev.everydaythings.graph.value;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.Factory;
import lombok.Getter;

import java.util.Objects;

/**
 * Network endpoint for Librarian-to-Librarian communication.
 *
 * <p>An Endpoint consists of:
 * <ul>
 *   <li>protocol - Protocol identifier (e.g., "cg" for Common Graph protocol)</li>
 *   <li>host - Binary IP address (IPv4 or IPv6)</li>
 *   <li>port - Port number (0-65535)</li>
 * </ul>
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Host is binary IP only (no DNS) for canonical representation</li>
 *   <li>Protocol is a simple string; version negotiation happens at connection time</li>
 *   <li>Stored as structured CBOR via Canonical for type safety</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * Endpoint ep = Endpoint.cg(IpAddress.parse("192.168.1.1"), 7432);
 * Literal lit = Literal.of(ep);  // Uses @Value.Type annotation
 * }</pre>
 */
@Getter
@Value.Type("cg.value:endpoint")
@Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
public final class Endpoint implements Value {

    /** Default protocol for Common Graph communication. */
    public static final String PROTOCOL_CG = "cg";

    @Canon(order = 0)
    private final String protocol;

    @Canon(order = 1)
    private final IpAddress host;

    @Canon(order = 2)
    private final int port;

    public Endpoint(String protocol, IpAddress host, int port) {
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.host = Objects.requireNonNull(host, "host");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        this.port = port;
    }

    /**
     * Create an endpoint with the specified protocol.
     */
    public static Endpoint of(String protocol, IpAddress host, int port) {
        return new Endpoint(protocol, host, port);
    }

    /**
     * Create a Common Graph protocol endpoint.
     */
    public static Endpoint cg(IpAddress host, int port) {
        return new Endpoint(PROTOCOL_CG, host, port);
    }

    @Override
    public String token() {
        String hostString = host.isV6() ? "[" + host.toHostString() + "]" : host.toHostString();
        return protocol + "://" + hostString + ":" + port;
    }

    @Override
    public String toString() {
        return token();
    }

    // No-arg constructor for Canonical decoding
    @SuppressWarnings("unused")
    private Endpoint() {
        this.protocol = null;
        this.host = null;
        this.port = 0;
    }

    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject obj = CBORObject.NewMap();
        obj.set("protocol", CBORObject.FromString(protocol));
        obj.set("host", host.toCborTree(scope));
        obj.set("port", CBORObject.FromInt32(port));
        return obj;
    }

    /**
     * Decode from CBOR.
     */
    @Factory
    public static Endpoint fromCborTree(CBORObject obj) {
        String protocol = obj.get("protocol").AsString();
        IpAddress host = IpAddress.fromCborTree(obj.get("host"));
        int port = obj.get("port").AsInt32();
        return new Endpoint(protocol, host, port);
    }
}
