package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * A single entry in the session activity log.
 *
 * <p>Captures what the user typed, what happened, and where they were.
 * The activity log is the persistent record of user interactions in a session.
 *
 * <p>Entries are lightweight summaries — they don't hold live Item references,
 * just IIDs and display text. This makes them safe to serialize and query.
 *
 * @see ActivityLog
 */
@Getter
@Accessors(fluent = true)
public class ActivityEntry implements Canonical {

    /** What the user typed. */
    @Canon(order = 0)
    private String input;

    /** The context item IID when this entry was created. */
    @Canon(order = 1)
    private ItemID contextIid;

    /** What kind of result was produced. */
    @Canon(order = 2)
    private Kind kind;

    /** Human-readable summary of the result. */
    @Canon(order = 3)
    private String resultText;

    /** IID of the result item, if the result references an item. */
    @Canon(order = 4)
    private ItemID resultIid;

    /** When this entry was created. */
    @Canon(order = 5)
    private Instant timestamp;

    /** Where this entry originated. */
    @Canon(order = 6)
    private Source source;

    /**
     * The kind of result produced by an evaluation.
     */
    public enum Kind {
        EMPTY,
        VALUE,
        ITEM,
        CREATED,
        ERROR
    }

    /**
     * Where this entry originated.
     */
    public enum Source {
        /** User interaction in a session. */
        SESSION,
        /** Infrastructure event from the librarian. */
        LIBRARIAN
    }

    // ==================================================================================
    // Constructors
    // ==================================================================================

    @SuppressWarnings("unused")
    private ActivityEntry() {} // Canonical decoding

    private ActivityEntry(String input, ItemID contextIid, Kind kind,
                          String resultText, ItemID resultIid, Source source) {
        this.input = input;
        this.contextIid = contextIid;
        this.kind = kind;
        this.resultText = resultText;
        this.resultIid = resultIid;
        this.timestamp = Instant.now();
        this.source = source;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create an activity entry from an EvalResult.
     *
     * @param input      The raw input text
     * @param contextIid The context item IID (where the user was)
     * @param result     The evaluation result
     * @return A new activity entry
     */
    public static ActivityEntry from(String input, ItemID contextIid,
                                     Eval.EvalResult result) {
        return switch (result) {
            case Eval.EvalResult.Empty() ->
                    new ActivityEntry(input, contextIid, Kind.EMPTY, null, null, Source.SESSION);

            case Eval.EvalResult.Value(Object value) ->
                    new ActivityEntry(input, contextIid, Kind.VALUE,
                            summarize(value), null, Source.SESSION);

            case Eval.EvalResult.ItemResult(Item item) ->
                    new ActivityEntry(input, contextIid, Kind.ITEM,
                            item.displayToken(), item.iid(), Source.SESSION);

            case Eval.EvalResult.Created(Item item) ->
                    new ActivityEntry(input, contextIid, Kind.CREATED,
                            item.displayToken(), item.iid(), Source.SESSION);

            case Eval.EvalResult.ValueWithTarget(Object value, Item target) ->
                    new ActivityEntry(input, contextIid, Kind.VALUE,
                            summarize(value), target.iid(), Source.SESSION);

            case Eval.EvalResult.Error(String message) ->
                    new ActivityEntry(input, contextIid, Kind.ERROR, message, null, Source.SESSION);

            case Eval.EvalResult.Ambiguous ambiguous ->
                    new ActivityEntry(input, contextIid, Kind.ERROR,
                            "Ambiguous: " + ambiguous.tokens().size() + " unresolved tokens", null, Source.SESSION);
        };
    }

    /**
     * Create an infrastructure activity entry for the librarian log.
     *
     * @param event   Short description of what happened
     * @param detail  Optional detail text
     * @return A new activity entry with Source.LIBRARIAN
     */
    public static ActivityEntry infrastructure(String event, String detail) {
        return new ActivityEntry(event, null, Kind.VALUE, detail, null, Source.LIBRARIAN);
    }

    // ==================================================================================
    // Summarization
    // ==================================================================================

    /**
     * Produce a short, single-line summary of a value for the activity log.
     *
     * <p>This is NOT for rendering — it's for the feedback line and log display.
     * Items and Components use their displayToken(); everything else gets a
     * truncated toString().
     */
    private static String summarize(Object value) {
        if (value == null) return null;
        if (value instanceof Item item) return item.displayToken();
        // Try @Type annotation for display token (covers all frame types)
        Type type = value.getClass().getAnnotation(Type.class);
        if (type != null) {
            String key = type.value();
            int slash = key.lastIndexOf('/');
            if (slash >= 0 && slash < key.length() - 1) {
                String shortName = key.substring(slash + 1);
                if (!shortName.isEmpty()) {
                    return Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1);
                }
            }
        }
        String text = String.valueOf(value);
        // Single-line, max 80 chars
        int newline = text.indexOf('\n');
        if (newline >= 0) text = text.substring(0, newline);
        if (text.length() > 80) text = text.substring(0, 77) + "...";
        return text;
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    /**
     * Whether this entry represents a successful result.
     */
    public boolean isSuccess() {
        return kind != Kind.ERROR && kind != Kind.EMPTY;
    }

    /**
     * Whether this entry has a displayable result.
     */
    public boolean hasResult() {
        return resultText != null && !resultText.isBlank();
    }

    @Override
    public String toString() {
        if (resultText != null) {
            return input + " → " + resultText;
        }
        return input;
    }
}
