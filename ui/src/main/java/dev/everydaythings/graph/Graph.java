package dev.everydaythings.graph;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.runtime.LibrarianHandle;
import dev.everydaythings.graph.runtime.RemoteLibrarian;
import dev.everydaythings.graph.runtime.Session;
import dev.everydaythings.graph.ui.host.HostPresence;
import dev.everydaythings.graph.runtime.options.GlobalOptions;
import dev.everydaythings.graph.runtime.options.LibrarianOptions;
import dev.everydaythings.graph.runtime.options.SessionOptions;
import lombok.extern.log4j.Log4j2;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;

/**
 * Main entry point for Common Graph.
 *
 * <p>Graph is a launcher that combines the two core commands:
 * <ul>
 *   <li>{@link Librarian} - the backend daemon</li>
 *   <li>{@link Session} - the UI frontend</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * graph                          # Start local librarian + session (default)
 * graph --daemon                 # Start librarian daemon only (no UI)
 * graph --to host:port           # Connect session to existing librarian
 * graph librarian                # Explicit librarian subcommand
 * graph session                  # Explicit session subcommand
 * }</pre>
 *
 * <p>Both {@code session} and {@code librarian} also work as standalone commands.
 */
@Log4j2
@Command(
    name = "graph",
    mixinStandardHelpOptions = true,
    version = "graph 0.1.0",
    description = "Common Graph - where everything is an Item",
    subcommands = {
        Librarian.class,
        Session.SessionShell.class  // Session.SessionShell is the concrete picocli command
    }
)
public class Graph implements Runnable {

    // ==================================================================================
    // Options
    // ==================================================================================

    @Mixin
    private final GlobalOptions global = new GlobalOptions();

    @Mixin
    private final LibrarianOptions libOpts = new LibrarianOptions();

    @Mixin
    private final SessionOptions sessionOpts = new SessionOptions();

    // ==================================================================================
    // State
    // ==================================================================================

    private Librarian librarian;
    private HostPresence hostPresence;

    // ==================================================================================
    // Entry Point
    // ==================================================================================

    public static void main(String[] args) {
        // Check for quiet modes BEFORE any logging
        boolean quietMode = shouldBeQuiet(args);

        if (!quietMode) {
            logger.info("main(): {}", String.join(" ", args));
        }

        // Register BouncyCastle early
        Security.addProvider(new BouncyCastleProvider());

        Graph graph = new Graph();
        int exitCode = new CommandLine(graph).execute(args);

        if (exitCode != 0) {
            if (!quietMode) {
                logger.error("Exited with code: {}", exitCode);
            }
            System.exit(exitCode);
        }
    }

    private static boolean shouldBeQuiet(String[] args) {
        for (String arg : args) {
            if (arg.equals("--eval") || arg.equals("-e") ||
                arg.startsWith("--eval=") || arg.startsWith("-e=") ||
                arg.equals("--ui=cli") || arg.equals("--ui=tui") ||
                arg.equals("-q") || arg.equals("--quiet")) {
                return true;
            }
        }
        // Also quiet if running from a TTY (will auto-detect to TUI or CLI)
        // unless explicitly requesting GUI
        if (System.console() != null) {
            for (String arg : args) {
                if (arg.equals("--ui=gui")) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    // ==================================================================================
    // Picocli Entry Point
    // ==================================================================================

    @Override
    public void run() {
        try {
            // Configure logging based on mode
            if (shouldSuppressConsoleLogging()) {
                configureFileOnlyLogging();
            }

            // Ensure home directory exists
            Path graphHome = global.effectiveHome();
            Files.createDirectories(graphHome);

            // Route to appropriate mode
            if (libOpts.daemon || libOpts.foreground) {
                runLibrarianOnly();
            } else if (sessionOpts.isRemote()) {
                runSessionOnly();
            } else {
                runCombined();
            }
        } catch (Exception e) {
            logger.error("Startup failed", e);
            throw new RuntimeException(e);
        } finally {
            cleanup();
        }
    }

    private boolean shouldSuppressConsoleLogging() {
        return sessionOpts.isEvalMode() ||
               (System.console() != null && !"gui".equalsIgnoreCase(sessionOpts.uiMode));
    }

    // ==================================================================================
    // Run Modes
    // ==================================================================================

    /**
     * Librarian only - daemon mode, no UI.
     */
    private void runLibrarianOnly() {
        logger.info("Running librarian daemon on port {}", libOpts.port);

        Path librarianPath = libOpts.effectivePath();
        logger.info("Opening Librarian at {}", librarianPath);
        librarian = Librarian.open(librarianPath);

        // open() starts Unix socket; also listen on TCP for remote connections
        librarian.startSessionServer("0.0.0.0", libOpts.port);

        // Host is ready — show system tray
        // TODO: daemon mode needs a minimal Cocoa/AppKit run loop for the tray
        // to be interactive. For now, tray works in combined mode (GLFW provides it).
        showHostPresence();

        System.out.println("Librarian daemon running. Press Ctrl+C to stop.");
        System.out.println("Path: " + librarianPath);
        System.out.println("Port: " + libOpts.port);
        System.out.println("IID: " + librarian.iid().encodeText());

        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered");
            cleanup();
        }, "graph-shutdown"));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Session only - connect to an existing librarian.
     */
    private void runSessionOnly() {
        logger.info("Running session only (connecting to {})", sessionOpts.connectionTarget);

        try {
            RemoteLibrarian handle = (RemoteLibrarian) LibrarianHandle.remote(sessionOpts.connectionTarget);

            if (sessionOpts.inviteCode != null && !sessionOpts.inviteCode.isBlank()) {
                // Engage flow — register a new identity via invite code
                String name = sessionOpts.registerName;
                if (name == null || name.isBlank()) {
                    name = System.getProperty("user.name", "user");
                }
                handle.connectAndEngage(sessionOpts.inviteCode, name);
                logger.info("Engaged as '{}'", name);
            } else if (sessionOpts.principal != null && !sessionOpts.principal.isBlank()) {
                // Existing user — resolve --as principal
                resolvePrincipal(handle, sessionOpts.principal);
            } else {
                // No identity specified — connect with auto-token (becomes principal)
                handle.connectAndAuthenticate(sessionOpts.authToken);
            }

            runSessionWithFallback(handle);
        } catch (Exception e) {
            logger.error("Failed to connect", e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Resolve the --as principal name or IID and set it on the handle.
     *
     * <p>If the principal looks like an IID (contains ':'), use it directly.
     * Otherwise, connect, authenticate, and look it up via the token dictionary.
     */
    private void resolvePrincipal(RemoteLibrarian handle, String principal) throws IOException {
        ItemID principalId;
        if (principal.contains(":")) {
            // Looks like an IID
            principalId = ItemID.fromString(principal);
        } else {
            // Resolve name via lookup after connecting
            handle.connectAndAuthenticate(sessionOpts.authToken);
            java.util.List<Posting> postings = handle.lookup(principal, 5);
            principalId = postings.stream()
                    .filter(p -> p.token().equalsIgnoreCase(principal))
                    .map(Posting::target)
                    .findFirst()
                    .orElse(null);

            if (principalId == null && !postings.isEmpty()) {
                // Fall back to first result
                principalId = postings.get(0).target();
            }

            if (principalId == null) {
                logger.warn("Could not resolve principal '{}' — continuing without identity", principal);
                return;
            }
        }

        handle.setPrincipalId(principalId);
        logger.info("Acting as principal: {} ({})", principal, principalId.encodeText());
    }

    /**
     * Combined mode - start local librarian + session.
     */
    private void runCombined() {
        LibrarianHandle handle;

        if (libOpts.path != null) {
            // Explicit path specified - use file-backed librarian
            logger.info("Opening Librarian at {}", libOpts.path);
            librarian = Librarian.open(libOpts.path);
            handle = LibrarianHandle.wrap(librarian);
        } else {
            // No path specified - use ephemeral in-memory librarian
            logger.info("Creating ephemeral in-memory librarian");
            librarian = Librarian.createInMemory();
            librarian.startSessionServer();  // Start session server so others can connect
            handle = LibrarianHandle.wrap(librarian);
        }

        // Also listen on TCP so "graph --to localhost:7474" can connect
        librarian.startSessionServer("0.0.0.0", libOpts.port);
        logger.info("Session server accepting connections on port {}", libOpts.port);

        // Host is ready — show system tray
        showHostPresence();

        runSessionWithFallback(handle);
    }

    /**
     * Run a session, falling back to text mode if 3D/GUI fails.
     *
     * <p>If the auto-detected mode (e.g. SPACE) fails to initialize,
     * retries with a text-based mode so the user always gets a prompt.
     */
    private void runSessionWithFallback(LibrarianHandle handle) {
        try (Session session = Session.create(handle, sessionOpts)) {
            int exitCode = session.run();
            if (exitCode != 0) {
                logger.warn("Session exited with code {}", exitCode);
                System.exit(exitCode);
            }
        } catch (Throwable t) {
            // If we were in a graphical mode and it failed, fall back to text
            String requestedMode = sessionOpts.uiMode;
            boolean wasAutoOrGraphical = requestedMode == null || requestedMode.isEmpty() ||
                    "auto".equalsIgnoreCase(requestedMode);

            if (wasAutoOrGraphical) {
                logger.warn("Graphical session failed, falling back to text mode: {}", t.getMessage());
                sessionOpts.uiMode = "auto-text";
                try (Session fallback = Session.createTextFallback(handle, sessionOpts)) {
                    int exitCode = fallback.run();
                    if (exitCode != 0) {
                        System.exit(exitCode);
                    }
                }
            } else {
                logger.error("Session failed", t);
                System.err.println("Error: " + t.getMessage());
                System.exit(1);
            }
        }
    }

    // ==================================================================================
    // Host Presence (System Tray)
    // ==================================================================================

    /**
     * Show the host presence (system tray icon) if the host item exists
     * and the platform supports it. No-ops on headless or unsupported platforms.
     */
    private void showHostPresence() {
        if (librarian == null || librarian.host() == null) return;
        if (GraphicsEnvironment.isHeadless()) return;

        try {
            hostPresence = HostPresence.create(librarian.host());
            hostPresence.show();
        } catch (Exception e) {
            logger.debug("Host presence unavailable: {}", e.getMessage());
            hostPresence = null;
        }
    }

    // ==================================================================================
    // Cleanup
    // ==================================================================================

    private void cleanup() {
        if (hostPresence != null) {
            try {
                hostPresence.destroy();
            } catch (Exception e) {
                logger.debug("Error destroying host presence", e);
            }
            hostPresence = null;
        }
        if (librarian != null) {
            try {
                librarian.close();
            } catch (Exception e) {
                logger.warn("Error closing librarian", e);
            }
            librarian = null;
        }
    }

    // ==================================================================================
    // Logging Configuration
    // ==================================================================================

    private void configureFileOnlyLogging() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        LoggerConfig rootLogger = config.getRootLogger();
        rootLogger.removeAppender("console");
        rootLogger.setLevel(Level.INFO);

        ctx.updateLoggers();
    }
}
