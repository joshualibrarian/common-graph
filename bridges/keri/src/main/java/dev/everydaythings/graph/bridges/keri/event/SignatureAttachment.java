package dev.everydaythings.graph.bridges.keri.event;

import java.util.ArrayList;
import java.util.List;

/**
 * A CESR attachment carrying one or more {@link IndexedSignature}s in the
 * "controller indexed signature group" form.
 *
 * <p>Wire layout:
 *
 * <pre>
 *   -A &lt;2-char base64 count&gt; &lt;count × 88-char indexed signature&gt;
 * </pre>
 *
 * <p>{@code -A} is the CESR group selector for indexed-signature groups; the
 * 2-char base64 count encodes the number of indexed signatures in the group
 * (so up to 4095 sigs per group).  The signatures follow concatenated, each
 * in its 88-character qb64 form.
 */
public final class SignatureAttachment {

    /** Group selector prefix. */
    public static final String GROUP_CODE = "-A";

    /** Length of the count field in base64 chars. */
    public static final int COUNT_LENGTH = 2;

    /** Length of the fixed header: group code (2) + count (2). */
    public static final int HEADER_LENGTH = GROUP_CODE.length() + COUNT_LENGTH;

    private static final String BASE64_URL_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private final List<IndexedSignature> signatures;

    public SignatureAttachment(List<IndexedSignature> signatures) {
        if (signatures.isEmpty()) {
            throw new IllegalArgumentException("SignatureAttachment must contain at least one signature");
        }
        if (signatures.size() > 4095) {
            throw new IllegalArgumentException(
                    "SignatureAttachment cannot exceed 4095 signatures, got " + signatures.size());
        }
        this.signatures = List.copyOf(signatures);
    }

    /** The attached signatures, in attachment order. */
    public List<IndexedSignature> signatures() {
        return signatures;
    }

    /** Encode the entire attachment group as a single qb64 string. */
    public String toQb64() {
        StringBuilder sb = new StringBuilder(HEADER_LENGTH + signatures.size() * IndexedSignature.QB64_LENGTH);
        sb.append(GROUP_CODE);
        sb.append(encodeCount(signatures.size()));
        for (IndexedSignature sig : signatures) {
            sb.append(sig.toQb64());
        }
        return sb.toString();
    }

    /**
     * Parse a single attachment group from the start of {@code qb64}.  Returns
     * both the parsed attachment and the number of characters consumed, so the
     * caller can continue parsing additional groups from the remaining text.
     */
    public static Parsed parse(String qb64) {
        if (qb64.length() < HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "attachment text too short to contain a group header: " + qb64.length());
        }
        if (!qb64.startsWith(GROUP_CODE)) {
            throw new IllegalArgumentException(
                    "attachment text does not start with group code '" + GROUP_CODE + "'");
        }
        int count = decodeCount(qb64.substring(GROUP_CODE.length(), HEADER_LENGTH));
        int consumed = HEADER_LENGTH + count * IndexedSignature.QB64_LENGTH;
        if (qb64.length() < consumed) {
            throw new IllegalArgumentException(
                    "attachment text declares " + count + " signatures but has only "
                            + (qb64.length() - HEADER_LENGTH) + " payload chars");
        }
        List<IndexedSignature> sigs = new ArrayList<>(count);
        int pos = HEADER_LENGTH;
        for (int i = 0; i < count; i++) {
            sigs.add(IndexedSignature.parse(qb64.substring(pos, pos + IndexedSignature.QB64_LENGTH)));
            pos += IndexedSignature.QB64_LENGTH;
        }
        return new Parsed(new SignatureAttachment(sigs), consumed);
    }

    /** Result of {@link #parse(String)}: the parsed attachment + chars consumed. */
    public static final class Parsed {
        private final SignatureAttachment attachment;
        private final int consumed;

        public Parsed(SignatureAttachment attachment, int consumed) {
            this.attachment = attachment;
            this.consumed = consumed;
        }

        public SignatureAttachment attachment() {
            return attachment;
        }

        public int consumed() {
            return consumed;
        }
    }

    // ==================================================================================
    // Count codec
    // ==================================================================================

    private static String encodeCount(int count) {
        int high = (count >>> 6) & 0x3F;
        int low = count & 0x3F;
        return new String(new char[]{
                BASE64_URL_ALPHABET.charAt(high),
                BASE64_URL_ALPHABET.charAt(low)});
    }

    private static int decodeCount(String two) {
        int high = BASE64_URL_ALPHABET.indexOf(two.charAt(0));
        int low = BASE64_URL_ALPHABET.indexOf(two.charAt(1));
        if (high < 0 || low < 0) {
            throw new IllegalArgumentException("attachment count contains non-base64 chars: " + two);
        }
        return (high << 6) | low;
    }
}
