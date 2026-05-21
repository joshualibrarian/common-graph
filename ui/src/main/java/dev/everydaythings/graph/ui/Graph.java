package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.network.parley.Parley;
import dev.everydaythings.graph.network.parley.RemoteConnection;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.transport.TransportRegistry;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.librarian.LibrarianOptions;
import dev.everydaythings.graph.runtime.librarian.LibrarianPresence;
import dev.everydaythings.graph.runtime.session.SessionOptions;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import dev.everydaythings.graph.value.UnixEndpoint;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Graph — the orchestrating entrypoint that gives a user a working CG
 * session against a local librarian, starting one in-VM if none is
 * running.  The common everyday-use binary.
 *
 * <h2>Decision tree</h2>
 *
 * <p>At startup, Graph probes the librarian data directory (default
 * {@code ~/.librarian}) and chooses one of two paths:
 *
 * <ol>
 *   <li><b>Librarian already running</b> — detected via
 *       {@link LibrarianPresence#isAlive(Path)}.  Graph constructs a
 *       {@link RemoteSession} that connects to the librarian via Parley
 *       over its Unix socket.  Multiple Graph processes can share a
 *       single running librarian.</li>
 *   <li><b>No librarian running</b> — Graph starts a Librarian in this
 *       JVM ({@link Librarian#load} if {@code .item/} exists, else
 *       {@link Librarian#fresh}), binds the librarian's Parley listener
 *       (so OTHER clients could still attach to it remotely), and
 *       constructs a {@link LocalSession} in this same JVM — no parley,
 *       just direct in-process composition for efficiency.</li>
 * </ol>
 *
 * <p>{@code Librarian.main()} (in {@code :core}) does ONLY the librarian
 * (no session).  {@code Graph.main()} (here in {@code :ui}) orchestrates
 * a session against a librarian (running or freshly started).  UI
 * bring-up happens inside the chosen {@link LocalSession} /
 * {@link RemoteSession} — those two subclasses are the only places UI
 * starts.  Putting Graph and the client-side session embodiments in
 * {@code :ui} lets them wire {@code Painter} + {@code Presenter} +
 * {@code RenderLoop} directly without crossing an SPI boundary back into
 * {@code :core}.
 *
 * <h2>What's not yet wired</h2>
 *
 * <p>UI bring-up (TUI / Skia / Filament per {@code --ui} mode) is the
 * next slice.  Graph today logs what it's doing and blocks until
 * interrupted; the actual rendering lands when {@link LocalSession} /
 * {@link RemoteSession} call {@code startUi(uiMode)} — discovery happens
 * via the {@link SurfaceRegistry} ServiceLoader in this module.
 */
@Log4j2
@CommandLine.Command(
        name = "graph",
        description = "Common Graph orchestrating entrypoint — finds or starts a librarian, brings up a session.")
public final class Graph {

    private static final ItemRef CG_CBOR = ItemRef.iid(Encoding.CgCborV1.KEY);

    @Mixin
    public LibrarianOptions librarianOptions = new LibrarianOptions();

    @Mixin
    public SessionOptions sessionOptions = new SessionOptions();

    public static void main(String[] args) {
        Graph cmd = new Graph();
        try {
            new CommandLine(cmd).parseArgs(args);
        } catch (CommandLine.ParameterException pe) {
            logger.error("Invalid arguments: {}", pe.getMessage());
            new CommandLine(cmd).usage(System.err);
            System.exit(2);
            return;
        }
        cmd.run();
    }

    public void run() {
        Path dataDir = librarianOptions.effectivePath();
        if (LibrarianPresence.isAlive(dataDir)) {
            runAsClient(dataDir);
        } else {
            runEmbedded(dataDir);
        }
    }

    // ==================================================================================
    // Client mode — a librarian is already running at the data dir; attach via Parley.
    // ==================================================================================

    private void runAsClient(Path dataDir) {
        Path socketPath = librarianOptions.effectiveSocketPath();
        logger.info("Librarian already running at {}; connecting via {}", dataDir, socketPath);

        UnixEndpoint endpoint = UnixEndpoint.of(socketPath.toString());
        Transport transport = TransportRegistry.require(endpoint);
        Librarian sessionLibrarian = Librarian.inMemory();
        Parley parley = new Parley(sessionLibrarian);

        RemoteConnection connection;
        try {
            Tunnel tunnel = transport.connect(endpoint).get(10, TimeUnit.SECONDS);
            connection = parley.connect(tunnel, CG_CBOR, Set.of(CG_CBOR))
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Failed to connect to running librarian at {}: {}",
                    socketPath, e.getMessage());
            System.exit(1);
            return;
        }
        logger.info("Connected to running librarian (codec: {})", connection.agreedCodec());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { connection.close(); } catch (RuntimeException ignored) {}
        }, "Graph-client-shutdown"));

        // TODO: construct a RemoteSession from the connection + vaults and
        //       call session.startUi(sceneSupplier, sessionOptions.uiMode,
        //       cadence) — pending the "what scene does Graph show by
        //       default" decision (splash? launcher? last-focused item?).
        //       Until then, block until interrupted or remote closes.
        blockUntilClosedOrInterrupted(connection);
    }

    // ==================================================================================
    // Embedded mode — no librarian running; start one + LocalSession in this VM.
    // ==================================================================================

    private void runEmbedded(Path dataDir) {
        ItemStage stage = new ItemStage();
        logger.info("ItemStage up. Polyglot: {}",
                stage.polyglotAvailable()
                        ? "GraalVM " + stage.polyglotLanguages()
                        : "Java-only");

        boolean existing = Files.isDirectory(dataDir.resolve(".item"));
        Librarian lib = existing
                ? Librarian.load(stage, dataDir)
                : Librarian.fresh(stage, dataDir);
        logger.info("Librarian ({}) at {}. IID: {}",
                existing ? "loaded" : "fresh", dataDir, lib.iid().encodeText());

        // Start parley listener — other clients could connect remotely even
        // though the in-VM session uses LocalSession (no parley between us).
        Path socketPath = librarianOptions.effectiveSocketPath();
        UnixEndpoint endpoint = UnixEndpoint.of(socketPath.toString());
        Transport transport = TransportRegistry.require(endpoint);
        Parley parley = new Parley(lib);
        Transport.Listener listener = parley.listen(
                transport, endpoint, CG_CBOR, Set.of(CG_CBOR));
        logger.info("Parley listening at {}", socketPath);

        // In-VM session: LocalSession against the same Librarian.  No parley
        // between session and librarian — direct in-process composition.
        ItemRef sessionIid = ItemRef.random();
        LocalSession session = new LocalSession(sessionIid, lib);
        logger.info("LocalSession started in-VM.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Graph shutting down.");
            try { listener.close(); } catch (RuntimeException ignored) {}
            try { lib.close();      } catch (RuntimeException ignored) {}
        }, "Graph-embedded-shutdown"));

        // TODO: session.startUi(sceneSupplier, sessionOptions.uiMode, cadence)
        //       — pending the "what scene does Graph show by default" decision
        //       (splash? launcher? last-focused item?).
        //       Until then, block until interrupted.
        blockUntilInterrupted();
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static void blockUntilClosedOrInterrupted(RemoteConnection connection) {
        try {
            while (connection.isOpen()) {
                Thread.sleep(500);
            }
            logger.info("Remote librarian closed the connection.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Graph interrupted.");
        }
    }

    private static void blockUntilInterrupted() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Graph interrupted; shutting down.");
        }
    }
}
