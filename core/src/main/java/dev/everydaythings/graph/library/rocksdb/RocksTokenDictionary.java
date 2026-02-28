package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.library.dictionary.TokenDictionary;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.nio.file.Path;

/**
 * RocksDB-backed TokenDictionary for persistent token lookup.
 *
 * <p>All operations are provided by {@link TokenDictionary} default methods.
 * Service lifecycle (isOpen, start, stop, close) is provided by RocksStore.
 */
public final class RocksTokenDictionary implements TokenDictionary, RocksStore<TokenDictionary.Column> {

    @Getter
    private final Opened<Column> opened;

    /** Storage path for this dictionary. */
    @Getter
    @Accessors(fluent = false)
    private final Path path;

    public static RocksTokenDictionary open(Path path) {
        return new RocksTokenDictionary(path);
    }

    private RocksTokenDictionary(Path rootPath) {
        this.path = rootPath;
        this.opened = RocksStore.open(rootPath, Column.class);
    }
}
