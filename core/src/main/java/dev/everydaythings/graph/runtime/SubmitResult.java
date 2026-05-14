package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.util.List;

/**
 * Outcome of {@link Librarian#submit(Frame)}.
 *
 * <p>Contains the submitted frame (as assembled — body plus any records that
 * came with it) and any response frames the handlers produced. Whether either
 * was persisted is determined by the predicate's RETENTION config (default
 * durable, opt-in ephemeral).
 *
 * @param submitted the frame that was submitted (after any internal augmentation)
 * @param responses zero or more response frames produced by handlers, in
 *                  invocation order; ephemeral by inheritance from the submitted
 *                  frame unless their own predicate overrides
 */
public record SubmitResult(Frame submitted, List<Frame> responses) {

    public SubmitResult {
        responses = List.copyOf(responses);
    }

    public static SubmitResult of(Frame submitted) {
        return new SubmitResult(submitted, List.of());
    }

    public static SubmitResult of(Frame submitted, List<Frame> responses) {
        return new SubmitResult(submitted, responses);
    }
}
