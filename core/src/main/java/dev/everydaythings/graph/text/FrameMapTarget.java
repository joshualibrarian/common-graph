package dev.everydaythings.graph.text;

import dev.everydaythings.graph.canonical.Scope;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.datum.BindingTarget;

import java.util.Objects;

/**
 * In-flight {@link BindingTarget} that wraps a nested {@link FrameMap} as the
 * value of a binding. Used during parsing to express sub-expressions ({@code 5+3*2}
 * → outer Add's GOAL is a {@code FrameMapTarget(Multiply{3,2})}) without
 * persisting them as standalone Bodies first.
 *
 * <p>FrameMaps may nest arbitrarily deep — a FrameMapTarget's inner FrameMap may
 * itself have bindings whose targets are FrameMapTargets, and so on. This is how
 * precedence-based grouping ({@code 5+3*2}), explicit grouping ({@code (5+3)*2}),
 * and chained left-associative operators ({@code 5-3-2}) all surface in the
 * parse output: as one outer FrameMap with sub-FrameMaps in operand positions.
 *
 * <p><b>Transient.</b> FrameMapTarget is a parse-time-only carrier. It is not
 * persisted to storage — when a parse settles and the orchestrator wants to
 * serialize the result, sub-FrameMaps get materialized as separate persisted
 * Bodies and the FrameMapTargets convert to {@code RefTarget(<sub-body-CID>)}.
 * Accordingly, {@link #toCborTree} throws — calling code should perform the
 * lift-to-Body conversion before any persistence path runs.
 */
public final class FrameMapTarget implements BindingTarget {

    private final FrameMap frameMap;

    public FrameMapTarget(FrameMap frameMap) {
        this.frameMap = Objects.requireNonNull(frameMap, "frameMap");
    }

    /** The nested FrameMap that fills this binding's target slot. */
    public FrameMap frameMap() {
        return frameMap;
    }

    @Override
    public CBORObject toCborTree(Scope scope) {
        throw new UnsupportedOperationException(
                "FrameMapTarget is transient (parse-time only); convert to RefTarget "
                        + "via persistence before encoding to CBOR.");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameMapTarget other)) return false;
        return Objects.equals(frameMap, other.frameMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(frameMap);
    }

    @Override
    public String toString() {
        return "FrameMapTarget(" + frameMap + ")";
    }
}
