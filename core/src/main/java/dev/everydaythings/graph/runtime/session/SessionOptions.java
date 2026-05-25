package dev.everydaythings.graph.runtime.session;

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
     * Resolve the UI mode to use, honoring an explicit {@code --ui} and
     * falling back to environment detection.
     *
     * <p>Heuristic, in order:
     * <ol>
     *   <li>Explicit {@code --ui=<mode>} wins, always.  Users override.</li>
     *   <li>Otherwise, if {@link System#console()} is non-null (the process
     *       has a controlling terminal — invoked from a real shell, even via
     *       {@code &}), return {@code "tui"}.  Terminal invocation gets a
     *       terminal session.</li>
     *   <li>Otherwise, if {@code DISPLAY} or {@code WAYLAND_DISPLAY} is set
     *       (graphical environment available — typical of launches from a
     *       desktop icon / launcher that detach the terminal), return
     *       {@code "skia"}.  The simpler of the two GUI painters, chosen as
     *       the default; users can pass {@code --ui=filament} to override.</li>
     *   <li>Otherwise throw — no UI environment can be detected.  Use
     *       {@code --ui=<mode>} to force, or {@code Librarian.main} for
     *       headless.</li>
     * </ol>
     */
    public String effectiveUiMode() {
        if (uiMode != null && !uiMode.isBlank()) return uiMode;
        if (System.console() != null) return "tui";
        if (hasGraphicalEnvironment()) return "skia";
        throw new IllegalStateException(
                "No UI environment detected (no controlling terminal, no DISPLAY / "
                        + "WAYLAND_DISPLAY).  Pass --ui=<tui|skia|filament> explicitly, "
                        + "or use Librarian.main for a headless backend.");
    }

    private static boolean hasGraphicalEnvironment() {
        String display = System.getenv("DISPLAY");
        if (display != null && !display.isBlank()) return true;
        String wayland = System.getenv("WAYLAND_DISPLAY");
        return wayland != null && !wayland.isBlank();
    }

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
