package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.library.index.TokenIndexByteStore;
import dev.everydaythings.graph.library.index.TokenIndexStore;
import lombok.Getter;

import java.util.Objects;

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
    private final Encoding encoder;

    /** Create a SkipListTokenIndexStore using the default {@link CgCbor} encoder. */
    public static SkipListTokenIndexStore create() {
        return new SkipListTokenIndexStore(CgCbor.codec());
    }

    /** Create a SkipListTokenIndexStore with an explicit encoder. */
    public static SkipListTokenIndexStore create(Encoding encoder) {
        return new SkipListTokenIndexStore(encoder);
    }

    private SkipListTokenIndexStore(Encoding encoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.opened = SkipListStore.create(TokenIndexStore.Column.class);
    }

    @Override
    public Encoding rawEncoder() {
        return encoder;
    }

    @Override
    public void close() {
        opened.close();
    }
}
