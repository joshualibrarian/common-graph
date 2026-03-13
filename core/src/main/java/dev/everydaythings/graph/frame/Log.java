package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Verb;

import dev.everydaythings.graph.item.Type;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.VerbSememe;
import dev.everydaythings.graph.library.ItemStore;

import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A linear append-only log component for single-writer scenarios.
 *
 * <p>Log stores entries as a simple linked list:
 * <pre>
 * entry0 (parent=null) ← entry1 ← entry2 ← entry3 (head)
 * </pre>
 *
 * <p>Use Log when:
 * <ul>
 *   <li>Single writer (no concurrent appends)</li>
 *   <li>Simple linear history</li>
 *   <li>No merge/sync requirements</li>
 * </ul>
 *
 * <p>For multi-writer scenarios with sync/merge, use {@link Dag} instead.
 *
 * <p>Example:
 * <pre>{@code
 * @Type("cg:type/chess")
 * public class ChessGame extends Log<ChessMove> {
 *
 *     @Verb(value = "cg.verb:move", doc = "Make a chess move")
 *     public void move(ActionContext ctx, String notation) {
 *         ChessMove move = parseAndValidate(notation, currentBoard());
 *         append(ctx, move);  // inherited from Log
 *     }
 *
 *     @Verb(value = VerbSememe.Get.KEY, doc = "Get current board")
 *     public BoardState board(ActionContext ctx) {
 *         return replay(readAll(ctx));
 *     }
 * }
 * }</pre>
 *
 * @param <E> The entry/operation type
 * @see Dag
 */
@Type(value = Log.KEY, glyph = "📜")
public abstract class Log<E> implements Inspectable {

    public static final String KEY = "cg:type/log";

    /** The entry payload class, extracted from generic parameter */
    private final Class<E> entryClass;

    /** Current head of the log (CID of latest entry) */
    private ContentID head;

    /** Current sequence number (next entry gets this + 1) */
    private long sequence = -1;

    /** Session-local cache of recent entry snapshots for tree display. */
    private static final int MAX_CACHED_ENTRIES = 100;
    private final List<InspectEntry> entryCache = new ArrayList<>();

    /**
     * Construct a Log, extracting the entry type from the generic parameter.
     */
    @SuppressWarnings("unchecked")
    protected Log() {
        // Extract E from "class ChessGame extends Log<ChessMove>"
        java.lang.reflect.Type superclass = this.getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt) {
            java.lang.reflect.Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> c) {
                this.entryClass = (Class<E>) c;
            } else if (arg instanceof ParameterizedType argPt) {
                // Handle nested generics like Log<List<String>>
                this.entryClass = (Class<E>) argPt.getRawType();
            } else {
                throw new IllegalStateException(
                        "Log entry type must be a concrete class, got: " + arg);
            }
        } else {
            throw new IllegalStateException(
                    "Log must be subclassed with a type parameter");
        }
    }

    /**
     * Get the entry payload class.
     */
    public Class<E> entryClass() {
        return entryClass;
    }

    /**
     * Get the current head CID.
     */
    public Optional<ContentID> head() {
        return Optional.ofNullable(head);
    }

    /**
     * Get the current sequence number (index of head entry).
     * Returns -1 if log is empty.
     */
    public long sequence() {
        return sequence;
    }

    /**
     * Check if the log is empty.
     */
    public boolean isEmpty() {
        return head == null;
    }

    // ==================================================================================
    // Component Tree
    // ==================================================================================

    public boolean isExpandable() {
        return !isEmpty();
    }

    public String displayToken() {
        long n = count();
        return entryClass.getSimpleName() + " log" + (n > 0 ? " (" + n + ")" : "");
    }

    @Override
    public List<InspectEntry> inspectEntries() {
        // Return newest first
        List<InspectEntry> result = new ArrayList<>(entryCache.size());
        for (int i = entryCache.size() - 1; i >= 0; i--) {
            result.add(entryCache.get(i));
        }
        return result;
    }

    /**
     * Subclasses can override to customize the label for a log entry in the tree.
     *
     * @param seq     the entry's sequence number
     * @param payload the entry payload
     * @return a human-readable label for the tree node
     */
    protected String entryLabel(long seq, E payload) {
        return "#" + seq + " " + String.valueOf(payload);
    }

    /**
     * Subclasses can override to customize the emoji for a log entry.
     */
    protected String entryEmoji(long seq, E payload) {
        return "📜";
    }

    // ==================================================================================
    // Actions
    // ==================================================================================

    /**
     * Append an entry to the log.
     *
     * @param ctx   The action context (provides author, storage access)
     * @param entry The entry payload to append
     * @return The CID of the stored entry
     */
    @Verb(value = VerbSememe.Put.KEY, doc = "Append an entry to the log")
    public ContentID append(ActionContext ctx, E entry) {
        // Create the log entry
        LogEntry<E> logEntry;
        if (head == null) {
            logEntry = LogEntry.first(ctx.caller(), entry);
        } else {
            // We need to load current head to get its sequence
            // For now, use tracked sequence
            logEntry = LogEntry.<E>builder()
                    .parent(head)
                    .sequence(sequence + 1)
                    .timestamp(Instant.now())
                    .author(ctx.caller())
                    .payload(entry)
                    .build();
        }

        // Store the entry (implementation depends on storage backend)
        ContentID entryCid = storeEntry(ctx, logEntry);

        // Update head
        this.head = entryCid;
        this.sequence = logEntry.sequence();

        // Cache entry snapshot for tree display
        long seq = logEntry.sequence();
        entryCache.add(new InspectEntry(
                String.valueOf(seq),
                entryLabel(seq, entry),
                entryEmoji(seq, entry),
                entry));
        if (entryCache.size() > MAX_CACHED_ENTRIES) {
            entryCache.removeFirst();
        }

        return entryCid;
    }

    /**
     * Read all entries from oldest to newest.
     *
     * @param ctx The action context
     * @return Stream of entries in chronological order
     */
    @Verb(value = VerbSememe.ListVerb.KEY, doc = "Read all log entries")
    public Stream<LogEntry<E>> read(ActionContext ctx) {
        if (head == null) {
            return Stream.empty();
        }

        // Walk backwards from head, then reverse
        List<LogEntry<E>> entries = new ArrayList<>();
        ContentID current = head;

        while (current != null) {
            LogEntry<E> entry = loadEntry(ctx, current);
            if (entry == null) break;
            entries.add(entry);
            current = entry.parent();
        }

        Collections.reverse(entries);
        return entries.stream();
    }

    /**
     * Read just the payloads (convenience method).
     *
     * @param ctx The action context
     * @return Stream of payloads in chronological order
     */
    public Stream<E> readPayloads(ActionContext ctx) {
        return read(ctx).map(LogEntry::payload);
    }

    /**
     * Read the N most recent entries.
     *
     * @param ctx   The action context
     * @param count Maximum number of entries to return
     * @return List of entries, newest first
     */
    @Verb(value = VerbSememe.Show.KEY, doc = "Show recent entries")
    public List<LogEntry<E>> tail(ActionContext ctx, int count) {
        if (head == null || count <= 0) {
            return List.of();
        }

        List<LogEntry<E>> entries = new ArrayList<>();
        ContentID current = head;

        while (current != null && entries.size() < count) {
            LogEntry<E> entry = loadEntry(ctx, current);
            if (entry == null) break;
            entries.add(entry);
            current = entry.parent();
        }

        return entries;  // Newest first
    }

    /**
     * Get the current entry count.
     *
     * @return Number of entries (sequence + 1, or 0 if empty)
     */
    @Verb(value = VerbSememe.Count.KEY, doc = "Get entry count")
    public long count() {
        return sequence + 1;
    }

    /**
     * Get the most recent entry.
     *
     * @param ctx The action context
     * @return The head entry, or empty if log is empty
     */
    @Verb(value = VerbSememe.Get.KEY, doc = "Get the most recent entry")
    public Optional<LogEntry<E>> latest(ActionContext ctx) {
        if (head == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(loadEntry(ctx, head));
    }

    // ==================================================================================
    // Storage
    // ==================================================================================

    /**
     * Store an entry and return its CID.
     *
     * <p>Flow:
     * <ol>
     *   <li>Encode payload to bytes</li>
     *   <li>Store payload, get payloadCid</li>
     *   <li>Set payloadCid on entry</li>
     *   <li>Encode entry to bytes</li>
     *   <li>Store entry, return entryCid</li>
     * </ol>
     *
     * @param ctx   The action context (provides store)
     * @param entry The entry to store (with payload attached)
     * @return The CID of the stored entry
     */
    protected ContentID storeEntry(ActionContext ctx, LogEntry<E> entry) {
        ItemStore store = ctx.store();
        if (store == null) {
            throw new IllegalStateException("No store available in action context");
        }

        // 1. Encode payload to bytes
        E payload = entry.payload();
        if (payload == null) {
            throw new IllegalArgumentException("Entry payload cannot be null");
        }

        byte[] payloadBytes = encodePayload(payload);

        // 2. Store payload, get payloadCid
        ContentID payloadCid = store.content(payloadBytes);

        // 3. Create entry with payloadCid
        LogEntry<E> entryWithCid = entry.withPayloadCid(payloadCid);

        // 4. Encode entry to bytes (canonical encoding, without transient payload)
        byte[] entryBytes = entryWithCid.encodeBinary(Canonical.Scope.RECORD);

        // 5. Store entry, return entryCid
        return store.content(entryBytes);
    }

    /**
     * Load an entry by its CID.
     *
     * <p>Flow:
     * <ol>
     *   <li>Load entry bytes</li>
     *   <li>Decode LogEntry (without payload)</li>
     *   <li>Load payload bytes by payloadCid</li>
     *   <li>Decode payload</li>
     *   <li>Return entry with payload attached</li>
     * </ol>
     *
     * @param ctx The action context (provides store)
     * @param cid The CID of the entry to load
     * @return The entry with payload, or null if not found
     */
    @SuppressWarnings("unchecked")
    protected LogEntry<E> loadEntry(ActionContext ctx, ContentID cid) {
        ItemStore store = ctx.store();
        if (store == null) {
            throw new IllegalStateException("No store available in action context");
        }

        // 1. Load entry bytes
        byte[] entryBytes = store.content(cid).orElse(null);
        if (entryBytes == null) {
            return null;
        }

        // 2. Decode LogEntry (payload will be null)
        LogEntry<E> entry = LogEntry.decode(entryBytes);

        // 3. Load payload bytes
        ContentID payloadCid = entry.payloadCid();
        if (payloadCid == null) {
            // Entry has no payload (shouldn't happen, but handle gracefully)
            return entry;
        }

        byte[] payloadBytes = store.content(payloadCid).orElse(null);
        if (payloadBytes == null) {
            // Payload not found - return entry without payload
            return entry;
        }

        // 4. Decode payload
        E payload = decodePayload(payloadBytes);

        // 5. Return entry with payload attached
        return entry.withPayload(payload);
    }

    /**
     * Encode a payload to bytes.
     *
     * <p>Default implementation uses Canonical encoding if the payload
     * implements Canonical, otherwise throws.
     *
     * <p>Subclasses can override for custom encoding (e.g., JSON, Protobuf).
     *
     * @param payload The payload to encode
     * @return The encoded bytes
     */
    protected byte[] encodePayload(E payload) {
        if (payload instanceof Canonical c) {
            return c.encodeBinary(Canonical.Scope.RECORD);
        }
        throw new UnsupportedOperationException(
                "Payload type " + entryClass.getName() + " must implement Canonical, " +
                "or override encodePayload/decodePayload");
    }

    /**
     * Decode a payload from bytes.
     *
     * <p>Default implementation uses Canonical decoding if the payload
     * type implements Canonical, otherwise throws.
     *
     * <p>Subclasses can override for custom decoding.
     *
     * @param bytes The bytes to decode
     * @return The decoded payload
     */
    @SuppressWarnings("unchecked")
    protected E decodePayload(byte[] bytes) {
        if (!Canonical.class.isAssignableFrom(entryClass)) {
            throw new UnsupportedOperationException(
                    "Payload type " + entryClass.getName() + " must implement Canonical, " +
                    "or override encodePayload/decodePayload");
        }

        try {
            // Cast to Canonical class type for decode
            Class<? extends Canonical> canonicalClass = (Class<? extends Canonical>) entryClass;
            return (E) Canonical.decode(bytes, canonicalClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode payload of type " + entryClass.getName(), e);
        }
    }

    // ==================================================================================
    // Initialization
    // ==================================================================================

    /**
     * Initialize the log with an existing head.
     * Called when loading a component from storage.
     *
     * @param head     The head CID
     * @param sequence The current sequence number
     */
    public void initialize(ContentID head, long sequence) {
        this.head = head;
        this.sequence = sequence;
    }
}
