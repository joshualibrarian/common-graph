package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.item.id.ItemID;

import java.util.List;

/**
 * A runtime aggregate of a {@link Body} with zero or more {@link Record}s.
 *
 * <p>Frame is the general case of {@link AttributedBody}: any body asserting any
 * content, with attestations from any number of signers. For bodies that
 * specifically represent versions of items (with an {@code ITEM_ID} binding), use
 * {@link Manifest} instead — it provides archetypal-flavored convenience methods.
 *
 * <p>A Frame is never serialized as a single object. Body and Records are
 * stored independently (each by its own CID); Frame is just the in-memory grouping
 * when you fetch them together.
 */
public final class Frame extends AttributedBody {

    public Frame(Body body, List<Record> records) {
        super(body, records);
    }

    /** Construct a Frame with the given body and no records. */
    public static Frame of(Body body) {
        return new Frame(body, List.of());
    }

    /** Construct a Frame with the given body and records. */
    public static Frame of(Body body, List<Record> records) {
        return new Frame(body, records);
    }

    /**
     * Open a fluent builder for a propositional frame whose head is the given
     * predicate IID.
     */
    public static FrameBuilder compose(ItemID predicate) {
        return new FrameBuilder(predicate);
    }
}
