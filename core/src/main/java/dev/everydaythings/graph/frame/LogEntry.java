package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * A single entry in a Log.
 *
 * <p>LogEntry wraps the payload with metadata:
 * <ul>
 *   <li>parent - CID of previous entry (forms the chain)</li>
 *   <li>sequence - index in the log (0-based)</li>
 *   <li>timestamp - when the entry was appended</li>
 *   <li>author - who appended the entry</li>
 *   <li>payload - the actual entry data (encoded separately)</li>
 * </ul>
 *
 * <p>The log is a linked list of entries via parent CIDs:
 * <pre>
 * entry0 (parent=null) ← entry1 ← entry2 ← entry3 (head)
 * </pre>
 *
 * @param <E> The payload type
 */
@Getter
public final class LogEntry<E> implements Canonical {

    @Canon(order = 0)
    private ContentID parent;

    @Canon(order = 1)
    private long sequence;

    @Canon(order = 2)
    private Instant timestamp;

    @Canon(order = 3)
    private ItemID author;

    @Canon(order = 4)
    private ContentID payloadCid;

    /** The decoded payload - transient, not part of canonical encoding */
    private transient E payload;

    @Builder
    public LogEntry(
            ContentID parent,
            long sequence,
            Instant timestamp,
            ItemID author,
            ContentID payloadCid,
            E payload
    ) {
        this.parent = parent;  // null for first entry
        this.sequence = sequence;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.author = Objects.requireNonNull(author, "author");
        this.payloadCid = payloadCid;  // set after storing payload
        this.payload = payload;
    }

    /** No-arg constructor for Canonical decoding */
    @SuppressWarnings("unused")
    private LogEntry() {}

    /**
     * Check if this is the first entry in the log.
     */
    public boolean isFirst() {
        return parent == null;
    }

    /**
     * Create the first entry in a log.
     */
    public static <E> LogEntry<E> first(ItemID author, E payload) {
        return LogEntry.<E>builder()
                .parent(null)
                .sequence(0)
                .timestamp(Instant.now())
                .author(author)
                .payload(payload)
                .build();
    }

    /**
     * Create a subsequent entry following this one.
     *
     * @param entryCid The CID of THIS entry (becomes parent of new entry)
     * @param author   Who is appending
     * @param payload  The new entry's payload
     */
    public <E> LogEntry<E> next(ContentID entryCid, ItemID author, E payload) {
        return LogEntry.<E>builder()
                .parent(entryCid)
                .sequence(this.sequence + 1)
                .timestamp(Instant.now())
                .author(author)
                .payload(payload)
                .build();
    }

    /**
     * Return a copy with the payloadCid set.
     */
    public LogEntry<E> withPayloadCid(ContentID cid) {
        return LogEntry.<E>builder()
                .parent(this.parent)
                .sequence(this.sequence)
                .timestamp(this.timestamp)
                .author(this.author)
                .payloadCid(cid)
                .payload(this.payload)
                .build();
    }

    /**
     * Return a copy with the decoded payload attached.
     */
    public LogEntry<E> withPayload(E payload) {
        return LogEntry.<E>builder()
                .parent(this.parent)
                .sequence(this.sequence)
                .timestamp(this.timestamp)
                .author(this.author)
                .payloadCid(this.payloadCid)
                .payload(payload)
                .build();
    }

    /**
     * Decode a LogEntry from bytes.
     *
     * <p>Note: The decoded entry will have payload=null. The caller must
     * separately load and attach the payload using {@link #withPayload}.
     *
     * @param bytes The encoded bytes
     * @return The decoded LogEntry (without payload)
     */
    @SuppressWarnings("unchecked")
    public static <E> LogEntry<E> decode(byte[] bytes) {
        return Canonical.decode(bytes, LogEntry.class);
    }
}
