package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.library.ItemStore;
import lombok.Getter;

import java.nio.file.Path;

/**
 * MapDB-backed implementation of ItemStore.
 *
 * <p>Provides both in-memory and file-based storage, making it ideal for:
 * <ul>
 *   <li>Testing - fast in-memory stores with no cleanup needed</li>
 *   <li>Embedded use - lightweight file-based persistence</li>
 *   <li>Development - quick iteration without RocksDB setup</li>
 * </ul>
 *
 * <p>Implements both ItemStore (domain operations) and MapDBStore (byte operations).
 * All persist/retrieve operations are inherited from ItemStore defaults,
 * which use the ByteStore methods provided by MapDBStore.
 */
public class MapDBItemStore implements ItemStore, MapDBStore<ItemStore.Column> {

    @Getter
    private final MapDBStore.Opened<Column> opened;

    /**
     * Create an in-memory MapDBItemStore.
     *
     * <p>Data is not persisted - ideal for testing.
     */
    public static MapDBItemStore memory() {
        return new MapDBItemStore(MapDBStore.memory(Column.class));
    }

    /**
     * Create a file-backed MapDBItemStore.
     *
     * <p>Data is persisted to the given path.
     */
    public static MapDBItemStore file(Path path) {
        return new MapDBItemStore(MapDBStore.file(path, Column.class));
    }

    private MapDBItemStore(MapDBStore.Opened<Column> opened) {
        this.opened = opened;
    }

    @Override
    public void close() {
        opened.close();
    }
}
