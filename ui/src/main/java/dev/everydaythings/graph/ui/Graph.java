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
import dev.everydaythings.graph.value.UnixEndpoint;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Graph — the convenience composer that wires a librarian and a UI session
 * in one process.  The common everyday-use binary for "I want a working
 * Common Graph window with one command."
 *
 * <p>Graph is UI-centric by definition.  Headless librarian-only
 * deployments use {@link Librarian#main} directly.  Librarian-less remote
 * frontends will use {@code RemoteSession.main} once that lands (TBD).
 * Graph exists for the common case of wanting both in one VM.
 *
 * <h2>Decision tree</h2>
 *
 * <p>At startup Graph probes the librarian data directory (default
 * {@code ~/.librarian}):
 *
 * <ol>
 *   <li><b>Librarian already running</b> — Graph constructs a
 *       {@link RemoteSession} that connects via Parley over its Unix
 *       socket.  Multiple Graph processes can share one running librarian.</li>
 *   <li><b>Nothing running</b> — Graph delegates the librarian bring-up to
 *       {@link Librarian#startInVm} (same path {@link Librarian#main}
 *       uses), then mints a {@link LocalSession} against the resulting
 *       in-VM Librarian.  Direct in-process composition; no parley between
 *       session and librarian even though the listener IS bound (so other
 *       clients could attach too).</li>
 * </ol>
 *
 * <h2>Composition, not logic</h2>
 *
 * <p>Graph owns no startup logic of its own.  Librarian bring-up lives on
 * {@link Librarian#startInVm}.  UI bring-up lives on {@link UiSession#startUi}
 * (overridden in {@link LocalSession} and {@link RemoteSession}).  UI-mode
 * detection lives on {@link SessionOptions#effectiveUiMode}.  Graph reads
 * the options, calls the two entry points in order, and blocks.
 *
 * <h2>What's not yet wired</h2>
 *
 * <p>Remote-mode session construction.  Graph today brings up the Parley
 * connection but stops short of constructing a {@link RemoteSession} from
 * it — pending the RemoteSession startup dance (ephemeral keypair,
 * DELEGATION, INCEPTION).  Until then the client path blocks until the
 * remote closes.
 */
@Log4j2
@CommandLine.Command(
        name = "graph",
        description = "Common Graph composer — wires a librarian and a UI session in one process.")
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
            runEmbedded();
        }
    }

    // ==================================================================================
    // Client mode — a librarian is already running; attach via Parley.
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

        // TODO: construct a RemoteSession from the connection + vaults, then
        //       session.startUi(sessionOptions.effectiveUiMode()).  Pending
        //       the RemoteSession startup dance.  Until then block.
        blockUntilClosedOrInterrupted(connection);
    }

    // ==================================================================================
    // Embedded mode — no librarian running; start one + LocalSession in this VM.
    // ==================================================================================

    private void runEmbedded() {
        Librarian lib = Librarian.startInVm(librarianOptions);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Graph shutting down.");
            lib.close();
        }, "Graph-embedded-shutdown"));

        LocalSession session = LocalSession.mint(lib);
        logger.info("LocalSession started in-VM at iid {}.", session.iid().encodeText());

        session.startUi(sessionOptions.effectiveUiMode());
        Runtime.getRuntime().addShutdownHook(new Thread(session::stopUi,
                "Graph-stopUi-shutdown"));

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
