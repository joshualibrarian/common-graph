package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.encoding.CgCbor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Compress and decompress {@link BindingTarget.CompressedTarget}s around
 * {@link Body} (and other datum) values.
 *
 * <p>Compression preserves the structural Merkle hash: the
 * {@code CompressedTarget}'s {@code originalDatumId} equals the DatumID of
 * the body it wraps.  Verifying a decompressed body's hash against the
 * cached one is how a consumer detects a forged payload.
 *
 * <p>Uses {@code java.util.zip} deflate — universally available, decent
 * compression on CBOR's prose-heavy payloads, no extra dependencies.
 */
public final class Compress {

    private Compress() {}

    /**
     * Wrap a {@link Body} as a {@code CompressedTarget}.  Computes the body's
     * DatumID, encodes the body to CG-CBOR bytes, deflates those bytes,
     * returns a target carrying both the hash and the compressed payload.
     */
    public static BindingTarget.CompressedTarget compress(Body body) {
        byte[] datumId = HashTree.hashOf(body, HashTree.DEFAULT_DIGEST);
        byte[] cborBytes = CgCbor.codec().encode(body);
        byte[] compressed = deflate(cborBytes);
        return new BindingTarget.CompressedTarget(datumId, compressed);
    }

    /**
     * Decompress and decode a {@code CompressedTarget}'s payload back to a
     * {@link Body}.  Does NOT verify the resulting body's hash against the
     * target's cached hash; callers wanting that check should compare
     * {@code HashTree.hashOf(decompressed)} against {@code target.originalDatumId()}.
     */
    public static Body decompress(BindingTarget.CompressedTarget target) {
        byte[] inflated = inflate(target.compressedPayload());
        Object decoded = CgCbor.codec().decode(inflated);
        if (!(decoded instanceof Body body)) {
            throw new IllegalStateException(
                    "CompressedTarget payload did not decode as a Body: "
                            + (decoded == null ? "null" : decoded.getClass().getName()));
        }
        return body;
    }

    /**
     * Decompress, decode, and verify hash matches the cached original.
     * Throws if the payload's hash differs from the cached hash — the only
     * defense against a forged compressed payload.
     */
    public static Body decompressAndVerify(BindingTarget.CompressedTarget target) {
        Body body = decompress(target);
        byte[] actual = HashTree.hashOf(body, HashTree.DEFAULT_DIGEST);
        byte[] expected = target.originalDatumId();
        if (!java.util.Arrays.equals(actual, expected)) {
            throw new IllegalStateException(
                    "CompressedTarget hash mismatch — payload may be forged");
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
