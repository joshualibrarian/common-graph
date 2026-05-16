package dev.everydaythings.graph.library;

import java.util.List;

/**
 * Outcome of validating a candidate body against an archetype's EXPECTS
 * declarations.
 *
 * <p>An empty issues list means the candidate satisfies every expectation;
 * a non-empty list lists what's wrong.  The first-cut SchemaWalker reports
 * presence violations only (the candidate is missing an expected binding);
 * value-shape checks (target type, range constraints via BETWEEN, etc.)
 * land as the matcher orchestrator comes online.
 */
public record ValidationResult(List<Issue> issues) {

    public boolean isValid() {
        return issues.isEmpty();
    }

    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    /** A single validation problem — its kind and a human-readable message. */
    public record Issue(Kind kind, String message) {
        public enum Kind {
            /** A binding the schema declares as expected isn't present on the candidate. */
            MISSING_BINDING,
            /** The candidate's binding target doesn't satisfy the schema's pattern. */
            TARGET_MISMATCH,
        }
    }
}
