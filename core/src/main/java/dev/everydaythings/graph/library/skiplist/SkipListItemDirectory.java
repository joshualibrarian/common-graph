package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.directory.ItemDirectory;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory ItemDirectory using ConcurrentSkipListMap.
 *
 * <p>Zero dependencies, pure Java. Perfect for tests.
 */
public final class SkipListItemDirectory implements ItemDirectory, SkipListStore<ItemDirectory.Column> {

    @Getter
    private final SkipListStore.Opened<Column> opened;

    @Getter
    private final Map<Path, ItemStore> storeRegistry = new ConcurrentHashMap<>();

    public static SkipListItemDirectory create() {
        return new SkipListItemDirectory();
    }

    private SkipListItemDirectory() {
        this.opened = SkipListStore.create(Column.class);
    }

    @Override
    public void close() {
        opened.close();
        storeRegistry.clear();
    }
}
