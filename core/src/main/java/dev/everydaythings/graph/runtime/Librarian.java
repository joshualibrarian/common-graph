package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.component.ExpressionComponent;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.SurfaceTemplateComponent;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.library.skiplist.SkipListItemStore;
import dev.everydaythings.graph.runtime.protocol.SessionServer;
import dev.everydaythings.graph.value.ValueType;
import lombok.extern.log4j.Log4j2;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.DisplayInfo;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.VerbEntry;
import dev.everydaythings.graph.item.action.ActionContext;
import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.VersionID;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.item.user.User;
import dev.everydaythings.graph.library.directory.ItemDirectory;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.library.dictionary.TokenExtractor;
import dev.everydaythings.graph.library.dictionary.TokenDictionary;
import dev.everydaythings.graph.library.SeedVocabulary;
import dev.everydaythings.graph.library.workingtree.WorkingTreeStore;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.network.CgProtocol;
import dev.everydaythings.graph.network.NetworkManager;
import dev.everydaythings.graph.network.ProtocolContext;
import dev.everydaythings.graph.network.peer.PeerConnection;
import dev.everydaythings.graph.network.peer.RemotePeer;
import dev.everydaythings.graph.network.peer.UnixPeerConnection;
import dev.everydaythings.graph.network.transport.UnixSocketServer;
import dev.everydaythings.graph.network.message.Delivery;
import dev.everydaythings.graph.network.message.Heartbeat;
import dev.everydaythings.graph.network.message.ProtocolMessage;
import dev.everydaythings.graph.network.message.Request;
import dev.everydaythings.graph.value.Endpoint;
import dev.everydaythings.graph.value.IpAddress;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import dev.everydaythings.graph.runtime.options.LibrarianOptions;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * The Librarian is the runtime's bootstrap item and primary access point.
 *
 * <p>Librarian is a special Item that:
 * <ul>
 *   <li>IS an Item (extends Signer, has IID, versions, can be signed)</li>
 *   <li>Owns the local storage and index (via Library)</li>
 *   <li>Provides a simple API for working with items and relations</li>
 *   <li>Optionally lives at {@code <root>/.librarian/} on disk</li>
 * </ul>
 *
 * <h2>Deployment Modes</h2>
 * <ul>
 *   <li><b>File-based</b>: {@link #open(Path)} - Persistent storage with RocksDB</li>
 *   <li><b>In-memory</b>: {@link #createInMemory()} - Ephemeral, no filesystem needed</li>
 * </ul>
 *
 * <p>In-memory mode uses:
 * <ul>
 *   <li>{@link dev.everydaythings.graph.vault.InMemoryVault} - Ephemeral keys</li>
 *   <li>{@link Library#memory()} - SkipList-backed storage</li>
 *   <li>No Unix socket (uses TCP localhost for session server)</li>
 * </ul>
 *
 * <p>Librarian also serves as a daemon entry point, implementing both
 * {@link Callable} (for picocli) and {@link Daemon} (for jsvc).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Persistent (creates on first boot, loads on subsequent)
 * try (Librarian lib = Librarian.open(Paths.get("~/.common-graph"))) {
 *     // ...
 * }
 *
 * // Ephemeral (testing, demos, temporary sessions)
 * try (Librarian lib = Librarian.createInMemory()) {
 *     // Fetch an item
 *     Optional<MyItem> item = lib.get(someIid, MyItem.class);
 *
 *     // Query relations
 *     lib.relationsFrom(subjectIid).forEach(r -> ...);
 * }
 * }</pre>
 */
@Log4j2
@Type(value = Librarian.KEY, glyph = "📚", color = 0x4B6EAF)
@Command(
    name = "librarian",
    mixinStandardHelpOptions = true,
    description = "Run the Librarian backend daemon"
)
public final class Librarian extends Signer implements AutoCloseable, Daemon, Callable<Integer> {

    // === TYPE DEFINITION ===
    public static final String KEY = "cg:type/librarian";

    /** Default port for Common Graph protocol. */
    public static final int DEFAULT_PORT = 7432;

    /** Socket filename for local IPC. */
    public static final String SOCKET_FILENAME = "graph.sock";

    /** Sentinel IID for CLI shell instances. These instances only hold CLI options and are not functional. */
    private static final ItemID CLI_SHELL_IID = ItemID.fromString("cg:shell/librarian-cli");

    // ==================================================================================
    // CLI Options (transient - only used when running as daemon command)
    // ==================================================================================

    @Mixin
    private transient LibrarianOptions cliOpts = new LibrarianOptions();

    // ==================================================================================
    // Daemon State
    // ==================================================================================

    /** True when this instance is being used as a daemon (vs direct API usage). */
    private transient boolean runningAsDaemon = false;

    /** The live librarian instance when running as daemon. */
    private transient Librarian liveInstance;

    // ==================================================================================
    // Session Tracking
    // ==================================================================================

    /** Sessions currently connected to this librarian. */
    private final transient Set<SessionInfo> connectedSessions = ConcurrentHashMap.newKeySet();

    // --- On-disk paths ---
    private final Path rootPath;

    // Library holds ALL storage and indexing:
    // - Primary ItemStore (block storage)
    // - Store registry (additional stores)
    // - LibraryIndex (relation queries, head tracking)
    // - ItemDirectory (which store has item X?)
    // - TokenDictionary (human text → item lookup)
    @Frame(handle = "library", path = "library", localOnly = true)
    private Library library;

    // Expression: ? → implemented-by → * (all types - subjects of implemented-by relations)
    @Frame(handle = "types")
    ExpressionComponent typesExpr = ExpressionComponent.subjects(Sememe.IMPLEMENTED_BY.iid());

    // --- Services ---
    private final Clock clock = Clock.systemUTC();

    // --- Network ---
    private NetworkManager network;
    private CgProtocol cgProtocol;
    private UnixSocketServer unixSocket;

    // --- Session Protocol Server ---
    private SessionServer sessionServer;

    // --- Principal (role) and Workspace ---
    /**
     * The human/entity that owns this Librarian.
     *
     * <p>Principal is a role, not a type - any Signer can be a principal.
     */
    @Frame(key = {"cg.core:serves"}, endorsed = false)
    private Signer principal;

    @Frame(key = {"cg.core:available-at"}, endorsed = false)
    private Host host;

    /**
     * Items currently acting as workspaces (navigable container windows).
     *
     * <p>Any item with a Surface component can be a workspace - it's a ROLE, not a type.
     * Each workspace is a window containing its Surface's contents. You can have
     * multiple workspaces open simultaneously on large screens.
     *
     * <p>When empty, items render as floating OS windows (no enclosing workspace).
     */
    private final Set<ItemID> activeWorkspaces = new LinkedHashSet<>();

    /**
     * The workspace currently in full-screen mode (if any).
     *
     * <p>When a workspace is full-screened, all other workspaces are hidden.
     * Only one workspace can be full-screen at a time.
     */
    private ItemID fullscreenWorkspace;

    // --- Librarian's own relations ---
    @Frame(key = {"cg.core:reachable-at"}, endorsed = false)
    private List<Endpoint> endpoints;

    // ==================================================================================
    // Factory / Lifecycle
    // ==================================================================================

    /**
     * Open or create a Librarian at the given root path.
     *
     * <p>On first boot, creates a fresh Librarian with new identity and keypair.
     * On subsequent boots, loads from disk and verifies integrity.
     *
     * <p>Bootstrap sequence:
     * <ol>
     *   <li>Create SeedStore (in-memory, has SeedLibraryIndex for type lookups)</li>
     *   <li>Create Librarian (SeedStore as fallback during construction)</li>
     *   <li>Library created and initialized</li>
     *   <li>onFullyInitialized: import seed data into RocksDB, clear seedStore</li>
     * </ol>
     *
     * @param rootPath The graph root directory (e.g., ~/.common-graph/)
     * @return The initialized Librarian
     */
    public static Librarian open(Path rootPath) {
        logger.info("Opening Librarian at {}", rootPath);

        // Create in-memory seed store (provides type resolution during construction)
        ItemStore seeds = SkipListItemStore.create();
        List<Item> seedItems = SeedVocabulary.bootstrap(seeds);

        // Create Librarian (seed store as fallback for type lookups)
        Librarian librarian = new Librarian(rootPath, seeds);

        // Cache fully-populated seed items (with components attached during bootstrap)
        for (Item seed : seedItems) {
            librarian.library().cache(seed);
        }

        // Start Unix socket for local IPC (must happen after constructor completes)
        librarian.startUnixSocket().thenAccept(path ->
                logger.info("Unix socket ready at {}", path));

        // Start Session Protocol server
        librarian.startSessionServer();

        logger.info("Librarian ready: iid={}, freshBoot={}", librarian.iid(), librarian.freshBoot());
        return librarian;
    }

    /**
     * Create a new ephemeral in-memory Librarian.
     *
     * <p>This creates a fully functional Librarian with no persistent storage.
     * Useful for:
     * <ul>
     *   <li>Testing and experimentation</li>
     *   <li>Creating a librarian that will be configured and then deployed</li>
     *   <li>Temporary sessions</li>
     * </ul>
     *
     * @return A new in-memory Librarian
     */
    public static Librarian createInMemory() {
        logger.info("Creating in-memory Librarian");

        // Create in-memory seed store with vocabulary
        ItemStore seeds = SkipListItemStore.create();
        List<Item> seedItems = SeedVocabulary.bootstrap(seeds);

        // Create librarian using in-memory constructor
        Librarian librarian = new Librarian(seeds, InMemoryMarker.INSTANCE);

        // Cache fully-populated seed items (with components attached during bootstrap)
        for (Item seed : seedItems) {
            librarian.library().cache(seed);
        }

        logger.info("In-memory Librarian ready: iid={}", librarian.iid());
        return librarian;
    }

    /**
     * Type seed constructor.
     *
     * <p>Creates a minimal Librarian instance for use as a type seed.
     * This is NOT a functional Librarian - it just represents the type in the graph.
     *
     * @param iid The type's ItemID
     */
    protected Librarian(ItemID iid) {
        super(iid);
        this.rootPath = null;
        // Type seeds don't need content - null out the field initializer
        this.typesExpr = null;
    }

    /**
     * Hydration constructor for loading Librarian type seeds from DB.
     *
     * <p>NOTE: This creates a non-functional Librarian (no storage, no networking).
     * It's only used to hydrate the type seed so it can provide displayInfo.
     *
     * @param librarian The librarian performing hydration (unused for type seeds)
     * @param manifest  The manifest to hydrate from
     */
    protected Librarian(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        this.rootPath = null;
    }

    /**
     * Create or load a Librarian at the given root path.
     *
     * @param rootPath  The graph root directory
     * @param seedStore In-memory seed store for type lookups during construction (becomes fallback)
     */
    private Librarian(Path rootPath, ItemStore seedStore) {
        // Path-based construction: Item handles create vs load
        // Seed store provides type resolution during component initialization via fallback
        super(rootPath, seedStore);

        this.rootPath = rootPath;

        // NOTE: Library is created in onFullyInitialized() because
        // super() triggers onFullyInitialized() before we get here.

        // Sync pre-initialized field values that were set AFTER super() returned
        // (Field initializers like typesExpr run after super constructor completes)
        if (freshBoot) {
            content().setLive(
                    HandleID.of("types"),
                    "types",
                    typesExpr);
        }
    }

    /**
     * In-memory constructor for ephemeral Librarian.
     *
     * <p>Creates a fully functional Librarian with no persistent storage.
     * Used for testing, demos, and temporary sessions.
     *
     * @param seedStore In-memory store with bootstrapped vocabulary
     * @param marker    Marker parameter to distinguish from path-based constructor
     */
    private Librarian(ItemStore seedStore, InMemoryMarker marker) {
        super(seedStore, marker);

        this.rootPath = null;  // No filesystem path for in-memory librarian

        // NOTE: Library is created in onFullyInitialized() because
        // super() triggers onFullyInitialized() before we get here.

        // Sync pre-initialized field values
        if (freshBoot) {
            content().setLive(
                    HandleID.of("types"),
                    "types",
                    typesExpr);
        }
    }

    /**
     * No-arg constructor for picocli CLI parsing.
     *
     * <p>Creates a "shell" Librarian instance that only holds CLI options.
     * The actual librarian is created via {@link #open(Path)} when {@link #call()} runs.
     * This instance is NOT a functional Librarian - it uses a sentinel IID.
     */
    public Librarian() {
        super(CLI_SHELL_IID);  // Use sentinel IID - this is NOT a functional Librarian
        this.rootPath = null;
        this.typesExpr = null;
    }

    /**
     * Called after all components are initialized.
     *
     * <p>This runs boot-specific initialization after Library is ready.
     * On fresh boot, imports seed data into RocksDB from the store's fallback.
     *
     * <p>MUST call super.onFullyInitialized() first to initialize signing keys.
     */
    @Override
    protected void onFullyInitialized() {
        // Initialize signing keys from Vault (required before commit)
        super.onFullyInitialized();

        // Set librarian reference
        this.librarian = this;

        // Create the Library (must happen here, not in constructor,
        // because super() triggers onFullyInitialized() before constructor body runs)
        if (this.library == null) {
            if (store != null && store.root() != null) {
                // File-based: use RocksDB Library
                this.library = Library.file(store.root().resolve("library"));
            } else {
                // In-memory: use SkipList Library
                this.library = Library.memory();
            }
            this.library.setLibrarian(this);

            // Register as live instance so getLive() can find it
            // (ContentField annotation only registers during initializeComponents,
            // but library is created here after that phase completes)
            content().setLive(
                    HandleID.of("library"),
                    "library",
                    this.library);
        }

        // On fresh boot, import seed data
        if (freshBoot && library != null) {
            if (store instanceof WorkingTreeStore wts) {
                // File-based: import from seed store and swap fallback
                ItemStore seedStore = wts.fallback();
                if (seedStore != null) {
                    library.importFrom(seedStore, this::predicateIndexWeight);
                    wts.setFallback(library.store());
                }
            } else {
                // In-memory: import seeds directly into library
                ItemStore seeds = SkipListItemStore.create();
                SeedVocabulary.bootstrap(seeds);
                library.importFrom(seeds, this::predicateIndexWeight);
            }
        }

        if (freshBoot) {
            onFirstBoot();
        } else {
            onReload();
        }

        // Cache ourselves so library.get(iid) can find us
        if (library != null) {
            library.cache(this);
        }

        // Re-populate relation table now that endpoints are gathered
        populateRelationTable();

        // Ensure a host item exists for this machine
        ensureHost();

        // Ensure a principal (user) is set
        ensurePrincipal();
    }

    /**
     * First boot: gather network endpoints and commit first version.
     */
    private void onFirstBoot() {
        logger.debug("First boot - importing seeds and committing initial version");

        // Placeholder name — will be updated after ensureHost()/ensurePrincipal()
        setName("librarian");

        // Gather our network endpoints
        gatherEndpoints();

        // Commit our first version (self-sign)
        commit(this);

        // Persist manifest through the working tree store (if file-based)
        if (store instanceof WorkingTreeStore wts) {
            byte[] record = current.encodeBinary(Canonical.Scope.RECORD);
            var storedVid = new VersionID[1];
            wts.runInWriteTransaction(tx -> {
                storedVid[0] = wts.persistManifest(iid(), record, tx);
                wts.setCurrentVersion(storedVid[0], tx);
            });
        }

        // Register ourselves in the directory so we can be found by IID
        library().directory().ifPresent(dir -> {
            dir.runInWriteTransaction(tx -> {
                // Register with the library's primary store
                library().primaryStore().ifPresent(primaryStore ->
                    dir.register(iid(), primaryStore, tx));
            });
        });
    }

    /**
     * Reload: load existing state from working tree.
     *
     * <p>Note: Components are already hydrated by the path-based constructor
     * (via hydrate()). This method just loads the manifest to set current/base
     * and refreshes endpoints.
     *
     * <p>For in-memory librarians, this is a no-op since there's nothing to reload.
     */
    private void onReload() {
        // Only reload from WorkingTreeStore (file-based mode)
        if (!(store instanceof WorkingTreeStore wts)) {
            // In-memory mode - nothing to reload
            gatherEndpoints();
            return;
        }

        Optional<VersionID> currentVid = wts.currentVersion();
        if (currentVid.isEmpty()) {
            throw new IllegalStateException("Working tree has no version checked out but freshBoot is false");
        }

        VersionID vid = currentVid.get();

        // Load manifest from working tree (consumer API)
        this.current = wts.manifest(iid(), vid)
                .orElseThrow(() -> new IllegalStateException(
                        "Head points to version " + vid + " but manifest not found"));
        this.base = vid;

        // Swap fallback from seed store to RocksItemStore
        // (Seed store was only needed during construction for type lookups)
        ItemStore fallback = wts.fallback();
        if (fallback != null && fallback != library.store()) {
            wts.setFallback(library.store());
        }

        // Refresh network endpoints
        gatherEndpoints();
    }

    /**
     * Gather local network endpoints for Common Graph communication.
     *
     * <p>Filters to only include routable addresses:
     * <ul>
     *   <li>Excludes loopback (127.x.x.x, ::1)</li>
     *   <li>Excludes link-local (169.254.x.x, fe80::)</li>
     *   <li>Prefers IPv4 for now (simpler, more universally supported)</li>
     * </ul>
     */
    private void gatherEndpoints() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            InetAddress[] addrs = InetAddress.getAllByName(localHost.getHostName());
            endpoints = Arrays.stream(addrs)
                    .filter(addr -> !addr.isLoopbackAddress())
                    .filter(addr -> !addr.isLinkLocalAddress())
                    .filter(addr -> addr instanceof Inet4Address) // IPv4 only for now
                    .map(addr -> Endpoint.cg(IpAddress.fromInetAddress(addr), DEFAULT_PORT))
                    .toList();
        } catch (UnknownHostException e) {
            endpoints = List.of();
        }
    }

    @Override
    public void close() {
        logger.debug("Closing Librarian at {}", rootPath);

        // Stop session server first
        stopSessionServer();

        // Stop network
        stopNetwork();

        // Close Library (closes store, index, directory, tokenDict)
        if (library != null) {
            try { library.close(); } catch (Exception ignore) {}
        }
        // Close working tree store
        if (store != null) {
            try { store.close(); } catch (Exception ignore) {}
        }
    }

    // ==================================================================================
    // Display Info
    // ==================================================================================

    @Override
    public DisplayInfo displayInfo() {
        // Override name to use the root path (more descriptive than just "Librarian")
        // Delegate to parent's resolution (instance SurfaceTemplateComponent → type → annotation)
        DisplayInfo base = super.displayInfo();
        String name = rootPath != null ? rootPath.getFileName().toString() : "Librarian";
        return base.withName(name);
    }

    @Override
    public String displayToken() {
        return displayInfo().displayName();
    }

    /**
     * Resolve display info for a type from the graph.
     *
     * <p>Looks up the type Item and extracts its SurfaceTemplateComponent. This is the
     * proper way to get display metadata for any type - the graph is the source
     * of truth for all display information.
     *
     * @param typeId The type's ItemID (e.g., "cg:type/log")
     * @return DisplayInfo from the type, or a default based on the type key
     */
    public DisplayInfo resolveTypeDisplay(ItemID typeId) {
        if (typeId == null) {
            return DisplayInfo.builder()
                    .name("Unknown")
                    .iconText("\u2753")
                    .build();
        }

        // Try to get the type Item from the graph
        Optional<Item> typeItem = get(typeId, Item.class);
        if (typeItem.isPresent()) {
            Item type = typeItem.get();

            // Look for SurfaceTemplateComponent on the type
            var stc = type.content().getLive(
                    SurfaceTemplateComponent.HANDLE,
                    SurfaceTemplateComponent.class
            );

            if (stc.isPresent()) {
                // Extract name from type key for the display
                String typeName = extractTypeShortName(typeId);
                return stc.get().toDisplayInfo(typeName);
            }

            // Type exists but no SurfaceTemplateComponent - use type's own display
            return type.displayInfo();
        }

        // Type not in graph - extract what we can from the key
        String typeName = extractTypeShortName(typeId);
        return DisplayInfo.builder()
                .name(typeName != null ? typeName : typeId.encodeText())
                .typeName(typeName)
                .iconText("\uD83D\uDCE6")  // Default package icon
                .build();
    }

    /**
     * Extract a short readable name from a type ItemID.
     * <p>e.g., "cg:type/log" → "Log", "cg:type/expression" → "Expression"
     */
    private static String extractTypeShortName(ItemID typeId) {
        String text = typeId.encodeText();
        int lastSlash = text.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < text.length() - 1) {
            String shortName = text.substring(lastSlash + 1);
            if (!shortName.isEmpty()) {
                return Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1);
            }
        }
        return null;
    }

    // ==================================================================================
    // Network Operations
    // ==================================================================================

    /**
     * Start the network layer, listening for peer connections.
     *
     * <p>This starts the NetworkManager which:
     * <ul>
     *   <li>Listens on the configured port for incoming connections</li>
     *   <li>Provides a client for outbound connections to peers</li>
     *   <li>Dispatches incoming messages to the appropriate handlers</li>
     * </ul>
     *
     * @return Future that completes when the network is ready
     */
    public CompletableFuture<Void> startNetwork() {
        return startNetwork(DEFAULT_PORT);
    }

    /**
     * Start the network layer on a specific port.
     *
     * @param port The port to listen on (0 for auto-assign)
     * @return Future that completes when the network is ready
     */
    public CompletableFuture<Void> startNetwork(int port) {
        if (network != null) {
            return CompletableFuture.completedFuture(null);
        }

        // Get Netty SslContext from vault for TLS
        io.netty.handler.ssl.SslContext serverSsl = null;
        io.netty.handler.ssl.SslContext clientSsl = null;
        if (vault() != null) {
            try {
                serverSsl = vault().serverSslContext();
                clientSsl = vault().clientSslContext();
                logger.info("TLS enabled with certificate from vault");
            } catch (Exception e) {
                logger.warn("Could not get SslContext from vault, running without TLS: {}", e.getMessage());
            }
        }

        logger.info("Starting network on port {} (TLS: {})", port, serverSsl != null);

        // Create protocol handler
        cgProtocol = new CgProtocol(new ProtocolContext(this));

        network = new NetworkManager(port, new NetworkManager.MessageDispatcher() {
            @Override
            public void onPeerConnected(PeerConnection connection, X509Certificate peerCert) {
                logger.info("Peer connected: {} (cert: {})",
                        connection.remoteAddress(),
                        peerCert != null ? peerCert.getSubjectX500Principal().getName() : "none");

                // Delegate to CgProtocol
                cgProtocol.onPeerConnected(connection, false);  // inbound connection
            }

            @Override
            public void onMessage(PeerConnection connection, ProtocolMessage message) {
                // Delegate to CgProtocol
                cgProtocol.handleMessage(connection, message);
            }

            @Override
            public void onPeerDisconnected(PeerConnection connection) {
                logger.info("Peer disconnected: {}", connection.remoteAddress());
                cgProtocol.onPeerDisconnected(connection);
            }
        }, serverSsl, clientSsl);

        return network.start();
    }

    /**
     * Stop the network layer.
     */
    public void stopNetwork() {
        stopUnixSocket();
        if (network != null) {
            logger.info("Stopping network");
            network.close();
            network = null;
        }
        cgProtocol = null;
    }

    // ==================================================================================
    // Unix Socket (Local IPC)
    // ==================================================================================

    /**
     * Get the path to the Unix socket file for local IPC.
     *
     * <p>The socket is created at {@code <rootPath>/graph.sock}.
     *
     * @return Path to the socket file
     */
    public Path socketPath() {
        return rootPath.resolve(SOCKET_FILENAME);
    }

    /**
     * Start the Unix socket server for local IPC.
     *
     * <p>Creates a Unix domain socket at {@link #socketPath()} for local
     * communication with other processes on this machine. This is the preferred
     * transport for local librarian-to-librarian communication.
     *
     * @return Future that completes with the socket path when ready
     */
    public CompletableFuture<Path> startUnixSocket() {
        if (unixSocket != null) {
            return CompletableFuture.completedFuture(socketPath());
        }

        if (!UnixSocketServer.isAvailable()) {
            logger.warn("Unix domain sockets not available on this platform");
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Unix domain sockets require native transport (epoll or kqueue)"));
        }

        Path sockPath = socketPath();
        logger.info("Starting Unix socket server at {}", sockPath);

        unixSocket = new UnixSocketServer(sockPath, new UnixSocketServer.IncomingHandler() {
            @Override
            public void onConnect(UnixPeerConnection connection) {
                logger.debug("Local client connected: {}", connection);
            }

            @Override
            public void onMessage(UnixPeerConnection connection, byte[] data) {
                handleUnixMessage(connection, data);
            }

            @Override
            public void onDisconnect(UnixPeerConnection connection) {
                logger.debug("Local client disconnected: {}", connection);
            }
        });

        return unixSocket.start();
    }

    /**
     * Stop the Unix socket server.
     */
    public void stopUnixSocket() {
        if (unixSocket != null) {
            logger.info("Stopping Unix socket server");
            unixSocket.close();
            unixSocket = null;
        }
    }

    /**
     * Check if the Unix socket server is running.
     */
    public boolean isUnixSocketRunning() {
        return unixSocket != null;
    }

    // ==================================================================================
    // Session Protocol Server
    // ==================================================================================

    /**
     * Start the Session Protocol server.
     *
     * <p>Listens for local session connections. Prefers Unix socket if we have
     * a directory, falls back to TCP localhost for in-memory librarians.
     *
     * <p>This is used by TextSession (CLI/TUI) and embedded terminals.
     */
    public void startSessionServer() {
        if (sessionServer != null) {
            return;  // Already running
        }

        try {
            sessionServer = new SessionServer(this);

            if (rootPath != null) {
                // We have a directory - use Unix socket (preferred)
                Path sessionSocketPath = rootPath.resolve("session.sock");
                sessionServer.listenUnix(sessionSocketPath);

                // Write auto-token to file for local clients
                writeAutoTokenFile();

                logger.info("Session server started on Unix socket: {}", sessionSocketPath);
            } else {
                // In-memory librarian - use TCP localhost
                // Pick a random available port
                sessionServer.listenTcp("127.0.0.1", 0);
                logger.info("Session server started on TCP localhost (in-memory mode)");
            }
        } catch (Exception e) {
            logger.warn("Failed to start session server: {}", e.getMessage());
            // Non-fatal - librarian can still work without session server
        }
    }

    /**
     * Start the Session Protocol server on a specific TCP port.
     *
     * <p>Use this for explicit TCP configuration or remote access.
     *
     * @param host Host to bind to ("127.0.0.1" for local only, "0.0.0.0" for all interfaces)
     * @param port Port to listen on
     */
    public void startSessionServer(String host, int port) {
        if (sessionServer != null) {
            // Already have a server - add TCP listener
            try {
                sessionServer.listenTcp(host, port);
                logger.info("Session server also listening on TCP {}:{}", host, port);
            } catch (Exception e) {
                logger.warn("Failed to add TCP listener: {}", e.getMessage());
            }
            return;
        }

        try {
            sessionServer = new SessionServer(this);
            sessionServer.listenTcp(host, port);
            logger.info("Session server started on TCP {}:{}", host, port);
        } catch (Exception e) {
            logger.warn("Failed to start session server: {}", e.getMessage());
        }
    }

    /**
     * Stop the Session Protocol server.
     */
    public void stopSessionServer() {
        if (sessionServer != null) {
            sessionServer.close();
            sessionServer = null;
            deleteAutoTokenFile();
            logger.info("Session server stopped");
        }
    }

    /**
     * Get the path to the session socket (if using Unix socket).
     */
    public Path sessionSocketPath() {
        return rootPath != null ? rootPath.resolve("session.sock") : null;
    }

    /**
     * Get the connection string for connecting to this librarian's session server.
     *
     * <p>Returns a string that can be passed to `graph session --to <connection>`:
     * <ul>
     *   <li>Unix socket path (if available): "/path/to/session.sock"</li>
     *   <li>TCP localhost: "127.0.0.1:port"</li>
     *   <li>null if session server is not running</li>
     * </ul>
     *
     * @return Connection string, or null if session server isn't running
     */
    public String sessionConnectionString() {
        if (sessionServer == null) {
            return null;
        }

        // Prefer Unix socket if available (faster, more secure)
        if (rootPath != null && sessionServer.hasUnixSocket()) {
            return sessionSocketPath().toString();
        }

        // Fall back to TCP localhost
        if (sessionServer.hasTcpServer()) {
            int port = sessionServer.getTcpPort();
            if (port > 0) {
                return "127.0.0.1:" + port;
            }
        }

        return null;
    }

    /**
     * Get the local auto-token for session authentication.
     *
     * <p>This token is valid for local connections (Unix socket or TCP localhost).
     * It's regenerated each time the session server starts.
     *
     * @return The local auto-token, or null if session server isn't running
     */
    public String sessionLocalToken() {
        return sessionServer != null ? sessionServer.localAutoToken() : null;
    }

    /**
     * Create a pairing code for a new device/client.
     *
     * <p>The code is 6 digits, valid for 5 minutes, one-time use.
     * When a client authenticates with this code, they receive a
     * persistent token for future connections.
     *
     * @return The pairing code, or null if session server isn't running
     */
    public String createPairingCode() {
        return sessionServer != null ? sessionServer.createInviteCode() : null;
    }

    /**
     * Check if the session server is running.
     */
    public boolean isSessionServerRunning() {
        return sessionServer != null;
    }

    /**
     * Write the auto-token to a file for local clients.
     *
     * <p>The token file is written with restricted permissions (owner-only read)
     * so only the same user can read it.
     */
    private void writeAutoTokenFile() {
        if (rootPath == null || sessionServer == null) return;

        try {
            Path tokenPath = rootPath.resolve("session.token");
            String token = sessionServer.localAutoToken();
            if (token != null) {
                Files.writeString(tokenPath, token);
                // Set restrictive permissions (Unix only)
                try {
                    Files.setPosixFilePermissions(tokenPath,
                        PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException e) {
                    // Not Unix, skip permission setting
                }
                logger.debug("Wrote session token to {}", tokenPath);
            }
        } catch (Exception e) {
            logger.warn("Failed to write session token file: {}", e.getMessage());
        }
    }

    /**
     * Delete the auto-token file on shutdown.
     */
    private void deleteAutoTokenFile() {
        if (rootPath == null) return;

        try {
            Path tokenPath = rootPath.resolve("session.token");
            Files.deleteIfExists(tokenPath);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Handle an incoming message from a local Unix socket client.
     *
     * <p>Local messages use the same protocol as TCP, but with implicit trust
     * for same-machine communication.
     */
    private void handleUnixMessage(UnixPeerConnection connection, byte[] data) {
        try {
            ProtocolMessage message = ProtocolMessage.decode(data);
            logger.debug("Received {} from local client", message.getClass().getSimpleName());

            switch (message) {
                case Delivery delivery -> handleLocalDelivery(connection, delivery);
                case Request request -> handleLocalRequest(connection, request);
                case Heartbeat ignored -> {} // ignore
            }
        } catch (Exception e) {
            logger.warn("Failed to decode message from local client: {}", e.getMessage());
        }
    }

    /**
     * Handle a DELIVERY message from a local client.
     */
    private void handleLocalDelivery(UnixPeerConnection connection, Delivery delivery) {
        // Local deliveries are treated similarly to network deliveries
        // but with implicit trust (same machine = same user context)
        for (Delivery.Payload payload : delivery.payloads()) {
            switch (payload) {
                case Delivery.Payload.Item item -> {
                    Manifest manifest = item.manifest();
                    logger.debug("Local delivery: item {}", manifest.iid());
                    // TODO: Store manifest
                }
                case Delivery.Payload.Content content -> {
                    logger.debug("Local delivery: content {} ({} bytes)",
                            content.cid(), content.data().length);
                    // TODO: Store content
                }
                case Delivery.Payload.Relations relations -> {
                    logger.debug("Local delivery: {} relations", relations.relations().size());
                    // TODO: Store relations
                }
                case Delivery.Payload.NotFound notFound -> {
                    logger.debug("Local: not found {}", notFound.iid());
                }
                case Delivery.Payload.Envelope envelope -> {
                    logger.debug("Local: envelope for {} (ignoring)", envelope.nextHop());
                }
            }
        }
    }

    /**
     * Handle a REQUEST message from a local client.
     */
    private void handleLocalRequest(UnixPeerConnection connection, Request request) {
        logger.debug("Local request {} with {} targets",
                request.requestId(), request.targets().size());

        // TODO: Fulfill requests and send DELIVERY responses
        for (Request.Target target : request.targets()) {
            switch (target) {
                case Request.Target.Item item ->
                    logger.debug("  Local request for item {} @ {}", item.iid(), item.vid());
                case Request.Target.Content content ->
                    logger.debug("  Local request for content {}", content.cid());
                case Request.Target.Relations rel ->
                    logger.debug("  Local request for relations (item={}, pred={})",
                            rel.item(), rel.predicate());
            }
        }
    }

    /**
     * Check if the network is running.
     */
    public boolean isNetworkRunning() {
        return network != null && network.isRunning();
    }

    /**
     * Get the network manager (for advanced operations).
     */
    public Optional<NetworkManager> network() {
        return Optional.ofNullable(network);
    }

    /**
     * Connect to a peer at the given endpoint.
     *
     * @param endpoint The endpoint to connect to
     * @return Future that completes with the connection
     */
    public CompletableFuture<PeerConnection> connect(Endpoint endpoint) {
        if (network == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Network not started"));
        }
        return network.connect(endpoint).thenApply(connection -> {
            // Notify CgProtocol about outbound connection
            if (cgProtocol != null) {
                cgProtocol.onPeerConnected(connection, false);  // outbound connection
            }
            return connection;
        });
    }

    /**
     * Get the CG protocol handler.
     */
    public Optional<CgProtocol> cgProtocol() {
        return Optional.ofNullable(cgProtocol);
    }

    /**
     * Get the IID of a connected peer, if known.
     */
    public Optional<ItemID> peerIdentity(PeerConnection connection) {
        if (cgProtocol == null) return Optional.empty();
        return cgProtocol.peer(connection)
                .filter(RemotePeer::isIdentified)
                .map(RemotePeer::librarianId);
    }

    /**
     * Get all connected peers.
     */
    public Iterable<RemotePeer> connectedPeers() {
        if (cgProtocol == null) return List.of();
        return cgProtocol.peers();
    }

    // ==================================================================================
    // Item Operations (Public API)
    // ==================================================================================

    /**
     * Fetch an item by ID.
     *
     * <p>For seed items (static vocabulary like Sememes), returns the in-memory instance.
     * For other items, hydrates from the stored manifest.
     *
     * @param iid  The item ID
     * @param type The expected item class
     * @return The item, or empty if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends Item> Optional<T> get(ItemID iid, Class<T> type) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(type, "type");

        logger.trace("get() called for iid={}", iid.encodeText());

        // First, check the cache (includes the librarian itself)
        Optional<Item> cached = library().getCached(iid);
        if (cached.isPresent()) {
            Item item = cached.get();
            if (type.isInstance(item)) {
                logger.trace("get() - found in cache: {}", item.getClass().getSimpleName());
                return Optional.of((T) item);
            }
        }

        // Next, try to find in directory
        Optional<ItemDirectory> dir = library().directory();
        if (dir.isEmpty()) {
            logger.debug("get() - directory is empty");
            return Optional.empty();
        }

        Optional<ItemDirectory.Entry> entry = dir.get().locate(iid);
        if (entry.isEmpty()) {
            logger.trace("get() - locate returned empty for iid={}", iid.encodeText());
            return Optional.empty();
        }

        // Found in directory - get from the store
        return switch (entry.get().location()) {
            case ItemDirectory.InStore(var itemStore) -> {
                // Load the manifest
                Optional<Manifest> manifestOpt = loadManifest(iid, itemStore);
                if (manifestOpt.isEmpty()) {
                    yield Optional.empty();
                }

                Manifest manifest = manifestOpt.get();

                // Hydrate the item from manifest
                yield hydrateItem(manifest, type);
            }
            case ItemDirectory.Rumor r -> {
                // TODO: Fetch from network
                yield Optional.empty();
            }
        };
    }

    /**
     * Load a manifest for an item from a store.
     */
    private Optional<Manifest> loadManifest(ItemID iid, ItemStore store) {
        // Get VID from library (index is internal to library)
        Optional<byte[]> vidBytes = library().getItemRecord(iid);
        if (vidBytes.isEmpty()) {
            logger.trace("loadManifest() - getItemRecord empty for iid={}", iid.encodeText());
            return Optional.empty();
        }

        VersionID vid = new VersionID(vidBytes.get());
        logger.trace("loadManifest() - got VID for iid={}, vid={}", iid.encodeText(), vid.encodeText());

        // Load manifest using consumer API
        Optional<Manifest> result = store.manifest(iid, vid);
        if (result.isEmpty()) {
            logger.debug("loadManifest() - store.manifest returned empty for iid={}, vid={}", iid.encodeText(), vid.encodeText());
        }
        return result;
    }

    /**
     * Hydrate an item from its manifest.
     *
     * <p>Looks up the implementing class via the manifest's type ID,
     * then instantiates it using the (Librarian, Manifest) constructor.
     *
     * @param manifest The item's manifest
     * @param expectedType The expected Java type (for casting)
     * @return The hydrated item, or empty if hydration fails
     */
    @SuppressWarnings("unchecked")
    private <T extends Item> Optional<T> hydrateItem(Manifest manifest, Class<T> expectedType) {
        ItemID typeId = manifest.type();
        if (typeId == null) {
            logger.warn("hydrateItem() - manifest has null typeId for iid={}", manifest.iid().encodeText());
            return Optional.empty();
        }

        logger.trace("hydrateItem() - typeId={} for iid={}", typeId.encodeText(), manifest.iid().encodeText());

        // Find the implementing class
        Optional<Class<? extends Item>> implClassOpt = library().findItemImplementation(typeId);
        if (implClassOpt.isEmpty()) {
            logger.debug("hydrateItem() - findItemImplementation returned empty for typeId={}", typeId.encodeText());
            return Optional.empty();
        }

        Class<? extends Item> implClass = implClassOpt.get();
        logger.trace("hydrateItem() - implClass={} for typeId={}", implClass.getSimpleName(), typeId.encodeText());

        // Check type compatibility
        if (!expectedType.isAssignableFrom(implClass)) {
            logger.debug("hydrateItem() - type mismatch: expectedType={}, implClass={}", expectedType.getSimpleName(), implClass.getSimpleName());
            return Optional.empty();
        }

        try {
            // Find and invoke the (Librarian, Manifest) constructor
            var ctor = implClass.getDeclaredConstructor(Librarian.class, Manifest.class);
            ctor.setAccessible(true);
            Item item = ctor.newInstance(this, manifest);
            return Optional.of((T) item);
        } catch (NoSuchMethodException e) {
            logger.warn("hydrateItem() - no (Librarian,Manifest) ctor for {}", implClass.getSimpleName());
            return Optional.empty();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            logger.error("hydrateItem() - ctor threw InvocationTargetException for {}", implClass.getSimpleName(), cause);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("hydrateItem() - ctor threw exception for {}", implClass.getSimpleName(), e);
            return Optional.empty();
        }
    }

    /**
     * Fetch the latest manifest for an item.
     */
    public Optional<Manifest> manifest(ItemID iid) {
        Objects.requireNonNull(iid, "iid");

        // Find in directory
        Optional<ItemDirectory> dir = library().directory();
        if (dir.isEmpty()) {
            return Optional.empty();
        }

        Optional<ItemDirectory.Entry> entry = dir.get().locate(iid);
        if (entry.isEmpty()) {
            return Optional.empty();
        }

        return switch (entry.get().location()) {
            case ItemDirectory.InStore(var store) -> loadManifest(iid, store);
            case ItemDirectory.Rumor r -> Optional.empty();
        };
    }

    /**
     * Save an item (store its current manifest and content).
     */
    public void save(Item item) {
        // TODO: Store manifest and update index
    }

    // ==================================================================================
    // Relation Operations
    // ==================================================================================

    /**
     * Resolve a predicate's index weight from its Sememe definition.
     *
     * <p>Looks up the predicate IID in the library, hydrates as a Sememe,
     * and returns the scaled indexWeight (1000 = 1.0f). Returns 0 if the
     * predicate is not a Sememe or has no index weight set.
     */
    float predicateIndexWeight(ItemID predicateId) {
        return get(predicateId, Sememe.class)
                .map(s -> s.indexWeight() / 1000.0f)
                .orElse(0.0f);
    }

    /**
     * Store and index a relation via the library.
     */
    @Verb(value = Sememe.PUT, doc = "Store and index a relation")
    public void relation(Relation relation) {
        // Library handles both storage and indexing
        library().relation(relation);

        // Also index in TokenDictionary (for title/name lookup)
        TokenDictionary tokenDict = tokenIndex();
        if (tokenDict != null) {
            List<Posting> postings = TokenExtractor.fromRelation(relation, this::predicateIndexWeight);
            if (!postings.isEmpty()) {
                tokenDict.runInWriteTransaction(tidxTx -> {
                    for (Posting p : postings) {
                        tokenDict.index(p, tidxTx);
                    }
                });
            }
        }
    }

    /**
     * Find relations where the given item is the subject.
     */
    public List<Relation> relationsFrom(ItemID subject) {
        // TODO: Query index
        return List.of();
    }

    /**
     * Find relations where the given item is the object.
     */
    public List<Relation> relationsTo(ItemID object) {
        // TODO: Query index
        return List.of();
    }

    /**
     * Find relations with a specific predicate.
     */
    public List<Relation> relationsWithPredicate(ItemID predicate) {
        // TODO: Query index
        return List.of();
    }

    // ==================================================================================
    // Content Operations
    // ==================================================================================

    /**
     * Fetch raw content by ID.
     */
    public Optional<byte[]> content(ContentID cid) {
        byte[] data = library().content(cid);
        return Optional.ofNullable(data);
    }

    /**
     * Store raw content, returning its content ID.
     */
    public ContentID storeContent(byte[] data) {
        var cid = new ContentID[1];
        library().runInWriteTransaction(tx -> {
            cid[0] = library().writableStore()
                    .orElseThrow(() -> new IllegalStateException("No writable store available"))
                    .persistContent(data, tx);
        });
        return cid[0];
    }

    /**
     * Store a manifest (used by Item during commit).
     *
     * <p>Uses library.manifest() to ensure both storage AND indexing happen,
     * so the item can be found via library.get(iid).
     */
    public void storeManifest(byte[] manifestBytes) {
        Manifest m = Manifest.decode(manifestBytes);
        library().manifest(m);  // Store AND index
    }

    /**
     * Store payload content (used by Item during commit).
     */
    public void storePayload(byte[] payloadBytes) {
        storeContent(payloadBytes);
    }

    // ==================================================================================
    // Services (for internal use by Items)
    // ==================================================================================

    /**
     * Clock for timestamps.
     */
    public Clock clock() {
        return clock;
    }

    /**
     * The principal (human/entity) that owns this Librarian.
     *
     * <p>Principal is a role - any Signer can be a principal.
     * May be null if no principal has been configured yet.
     */
    public Optional<Signer> principal() {
        return Optional.ofNullable(principal);
    }

    /**
     * Set the principal for this Librarian.
     */
    public void setPrincipal(Signer principal) {
        this.principal = principal;
    }

    /**
     * The Host item representing this machine.
     */
    public Host host() {
        return host;
    }

    /**
     * Ensure a principal is set, reloading from stored relation or auto-creating.
     *
     * <p>On reboot, looks up the "cg.core:serves" relation to find the previously
     * served user. On first boot, auto-creates a user from the system username.
     *
     * <p>Called at the end of {@link #onFullyInitialized()}.
     */
    private void ensurePrincipal() {
        if (principal != null) return;

        // Reload from stored relation (reboot case)
        ItemID servesId = ItemID.fromString("cg.core:serves");
        library().byItemPredicate(iid(), servesId)
                .findFirst()
                .ifPresent(rel -> {
                    if (rel.object() instanceof Relation.IidTarget target) {
                        get(target.iid(), User.class).ifPresent(this::setPrincipal);
                    }
                });

        if (principal != null) {
            logger.info("Reloaded principal: {}", principal.displayToken());
            updateLibrarianName();
            return;
        }

        // First boot: auto-create from system username
        String name = System.getProperty("user.name", "user");
        if (isSystemUser(name)) name = "user";

        User user = User.create(this, name);
        setPrincipal(user);
        commit(this);  // Persist the "serves" relation
        logger.info("Auto-created principal: {}", user.displayToken());

        // Update librarian name now that both host and principal are known
        updateLibrarianName();
    }

    /**
     * Ensure a host item exists, reloading from stored relation or creating fresh.
     *
     * <p>On reboot, looks up the "cg.core:available-at" relation to find the
     * previously created Host. On first boot, creates a new Host item.
     *
     * <p>Called before {@link #ensurePrincipal()} in {@link #onFullyInitialized()}.
     */
    private void ensureHost() {
        if (host != null) return;

        // Reload from stored relation (reboot case)
        ItemID availableAtId = ItemID.fromString("cg.core:available-at");
        library().byItemPredicate(iid(), availableAtId)
                .findFirst()
                .ifPresent(rel -> {
                    if (rel.object() instanceof Relation.IidTarget target) {
                        get(target.iid(), Host.class).ifPresent(h -> this.host = h);
                    }
                });

        if (host != null) {
            logger.info("Reloaded host: {}", host.displayToken());
            return;
        }

        // First boot: create host item
        if (rootPath() != null) {
            Path hostPath = rootPath().resolve("host");
            host = new Host(this, hostPath);
        } else {
            host = new Host(this);
        }
        host.commit(this);
        library().cache(host);
        logger.info("Created host: {}", host.hostname());
    }

    /**
     * Update the librarian name to "for &lt;user&gt; on &lt;host&gt;" format.
     */
    private void updateLibrarianName() {
        String userName = principal != null ? principal.name() : "unknown";
        String hostName = host != null ? host.hostname() : "localhost";
        setName("for " + userName + " on " + hostName);
        commit(this);
    }

    /**
     * Detect system/service accounts that should not be used as principal names.
     */
    private static boolean isSystemUser(String name) {
        if (name == null || name.isEmpty()) return true;
        return Set.of("root", "daemon", "bin", "sys", "nobody",
                "www-data", "systemd-resolve", "sshd", "messagebus")
                .contains(name.toLowerCase());
    }

    /**
     * Get all currently active workspaces.
     *
     * <p>Any item with a Surface can be a workspace. Multiple workspaces can
     * be open simultaneously, each in its own window.
     *
     * @return Unmodifiable set of workspace item IDs
     */
    public Set<ItemID> activeWorkspaces() {
        return Collections.unmodifiableSet(activeWorkspaces);
    }

    /**
     * Add an item as an active workspace.
     *
     * <p>Opens the item as a workspace window. The item should have a Surface
     * component that defines the layout of contents.
     *
     * @param itemId The item to use as workspace
     * @return true if added, false if already a workspace
     */
    public boolean addWorkspace(ItemID itemId) {
        return activeWorkspaces.add(itemId);
    }

    /**
     * Remove an item from active workspaces.
     *
     * @param itemId The workspace to close
     * @return true if removed, false if wasn't a workspace
     */
    public boolean removeWorkspace(ItemID itemId) {
        if (itemId.equals(fullscreenWorkspace)) {
            fullscreenWorkspace = null;
        }
        return activeWorkspaces.remove(itemId);
    }

    /**
     * Check if an item is currently an active workspace.
     */
    public boolean isWorkspace(ItemID itemId) {
        return activeWorkspaces.contains(itemId);
    }

    /**
     * Check if any workspaces are currently active.
     */
    public boolean hasActiveWorkspaces() {
        return !activeWorkspaces.isEmpty();
    }

    /**
     * Clear all active workspaces.
     *
     * <p>When no workspaces are active, items render as floating OS windows.
     */
    public void clearAllWorkspaces() {
        activeWorkspaces.clear();
        fullscreenWorkspace = null;
    }

    /**
     * Get the workspace currently in full-screen mode.
     *
     * @return The full-screen workspace ID, or empty if none
     */
    public Optional<ItemID> fullscreenWorkspace() {
        return Optional.ofNullable(fullscreenWorkspace);
    }

    /**
     * Set a workspace to full-screen mode.
     *
     * <p>When a workspace is full-screened, all other workspaces are hidden
     * until full-screen is exited. The item must already be an active workspace.
     *
     * @param itemId The workspace to full-screen, or null to exit full-screen
     */
    public void setFullscreenWorkspace(ItemID itemId) {
        if (itemId != null && !activeWorkspaces.contains(itemId)) {
            // Auto-add as workspace if not already
            activeWorkspaces.add(itemId);
        }
        this.fullscreenWorkspace = itemId;
    }

    /**
     * Exit full-screen mode (if any workspace is full-screened).
     */
    public void exitFullscreen() {
        this.fullscreenWorkspace = null;
    }

    /**
     * Check if a workspace is currently full-screened.
     */
    public boolean isFullscreen() {
        return fullscreenWorkspace != null;
    }

    /**
     * The Library providing storage, indexing, and directory services.
     *
     * <p>Library is created with a backend based on configuration:
     * RocksDB for file-backed (production), SkipList for memory (tests).
     *
     * @return The Library
     */
    public Library library() {
        return library;
    }

    /**
     * The TokenDictionary for human text → item resolution.
     *
     * <p>TokenDictionary is owned by the Library as one of its 5 parts.
     *
     * @return The TokenDictionary, or null if library not yet initialized
     */
    public TokenDictionary tokenIndex() {
        return library != null
                ? library.tokenDictionary().orElse(null)
                : null;
    }

    // ==================================================================================
    // Store Access
    // ==================================================================================

    /**
     * The primary store for all item and relation operations.
     *
     * @return The RocksItemStore
     */
    public Optional<ItemStore> primaryStore() {
        return library().primaryStore();
    }

    /**
     * Get all known Item types in the graph.
     *
     * <p>This queries the graph for all items that have an {@code implementedBy}
     * relation, which indicates they are type definitions. This is a "pure graph"
     * query - no special type registry.
     *
     * <p>Returns a stream of type ItemIDs. To get the actual type item, use
     * {@link #get(ItemID, Class)} or navigate to it.
     *
     * @return Stream of type ItemIDs
     */
    @Verb(value = Sememe.LIST, doc = "List all known item types")
    public Stream<ItemID> types() {
        return library().byPredicate(Sememe.IMPLEMENTED_BY.iid())
                .map(Relation::subject);
    }

    /**
     * Set the principal (user) that this librarian serves.
     *
     * <p>Looks up the user by name in the token dictionary, then sets them
     * as the principal. The librarian is a servant — it executes items' actions
     * on behalf of its principal.
     *
     * @param userName The name of the user to serve
     * @return The user that is now the principal
     */
    @Verb(value = Sememe.SERVE, doc = "Set the principal (user) this librarian serves")
    public Signer serve(@Param(value = "user", doc = "The user to serve") ItemID userId) {
        // By the time we get here, Eval has already resolved the token to an IID
        Optional<User> found = get(userId, User.class);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("Not a user: " + userId.encodeText());
        }

        User user = found.get();
        setPrincipal(user);
        return user;
    }

    // ==================================================================================
    // Type Catalogs
    // ==================================================================================

    /**
     * Represents an entry in a type catalog.
     *
     * @param typeId The type's ItemID (e.g., "cg:type/librarian")
     * @param implClass The implementing Java class
     * @param displayName Human-readable name derived from the type key
     */
    public record TypeEntry(
            ItemID typeId,
            Class<?> implClass,
            String displayName
    ) {
        /**
         * Create a TypeEntry, deriving display name from the type ID.
         */
        public static TypeEntry of(ItemID typeId, Class<?> implClass) {
            String display = deriveDisplayName(typeId);
            return new TypeEntry(typeId, implClass, display);
        }

        private static String deriveDisplayName(ItemID typeId) {
            String key = typeId.toString();
            // Extract short name after last / or :
            int lastSlash = key.lastIndexOf('/');
            int lastColon = key.lastIndexOf(':');
            int lastSep = Math.max(lastSlash, lastColon);
            if (lastSep >= 0 && lastSep < key.length() - 1) {
                String shortName = key.substring(lastSep + 1);
                // Convert kebab-case to Title Case
                return toTitleCase(shortName);
            }
            return key;
        }

        private static String toTitleCase(String s) {
            if (s == null || s.isEmpty()) return s;
            StringBuilder result = new StringBuilder();
            boolean capitalizeNext = true;
            for (char c : s.toCharArray()) {
                if (c == '-' || c == '_') {
                    result.append(' ');
                    capitalizeNext = true;
                } else if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        }
    }

    /**
     * Get all registered Item types.
     *
     * <p>Queries the graph for all implementedBy relations where the object
     * is a Java class that extends Item.
     *
     * @return Stream of TypeEntry for each Item type
     */
    public Stream<TypeEntry> itemTypes() {
        return library().byPredicate(Sememe.IMPLEMENTED_BY.iid())
                .filter(r -> {
                    if (r.object() instanceof Literal lit) {
                        Class<?> c = lit.asJavaClass();
                        return c != null && Item.class.isAssignableFrom(c);
                    }
                    return false;
                })
                .map(r -> {
                    Literal lit = (Literal) r.object();
                    return TypeEntry.of(r.subject(), lit.asJavaClass());
                });
    }

    /**
     * Get all registered component types.
     *
     * <p>Queries the graph for all implementedBy relations where the object
     * is a Java class annotated with {@link Type}.
     *
     * @return Stream of TypeEntry for each component type
     */
    public Stream<TypeEntry> componentTypes() {
        return library().byPredicate(Sememe.IMPLEMENTED_BY.iid())
                .filter(r -> {
                    if (r.object() instanceof Literal lit) {
                        Class<?> c = lit.asJavaClass();
                        return c != null && c.isAnnotationPresent(Type.class);
                    }
                    return false;
                })
                .map(r -> {
                    Literal lit = (Literal) r.object();
                    return TypeEntry.of(r.subject(), lit.asJavaClass());
                });
    }

    /**
     * Get all registered ValueType seed items.
     *
     * <p>ValueTypes are Items that define value semantics (Decimal, Text, etc).
     * Returns the known seed value types directly since they are static.
     *
     * @return Stream of ValueType items
     */
    public Stream<ValueType> valueTypes() {
        // Return known seed value types directly
        return Stream.of(
                ValueType.BOOLEAN,
                ValueType.TEXT,
                ValueType.BYTES,
                ValueType.IP,
                ValueType.ENDPOINT,
                ValueType.INSTANT,
                ValueType.QUANTITY,
                ValueType.DECIMAL,
                ValueType.RATIONAL,
                ValueType.COUNT,
                ValueType.FLOAT64,
                ValueType.INTEGER
        );
    }

    // ==================================================================================
    // Info
    // ==================================================================================

    /**
     * The root path this Librarian manages.
     */
    public Path rootPath() {
        return rootPath;
    }

    /**
     * Check if this was a fresh boot (no prior data).
     */
    public boolean isFreshBoot() {
        return freshBoot;
    }

    // ==================================================================================
    // Command Dispatch
    // ==================================================================================

    /**
     * Dispatch a command to an item.
     *
     * <p>Delegates to the target item's dispatch method, with this librarian
     * as the caller.
     *
     * @param target  The item to dispatch the command to
     * @param command The action name
     * @param args    The arguments (as strings)
     * @return The action result
     */
    public ActionResult dispatch(Item target, String command, List<String> args) {
        return target.dispatch(this.iid(), command, args);
    }

    /**
     * Dispatch a command to this librarian.
     *
     * <p>Convenience method that dispatches to this librarian as the target.
     *
     * @param command The action name
     * @param args    The arguments (as strings)
     * @return The action result
     */
    public ActionResult dispatch(String command, List<String> args) {
        return dispatch(this, command, args);
    }

    // ==================================================================================
    // Verbs — UI-Accessible Operations (dispatched via Vocabulary)
    // ==================================================================================

    /**
     * Fetch an item by ID (verb overload for CLI dispatch).
     *
     * <p>VerbInvoker converts the string argument to ItemID via
     * {@link ItemID#fromString(String)}.
     *
     * @param iid The item ID
     * @return The item, or empty if not found
     */
    @Verb(value = Sememe.GET, doc = "Fetch an item by ID")
    public Optional<Item> get(
            @Param(value = "iid", doc = "Item ID") ItemID iid) {
        return get(iid, Item.class);
    }

    /**
     * Search for items by name/token.
     *
     * @param queryText The search query
     * @param limit     Maximum results (default 20)
     * @return Matching postings
     */
    @Verb(value = Sememe.QUERY, doc = "Search for items by name")
    public List<Posting> query(
            @Param(value = "query", doc = "Search query") String queryText,
            @Param(value = "limit", doc = "Maximum results", required = false) Integer limit) {
        int maxResults = limit != null ? limit : 20;
        TokenDictionary dict = tokenIndex();
        if (dict == null) {
            return List.of();
        }
        return dict.lookup(queryText).limit(maxResults).toList();
    }

    /**
     * Find items by relation predicate with optional subject/object constraints.
     *
     * <p>Thematic-role mapping:
     * <ul>
     *   <li>THEME      → predicate sememe/item ID (required)</li>
     *   <li>RECIPIENT  → object constraint (e.g. "for chess")</li>
     *   <li>SOURCE     → subject constraint (e.g. "from chess")</li>
     * </ul>
     *
     * <p>Examples:
     * <pre>{@code
     * find implemented-by
     * find implemented-by for chess
     * find implemented-by from chess
     * }</pre>
     */
    @Verb(value = Sememe.FIND, doc = "Find items by relation predicate and optional role constraints")
    public List<ItemID> find(
            @Param(value = "predicate", role = "THEME", doc = "Relation predicate sememe/item ID")
            ItemID predicate,
            @Param(value = "object", role = "RECIPIENT", required = false,
                    doc = "Object constraint (e.g. 'for chess')")
            ItemID object,
            @Param(value = "subject", role = "SOURCE", required = false,
                    doc = "Subject constraint (e.g. 'from chess')")
            ItemID subject) {
        if (predicate == null) return List.of();

        Stream<Relation> relations;
        if (subject != null && object != null) {
            // Both provided: query by one, filter by the other
            relations = library().byItemPredicate(subject, predicate)
                    .filter(r -> {
                        Relation.Target tgt = r.object();
                        return tgt instanceof Relation.IidTarget iidTarget
                                && object.equals(iidTarget.iid());
                    });
        } else if (subject != null) {
            relations = library().byItemPredicate(subject, predicate);
        } else if (object != null) {
            relations = library().byItemPredicate(object, predicate);
        } else {
            relations = library().byPredicate(predicate);
        }

        if (subject != null && object == null) {
            // from <subject>: return objects
            return relations
                    .flatMap(r -> {
                        Relation.Target tgt = r.object();
                        if (tgt instanceof Relation.IidTarget iidTarget) {
                            return Stream.of(iidTarget.iid());
                        }
                        return Stream.empty();
                    })
                    .distinct()
                    .toList();
        }

        // default + "for <object>": return subjects
        return relations
                .map(Relation::subject)
                .distinct()
                .toList();
    }

    /**
     * Show librarian status including path, library info, and network status.
     */
    @Verb(value = Sememe.DESCRIBE, doc = "Show librarian status")
    public String status() {
        StringBuilder sb = new StringBuilder();
        sb.append("Librarian: ").append(rootPath != null ? rootPath : "(in-memory)").append("\n");
        sb.append("IID: ").append(iid().encodeText()).append("\n");

        if (library != null) {
            sb.append("Library: open\n");
            // Count types as a proxy for library content
            long typeCount = itemTypes().count();
            sb.append("  Registered types: ").append(typeCount).append("\n");
        } else {
            sb.append("Library: not initialized\n");
        }

        if (network != null) {
            sb.append("Network: running\n");
        } else {
            sb.append("Network: stopped\n");
        }

        if (principal != null) {
            sb.append("Principal: ").append(principal.iid().encodeText()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate an invite code for someone to engage (register) with this librarian.
     *
     * <p>The invite code is a one-time, 5-minute expiry code. Share it with someone
     * who wants to connect. They use {@code graph --to <host:port> --engage <code> --name <name>}
     * to register a new identity and start a session.
     *
     * @return A message with the invite code
     */
    @Verb(value = Sememe.INVITE, doc = "Generate an invite code for a new user")
    public String invite() {
        if (sessionServer == null) {
            return "No session server running — start with --daemon or combined mode.";
        }

        String code = sessionServer.createInviteCode();
        return "Invite code: " + code + "\nExpires in 5 minutes. Share with: graph --to <host:port> --engage " + code + " --name <name>";
    }

    /**
     * Show available verbs on this librarian.
     */
    @Verb(value = Sememe.HELP, doc = "Show available verbs")
    public String help() {
        StringBuilder sb = new StringBuilder();
        sb.append("Librarian Verbs:\n");
        for (VerbEntry verb : vocabulary()) {
            String key = verb.sememeKey();
            // Extract short name from canonical key (e.g., "cg.verb:get" -> "get")
            int colonIdx = key.lastIndexOf(':');
            String shortName = colonIdx >= 0 ? key.substring(colonIdx + 1) : key;
            sb.append("  ").append(shortName);
            if (verb.doc() != null && !verb.doc().isEmpty()) {
                sb.append(" - ").append(verb.doc());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Create a new plain Item.
     *
     * <p>Overrides {@link Item#actionNew} because the inherited version uses
     * {@code this.getClass()} which would try to construct a new Librarian
     * (no suitable constructor exists).
     *
     * <p>At the Librarian level, "create" produces a plain Item.
     * For typed items ("create signer"), Eval dispatches CREATE on the type
     * seed item directly, not on Librarian.
     */
    @Override
    public Item actionNew(
            ActionContext ctx,
            @Param(
                    value = "name", required = false, role = "NAME") String name) {
        Item newItem = Item.create(this);
        if (name != null && !name.isBlank()) {
            newItem.relate(
                    Sememe.TITLE.iid(),
                    Literal.ofText(name));
        }
        return newItem;
    }

    // ==================================================================================
    // Callable Implementation (picocli entry point)
    // ==================================================================================

    /**
     * Called by picocli after parsing CLI arguments.
     *
     * <p>Opens the librarian at the specified path and runs it as a daemon,
     * blocking until interrupted.
     */
    @Override
    public Integer call() {
        Path librarianPath = cliOpts.effectivePath();

        logger.info("Starting Librarian daemon at {} on port {}", librarianPath, cliOpts.port);

        try {
            // Open the actual librarian
            liveInstance = Librarian.open(librarianPath);

            // TODO: Start TCP listener on cliOpts.port

            System.out.println("Librarian running at " + librarianPath);
            System.out.println("IID: " + liveInstance.iid().encodeText());
            System.out.println("Port: " + cliOpts.port + " (not yet implemented)");
            System.out.println("Unix socket: " + cliOpts.effectiveSocketPath());
            System.out.println();
            System.out.println("Press Ctrl+C to stop.");

            // Block until interrupted
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Librarian interrupted, shutting down");
            }

            return 0;
        } catch (Exception e) {
            logger.error("Librarian failed", e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        } finally {
            if (liveInstance != null) {
                liveInstance.close();
                liveInstance = null;
            }
        }
    }

    // ==================================================================================
    // Daemon Implementation (jsvc entry point)
    // ==================================================================================

    /**
     * Called by jsvc to initialize the daemon.
     */
    @Override
    public void init(DaemonContext context) throws DaemonInitException {
        logger.info("Daemon init: {}", String.join(" ", context.getArguments()));
        runningAsDaemon = true;

        // Parse CLI arguments
        int exitCode = new CommandLine(this).execute(context.getArguments());
        if (exitCode != 0) {
            throw new DaemonInitException("Argument parsing failed with code: " + exitCode);
        }
    }

    /**
     * Called by jsvc to start the daemon.
     */
    @Override
    public void start() throws Exception {
        logger.info("Daemon start");

        Path librarianPath = cliOpts.effectivePath();
        liveInstance = Librarian.open(librarianPath);

        // TODO: Start network listeners
        logger.info("Librarian daemon started: iid={}", liveInstance.iid());
    }

    /**
     * Called by jsvc to stop the daemon.
     */
    @Override
    public void stop() throws Exception {
        logger.info("Daemon stop");
        // Close handled in destroy()
    }

    /**
     * Called by jsvc to destroy the daemon.
     */
    @Override
    public void destroy() {
        logger.info("Daemon destroy");
        if (liveInstance != null) {
            liveInstance.close();
            liveInstance = null;
        }
    }

    // ==================================================================================
    // Session Tracking
    // ==================================================================================

    /**
     * Register a session as connected to this librarian.
     *
     * @param session The session info
     */
    public void registerSession(SessionInfo session) {
        connectedSessions.add(session);
        logger.info("Session connected: {}", session);
    }

    /**
     * Unregister a session from this librarian.
     *
     * @param session The session info
     */
    public void unregisterSession(SessionInfo session) {
        connectedSessions.remove(session);
        logger.info("Session disconnected: {}", session);
    }

    /**
     * Get all currently connected sessions.
     *
     * @return Immutable copy of connected sessions
     */
    public Set<SessionInfo> connectedSessions() {
        return Set.copyOf(connectedSessions);
    }

    /**
     * Information about a connected session.
     */
    public record SessionInfo(
        ItemID sessionId,      // Session's IID (null for anonymous local sessions)
        String connectionType, // "local", "unix", "tcp"
        Instant connectedAt,
        String description     // User-friendly description
    ) {
        /**
         * Create a SessionInfo for a local (same-process) session.
         */
        public static SessionInfo local() {
            return new SessionInfo(null, "local", Instant.now(), "Local session");
        }

        /**
         * Create a SessionInfo for a Unix socket session.
         */
        public static SessionInfo unix(String description) {
            return new SessionInfo(null, "unix", Instant.now(), description);
        }
    }

    // ==================================================================================
    // CLI Entry Point
    // ==================================================================================

    /**
     * Standalone entry point for running Librarian as a daemon.
     *
     * <p>Usage:
     * <pre>
     * # Start librarian at default location
     * java Librarian
     *
     * # Start at specific path
     * java Librarian --path /path/to/library
     *
     * # Start on specific port
     * java Librarian --port 7474
     * </pre>
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Librarian()).execute(args);
        System.exit(exitCode);
    }
}
