package dev.everydaythings.graph.library.tokens;

import dev.everydaythings.graph.library.skiplist.SkipListStore;
import lombok.Getter;

/**
 * In-memory {@link TokenDictionary} backed by {@code ConcurrentSkipListMap}.
 *
 * <p>Zero external dependencies, pure Java. Suitable for tests and ephemeral
 * librarians. Production / persistent variants (RocksDB, MapDB) follow the same
 * pattern but with different backing stores.
 */
public final class SkipListTokenDictionary
        implements TokenDictionary, SkipListStore<TokenDictionary.Column> {

    @Getter
    private final SkipListStore.Opened<Column> opened;

    public static SkipListTokenDictionary create() {
        return new SkipListTokenDictionary();
    }

    private SkipListTokenDictionary() {
        this.opened = SkipListStore.create(Column.class);
    }
}
