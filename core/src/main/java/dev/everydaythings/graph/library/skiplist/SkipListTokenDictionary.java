package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.library.dictionary.TokenDictionary;
import lombok.Getter;

/**
 * In-memory TokenDictionary using ConcurrentSkipListMap.
 *
 * <p>Zero dependencies, pure Java. Perfect for tests.
 */
public final class SkipListTokenDictionary implements TokenDictionary, SkipListStore<TokenDictionary.Column> {

    @Getter
    private final SkipListStore.Opened<Column> opened;

    public static SkipListTokenDictionary create() {
        return new SkipListTokenDictionary();
    }

    private SkipListTokenDictionary() {
        this.opened = SkipListStore.create(Column.class);
    }
}
