package dev.everydaythings.graph.library.bytestore;

import dev.everydaythings.graph.item.id.*;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Key part encoders for byte store key composition.
 *
 * <p>Each constant knows how to turn a strongly-typed input into a canonical
 * byte slice. Column schemas compose several of these parts with {@link #cat(byte[]...)}.
 *
 * <p>Supported encoders:
 * <ul>
 *   <li>RAW - pass-through for byte[], ByteBuffer, or String</li>
 *   <li>ID - HashID (32-byte item identifier)</li>
 *   <li>U64 - unsigned 64-bit integer, big-endian</li>
 *   <li>HANDLE - UTF-8 handle string</li>
 *   <li>IP4 - IPv4 address (4 bytes)</li>
 *   <li>IP6 - IPv6 address (16 bytes)</li>
 *   <li>TAG - 1-byte tag prefix</li>
 * </ul>
 */
public enum KeyEncoder {
    /** Pass-through: byte[], ByteBuffer (remaining), or String (UTF-8). */
    RAW {
        @Override
        public byte[] bytes(Object o) {
            switch (o) {
                case null -> {
                    return EMPTY;
                }
                case byte[] b -> {
                    return b;
                }
                case ByteBuffer b -> {
                    ByteBuffer dup = b.slice();
                    byte[] out = new byte[dup.remaining()];
                    dup.get(out);
                    return out;
                }
                case CharSequence s -> {
                    return s.toString().getBytes(StandardCharsets.UTF_8);
                }
                default -> throw type(o, "byte[], ByteBuffer, or String");
            }

        }
    },

    /** Base ItemID (32B random id). Accepts ItemID or any ItemScopedID (uses its scope). */
    ID {
        @Override public byte[] bytes(Object o) {
            if (o instanceof HashID id) return id.encodeBinary();
            throw type(o, "HashID");
        }
    },

    /** Unsigned 64-bit integer, big-endian (8 bytes). Ordering-safe for sorted stores. */
    U64 {
        @Override
        public byte[] bytes(Object o) {
            long v = switch (o) {
                case Long l -> l;
                case Integer i -> i.longValue();
                case Number n -> n.longValue();
                case null, default -> throw type(o, "long | int | Number (U64)");
            };

            // Interpret as unsigned; caller is responsible for semantic meaning
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.putLong(v);
            return buf.array(); // big-endian by default
        }
    },

    /**
     * Handle string (UTF-8). Null or empty returns zero-length (useful as a terminal key part).
     * Keep this as the LAST key part in a schema if you intend to support "no handle".
     */
    HANDLE {
        @Override public byte[] bytes(Object o) {
            if (o == null) return EMPTY;
            String s = (o instanceof CharSequence cs) ? cs.toString() : null;
            if (s == null) throw type(o, "String (handle)");
            return s.isEmpty() ? EMPTY : s.getBytes(StandardCharsets.UTF_8);
        }
    },

    /** IPv4 address: exactly 4 bytes (network order). Accepts byte[4], Inet4Address, or IPv4 string. */
    IP4 {
        @Override public byte[] bytes(Object o) {
            if (o instanceof byte[] b) {
                if (b.length == 4) return b;
                if (b.length == 16 && isV4Mapped(b)) return Arrays.copyOfRange(b, 12, 16);
                throw type(o, "byte[4] or mapped IPv4 in byte[16]");
            }
            if (o instanceof Inet4Address v4) return v4.getAddress();
            if (o instanceof InetAddress ia) {
                byte[] a = ia.getAddress();
                if (a.length == 4) return a;
                if (a.length == 16 && isV4Mapped(a)) return Arrays.copyOfRange(a, 12, 16);
                throw new IllegalArgumentException("Expected IPv4, got IPv6: " + ia);
            }
            if (o instanceof CharSequence cs) {
                InetAddress ia = parseInet(cs.toString());
                return IP4.bytes(ia); // recurse to normalize
            }
            throw type(o, "Inet4Address | String(IPv4) | byte[4]");
        }
    },

    /** IPv6 address: exactly 16 bytes (network order). Accepts byte[16], Inet6Address, or IPv6 string. */
    IP6 {
        @Override public byte[] bytes(Object o) {
            if (o instanceof byte[] b) {
                if (b.length == 16) return b;
                throw type(o, "byte[16]");
            }
            if (o instanceof Inet6Address v6) return v6.getAddress();
            if (o instanceof InetAddress ia) {
                byte[] a = ia.getAddress();
                if (a.length == 16) return a;
                throw new IllegalArgumentException("Expected IPv6, got IPv4: " + ia);
            }
            if (o instanceof CharSequence cs) {
                InetAddress ia = parseInet(cs.toString());
                if (!(ia instanceof Inet6Address)) {
                    throw new IllegalArgumentException("Expected IPv6 string, got IPv4: " + cs);
                }
                return ia.getAddress();
            }
            throw type(o, "Inet6Address | String(IPv6) | byte[16]");
        }
    },

    /** 1-byte tag prefix for composite keys (e.g., pin keys). */
    TAG {
        @Override public byte[] bytes(Object o) {
            if (o instanceof HashID.IdType t) return new byte[] { t.tag() };
            if (o instanceof Byte b) return new byte[] { b };
            if (o instanceof Integer i) return new byte[] { (byte)(i & 0xff) };
            throw type(o, "IdType | byte | int (0..255)");
        }
    },
    ;

    private static final byte[] EMPTY = new byte[0];

    /**
     * Encode the given object to bytes according to this encoder's rules.
     */
    public abstract byte[] bytes(Object o);

    /**
     * Concatenate key parts (no separators). Parts should be fixed-size or well-delimited by position.
     */
    public static byte[] cat(byte[]... segments) {
        int len = 0;
        for (byte[] s : segments) len += (s == null ? 0 : s.length);
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] s : segments) {
            if (s == null || s.length == 0) continue;
            System.arraycopy(s, 0, out, pos, s.length);
            pos += s.length;
        }
        return out;
    }

    // --- helpers for IP parsing / normalization ---
    private static InetAddress parseInet(String s) {
        String v = s.trim();
        if (v.startsWith("[") && v.endsWith("]")) v = v.substring(1, v.length() - 1); // tolerate [v6]
        try { return InetAddress.getByName(v); }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + s, e);
        }
    }

    private static boolean isV4Mapped(byte[] a) {
        if (a.length != 16) return false;
        for (int i = 0; i < 10; i++) if (a[i] != 0) return false;
        return (a[10] == (byte)0xff && a[11] == (byte)0xff);
    }

    private static IllegalArgumentException type(Object o, String expected) {
        return new IllegalArgumentException("KeyEncoder expects " + expected + " but got: " +
                (o == null ? "null" : o.getClass().getName()));
    }
}
