package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Local (same JVM) reference to a Librarian.
 *
 * <p>This is a thin wrapper that delegates directly to the Librarian instance.
 * Used when running librarian and session in the same process.
 */
public final class LocalLibrarian implements LibrarianHandle {

    private final Librarian librarian;
    private boolean closed = false;

    LocalLibrarian(Librarian librarian) {
        this.librarian = Objects.requireNonNull(librarian, "librarian");
    }

    /**
     * Get the underlying Librarian instance.
     *
     * <p>Only available for local references. Use with care - prefer
     * using the LibrarianRef interface methods for portability.
     */
    public Librarian librarian() {
        checkOpen();
        return librarian;
    }

    @Override
    public ItemID iid() {
        checkOpen();
        return librarian.iid();
    }

    @Override
    public ItemID principalId() {
        checkOpen();
        return librarian.principal().map(s -> s.iid()).orElse(null);
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public String connectionString() {
        Path rootPath = librarian.rootPath();
        return rootPath != null ? rootPath.toString() : "<in-memory>";
    }

    @Override
    public ActionResult dispatch(String command, List<String> args) {
        checkOpen();
        return librarian.dispatch(command, args);
    }

    @Override
    public ActionResult dispatch(ItemID target, String command, List<String> args) {
        checkOpen();
        Optional<Item> item = librarian.get(target, Item.class);
        if (item.isEmpty()) {
            return ActionResult.failure(
                    new IllegalArgumentException("Item not found: " + target.encodeText()));
        }
        return item.get().dispatch(command, args);
    }

    @Override
    public <T extends Item> Optional<T> get(ItemID iid, Class<T> type) {
        checkOpen();
        return librarian.get(iid, type);
    }

    @Override
    public Stream<Posting> lookup(String query) {
        checkOpen();
        var tokenDict = librarian.tokenIndex();
        if (tokenDict == null) {
            return Stream.empty();
        }
        return tokenDict.lookup(query);
    }

    @Override
    public Stream<Posting> lookup(String query, ItemID... scopes) {
        checkOpen();
        var tokenDict = librarian.tokenIndex();
        if (tokenDict == null) {
            return Stream.empty();
        }
        return tokenDict.lookup(query, scopes);
    }

    @Override
    public Stream<Posting> prefix(String text, int limit) {
        checkOpen();
        var tokenDict = librarian.tokenIndex();
        if (tokenDict == null) return Stream.empty();
        return tokenDict.prefix(text, limit);
    }

    @Override
    public Vocabulary vocabulary() {
        checkOpen();
        return librarian.vocabulary();
    }

    @Override
    public Optional<Item> principal() {
        checkOpen();
        return librarian.principal().map(s -> (Item) s);
    }

    @Override
    public boolean isOpen() {
        return !closed && librarian != null;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            librarian.close();
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("LibrarianRef is closed");
        }
    }

    @Override
    public String toString() {
        return "LocalLibrarianRef[" + connectionString() + "]";
    }
}
