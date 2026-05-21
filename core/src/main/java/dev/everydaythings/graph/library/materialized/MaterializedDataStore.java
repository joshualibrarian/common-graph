package dev.everydaythings.graph.library.materialized;

import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.encoding.EncodingRegistry;
import dev.everydaythings.graph.library.data.DataStore;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * MaterializedDataStore — a {@link DataStore} backed by an on-disk
 * {@code .item/} directory.  The directory IS the durable storage for
 * an item: its identity, its current manifest CID, its codec, its
 * content-addressed objects.
 *
 * <h2>On-disk layout</h2>
 *
 * <pre>
 * /some/path/                    ← the item's root; "mounted" content lives here
 * └── .item/                     ← the CG metadata directory (analogous to .git/)
 *     ├── iid                    ← raw multihash bytes of the item's IID
 *     ├── codec                  ← raw multihash bytes of the encoding's IID
 *     ├── head                   ← raw multihash bytes of the current manifest CID
 *     └── objects/               ← content-addressed bytes by ContentRef.encodeText()
 *         ├── ~base32-cid-1
 *         ├── ~base32-cid-2
 *         └── ...
 * </pre>
 *
 * <h2>Fat items</h2>
 *
 * <p>An item's store can hold its own data alone OR additional related
 * items' data alongside.  The directory is content-addressed; whatever
 * bytes are in {@code objects/} are queryable by their CID regardless of
 * which item "owns" them.  This is also the export mechanism: zip the
 * directory, transfer it, unzip — the recipient now has a working
 * MaterializedDataStore.
 *
 * <h2>Index</h2>
 *
 * <p>The {@link DatumRef}-to-{@link ContentRef} index (semantic identity
 * → on-disk realization) is built lazily by walking {@code objects/} on
 * first need, decoding each blob to ask its DatumRef, accumulating.
 * Cheap for small stores; can be cached to a {@code .item/datum-index}
 * file later when stores grow beyond casual sizes.
 */
@Log4j2
public final class MaterializedDataStore implements DataStore {

    private static final String META_DIR    = ".item";
    private static final String IID_FILE    = "iid";
    private static final String HEAD_FILE   = "head";
    private static final String CODEC_FILE  = "codec";
    private static final String OBJECTS_DIR = "objects";

    private final Path root;
    private final Path metaDir;
    private final Path objectsDir;
    private final ItemRef iid;
    private final ItemRef codec;
    private final Encoding encoding;

    private volatile ContentRef head;
    private final ConcurrentMap<DatumRef, Set<ContentRef>> datumIndex = new ConcurrentHashMap<>();
    private volatile boolean indexLoaded;

    private MaterializedDataStore(Path root,
                                   ItemRef iid,
                                   ItemRef codec,
                                   Encoding encoding,
                                   ContentRef head) {
        this.root       = root;
        this.metaDir    = root.resolve(META_DIR);
        this.objectsDir = metaDir.resolve(OBJECTS_DIR);
        this.iid        = iid;
        this.codec      = codec;
        this.encoding   = encoding;
        this.head       = head;
    }

    // ==================================================================================
    // Construction
    // ==================================================================================

    /**
     * Mint a fresh materialized item at {@code root}: create {@code .item/},
     * write {@code iid} and {@code codec}, create an empty {@code objects/}.
     * The codec IID is derived from {@link Encoding#encoding()}.  No head
     * is written until {@link #setHead(ContentRef)} is called.
     *
     * @throws IOException if the directory or any of the metadata files
     *                     can't be created, or if {@code .item/} already
     *                     exists (we don't clobber)
     */
    public static MaterializedDataStore mint(Path root,
                                              ItemRef iid,
                                              Encoding encoding) throws IOException {
        Objects.requireNonNull(root,     "root");
        Objects.requireNonNull(iid,      "iid");
        Objects.requireNonNull(encoding, "encoding");
        ItemRef codec = encoding.encoding();

        Path metaDir = root.resolve(META_DIR);
        if (Files.exists(metaDir)) {
            throw new IOException("Refusing to clobber existing .item/ at " + root);
        }
        Files.createDirectories(metaDir.resolve(OBJECTS_DIR));
        Files.write(metaDir.resolve(IID_FILE),   iid.toRefBytes());
        Files.write(metaDir.resolve(CODEC_FILE), codec.toRefBytes());
        return new MaterializedDataStore(root, iid, codec, encoding, null);
    }

    /**
     * Open an existing materialized item directory.  Reads {@code iid},
     * {@code codec}, and {@code head} (if present).  The codec IID
     * recorded on disk is looked up in the registry to find the matching
     * {@link Encoding}; if the registry doesn't know it, the open fails.
     */
    public static MaterializedDataStore open(Path root, EncodingRegistry registry) throws IOException {
        Objects.requireNonNull(root,     "root");
        Objects.requireNonNull(registry, "registry");

        Path metaDir = root.resolve(META_DIR);
        if (!Files.isDirectory(metaDir)) {
            throw new IOException("No .item/ directory at " + root);
        }
        ItemRef iid   = readItemRef(metaDir.resolve(IID_FILE));
        ItemRef codec = readItemRef(metaDir.resolve(CODEC_FILE));

        Encoding encoding = registry.get(codec).orElseThrow(() -> new IOException(
                "No codec registered for " + codec + " (declared in " + metaDir.resolve(CODEC_FILE) + "); "
                        + "known: " + registry.known()));

        Path headFile = metaDir.resolve(HEAD_FILE);
        ContentRef head = Files.exists(headFile)
                ? (ContentRef) HashID.fromRefBytes(Files.readAllBytes(headFile))
                : null;

        Path objectsDir = metaDir.resolve(OBJECTS_DIR);
        if (!Files.isDirectory(objectsDir)) {
            Files.createDirectories(objectsDir);
        }
        return new MaterializedDataStore(root, iid, codec, encoding, head);
    }

    // ==================================================================================
    // Materialization-specific
    // ==================================================================================

    public ItemRef iid()                  { return iid;   }
    public ItemRef codecRef()             { return codec; }
    public Optional<ContentRef> head()    { return Optional.ofNullable(head); }
    public Path root()                    { return root;  }
    public Path metaDir()                 { return metaDir; }

    /** Set the current manifest CID.  Writes {@code .item/head} atomically (tmp + rename). */
    public synchronized void setHead(ContentRef head) {
        Objects.requireNonNull(head, "head");
        Path headFile = metaDir.resolve(HEAD_FILE);
        Path tmpFile  = metaDir.resolve(HEAD_FILE + ".tmp");
        try {
            Files.write(tmpFile, head.toRefBytes());
            Files.move(tmpFile, headFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            this.head = head;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ==================================================================================
    // DataStore — Datum API
    // ==================================================================================

    @Override
    public Optional<Encoding> encoder() {
        return Optional.of(encoding);
    }

    @Override
    public DatumRef put(Datum datum) {
        Objects.requireNonNull(datum, "datum");
        byte[] bytes = encoding.encode(datum);
        ContentRef cid = writeBytes(bytes);
        DatumRef did = datum.datumId();
        datumIndex.computeIfAbsent(did, k -> ConcurrentHashMap.newKeySet()).add(cid);
        return did;
    }

    @Override
    public Optional<Datum> get(DatumRef datumId) {
        Objects.requireNonNull(datumId, "datumId");
        loadIndex();
        Set<ContentRef> cids = datumIndex.get(datumId);
        if (cids == null || cids.isEmpty()) return Optional.empty();
        // For first cut: any one CID is fine.  Multiple CIDs per DatumRef
        // appear when the same semantic datum has multiple wire forms
        // (full + redacted); strategy for picking lands later.
        ContentRef cid = cids.iterator().next();
        return readDatum(cid);
    }

    @Override
    public boolean has(DatumRef datumId) {
        Objects.requireNonNull(datumId, "datumId");
        loadIndex();
        Set<ContentRef> cids = datumIndex.get(datumId);
        return cids != null && !cids.isEmpty();
    }

    @Override
    public boolean delete(DatumRef datumId) {
        Objects.requireNonNull(datumId, "datumId");
        loadIndex();
        Set<ContentRef> cids = datumIndex.remove(datumId);
        if (cids == null) return false;
        boolean any = false;
        for (ContentRef cid : cids) {
            any |= deleteFile(cid);
        }
        return any;
    }

    // ==================================================================================
    // DataStore — Content-blob API
    // ==================================================================================

    @Override
    public ContentRef putContent(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return writeBytes(bytes);
    }

    @Override
    public Optional<byte[]> getContent(ContentRef cid) {
        Objects.requireNonNull(cid, "cid");
        Path file = objectsDir.resolve(cid.encodeText());
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasContent(ContentRef cid) {
        Objects.requireNonNull(cid, "cid");
        return Files.isRegularFile(objectsDir.resolve(cid.encodeText()));
    }

    @Override
    public boolean deleteContent(ContentRef cid) {
        Objects.requireNonNull(cid, "cid");
        return deleteFile(cid);
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public void close() {
        datumIndex.clear();
        indexLoaded = false;
    }

    // ==================================================================================
    // Internal helpers
    // ==================================================================================

    private ContentRef writeBytes(byte[] bytes) {
        ContentRef cid = ContentRef.of(bytes);
        Path file = objectsDir.resolve(cid.encodeText());
        if (Files.isRegularFile(file)) return cid;     // already stored
        try {
            Path tmp = objectsDir.resolve(cid.encodeText() + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return cid;
    }

    private boolean deleteFile(ContentRef cid) {
        try {
            return Files.deleteIfExists(objectsDir.resolve(cid.encodeText()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<Datum> readDatum(ContentRef cid) {
        Path file = objectsDir.resolve(cid.encodeText());
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            byte[] bytes = Files.readAllBytes(file);
            Object decoded = encoding.decode(bytes);
            return decoded instanceof Datum d ? Optional.of(d) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ItemRef readItemRef(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return (ItemRef) HashID.fromRefBytes(bytes);
    }

    /**
     * Lazily build the {@link DatumRef} → {@link ContentRef} index by
     * walking {@code objects/} and decoding each blob.  Idempotent; runs
     * at most once per store unless explicitly cleared.
     */
    private synchronized void loadIndex() {
        if (indexLoaded) return;
        Set<ContentRef> alreadySeen = new HashSet<>();
        try (Stream<Path> files = Files.list(objectsDir)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                 .forEach(p -> indexOne(p, alreadySeen));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        indexLoaded = true;
    }

    private void indexOne(Path file, Set<ContentRef> alreadySeen) {
        try {
            ContentRef cid = ContentRef.fromText(file.getFileName().toString());
            if (!alreadySeen.add(cid)) return;
            byte[] bytes = Files.readAllBytes(file);
            Object decoded = encoding.decode(bytes);
            if (decoded instanceof Datum d) {
                datumIndex.computeIfAbsent(d.datumId(), k -> ConcurrentHashMap.newKeySet()).add(cid);
            }
        } catch (Exception e) {
            // Files we can't decode are content blobs (or corrupt); skip.
            logger.debug("indexOne skipping {} ({})", file, e.toString());
        }
    }
}
