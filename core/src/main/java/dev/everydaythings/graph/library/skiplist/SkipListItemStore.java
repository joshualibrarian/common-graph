package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.crypt.AtRestEncryption;
import dev.everydaythings.graph.library.ItemStore;
import lombok.Getter;

/**
 * In-memory ItemStore for tests.
 *
 * <p>Zero dependencies, pure Java. Perfect for unit tests that need
 * a full ItemStore without disk I/O or external libraries.
 */
public class SkipListItemStore implements ItemStore, SkipListStore<ItemStore.Column> {

    @Getter
    private final SkipListStore.Opened<Column> opened;

    private AtRestEncryption encryption;

    public static SkipListItemStore create() {
        return new SkipListItemStore();
    }

    private SkipListItemStore() {
        this.opened = SkipListStore.create(Column.class);
    }

    @Override
    public AtRestEncryption atRestEncryption() {
        return encryption;
    }

    @Override
    public void enableEncryption(AtRestEncryption encryption) {
        this.encryption = encryption;
    }

    @Override
    public void close() {
        opened.close();
    }
}
