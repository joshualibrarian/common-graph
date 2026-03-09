package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
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
 * @see SessionItem
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

    // ==================================================================================
    // Constructors
    // ==================================================================================

    @SuppressWarnings("unused")
    private ActivityEntry() {} // Canonical decoding

    private ActivityEntry(String input, ItemID contextIid, Kind kind,
                          String resultText, ItemID resultIid) {
        this.input = input;
        this.contextIid = contextIid;
        this.kind = kind;
        this.resultText = resultText;
        this.resultIid = resultIid;
        this.timestamp = Instant.now();
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
                    new ActivityEntry(input, contextIid, Kind.EMPTY, null, null);

            case Eval.EvalResult.Value(Object value) ->
                    new ActivityEntry(input, contextIid, Kind.VALUE,
                            value != null ? String.valueOf(value) : null, null);

            case Eval.EvalResult.ItemResult(Item item) ->
                    new ActivityEntry(input, contextIid, Kind.ITEM,
                            item.displayToken(), item.iid());

            case Eval.EvalResult.Created(Item item) ->
                    new ActivityEntry(input, contextIid, Kind.CREATED,
                            item.displayToken(), item.iid());

            case Eval.EvalResult.ValueWithTarget(Object value, Item target) ->
                    new ActivityEntry(input, contextIid, Kind.VALUE,
                            value != null ? String.valueOf(value) : null,
                            target.iid());

            case Eval.EvalResult.Error(String message) ->
                    new ActivityEntry(input, contextIid, Kind.ERROR, message, null);
        };
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
