package dev.everydaythings.graph.bridges.keri.event;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A KEL event bundled with the controller signatures attesting to it — the
 * unit KERI nodes actually exchange on the wire.
 *
 * <p>Wire layout: {@code &lt;event-bytes&gt; &lt;attachment-qb64-as-utf8&gt;}.
 * No length-prefix is needed before the attachment because the event's
 * version string declares its own byte count, so the framing for "where does
 * the event end and the attachment begin" is intrinsic.
 *
 * <p>v1 supports a single attachment group (controller indexed signatures).
 * KERI defines several other group codes (witness receipts, source seals,
 * trans receipts).  Adding those follows the same pattern: parse a group
 * header at the current position and continue.
 */
public final class SignedKelEvent {

    private final byte[] eventBytes;
    private final SignatureAttachment attachment;

    public SignedKelEvent(byte[] eventBytes, SignatureAttachment attachment) {
        this.eventBytes = eventBytes.clone();
        this.attachment = attachment;
    }

    public byte[] eventBytes() {
        return eventBytes.clone();
    }

    public SignatureAttachment attachment() {
        return attachment;
    }

    /** The parsed event map (re-parsed each call; cache at call site if hot). */
    public Map<String, Object> event() {
        return KelJson.decode(eventBytes);
    }

    /** Serialize event + attachment as a single byte sequence. */
    public byte[] toWire() {
        byte[] attachmentBytes = attachment.toQb64().getBytes(StandardCharsets.US_ASCII);
        byte[] wire = new byte[eventBytes.length + attachmentBytes.length];
        System.arraycopy(eventBytes, 0, wire, 0, eventBytes.length);
        System.arraycopy(attachmentBytes, 0, wire, eventBytes.length, attachmentBytes.length);
        return wire;
    }

    /**
     * Parse a complete signed event off the wire.  Uses the event's version
     * string to determine where the JSON ends, then parses the remainder as
     * a single attachment group.
     */
    public static SignedKelEvent parseWire(byte[] wire) {
        int eventSize = peekEventSize(wire);
        if (wire.length < eventSize) {
            throw new IllegalArgumentException(
                    "wire shorter than declared event size: " + wire.length + " < " + eventSize);
        }
        byte[] eventBytes = new byte[eventSize];
        System.arraycopy(wire, 0, eventBytes, 0, eventSize);
        KelJson.decode(eventBytes);

        String attachmentText = new String(
                wire, eventSize, wire.length - eventSize, StandardCharsets.US_ASCII);
        SignatureAttachment.Parsed parsed = SignatureAttachment.parse(attachmentText);
        if (parsed.consumed() != attachmentText.length()) {
            throw new IllegalArgumentException(
                    "unconsumed trailing data after attachment: "
                            + (attachmentText.length() - parsed.consumed()) + " chars");
        }
        return new SignedKelEvent(eventBytes, parsed.attachment());
    }

    /**
     * Read the declared event size out of the version string at the head of
     * the wire bytes, without parsing the full JSON.  The version string is
     * always at the same position: opening brace, {@code "v":"} key prefix,
     * then 17 chars of version, where positions 10–15 carry the size in hex.
     */
    private static int peekEventSize(byte[] wire) {
        if (wire.length < 22) {
            throw new IllegalArgumentException(
                    "wire too short to contain a KEL event header: " + wire.length);
        }
        String header = new String(wire, 0, 22, StandardCharsets.US_ASCII);
        if (!header.startsWith("{\"v\":\"KERI10JSON")) {
            throw new IllegalArgumentException(
                    "wire does not start with a KEL event header: " + header);
        }
        try {
            return Integer.parseInt(header.substring(16, 22), 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "KEL event header has non-hex size: " + header.substring(16, 22), e);
        }
    }
}
