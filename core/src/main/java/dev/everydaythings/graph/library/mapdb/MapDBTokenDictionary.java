package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.library.dictionary.TokenDictionary;
import lombok.Getter;

import java.nio.file.Path;

/**
 * MapDB-backed TokenDictionary for token lookup.
 *
 * <p>Supports both in-memory and file-based storage.
 */
public final class MapDBTokenDictionary implements TokenDictionary, MapDBStore<TokenDictionary.Column> {

    @Getter
    private final MapDBStore.Opened<Column> opened;

    public static MapDBTokenDictionary memory() {
        return new MapDBTokenDictionary(MapDBStore.memory(Column.class));
    }

    public static MapDBTokenDictionary file(Path path) {
        return new MapDBTokenDictionary(MapDBStore.file(path, Column.class));
    }

    private MapDBTokenDictionary(MapDBStore.Opened<Column> opened) {
        this.opened = opened;
    }
}
