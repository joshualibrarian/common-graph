package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.library.index.TokenIndexByteStore;
import dev.everydaythings.graph.library.index.TokenIndexStore;
import lombok.Getter;

/**
 * In-memory {@link TokenIndexByteStore} backed by SkipList byte stores.
 *
 * <p>Zero external dependencies, pure Java. Suitable for tests and ephemeral
 * librarians.
 */
public final class SkipListTokenIndexStore
        implements TokenIndexByteStore, SkipListStore<TokenIndexStore.Column> {

    @Getter
    private final SkipListStore.Opened<TokenIndexStore.Column> opened;

    public static SkipListTokenIndexStore create() {
        return new SkipListTokenIndexStore();
    }

    private SkipListTokenIndexStore() {
        this.opened = SkipListStore.create(TokenIndexStore.Column.class);
    }

    @Override
    public void close() {
        opened.close();
    }
}
