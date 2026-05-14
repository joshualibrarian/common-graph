package dev.everydaythings.graph.value;

import dev.everydaythings.graph.canonical.Scope;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.canonical.Factory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Canonical binary IP representation (4 or 16 bytes).
 * String forms are rendering only.
 *
 * <p>Encodes as a CBOR byte string containing the raw IP bytes (4 or 16).
 */
public final class IpAddress implements Value {

    private final byte[] bytes; // 4 or 16

    public IpAddress(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != 4 && bytes.length != 16)
            throw new IllegalArgumentException("IP must be 4 or 16 bytes, got " + bytes.length);
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

    // === Canonical encoding ===

    @Override
    public CBORObject toCborTree(Scope scope) {
        return CBORObject.FromByteArray(bytes);
    }

    /**
     * Decode from CBOR byte string.
     */
    @Factory
    public static IpAddress fromCborTree(CBORObject node) {
        return new IpAddress(node.GetByteString());
    }

    // === Value implementation ===

    @Override
    public String token() {
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception e) {
            // should never happen with 4/16 bytes
            return Arrays.toString(bytes);
        }
    }

    /** Alias for token() */
    public String toHostString() {
        return token();
    }

    /**
     * Convert to java.net.InetAddress.
     */
    public InetAddress toInetAddress() {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            // Should never happen with 4 or 16 bytes
            throw new RuntimeException("Invalid IP bytes", e);
        }
    }

    @Override
    public String toString() {
        return token();
    }

    // ==================================================================================
    // Renderable Implementation
    // ==================================================================================

    @Override
    public String emoji() {
        return "⊕";  // Network/IP address
    }

    @Override
    public String colorCategory() {
        return "value";
    }
}
