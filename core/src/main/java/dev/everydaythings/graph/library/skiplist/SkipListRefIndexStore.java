package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.library.index.RefIndexByteStore;
import dev.everydaythings.graph.library.index.RefIndexStore;
import lombok.Getter;

import java.util.Objects;

/**
 * In-memory {@link RefIndexByteStore} backed by SkipList byte stores.
 *
 * <p>Zero dependencies; pure Java. Use {@link #create()} to obtain an instance.
 */
public final class SkipListRefIndexStore
        implements RefIndexByteStore, SkipListStore<RefIndexStore.Column> {

    @Getter
    private final SkipListStore.Opened<RefIndexStore.Column> opened;
    private final Encoding encoder;

    /** Create a SkipListRefIndexStore using the default {@link CgCbor} encoder. */
    public static SkipListRefIndexStore create() {
        return new SkipListRefIndexStore(CgCbor.codec());
    }

    /** Create a SkipListRefIndexStore with an explicit encoder. */
    public static SkipListRefIndexStore create(Encoding encoder) {
        return new SkipListRefIndexStore(encoder);
    }

    private SkipListRefIndexStore(Encoding encoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.opened = SkipListStore.create(RefIndexStore.Column.class);
    }

    @Override
    public Encoding rawEncoder() {
        return encoder;
    }

    @Override
    public void close() {
        opened.close();
    }
}
