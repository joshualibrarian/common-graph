package dev.everydaythings.graph.ui.input;

import dev.everydaythings.graph.expression.ExpressionToken;
import dev.everydaythings.graph.item.action.ActionResult;

import java.util.List;

/**
 * Result of input completion and dispatch.
 *
 * <p>Returned by {@link InputController#handle(InputAction)} when
 * the user completes input (e.g., presses Enter).
 *
 * @param success Whether dispatch succeeded
 * @param value The result value (if success)
 * @param error The error (if failed)
 * @param inputTokens The tokens that were dispatched
 */
public record InputResult(
        boolean success,
        Object value,
        Throwable error,
        List<ExpressionToken> inputTokens
) {

    /**
     * Create a success result.
     */
    public static InputResult success(Object value) {
        return new InputResult(true, value, null, List.of());
    }

    /**
     * Create a success result with the input tokens.
     */
    public static InputResult success(List<ExpressionToken> tokens) {
        return new InputResult(true, tokens, null, tokens);
    }

    /**
     * Create a success result with value and input tokens.
     */
    public static InputResult success(Object value, List<ExpressionToken> tokens) {
        return new InputResult(true, value, null, tokens);
    }

    /**
     * Create a failure result.
     */
    public static InputResult failure(Throwable error) {
        return new InputResult(false, null, error, List.of());
    }

    /**
     * Create a failure result with message.
     */
    public static InputResult failure(String message) {
        return new InputResult(false, null, new RuntimeException(message), List.of());
    }

    /**
     * Create a failure result with input tokens.
     */
    public static InputResult failure(Throwable error, List<ExpressionToken> tokens) {
        return new InputResult(false, null, error, tokens);
    }

    /**
     * Create from an ActionResult.
     */
    public static InputResult from(ActionResult actionResult) {
        if (actionResult.success()) {
            return new InputResult(true, actionResult.value(), null, List.of());
        } else {
            return new InputResult(false, null, actionResult.error(), List.of());
        }
    }

    /**
     * Get error message, if any.
     */
    public String errorMessage() {
        return error != null ? error.getMessage() : null;
    }

    /**
     * Get value as specific type.
     */
    @SuppressWarnings("unchecked")
    public <T> T valueAs(Class<T> type) {
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Get value or throw if failed.
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
