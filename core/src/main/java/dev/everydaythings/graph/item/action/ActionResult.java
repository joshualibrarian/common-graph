package dev.everydaythings.graph.item.action;

/**
 * Result of a verb or action invocation.
 *
 * <p>Wraps success/failure state with an optional return value or error.
 * Used as the universal return type across all dispatch paths:
 * {@link dev.everydaythings.graph.item.VerbInvoker},
 * {@link dev.everydaythings.graph.item.Item#dispatch}.
 */
@lombok.Value
public class ActionResult {
    /** Whether the invocation succeeded. */
    boolean success;
    /** The return value (may be null for void methods). */
    Object value;
    /** The error if failed (null if success). */
    Throwable error;

    public static ActionResult success(Object value) {
        return new ActionResult(true, value, null);
    }

    public static ActionResult failure(Throwable error) {
        return new ActionResult(false, null, error);
    }

    /**
     * Get the value, throwing if the invocation failed.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrThrow() throws Exception {
        if (!success) {
            if (error instanceof Exception e) throw e;
            throw new RuntimeException(error);
        }
        return (T) value;
    }
}
