package dev.everydaythings.graph.library_old;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.FrameRecordOld;
import dev.everydaythings.graph.Implements;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library_old.dictionary.TokenDictionary;
import dev.everydaythings.graph.library_old.directory.ItemDirectory;
import dev.everydaythings.graph.crypt.Vault;
import dev.everydaythings.graph.runtime.LibrarianOld;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * INERT SHIM — the OLD library. Every method is a stub that returns
 * empty/null/no-op. Method signatures are preserved so that the rest of the
 * OLD code (LibrarianOld, ItemOld, FrameOld, games/ui/web modules, etc.)
 * continues to compile against this surface.
 *
 * <p>No instance of this class does anything meaningful at runtime — the OLD
 * runtime is gone. NEW code uses {@link dev.everydaythings.graph.library.Library}.
 * This shim exists only to keep the OLD compile graph satisfied while the OLD
 * code waits to be deleted in a future sweep (along with its dependents in
 * games/ui/web).
 *
 * <p>The shim version replaces ~1100 lines of real implementation with
 * minimal stubs. All backing implementation files (RocksDB / MapDB / SkipList
 * variants of the stores) are gone.
 */
@Implements(LibraryOld.KEY)
@ItemSeed(key = LibraryOld.KEY)
public final class LibraryOld implements Canonical, AutoCloseable {

    public static final String KEY = "cg.sememe:library";

    /** Inert backend marker. */
    public enum Backend { SKIPLIST, MAPDB, ROCKS }

    // ==================================================================================
    // Factories — all return inert instances
    // ==================================================================================

    public static LibraryOld memory() { return new LibraryOld(); }
    public static LibraryOld file(Path rootPath) { return new LibraryOld(); }
    public static LibraryOld mapdb(Path rootPath) { return new LibraryOld(); }
    public static LibraryOld mapdbMemory() { return new LibraryOld(); }
    public static LibraryOld memoryEncrypted(Vault vault) { return new LibraryOld(); }
    public static LibraryOld fileEncrypted(Path rootPath, Vault vault) { return new LibraryOld(); }
    public static LibraryOld mapdbEncrypted(Path rootPath, Vault vault) { return new LibraryOld(); }

    private LibraryOld() {}

    // ==================================================================================
    // Surface — all no-op
    // ==================================================================================

    public Backend backend() { return Backend.SKIPLIST; }
    public Path rootPath() { return null; }

    public void setLibrarian(LibrarianOld librarian) {}
    public LibrarianOld librarian() { return null; }

    public void registerStore(ItemStore store) {}
    public void registerStore(int position, ItemStore store) {}
    public void unregisterStore(ItemStore store) {}
    public List<ItemStore> stores() { return List.of(); }
    public Optional<ItemStore> primaryStore() { return Optional.empty(); }
    public ItemStore store() { return null; }
    public Optional<ItemStore> writableStore() { return Optional.empty(); }
    public Optional<ItemDirectory> directory() { return Optional.empty(); }
    public Optional<TokenDictionary> tokenDictionary() { return Optional.empty(); }
    public Optional<LibraryIndex> index() { return Optional.empty(); }

    public ContentID storeFrameBody(FrameBodyOld body) { return null; }
    public ContentID storeFrameBody(FrameBodyOld body, ItemStore targetStore) { return null; }
    public ContentID manifest(ManifestOld manifest) { return null; }
    public ContentID storeFrame(FrameBodyOld body, FrameRecordOld record) { return null; }

    public void importFrom(ItemStore source, Function<ItemID, Float> predicateWeightResolver) {}
    public void indexItemFrames(Collection<ItemOld> items) {}

    public Stream<LibraryIndex.FrameRef> framesByItem(ItemID item) { return Stream.empty(); }
    public Stream<LibraryIndex.FrameRef> framesByItemPredicate(ItemID item, ItemID predicate) { return Stream.empty(); }
    public Stream<LibraryIndex.FrameRef> framesByPredicate(ItemID predicate) { return Stream.empty(); }
    public Stream<LibraryIndex.RecordRef> recordsByBody(ContentID bodyHash) { return Stream.empty(); }
    public long attestationCount(ContentID bodyHash) { return 0L; }

    public Stream<FrameBodyOld> byItem(ItemID item) { return Stream.empty(); }
    public Stream<FrameBodyOld> byItemPredicate(ItemID item, ItemID predicate) { return Stream.empty(); }
    public Stream<FrameBodyOld> byPredicate(ItemID predicate) { return Stream.empty(); }

    public Set<ItemID> queryItems(Set<ItemID> pattern) { return Set.of(); }
    public Set<ItemID> queryItems(ItemID... pattern) { return Set.of(); }

    public Optional<FrameBodyOld> loadFrameBody(ContentID bodyHash) { return Optional.empty(); }
    public Optional<FrameRecordOld> loadFrameRecord(ContentID storageCid) { return Optional.empty(); }
    public List<FrameRecordOld> loadRecords(ContentID bodyHash) { return List.of(); }
    public Optional<ContentID> latestVersion(ItemID iid) { return Optional.empty(); }
    public Optional<ItemOld> get(ItemID iid) { return Optional.empty(); }

    public Optional<Class<?>> findImplementation(ItemID typeId) { return Optional.empty(); }
    public Optional<Class<?>> findComponentImplementation(ItemID typeId) { return Optional.empty(); }
    public Optional<Class<? extends ItemOld>> findItemImplementation(ItemID typeId) { return Optional.empty(); }
    public Optional<Class<? extends dev.everydaythings.graph.value.Value>> findValueImplementation(ItemID typeId) { return Optional.empty(); }

    public void runInWriteTransaction(Consumer<WriteTransaction> work) {}
    public byte[] content(ContentID cid) { return null; }

    @Override
    public void close() {}

    public boolean isEncrypted() { return false; }

    public boolean delete(ItemID iid) { return false; }

    // ==================================================================================
    // Find-builder shim — chains of .from()/.where() etc. dissolve into a no-op terminal.
    // ==================================================================================

    public FindBuilder find() { return new FindBuilder(); }

    /**
     * Inert query builder. Any chain of method calls terminates in an empty
     * result. Reproduces the shape of the old fluent find() API.
     */
    public static final class FindBuilder {
        public FindBuilder from(Object... ignored) { return this; }
        public FindBuilder where(Object... ignored) { return this; }
        public FindBuilder predicate(Object... ignored) { return this; }
        public FindBuilder limit(int ignored) { return this; }
        public Stream<FrameBodyOld> stream() { return Stream.empty(); }
        public List<FrameBodyOld> toList() { return List.of(); }
    }

    // ==================================================================================
    // Canonical implementation — empty
    // ==================================================================================

    @Override
    public com.upokecenter.cbor.CBORObject toCborTree(Canonical.Scope scope) {
        return com.upokecenter.cbor.CBORObject.NewArray();
    }
}
