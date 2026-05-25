package dev.everydaythings.graph.encoding;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Opaque;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Compress and decompress {@link Body} values into {@link Opaque.Compressed}
 * wrappers, using a caller-supplied {@link Encoding} to turn the body into
 * (and out of) bytes.
 *
 * <p>Lives in {@code encoding/} because compression operates on encoded
 * bytes — you can't deflate an abstract structure, only its byte form.
 * The {@code datum/} package stays encoding-agnostic; the
 * {@link Opaque.Compressed} type it defines simply carries the resulting
 * bytes plus the original body's structural hash.
 *
 * <h2>Hash preservation</h2>
 *
 * <p>The returned {@code Opaque.Compressed}'s {@code wrappedHash} equals the
 * DatumID of the body it wraps.  This is what makes compression
 * Merkle-transparent: a parent body containing a compressed child hashes
 * identically to the same parent containing the child inline.  See
 * {@link dev.everydaythings.graph.canonical.CanonWalker}'s Opaque handling
 * for the short-circuit that makes this work.
 *
 * <h2>Encoding contract</h2>
 *
 * <p>The encoding used at {@link #compress} time must be the same one used
 * at {@link #decompress} time.  {@code Opaque.Compressed} does not self-
 * describe its encoding; the caller knows from context (the library it came
 * from, the codec the connection negotiated, etc.).
 *
 * <p>Uses {@code java.util.zip} deflate for the byte compression itself —
 * universally available, decent on prose-heavy payloads, no extra
 * dependencies.
 */
public final class Compress {

    private Compress() {}

    /**
     * Wrap a {@link Body} as an {@link Opaque.Compressed}.  Computes the
     * body's DatumID (encoding-independent), encodes the body to bytes via
     * {@code encoding}, deflates those bytes, returns an Opaque carrying both
     * the hash and the compressed payload.
     */
    public static Opaque.Compressed compress(Body body, Encoding encoding) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(encoding, "encoding");
        byte[] datumId = HashTree.hashOf(body, HashTree.DEFAULT_DIGEST);
        byte[] encoded = encoding.encode(body);
        byte[] compressed = deflate(encoded);
        return new Opaque.Compressed(datumId, compressed);
    }

    /**
     * Decompress and decode an {@link Opaque.Compressed}'s payload back to a
     * {@link Body} via {@code encoding}.  Does NOT verify the resulting
     * body's hash against the cached hash; callers wanting that check should
     * use {@link #decompressAndVerify(Opaque.Compressed, Encoding)} instead.
     */
    public static Body decompress(Opaque.Compressed opaque, Encoding encoding) {
        Objects.requireNonNull(opaque, "opaque");
        Objects.requireNonNull(encoding, "encoding");
        byte[] inflated = inflate(opaque.compressedPayload());
        Object decoded = encoding.decode(inflated);
        if (!(decoded instanceof Body body)) {
            throw new IllegalStateException(
                    "Opaque.Compressed payload did not decode as a Body: "
                            + (decoded == null ? "null" : decoded.getClass().getName()));
        }
        return body;
    }

    /**
     * Decompress, decode, and verify hash matches the cached original.
     * Throws if the payload's hash differs from the cached hash — the only
     * defense against a forged compressed payload.
     */
    public static Body decompressAndVerify(Opaque.Compressed opaque, Encoding encoding) {
        Body body = decompress(opaque, encoding);
        byte[] actual = HashTree.hashOf(body, HashTree.DEFAULT_DIGEST);
        byte[] expected = opaque.wrappedHash();
        if (!Arrays.equals(actual, expected)) {
            throw new IllegalStateException(
                    "Opaque.Compressed hash mismatch — payload may be forged");
        }
        return body;
    }

    private static byte[] deflate(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(out)) {
            deflater.write(input);
        } catch (IOException e) {
            throw new RuntimeException("Deflate failed", e);
        }
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InflaterInputStream inflater =
                     new InflaterInputStream(new ByteArrayInputStream(input))) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = inflater.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            throw new RuntimeException("Inflate failed", e);
        }
        return out.toByteArray();
    }
}
