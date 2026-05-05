package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.library.DataStore;
import lombok.Getter;

/**
 * In-memory {@link DataStore} for tests and ephemeral runs.
 *
 * <p>Zero dependencies; pure Java. Use {@link #create()} to obtain an instance.
 */
public class SkipListDataStore implements DataStore, SkipListStore<DataStore.Column> {

    @Getter
    private final SkipListStore.Opened<Column> opened;

    public static SkipListDataStore create() {
        return new SkipListDataStore();
    }

    private SkipListDataStore() {
        this.opened = SkipListStore.create(Column.class);
    }

    @Override
    public void close() {
        opened.close();
    }
}
