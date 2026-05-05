package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Local (same JVM) reference to a Librarian.
 *
 * <p>This is a thin wrapper that delegates directly to the Librarian instance.
 * Used when running librarian and session in the same process.
 */
public final class LocalLibrarian implements LibrarianHandle {

    private final LibrarianOld librarian;
    private boolean closed = false;

    LocalLibrarian(LibrarianOld librarian) {
        this.librarian = Objects.requireNonNull(librarian, "librarian");
    }

    /**
     * Get the underlying Librarian instance.
     *
     * <p>Only available for local references. Use with care - prefer
     * using the LibrarianRef interface methods for portability.
     */
    public LibrarianOld librarian() {
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
    public Optional<Host> host() {
        checkOpen();
        return Optional.ofNullable(librarian.host());
    }

    @Override
    public void put(ItemOld item) {
        checkOpen();
        librarian.put(item);
    }

    @Override
    public <T extends ItemOld> Optional<T> get(ItemID iid, Class<T> type) {
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
        return tokenDict.lookup(query, bodyResolver());
    }

    @Override
    public Stream<Posting> lookup(String query, ItemID... scopes) {
        checkOpen();
        var tokenDict = librarian.tokenIndex();
        if (tokenDict == null) {
            return Stream.empty();
        }
        return tokenDict.lookup(query, bodyResolver(), scopes);
    }

    @Override
    public Stream<Posting> prefix(String text, int limit) {
        checkOpen();
        var tokenDict = librarian.tokenIndex();
        if (tokenDict == null) return Stream.empty();
        return tokenDict.prefix(text, limit, bodyResolver());
    }

    private java.util.function.Function<dev.everydaythings.graph.item.id.ContentID,
            Optional<FrameBodyOld>> bodyResolver() {
        return bodyHash -> librarian.library().loadFrameBody(bodyHash);
    }

    @Override
    public Set<ItemID> queryItems(Set<ItemID> pattern) {
        checkOpen();
        return librarian.library().queryItems(pattern);
    }

    @Override
    public Vocabulary vocabulary() {
        checkOpen();
        return librarian.vocabulary();
    }

    @Override
    public Optional<ItemOld> principal() {
        checkOpen();
        return librarian.principal().map(s -> (ItemOld) s);
    }

    @Override
    public void storeFrame(FrameBodyOld body) {
        checkOpen();
        librarian.storeFrame(body);
    }

    @Override
    public List<FrameBodyOld> ephemeralFrames(ItemID itemId) {
        checkOpen();
        return librarian.ephemeralFramesForItem(itemId);
    }

    @Override
    public void onEphemeralChanged(ItemID itemId, Runnable listener) {
        checkOpen();
        librarian.onEphemeralChanged(itemId, listener);
    }

    @Override
    public void removeEphemeralListener(ItemID itemId, Runnable listener) {
        checkOpen();
        librarian.removeEphemeralListener(itemId, listener);
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
