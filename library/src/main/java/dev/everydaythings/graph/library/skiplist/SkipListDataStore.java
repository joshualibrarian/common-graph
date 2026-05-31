package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.library.data.DataByteStore;
import dev.everydaythings.graph.library.data.DataStore;
import lombok.Getter;

import java.util.Objects;

/**
 * In-memory {@link DataByteStore} backed by SkipList byte stores.
 *
 * <p>Zero external dependencies, pure Java. Suitable for tests and ephemeral
 * librarians.
 */
public class SkipListDataStore implements DataByteStore, SkipListStore<DataStore.Column> {

    @Getter
    private final SkipListStore.Opened<DataStore.Column> opened;
    private final Encoding encoder;

    /** Create a SkipListDataStore using the default {@link CgCbor} encoder. */
    public static SkipListDataStore create() {
        return new SkipListDataStore(CgCbor.codec());
    }

    /** Create a SkipListDataStore with an explicit encoder. */
    public static SkipListDataStore create(Encoding encoder) {
        return new SkipListDataStore(encoder);
    }

    private SkipListDataStore(Encoding encoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.opened = SkipListStore.create(DataStore.Column.class);
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
