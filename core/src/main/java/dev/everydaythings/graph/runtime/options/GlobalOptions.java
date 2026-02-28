package dev.everydaythings.graph.runtime.options;

import picocli.CommandLine.Option;

import java.nio.file.Path;

/**
 * Global command-line options shared across all commands.
 *
 * <p>These are high-level options that affect the overall graph environment
 * rather than specific components.
 */
public class GlobalOptions {

    @Option(names = {"--home"},
            description = "Graph home directory (default: $GRAPH_HOME or $HOME)")
    public Path home;

    @Option(names = {"--verbose", "-v"},
            description = "Enable verbose logging")
    public boolean verbose;

    @Option(names = {"--quiet", "-q"},
            description = "Suppress non-essential output")
    public boolean quiet;

    /**
     * Get the effective home directory.
     */
    public Path effectiveHome() {
        if (home != null) {
            return home;
        }
        String envHome = System.getenv("GRAPH_HOME");
        if (envHome != null && !envHome.isBlank()) {
            return Path.of(envHome);
        }
        return Path.of(System.getProperty("user.home"));
    }
}
