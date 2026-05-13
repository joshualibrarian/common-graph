package dev.everydaythings.graph.library_old.workingtree;

import dev.everydaythings.graph.frame.EndorsementsTable;
import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library_old.ItemStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * INERT SHIM — the OLD working-tree store. Every method is a no-op; the OLD
 * filesystem-as-graph mount feature is gone.
 *
 * <p>Method signatures preserved so LibrarianOld / ItemOld / ItemOldTest still
 * compile against this surface while the OLD code waits to be deleted in a
 * future sweep.
 */
public final class WorkingTreeStore implements ItemStore {

    public static final String DOT_ITEM = ".item";

    private final Path root;
    private final ItemID iid;
    private ItemStore fallback;

    private WorkingTreeStore(Path root, ItemID iid, ItemStore fallback) {
        this.root = root;
        this.iid = iid;
        this.fallback = fallback;
    }

    public static WorkingTreeStore open(Path rootDir) {
        return new WorkingTreeStore(rootDir, null, null);
    }

    public static WorkingTreeStore open(Path rootDir, ItemStore fallback) {
        return new WorkingTreeStore(rootDir, null, fallback);
    }

    public static WorkingTreeStore materialize(Path rootDir, ItemID iid) {
        return new WorkingTreeStore(rootDir, iid, null);
    }

    public static WorkingTreeStore materialize(Path rootDir, ItemID iid, ItemStore fallback) {
        return new WorkingTreeStore(rootDir, iid, fallback);
    }

    public static boolean exists(Path rootDir) {
        return false;
    }

    public ItemID iid() { return iid; }
    public Path root() { return root; }
    public Path dotItem() { return root == null ? null : root.resolve(DOT_ITEM); }
    public boolean isThin() { return fallback != null; }
    public ItemStore fallback() { return fallback; }
    public void setFallback(ItemStore fallback) { this.fallback = fallback; }
    public boolean isFresh() { return true; }

    public Optional<ContentID> currentVersion() { return Optional.empty(); }
    public void setCurrentVersion(ContentID vid, WriteTransaction wtx) {}

    public void saveHeadComponents(List<FrameOld> frames, WriteTransaction wtx) {}
    public void saveHeadComponents(EndorsementsTable table, WriteTransaction wtx) {}
    public List<FrameOld> loadHeadComponents() { return List.of(); }
    public Optional<FrameOld> loadHeadComponent(CompoundKey key) { return Optional.empty(); }

    public void materializeMountPaths(EndorsementsTable content) {}

    public WriteTransaction beginWriteTransaction() {
        return new WriteTransaction() {
            @Override public void commit() {}
            @Override public void rollback() {}
            @Override public void close() {}
        };
    }

    public void runInWriteTransaction(Consumer<WriteTransaction> work) {
        try (WriteTransaction tx = beginWriteTransaction()) {
            work.accept(tx);
            tx.commit();
        }
    }

    public Stream<ManifestOld> manifests(ItemID iid) { return Stream.empty(); }
    public Iterable<byte[]> iterateObjects() { return List.of(); }

    public ContentID persistManifest(ItemID iid, byte[] record, WriteTransaction tx) {
        return null;
    }

    public Optional<ManifestOld> manifest(ItemID iid, ContentID vid) {
        return Optional.empty();
    }

    @Override
    public void close() {}

    public Optional<Path> path() {
        return Optional.ofNullable(root);
    }
}
