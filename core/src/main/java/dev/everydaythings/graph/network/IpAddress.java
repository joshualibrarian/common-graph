package dev.everydaythings.graph.network;

import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.canonical.Encode;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Canonical binary IP representation (4 or 16 bytes). String forms are rendering only.
 *
 * <p>Wire form: bare CBOR byte string containing the raw IP bytes. Length
 * (4 or 16) discriminates v4 vs v6 without a separate flag.
 *
 * <p>What the bytes <i>mean</i> — that they're an IP address rather than
 * some other 4/16-byte blob — comes from the surrounding binding's
 * qualifiers, not from any type marker on the value itself.
 */
public final class IpAddress {

    private final byte[] bytes; // 4 or 16

    public IpAddress(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != 4 && bytes.length != 16) {
            throw new IllegalArgumentException("IP must be 4 or 16 bytes, got " + bytes.length);
        }
        this.bytes = bytes.clone();
    }

    public static IpAddress fromInetAddress(InetAddress addr) {
        Objects.requireNonNull(addr, "addr");
        return new IpAddress(addr.getAddress());
    }

    public static IpAddress parse(String text) {
        try {
            return fromInetAddress(InetAddress.getByName(text));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP: " + text, e);
        }
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public boolean isV4() { return bytes.length == 4; }
    public boolean isV6() { return bytes.length == 16; }

    /** Self-describing wire form: the raw 4 or 16 bytes. */
    @Encode
    public byte[] encodeBinary() {
        return bytes.clone();
    }

    @Decode
    public static IpAddress fromBinary(byte[] bytes) {
        return new IpAddress(bytes);
    }

    /** Human-readable form (dotted-quad for v4, colon-hex for v6). */
    public String toHostString() {
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception e) {
            return Arrays.toString(bytes);
        }
    }

    public InetAddress toInetAddress() {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Invalid IP bytes", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IpAddress other)) return false;
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return toHostString();
    }
}
