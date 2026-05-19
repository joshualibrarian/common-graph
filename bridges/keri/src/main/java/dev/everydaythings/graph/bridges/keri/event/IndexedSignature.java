package dev.everydaythings.graph.bridges.keri.event;

import java.util.Base64;

/**
 * A single controller signature with its key-list index, encoded in CESR's
 * "indexed signature" form.
 *
 * <p>An indexed signature wraps an Ed25519 signature with the position of the
 * key that produced it within the signing-keys list of the relevant KEL event.
 * On the wire it's a fixed-width 88-character qb64 string:
 *
 * <pre>
 *   A &lt;idx-char&gt; &lt;86 base64-url chars of the 64-byte signature&gt;
 * </pre>
 *
 * <p>The code character {@code A} selects "Ed25519 indexed signature, current
 * and next indices identical" — the simple v1 case.  KERI also defines
 * dual-indexed forms where the current and next-rotation indices differ; we
 * don't model those in v1 (single-key controllers don't need them).
 */
public final class IndexedSignature {

    /** 1-char CESR code for Ed25519 indexed signature (same current + next index). */
    public static final char CODE = 'A';

    /** Fixed total length of an indexed-signature qb64 string. */
    public static final int QB64_LENGTH = 88;

    /** Length of the Ed25519 signature payload in raw bytes. */
    public static final int SIGNATURE_LENGTH = 64;

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    /**
     * Base64-url alphabet for the index character: 0–25 ⇒ A–Z, 26–51 ⇒ a–z,
     * 52–61 ⇒ 0–9, 62 ⇒ -, 63 ⇒ _ (same alphabet base64-url uses for its
     * payload, since they share the same encoding).
     */
    private static final String BASE64_URL_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private final int index;
    private final byte[] signature;

    public IndexedSignature(int index, byte[] signature) {
        if (index < 0 || index > 63) {
            throw new IllegalArgumentException("indexed signature index must be 0..63, got " + index);
        }
        if (signature.length != SIGNATURE_LENGTH) {
            throw new IllegalArgumentException(
                    "Ed25519 signature must be " + SIGNATURE_LENGTH + " bytes, got " + signature.length);
        }
        this.index = index;
        this.signature = signature.clone();
    }

    /** The signer's index in the event's signing-keys list. */
    public int index() {
        return index;
    }

    /** Defensive copy of the signature bytes. */
    public byte[] signature() {
        return signature.clone();
    }

    /** Encode this signature in its 88-character qb64 form. */
    public String toQb64() {
        char idxChar = BASE64_URL_ALPHABET.charAt(index);
        return CODE + String.valueOf(idxChar) + URL_ENCODER.encodeToString(signature);
    }

    /** Parse a qb64-encoded indexed signature. */
    public static IndexedSignature parse(String qb64) {
        if (qb64.length() != QB64_LENGTH) {
            throw new IllegalArgumentException(
                    "indexed signature must be " + QB64_LENGTH + " chars, got " + qb64.length());
        }
        if (qb64.charAt(0) != CODE) {
            throw new IllegalArgumentException(
                    "indexed signature must start with code '" + CODE + "', got '" + qb64.charAt(0) + "'");
        }
        int idx = BASE64_URL_ALPHABET.indexOf(qb64.charAt(1));
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "indexed signature has non-base64 index char: " + qb64.charAt(1));
        }
        byte[] sig = URL_DECODER.decode(qb64.substring(2));
        return new IndexedSignature(idx, sig);
    }
}
