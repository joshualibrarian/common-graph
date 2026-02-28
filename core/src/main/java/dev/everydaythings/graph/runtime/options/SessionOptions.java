package dev.everydaythings.graph.runtime.options;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

/**
 * Command-line options for configuring a Session (UI frontend).
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code graph session} - connect to existing librarian</li>
 *   <li>{@code graph} - when starting local librarian + session</li>
 * </ul>
 */
public class SessionOptions {

    @Option(names = {"--to"},
            description = "Librarian to connect to (host:port, socket path, or 'local')")
    public String connectionTarget;

    @Option(names = {"--ui"},
            description = "UI mode: gui, tui, cli (auto-detected if not specified)")
    public String uiMode;

    @Option(names = {"--eval", "-e"},
            description = "Evaluate expression and exit")
    public String evalExpression;

    @Option(names = {"--json"},
            description = "Output in JSON format (with --eval)")
    public boolean jsonOutput;

    @Option(names = {"--as"},
            description = "Principal (user) to act as (name or IID)")
    public String principal;

    @Option(names = {"--engage"},
            description = "Engage with an invite code to register as a new user")
    public String inviteCode;

    @Option(names = {"--name"},
            description = "Name to register as (used with --engage)")
    public String registerName;

    @Option(names = {"--token"},
            description = "Auth token (or 'auto' for local auto-detection)")
    public String authToken;

    @Parameters(description = "Item to open (handle, path, or IID), and optional command + args")
    public List<String> positionalArgs;

    /**
     * Check if this is an eval-and-exit invocation.
     */
    public boolean isEvalMode() {
        return evalExpression != null && !evalExpression.isBlank();
    }

    /**
     * Check if connecting to a remote (non-local) librarian.
     */
    public boolean isRemote() {
        if (connectionTarget == null || connectionTarget.isBlank()) {
            return false;
        }
        if ("local".equalsIgnoreCase(connectionTarget)) {
            return false;
        }
        // If it contains ":" and doesn't start with "/", it's host:port
        // If it ends with ".sock" or contains "socket", it's a Unix socket to a different process
        return connectionTarget.contains(":") ||
               connectionTarget.endsWith(".sock") ||
               connectionTarget.contains("socket");
    }
}
