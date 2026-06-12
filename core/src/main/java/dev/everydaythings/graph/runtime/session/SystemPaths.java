package dev.everydaythings.graph.runtime.session;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Source of system / environment paths used by session resolution and other
 * runtime placement decisions.
 *
 * <p>Each accessor returns an absolute {@link Path} for one of:
 *
 * <ul>
 *   <li><b>home</b> — the OS home directory (Java's {@code user.home}).</li>
 *   <li><b>xdgDataHome</b> — {@code $XDG_DATA_HOME} if set, else
 *       {@code ~/.local/share} per the XDG Base Directory spec.</li>
 *   <li><b>xdgRuntimeDir</b> — {@code $XDG_RUNTIME_DIR} if set; falls back
 *       to a stable per-user tmp path when unset (the spec says applications
 *       SHOULD warn in that case, but for a substrate that must work we
 *       fall back rather than fail).</li>
 *   <li><b>xdgConfigHome</b> — {@code $XDG_CONFIG_HOME} if set, else
 *       {@code ~/.config}.</li>
 * </ul>
 *
 * <p>The {@link #system()} factory reads real environment + system
 * properties.  For testing, construct a {@code SystemPaths} directly with
 * known values via {@link #of}.
 *
 * <p>Used by {@link SessionResolver}; designed to be reused by any other
 * placement helper that needs the same standard locations.
 */
public final class SystemPaths {

    private final Path home;
    private final Path xdgDataHome;
    private final Path xdgRuntimeDir;
    private final Path xdgConfigHome;

    private SystemPaths(Path home, Path xdgDataHome,
                        Path xdgRuntimeDir, Path xdgConfigHome) {
        this.home = home;
        this.xdgDataHome = xdgDataHome;
        this.xdgRuntimeDir = xdgRuntimeDir;
        this.xdgConfigHome = xdgConfigHome;
    }

    /** Build a {@code SystemPaths} from real system values. */
    public static SystemPaths system() {
        Path home = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        Path data = resolveXdg("XDG_DATA_HOME", home.resolve(".local").resolve("share"));
        Path runtime = resolveXdg("XDG_RUNTIME_DIR", fallbackRuntimeDir());
        Path config = resolveXdg("XDG_CONFIG_HOME", home.resolve(".config"));
        return new SystemPaths(home, data, runtime, config);
    }

    /** Construct with all paths explicit — for tests and overrides. */
    public static SystemPaths of(Path home, Path xdgDataHome,
                                 Path xdgRuntimeDir, Path xdgConfigHome) {
        return new SystemPaths(
                home.toAbsolutePath().normalize(),
                xdgDataHome.toAbsolutePath().normalize(),
                xdgRuntimeDir.toAbsolutePath().normalize(),
                xdgConfigHome.toAbsolutePath().normalize());
    }

    public Path home()           { return home; }
    public Path xdgDataHome()    { return xdgDataHome; }
    public Path xdgRuntimeDir()  { return xdgRuntimeDir; }
    public Path xdgConfigHome()  { return xdgConfigHome; }

    /**
     * Read {@code $envVar} as a Path if set and absolute; otherwise fall
     * back to {@code defaultPath}.  Per XDG spec: values that are not
     * absolute paths must be ignored, with the default substituted.
     */
    private static Path resolveXdg(String envVar, Path defaultPath) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            return defaultPath.toAbsolutePath().normalize();
        }
        Path p = Paths.get(value);
        if (!p.isAbsolute()) {
            return defaultPath.toAbsolutePath().normalize();
        }
        return p.normalize();
    }

    /**
     * Fallback when {@code XDG_RUNTIME_DIR} is unset.  The spec says
     * applications SHOULD fall back to "a replacement directory with similar
     * capabilities" and emit a warning.  We choose a stable per-user tmp
     * location so behavior is predictable on systems where the variable is
     * not set (some macOS sessions, minimal container environments).
     */
    private static Path fallbackRuntimeDir() {
        String tmp = System.getProperty("java.io.tmpdir");
        String user = System.getProperty("user.name");
        return Paths.get(tmp == null ? "/tmp" : tmp, "runtime-" + (user == null ? "default" : user))
                .toAbsolutePath().normalize();
    }
}
