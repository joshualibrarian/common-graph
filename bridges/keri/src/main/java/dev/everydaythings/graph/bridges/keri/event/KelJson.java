package dev.everydaythings.graph.bridges.keri.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.everydaythings.graph.bridges.keri.Cesr;
import dev.everydaythings.graph.bridges.keri.MatterCode;
import dev.everydaythings.graph.identity.algorithm.Hash;
import io.ipfs.multihash.Multihash;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KEL event JSON codec — wire format for KERI's Key Event Log in JSON mode.
 *
 * <p>A KEL event on the wire is a JSON object whose first field is the
 * <b>version string</b> {@code v} (e.g. {@code "KERI10JSON000123_"}) carrying
 * the serialization format, KERI protocol version, and total byte size of the
 * serialized event.  Most events also carry a <b>self-addressing identifier</b>
 * (SAID) in the {@code d} field: the digest of the event itself with the
 * {@code d} field's value temporarily replaced by a same-length placeholder.
 *
 * <p>This class handles both pieces:
 *
 * <ul>
 *   <li>{@link #encode(Map)} serializes a JSON object that callers supply in
 *       canonical KERI field order, then back-patches the size in {@code v}
 *       and the SAID in {@code d}.</li>
 *   <li>{@link #decode(byte[])} parses bytes back to an ordered map and
 *       validates both the declared size and (if present) the SAID.</li>
 * </ul>
 *
 * <p>The hash algorithm for the SAID is parameterizable.  The no-arg encode
 * overload uses {@link #DEFAULT_SAID_CODE} (Blake3-256, matching KERI's
 * default); decode infers the algorithm from the declared SAID's CESR matter
 * code, so any single-byte-code 256-bit hash supported by {@link Hash} works
 * symmetrically.
 */
public final class KelJson {

    /** Length of the version string {@code v} field value (always 17 chars). */
    public static final int VERSION_LENGTH = 17;

    /**
     * Length of a 256-bit SAID in qb64 (1-char code + 43 base64 chars).
     * All v1-supported SAID hashes (Blake3, SHA2-256, SHA3-256, Blake2b-256,
     * Blake2s-256) produce 32 raw bytes and so share this length.
     */
    public static final int SAID_LENGTH = 44;

    /** Placeholder character that fills the SAID-sized hole during digest computation. */
    public static final char SAID_PLACEHOLDER_CHAR = '#';

    /** Placeholder string for {@code d} field while computing the SAID. */
    public static final String SAID_PLACEHOLDER =
            String.valueOf(SAID_PLACEHOLDER_CHAR).repeat(SAID_LENGTH);

    /** Version string with placeholder size; patched during {@link #encode(Map)}. */
    public static final String VERSION_PLACEHOLDER = "KERI10JSON000000_";

    /**
     * Default SAID hash algorithm: Blake3-256, matching KERI's spec default.
     * {@link Hash#compute} auto-installs BouncyCastle on first non-JDK-native
     * algorithm request, so this works out-of-the-box.  Callers can override
     * via {@link #encode(Map, String)} for any other supported algorithm
     * (pass a {@link MatterCode} constant).
     */
    public static final String DEFAULT_SAID_CODE = MatterCode.BLAKE3_256;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KelJson() {}

    /**
     * Serialize an event map to KEL-JSON wire bytes, using {@link
     * #DEFAULT_SAID_CODE} (Blake3-256) for the SAID.  See {@link #encode(Map,
     * String)} for the parameterized form.
     */
    public static byte[] encode(Map<String, Object> event) {
        return encode(event, DEFAULT_SAID_CODE);
    }

    /**
     * Serialize an event map to KEL-JSON wire bytes with the given SAID hash
     * algorithm.  The caller is responsible for field ordering: pass a
     * {@link LinkedHashMap} (or otherwise-ordered map) whose first key is
     * {@code v} and (if present) whose {@code d} field is set to
     * {@link #SAID_PLACEHOLDER}.
     *
     * <p>This method:
     * <ol>
     *   <li>Writes the map's {@code v} value to {@link #VERSION_PLACEHOLDER}
     *       (overriding whatever the caller put there).</li>
     *   <li>Serializes the JSON bytes.</li>
     *   <li>Patches the 6-hex-char size into {@code v}.</li>
     *   <li>If the map carries a {@code d} field, computes the digest under
     *       {@code saidCode} over the bytes (with {@code d} at the placeholder),
     *       qb64-encodes with that code, and patches it into the bytes in
     *       place.</li>
     * </ol>
     *
     * @throws IllegalArgumentException if {@code saidCode} doesn't name a
     *         256-bit hash supported by {@link Hash}
     */
    public static byte[] encode(Map<String, Object> event, String saidCode) {
        if (!event.containsKey("v")) {
            throw new IllegalArgumentException("KEL event missing required field 'v'");
        }
        Map<String, Object> working = new LinkedHashMap<>(event);
        working.put("v", VERSION_PLACEHOLDER);
        if (working.containsKey("d")) {
            working.put("d", SAID_PLACEHOLDER);
        }

        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(working);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to serialize KEL event", e);
        }

        patchVersionSize(bytes, bytes.length);
        if (working.containsKey("d")) {
            patchSaid(bytes, saidCode);
        }
        return bytes;
    }

    /**
     * Parse KEL-JSON wire bytes back to an event map.  Validates the declared
     * size in {@code v} and, if {@code d} is present, recomputes and compares
     * the SAID using the hash algorithm identified by the SAID's CESR matter
     * code prefix.
     *
     * @throws IllegalArgumentException if size mismatches, version string is
     *         malformed, or SAID verification fails
     */
    public static Map<String, Object> decode(byte[] wire) {
        LinkedHashMap<String, Object> parsed;
        try {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Object> raw = MAPPER.readValue(wire, LinkedHashMap.class);
            parsed = raw;
        } catch (IOException e) {
            throw new IllegalArgumentException("malformed KEL event JSON", e);
        }
        Object versionObj = parsed.get("v");
        if (!(versionObj instanceof String version)) {
            throw new IllegalArgumentException("KEL event missing or non-string 'v' field");
        }
        int declared = parseVersionSize(version);
        if (declared != wire.length) {
            throw new IllegalArgumentException(
                    "KEL event size mismatch: 'v' declares " + declared
                            + " bytes, actual " + wire.length);
        }

        if (parsed.containsKey("d")) {
            verifySaid(wire, (String) parsed.get("d"));
        }
        return parsed;
    }

    // ==================================================================================
    // Version string
    // ==================================================================================

    private static void patchVersionSize(byte[] bytes, int size) {
        String hex = String.format("%06X", size);
        int versionPos = indexOf(bytes, VERSION_PLACEHOLDER.getBytes(StandardCharsets.UTF_8));
        if (versionPos < 0) {
            throw new IllegalStateException("version placeholder not found in serialized bytes");
        }
        byte[] hexBytes = hex.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(hexBytes, 0, bytes, versionPos + "KERI10JSON".length(), 6);
    }

    private static int parseVersionSize(String version) {
        if (version.length() != VERSION_LENGTH
                || !version.startsWith("KERI10JSON")
                || version.charAt(VERSION_LENGTH - 1) != '_') {
            throw new IllegalArgumentException(
                    "KEL event has malformed version string: " + version);
        }
        String hex = version.substring("KERI10JSON".length(), VERSION_LENGTH - 1);
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("KEL event version size not hex: " + hex, e);
        }
    }

    // ==================================================================================
    // SAID (self-addressing identifier) — d field
    // ==================================================================================

    private static void patchSaid(byte[] bytes, String saidCode) {
        int dValuePos = locateSaidValue(bytes);
        byte[] digest = hashFor(saidCode).digest(bytes);
        String said = Cesr.encodePrimitive(saidCode, digest);
        byte[] saidBytes = said.getBytes(StandardCharsets.US_ASCII);
        if (saidBytes.length != SAID_LENGTH) {
            throw new IllegalStateException(
                    "computed SAID length " + saidBytes.length + " ≠ expected " + SAID_LENGTH);
        }
        System.arraycopy(saidBytes, 0, bytes, dValuePos, SAID_LENGTH);
    }

    private static void verifySaid(byte[] wire, String declared) {
        if (declared.length() != SAID_LENGTH) {
            throw new IllegalArgumentException(
                    "KEL event SAID has wrong length: " + declared.length());
        }
        Cesr.Primitive primitive = Cesr.decodePrimitive(declared);
        Hash hashAlg = hashFor(primitive.code());

        byte[] forDigest = wire.clone();
        int dValuePos = locateSaidValue(forDigest);
        byte[] placeholder = SAID_PLACEHOLDER.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(placeholder, 0, forDigest, dValuePos, SAID_LENGTH);

        byte[] digest = hashAlg.digest(forDigest);
        String recomputed = Cesr.encodePrimitive(primitive.code(), digest);
        if (!recomputed.equals(declared)) {
            throw new IllegalArgumentException(
                    "KEL event SAID mismatch: declared " + declared
                            + ", recomputed " + recomputed);
        }
    }

    private static int locateSaidValue(byte[] bytes) {
        byte[] marker = "\"d\":\"".getBytes(StandardCharsets.US_ASCII);
        int pos = indexOf(bytes, marker);
        if (pos < 0) {
            throw new IllegalStateException("d field not found in KEL event bytes");
        }
        return pos + marker.length;
    }

    /** Resolve a CESR matter code to the corresponding CG {@link Hash} instance. */
    private static Hash hashFor(String code) {
        Multihash.Type type = MatterCode.multihashType(code);
        Hash hash = type == null ? null : Hash.builtinByMultihashType(type);
        if (hash == null) {
            throw new IllegalArgumentException(
                    "No Hash algorithm available for CESR matter code " + code);
        }
        return hash;
    }

    // ==================================================================================
    // Internals
    // ==================================================================================

    private static int indexOf(byte[] haystack, byte[] needle) {
        if (needle.length == 0) return 0;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
