package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.ref.ItemRef;

import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for a propositional {@link Frame} — body's head is a predicate
 * sememe IID. Returned by {@link Frame#compose(ItemRef)}.
 *
 * <p>Build a frame with no records (ephemeral-style) via {@link #build()}; attach
 * one or more records via {@link #record} before calling {@code build()}.
 */
public final class FrameBuilder extends BodyBuilder<FrameBuilder, Frame> {

    private final ItemRef predicate;

    FrameBuilder(ItemRef predicate) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
    }

    @Override
    protected Body buildBody() {
        return Body.of(ItemRef.of(predicate), bindings);
    }

    @Override
    protected Frame finishBuild(Body body, List<Record> records) {
        return records.isEmpty() ? Frame.of(body) : Frame.of(body, records);
    }
}
