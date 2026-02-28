package dev.everydaythings.graph.runtime.options;

import picocli.CommandLine.Option;

import java.nio.file.Path;

/**
 * Command-line options for configuring a Librarian.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code graph librarian} - standalone daemon</li>
 *   <li>{@code graph} - when starting local librarian + session</li>
 * </ul>
 */
public class LibrarianOptions {

    public static final int DEFAULT_PORT = 7474;

    @Option(names = {"--lib-path"},
            description = "Library path (default: ~/.librarian)")
    public Path path;

    @Option(names = {"--lib-port"},
            description = "Port for session connections (default: ${DEFAULT-VALUE})")
    public int port = DEFAULT_PORT;

    @Option(names = {"--lib-socket"},
            description = "Unix socket path for session connections")
    public Path socketPath;

    @Option(names = {"--daemon", "-d"},
            description = "Run librarian as headless daemon (no UI)")
    public boolean daemon;

    @Option(names = {"--foreground", "-f"},
            description = "Run in foreground, don't detach (implies daemon)")
    public boolean foreground;

    /**
     * Get the effective library path, using default if not specified.
     */
    public Path effectivePath() {
        if (path != null) {
            return path;
        }
        return Path.of(System.getProperty("user.home"), ".librarian");
    }

    /**
     * Get the effective socket path, using default if not specified.
     */
    public Path effectiveSocketPath() {
        if (socketPath != null) {
            return socketPath;
        }
        return effectivePath().resolve("session.sock");
    }
}
