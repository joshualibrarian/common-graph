package dev.everydaythings.graph.library.workingtree;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.FrameEntry;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.FrameTable;
import dev.everydaythings.graph.item.mount.Mount;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.LibraryException;
import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.Encoding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Per-item WorkingTreeStore - materializes an Item into a filesystem directory.
 *
 * <p>Layout:
 * <pre>
 * {@code
 * <root>/
 *   .item/
 *     iid                     # Item identity (raw bytes or text)
 *     head/
 *       base                  # Reference to current version: "../versions/<vid>"
 *       components/<hid>.json # Current version's component metadata
 *       mounts/<mid>.json     # Current version's mount metadata
 *       actions/<aid>.json    # Current version's action metadata
 *     versions/<vid>          # All manifests (version-independent)
 *     content/<cid>           # All content blocks (version-independent)
 *     relations/<rid>         # All relations (version-independent)
 *   <mounted paths>           # Only mounts create things outside .item/
 * }
 * </pre>
 *
 * <p>The {@code head/} directory represents the CURRENT VERSION STATE - it's special.
 * Everything else in {@code .item/} is version-independent: all manifests, content,
 * and relations from all versions live together, like a mini-store.
 *
 * <p>This store can operate in FULL mode (self-contained) or THIN mode (with fallback
 * to another store for reads).
 */
public final class WorkingTreeStore implements ItemStore {

    private static final String DOT_ITEM = ".item";

    // Version-independent storage
    private static final String DIR_VERSIONS = "versions";
    private static final String DIR_CONTENT = "content";
    private static final String DIR_RELATIONS = "relations";

    // Current version state
    private static final String DIR_HEAD = "head";
    private static final String FILE_BASE = "base";
    private static final String DIR_COMPONENTS = "components";
    private static final String DIR_ACTIONS = "actions";

    // Local-only storage (never synced)
    private static final String DIR_LOCAL = "local";

    private final Path root;
    private final ItemID iid;
    private ItemStore fallback;
    private volatile boolean closed = false;

    // ==================================================================================
    // Static Factory Methods
    // ==================================================================================

    /**
     * Open an existing item's WorkingTreeStore.
     *
     * <p>This method loads an existing item from the filesystem. It does NOT create
     * new items - use {@link #materialize} for that.
     *
     * @param rootDir The directory containing the .item/ structure
     * @return The WorkingTreeStore for the existing item
     * @throws LibraryException.NotFound if no item exists at this location
     */
    public static WorkingTreeStore open(Path rootDir) {
        return open(rootDir, null);
    }

    /**
     * Open an existing item's WorkingTreeStore with a fallback store.
     *
     * @param rootDir  The directory containing the .item/ structure
     * @param fallback Fallback store for reads (null for FULL mode)
     * @return The WorkingTreeStore for the existing item
     * @throws LibraryException.NotFound if no item exists at this location
     */
    public static WorkingTreeStore open(Path rootDir, ItemStore fallback) {
        Objects.requireNonNull(rootDir, "rootDir");
        Path iidPath = rootDir.resolve(DOT_ITEM).resolve("iid");

        if (!Files.exists(iidPath)) {
            throw new LibraryException.NotFound("No item at: " + rootDir);
        }

        ItemID iid = loadIid(iidPath);
        return new WorkingTreeStore(rootDir, iid, fallback);
    }

    /**
     * Mount an item to a filesystem path, creating the .item/ structure.
     *
     * <p>This method materializes an item to disk. The item must not already
     * exist at this location - use {@link #open} for existing items.
     *
     * @param rootDir The directory to mount the item into
     * @param iid     The item's identity
     * @return The WorkingTreeStore for the newly mounted item
     * @throws LibraryException.AlreadyExists if an item already exists at this location
     */
    public static WorkingTreeStore materialize(Path rootDir, ItemID iid) {
        return materialize(rootDir, iid, null);
    }

    /**
     * Mount an item to a filesystem path with a fallback store.
     *
     * @param rootDir  The directory to mount the item into
     * @param iid      The item's identity
     * @param fallback Fallback store for reads (null for FULL mode)
     * @return The WorkingTreeStore for the newly mounted item
     * @throws LibraryException.AlreadyExists if an item already exists at this location
     */
    public static WorkingTreeStore materialize(Path rootDir, ItemID iid, ItemStore fallback) {
        Objects.requireNonNull(rootDir, "rootDir");
        Objects.requireNonNull(iid, "iid");

        Path dotItem = rootDir.resolve(DOT_ITEM);
        Path iidPath = dotItem.resolve("iid");

        if (Files.exists(iidPath)) {
            throw new LibraryException.AlreadyExists("Item already exists at: " + rootDir);
        }

        // Create .item directory and write IID
        writeIid(dotItem, iidPath, iid);

        return new WorkingTreeStore(rootDir, iid, fallback);
    }

    /**
     * Check if an item exists at the given path without opening it.
     *
     * @param rootDir The directory to check
     * @return true if an item exists at this location
     */
    public static boolean exists(Path rootDir) {
        return Files.exists(rootDir.resolve(DOT_ITEM).resolve("iid"));
    }

    // ==================================================================================
    // Private Constructor
    // ==================================================================================

    /**
     * Private constructor - use static factory methods.
     */
    private WorkingTreeStore(Path root, ItemID iid, ItemStore fallback) {
        this.root = root;
        this.iid = iid;
        this.fallback = fallback;
        ensureDirs();
    }

    public ItemID iid() { return iid; }
    public Path root() { return root; }
    public Path dotItem() { return root.resolve(DOT_ITEM); }
    public boolean isThin() { return fallback != null; }

    /**
     * Get the fallback store, if any.
     *
     * <p>In thin mode, the fallback provides data not present locally.
     * In full mode (no fallback), returns null.
     *
     * @return The fallback store, or null if in full mode
     */
    public ItemStore fallback() { return fallback; }

    /**
     * Replace the fallback store.
     *
     * <p>Used during bootstrap to swap SeedStore (temporary) for RocksItemStore
     * (permanent) after seed data has been imported.
     *
     * @param fallback The new fallback store
     */
    public void setFallback(ItemStore fallback) {
        this.fallback = fallback;
    }

    /**
     * Check if this is a fresh/new item (no version checked out yet).
     */
    public boolean isFresh() {
        return !Files.exists(currentVersionPath());
    }

    /* ================================================================================================
     * Current version state - which version is checked out in this working tree
     *
     * This is local state, not head tracking (which lives in LibraryIndex).
     * Like git's HEAD - tracks what's currently materialized, not trust-based version preferences.
     * ================================================================================================ */

    /**
     * Get the current version ID (what's checked out), if set.
     */
    public Optional<ContentID> currentVersion() {
        Path path = currentVersionPath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            String ref = Files.readString(path, StandardCharsets.UTF_8).trim();
            // Reference format: "../versions/<vid>" or just "<vid>"
            String vidStr = ref.contains("/") ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
            return Optional.of(new ContentID(unhex(vidStr)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read current version", e);
        }
    }

    /**
     * Set the current version (what's checked out).
     */
    public void setCurrentVersion(ContentID vid, WriteTransaction wtx) {
        Objects.requireNonNull(vid, "vid");
        Objects.requireNonNull(wtx, "wtx");

        String ref = "../" + DIR_VERSIONS + "/" + hex(vid.encodeBinary());
        ((FsTx) wtx).stageAtomicReplace(currentVersionPath(), ref.getBytes(StandardCharsets.UTF_8));
    }

    private Path currentVersionPath() {
        return root.resolve(DOT_ITEM).resolve(DIR_HEAD).resolve(FILE_BASE);
    }

    private Path headDir() {
        return root.resolve(DOT_ITEM).resolve(DIR_HEAD);
    }

    /* ================================================================================================
     * Head Metadata - Component Table Persistence
     * ================================================================================================ */

    /**
     * Save component metadata to head/components/ directory.
     * This allows fast component table loading on boot without parsing the full manifest.
     *
     * @param components The component entries to save
     * @param wtx        Write transaction
     */
    public void saveHeadComponents(List<FrameEntry> components, WriteTransaction wtx) {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(wtx, "wtx");

        Path componentsDir = headDir().resolve(DIR_COMPONENTS);
        FsTx fsTx = (FsTx) wtx;

        // Clear existing component files (mark for deletion)
        try {
            if (Files.exists(componentsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(componentsDir)) {
                    for (Path file : stream) {
                        fsTx.stageDelete(file);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clear head/components", e);
        }

        // Write each component entry
        for (FrameEntry entry : components) {
            String filename = hex(entry.frameKey().toCanonicalString().getBytes(StandardCharsets.UTF_8)) + ".cbor";
            Path file = componentsDir.resolve(filename);
            byte[] bytes = entry.encodeBinary(Canonical.Scope.RECORD);
            fsTx.stageAtomicReplace(file, bytes);
        }
    }

    /**
     * Load component table from head/components/ directory.
     * Returns empty list if no components are saved.
     *
     * @return List of component entries
     */
    public List<FrameEntry> loadHeadComponents() {
        Path componentsDir = headDir().resolve(DIR_COMPONENTS);
        if (!Files.exists(componentsDir)) {
            return List.of();
        }

        List<FrameEntry> components = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(componentsDir, "*.cbor")) {
            for (Path file : stream) {
                byte[] bytes = Files.readAllBytes(file);
                FrameEntry entry = FrameEntry.decode(bytes);
                components.add(entry);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load head/components", e);
        }

        return components;
    }

    /**
     * Load a specific component entry from head/components/.
     *
     * @param key The component frame key
     * @return The component entry, or empty if not found
     */
    public Optional<FrameEntry> loadHeadComponent(FrameKey key) {
        Objects.requireNonNull(key, "key");

        Path file = headDir().resolve(DIR_COMPONENTS).resolve(
                hex(key.toCanonicalString().getBytes(StandardCharsets.UTF_8)) + ".cbor");
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try {
            byte[] bytes = Files.readAllBytes(file);
            return Optional.of(FrameEntry.decode(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load component: " + key, e);
        }
    }

    /* ================================================================================================
     * Head Metadata - Mount Table Persistence
     * ================================================================================================ */

    /**
     * Save mount metadata to head/mounts/ directory and materialize mount paths.
     *
     * <p>Each mount is stored as a separate CBOR file, keyed by a hash of the path.
     * This allows fast mount table loading on boot.
     *
     * <p>After saving metadata, this method also materializes the mount directories
     * on the filesystem, presenting the mount tree as actual directories.
     *
     * @param mounts The mount entries to save
     * @param wtx    Write transaction
     */
    /**
     * Ensure mount directories exist on disk for all mounted content entries.
     *
     * <p>Creates the filesystem directories for each path mount, allowing
     * components to be materialized at their mount points.
     *
     * <p>Skips:
     * <ul>
     *   <li>Root mounts ("/") - the item directory itself</li>
     *   <li>Local resource entries - the component manages its own storage</li>
     *   <li>Directories that already exist</li>
     * </ul>
     *
     * @param content The content table to derive mount paths from
     */
    public void materializeMountPaths(FrameTable content) {
        for (FrameEntry entry : content) {
            if (entry.isLocalResource()) continue;
            for (Mount.PathMount pm : entry.pathMounts()) {
                String mountPath = pm.path();
                if ("/".equals(mountPath)) continue;

                String fsPath = mountPath.substring(1);
                Path dir = root.resolve(fsPath);
                if (Files.exists(dir)) continue;

                try {
                    Files.createDirectories(dir);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to create mount directory: " + dir, e);
                }
            }
        }
    }

    /* ================================================================================================
     * Transactions
     * ================================================================================================ */

    @Override
    public WriteTransaction beginWriteTransaction() {
        return new FsTx(root.resolve(DOT_ITEM));
    }

    @Override
    public void runInWriteTransaction(Consumer<WriteTransaction> work) {
        Objects.requireNonNull(work, "work");
        try (FsTx tx = (FsTx) beginWriteTransaction()) {
            work.accept(tx);
            tx.commit();
        }
    }

    /**
     * Stream manifests from the filesystem versions directory.
     *
     * <p>WorkingTreeStore keeps manifests in the versions/ directory (keyed by VID),
     * separate from content. This overrides the default trial-decode approach.
     */
    @Override
    public java.util.stream.Stream<Manifest> manifests(ItemID iid) {
        List<Manifest> results = new ArrayList<>();

        // Local filesystem manifests
        Path versionsDir = root.resolve(DOT_ITEM).resolve(DIR_VERSIONS);
        if (Files.exists(versionsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
                for (Path file : stream) {
                    byte[] bytes = Files.readAllBytes(file);
                    try {
                        Manifest m = Manifest.decode(bytes);
                        if (m != null && (iid == null || iid.equals(m.iid()))) {
                            results.add(m);
                        }
                    } catch (Exception e) {
                        // Skip corrupt manifests
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to iterate manifests", e);
            }
        }

        // Fallback store manifests (if configured)
        if (fallback != null) {
            fallback.manifests(iid).forEach(results::add);
        }

        return results.stream();
    }

    /**
     * Iterate all objects.
     *
     * <p>Iterates local filesystem content, falling back to the parent store if configured.
     */
    @Override
    public Iterable<byte[]> iterateObjects() {
        List<byte[]> results = new ArrayList<>();

        // Local filesystem content
        Path contentDir = root.resolve(DOT_ITEM).resolve(DIR_CONTENT);
        if (Files.exists(contentDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(contentDir)) {
                for (Path file : stream) {
                    results.add(Files.readAllBytes(file));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to iterate content", e);
            }
        }

        // Fallback store objects (if configured)
        if (fallback != null) {
            for (byte[] obj : fallback.iterateObjects()) {
                results.add(obj);
            }
        }

        return results;
    }

    // ==================================================================================
    // Service Implementation
    // ==================================================================================

    @Override
    public Status status() {
        return closed ? Status.STOPPED : Status.RUNNING;
    }

    @Override
    public void start() {
        // WorkingTreeStore is file-based, always "running" when not closed
        closed = false;
    }

    @Override
    public void stop() {
        closed = true;
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public java.util.Optional<Path> path() {
        return java.util.Optional.of(root);
    }

    /* ================================================================================================
     * Filesystem Transaction
     * ================================================================================================ */

    private static final class FsTx implements WriteTransaction, AutoCloseable {
        private final Path txDir;
        private final List<Runnable> commitOps = new ArrayList<>();
        private final List<Path> stagedTemps = new ArrayList<>();
        private boolean done;

        FsTx(Path dotItemDir) {
            try {
                this.txDir = Files.createDirectories(dotItemDir.resolve(".tx"));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create tx dir", e);
            }
        }

        void stageAtomicReplace(Path target, byte[] bytes) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(bytes, "bytes");
            try {
                Files.createDirectories(target.getParent());

                String tmpName = target.getFileName() + ".tmp-" + System.nanoTime();
                Path tmp = txDir.resolve(tmpName);

                Files.write(tmp, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                stagedTemps.add(tmp);

                commitOps.add(() -> {
                    try {
                        Files.createDirectories(target.getParent());
                        try {
                            Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING);
                        } catch (AtomicMoveNotSupportedException e) {
                            Files.move(tmp, target, REPLACE_EXISTING);
                        }
                    } catch (IOException ex) {
                        throw new UncheckedIOException("Commit move failed: " + target, ex);
                    }
                });

            } catch (IOException e) {
                throw new UncheckedIOException("Stage write failed: " + target, e);
            }
        }

        void stageDelete(Path target) {
            commitOps.add(() -> {
                try { Files.deleteIfExists(target); }
                catch (IOException ex) { throw new UncheckedIOException("Delete failed: " + target, ex); }
            });
        }

        @Override public void commit() {
            if (done) return;
            done = true;
            for (Runnable r : commitOps) r.run();
            cleanup();
        }

        @Override public void rollback() {
            if (done) return;
            done = true;
            cleanup();
        }

        private void cleanup() {
            for (Path p : stagedTemps) {
                try { Files.deleteIfExists(p); } catch (IOException ignore) {}
            }
        }

        @Override public void close() {
            if (!done) rollback();
        }
    }

    /* ================================================================================================
     * Block storage - Implementor API
     * ================================================================================================ */

    // --- Manifest ---

    @Override
    public ContentID persistManifest(ItemID iid, byte[] record, WriteTransaction wtx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(wtx, "wtx");

        // Decode to compute VID (hash of BODY)
        Manifest m = Manifest.decode(record);
        byte[] body = m.encodeBinary(dev.everydaythings.graph.Canonical.Scope.BODY);
        ContentID vid = new ContentID(Hash.DEFAULT.digest(body), Hash.DEFAULT);

        Path p = manifestPath(vid);
        if (!Files.exists(p)) {
            ((FsTx) wtx).stageAtomicReplace(p, record);
        }
        return vid;
    }

    @Override
    public byte[] retrieveManifest(ItemID iid, ContentID vid) {
        Objects.requireNonNull(vid, "vid");

        Path p = manifestPath(vid);
        if (Files.exists(p)) return readAllBytes(p);

        if (fallback != null) {
            return fallback.retrieveManifest(iid, vid);
        }
        return null;
    }

    // --- Content ---

    @Override
    public ContentID persistContent(byte[] data, WriteTransaction wtx) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(wtx, "wtx");

        ContentID cid = new ContentID(Hash.DEFAULT.digest(data), Hash.DEFAULT);

        Path p = contentPath(cid);
        if (!Files.exists(p)) {
            ((FsTx) wtx).stageAtomicReplace(p, data);
        }
        return cid;
    }

    @Override
    public byte[] retrieveContent(ContentID cid) {
        Objects.requireNonNull(cid, "cid");

        Path p = contentPath(cid);
        if (Files.exists(p)) return readAllBytes(p);

        if (fallback != null) {
            return fallback.retrieveContent(cid);
        }
        return null;
    }

    /* ================================================================================================
     * Item lookup - delegates to fallback for type items
     * ================================================================================================ */

    /**
     * Get an item by ID.
     *
     * <p>WorkingTreeStore delegates to the fallback store for item lookup.
     * This is needed for type resolution (finding ComponentType items).
     *
     * @param iid The item ID to look up
     * @return The item, or empty if not found
     */
    @Override
    public Optional<Item> item(ItemID iid) {
        if (fallback != null) {
            return fallback.item(iid);
        }
        return Optional.empty();
    }

    /**
     * Find the implementing Java class for a type ID.
     *
     * <p>Delegates to the fallback store for type resolution. This is critical
     * during bootstrap when the Librarian is constructed and needs to find
     * component implementations from the seed store.
     */
    @Override
    public Optional<Class<?>> findImplementation(ItemID typeId) {
        if (fallback != null) {
            return fallback.findImplementation(typeId);
        }
        return Optional.empty();
    }

    /**
     * Find the implementing component class for a type ID.
     *
     * <p>Delegates to the fallback store for type resolution.
     */
    @Override
    public Optional<Class<?>> findComponentImplementation(ItemID typeId) {
        if (fallback != null) {
            return fallback.findComponentImplementation(typeId);
        }
        return Optional.empty();
    }

    /* ================================================================================================
     * Index - delegates to fallback
     * ================================================================================================ */

    /* ================================================================================================
     * Directory layout
     * ================================================================================================ */

    private void ensureDirs() {
        try {
            Path dotItem = root.resolve(DOT_ITEM);
            Files.createDirectories(dotItem);

            // Version-independent storage
            Files.createDirectories(dotItem.resolve(DIR_VERSIONS));
            Files.createDirectories(dotItem.resolve(DIR_CONTENT));
            Files.createDirectories(dotItem.resolve(DIR_RELATIONS));

            // Head structure (current version state)
            Path head = dotItem.resolve(DIR_HEAD);
            Files.createDirectories(head);
            Files.createDirectories(head.resolve(DIR_COMPONENTS));
            Files.createDirectories(head.resolve(DIR_ACTIONS));

            // Local-only storage (never synced)
            Files.createDirectories(dotItem.resolve(DIR_LOCAL));

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize working tree dirs", e);
        }
    }

    private Path manifestPath(ContentID vid) {
        return root.resolve(DOT_ITEM).resolve(DIR_VERSIONS).resolve(hex(vid.encodeBinary()));
    }

    private Path relationPath(ContentID cid) {
        return root.resolve(DOT_ITEM).resolve(DIR_RELATIONS).resolve(hex(cid.encodeBinary()));
    }

    private Path contentPath(ContentID cid) {
        return root.resolve(DOT_ITEM).resolve(DIR_CONTENT).resolve(hex(cid.encodeBinary()));
    }

    /* ================================================================================================
     * IID handling
     * ================================================================================================ */

    /**
     * Load an ItemID from an existing iid file.
     */
    private static ItemID loadIid(Path iidPath) {
        try {
            byte[] b = Files.readAllBytes(iidPath);

            // Try raw bytes first
            try {
                return new ItemID(b);
            } catch (RuntimeException ignore) {
                // Fallback: hex text
                String s = new String(b, StandardCharsets.UTF_8).trim();
                return new ItemID(unhex(s));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load .item/iid", e);
        }
    }

    /**
     * Write an ItemID to a new iid file.
     */
    private static void writeIid(Path dotItem, Path iidPath, ItemID iid) {
        try {
            Files.createDirectories(dotItem);
            Files.write(iidPath, iid.encodeBinary(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write .item/iid", e);
        }
    }

    /* ================================================================================================
     * Local Component Storage (never synced)
     * ================================================================================================ */

    /**
     * Get the local storage directory for local-only components.
     */
    public Path localDir() {
        return root.resolve(DOT_ITEM).resolve(DIR_LOCAL);
    }

    /**
     * Get the path for a local component by frame key.
     */
    public Path localComponentPath(FrameKey key) {
        return localDir().resolve(Encoding.hex(key.toCanonicalString().getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Get the path for a local component with a relative sub-path.
     * Used for path-based resources like RocksDB.
     *
     * @param key          The component frame key
     * @param relativePath Relative path within the component's local directory
     * @return Full path to the resource
     */
    public Path localComponentPath(FrameKey key, String relativePath) {
        return localComponentPath(key).resolve(relativePath);
    }

    /**
     * Store local-only component content (raw bytes).
     *
     * @param key     The component frame key
     * @param content The content bytes
     * @param wtx     Write transaction
     */
    public void putLocalContent(FrameKey key, byte[] content, WriteTransaction wtx) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(wtx, "wtx");

        Path file = localComponentPath(key);
        ((FsTx) wtx).stageAtomicReplace(file, content);
    }

    /**
     * Get local-only component content (raw bytes).
     *
     * @param key The component frame key
     * @return The content bytes, or empty if not found
     */
    public Optional<byte[]> getLocalContent(FrameKey key) {
        Objects.requireNonNull(key, "key");

        Path file = localComponentPath(key);
        if (Files.exists(file) && Files.isRegularFile(file)) {
            return Optional.of(readAllBytes(file));
        }
        return Optional.empty();
    }

    /**
     * Check if a local component directory exists (for path-based resources).
     *
     * @param key The component frame key
     * @return True if the local component directory exists
     */
    public boolean hasLocalComponent(FrameKey key) {
        Objects.requireNonNull(key, "key");
        Path path = localComponentPath(key);
        return Files.exists(path);
    }

    /* ================================================================================================
     * Helpers
     * ================================================================================================ */

    private static byte[] readAllBytes(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Read failed: " + p, e);
        }
    }

    // Delegate to Encoding
    private static String hex(byte[] bytes) {
        return Encoding.hex(bytes);
    }

    private static byte[] unhex(String s) {
        return Encoding.unhex(s);
    }

}
