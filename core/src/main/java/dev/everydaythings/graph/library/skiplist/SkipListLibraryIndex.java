package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.library.LibraryIndex;
import lombok.Getter;

/**
 * In-memory LibraryIndex using ConcurrentSkipListMap.
 *
 * <p>Zero dependencies, pure Java. Perfect for tests.
 */
public final class SkipListLibraryIndex implements LibraryIndex, SkipListStore<LibraryIndex.Column> {

    @Getter
    private final SkipListStore.Opened<Column> opened;

    public static SkipListLibraryIndex create() {
        return new SkipListLibraryIndex();
    }

    private SkipListLibraryIndex() {
        this.opened = SkipListStore.create(Column.class);
    }
}
