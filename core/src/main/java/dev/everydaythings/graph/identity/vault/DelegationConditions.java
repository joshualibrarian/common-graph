package dev.everydaythings.graph.identity.vault;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal v1 conditions attached to a delegation — what narrows the authority
 * granted to a delegate beyond just the purpose itself.
 *
 * <p>Phase 1 carries only an optional expiry. The conditions are written into
 * the DELEGATION frame's bindings (e.g., {@code ATTRIBUTE [EXPIRES] → timestamp})
 * at frame-assembly time. Future revisions may add scope-narrowing predicates,
 * threshold lifts, geographic constraints, etc. — additions should be backward
 * compatible (new optional fields, never removing).
 */
public final class DelegationConditions {

    /** A delegation with no constraints — fully general until revoked. */
    public static final DelegationConditions UNLIMITED = new DelegationConditions(Optional.empty());

    private final Optional<Instant> expiresAt;

    private DelegationConditions(Optional<Instant> expiresAt) {
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Construct conditions that expire at the given moment. */
    public static DelegationConditions expiringAt(Instant when) {
        return new DelegationConditions(Optional.of(Objects.requireNonNull(when, "when")));
    }

    /** Construct unconstrained conditions. Equivalent to {@link #UNLIMITED}. */
    public static DelegationConditions unlimited() {
        return UNLIMITED;
    }

    /** When this delegation's authority ends, if it expires at all. */
    public Optional<Instant> expiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DelegationConditions other)) return false;
        return expiresAt.equals(other.expiresAt);
    }

    @Override
    public int hashCode() {
        return expiresAt.hashCode();
    }

    @Override
    public String toString() {
        return expiresAt.map(t -> "DelegationConditions[expiresAt=" + t + "]")
                .orElse("DelegationConditions[unlimited]");
    }
}
