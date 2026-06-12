package dev.everydaythings.graph.runtime.session;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves where a Session lives on disk, given the inputs a client tool
 * (cg-eval, future cg-ui) provides.  Tries, in order:
 *
 * <ol>
 *   <li><b>Explicit path</b> — caller passed {@code --session &lt;path&gt;}.</li>
 *   <li><b>Walk-up</b> — search current dir and ancestors for a {@code .session/}
 *       subdirectory; the first hit is a project-local session, like git's
 *       {@code .git/} walk.</li>
 *   <li><b>Default persistent</b> — {@code $XDG_DATA_HOME/sessions/&lt;name&gt;/};
 *       {@code <name>} defaults to {@code "default"} and may be overridden
 *       by the caller for distinct workspaces (e.g., "work" vs "personal").</li>
 *   <li><b>Ephemeral</b> — {@code $XDG_RUNTIME_DIR/sessions/&lt;name&gt;/};
 *       selected when the caller requests one-shot mode (e.g., {@code --ephemeral}
 *       on cg-eval).</li>
 * </ol>
 *
 * <p>This class only RESOLVES the path; it does not create directories, mint
 * vaults, or touch the filesystem beyond probing existence.  Callers that
 * want to load or auto-mint a session use the resolved path together with
 * the vault / item materialization machinery.
 *
 * <p>Resolution is a pure function (almost — it probes the filesystem for
 * walk-up directory existence).  All inputs are explicit; system values
 * (home directory, XDG_DATA_HOME, XDG_RUNTIME_DIR) are read from
 * {@link SystemPaths} and can be overridden via
 * {@link #resolve(Path, String, boolean, Path, SystemPaths)} for testing.
 *
 * <p>See [[project_sessions_as_workspaces_2026_06_05]] for the broader
 * "sessions are workspaces" framing this resolver supports.
 */
@Log4j2
public final class SessionResolver {

    /** Default session name when the caller does not specify one. */
    public static final String DEFAULT_NAME = "default";

    /** Directory name used by the walk-up project-local convention. */
    public static final String WALK_UP_DIR = ".session";

    private SessionResolver() {}

    /**
     * Resolve a session location using real system paths and the current
     * working directory.  Equivalent to
     * {@link #resolve(Path, String, boolean, Path, SystemPaths)} with
     * {@code workingDir = Paths.get("")} and {@code paths = SystemPaths.system()}.
     */
    public static Resolved resolve(Path explicit, String name, boolean ephemeral) {
        return resolve(explicit, name, ephemeral,
                Paths.get("").toAbsolutePath(),
                SystemPaths.system());
    }

    /**
     * Full resolution with injected working directory and system paths.
     * Used by tests and by callers that want to override XDG-derived paths.
     *
     * @param explicit    --session path; null to skip explicit-path resolution
     * @param name        workspace name (e.g., "default", "work"); null → DEFAULT_NAME
     * @param ephemeral   true to select the XDG_RUNTIME_DIR shape; false for XDG_DATA_HOME
     * @param workingDir  starting directory for walk-up search; must be absolute
     * @param paths       system-paths source (home, XDG vars)
     * @return            a {@link Resolved} describing the chosen path, its kind,
     *                    and whether it currently exists / is materialized
     */
    public static Resolved resolve(Path explicit, String name, boolean ephemeral,
                                   Path workingDir, SystemPaths paths) {
        if (explicit != null) {
            return new Resolved(explicit.toAbsolutePath().normalize(), Kind.EXPLICIT);
        }
        Path walkUp = findWalkUp(workingDir);
        if (walkUp != null) {
            return new Resolved(walkUp, Kind.WALK_UP);
        }
        String effectiveName = (name == null || name.isBlank()) ? DEFAULT_NAME : name;
        if (ephemeral) {
            Path runtime = paths.xdgRuntimeDir().resolve("sessions").resolve(effectiveName);
            return new Resolved(runtime.toAbsolutePath().normalize(), Kind.EPHEMERAL);
        }
        Path data = paths.xdgDataHome().resolve("sessions").resolve(effectiveName);
        return new Resolved(data.toAbsolutePath().normalize(), Kind.DEFAULT_PERSISTENT);
    }

    /**
     * Walk up from {@code startDir} looking for a {@code .session/} subdirectory.
     * Returns the absolute path to the first match found, or null if none in
     * the ancestor chain (up to filesystem root).
     */
    private static Path findWalkUp(Path startDir) {
        Path current = startDir.toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(WALK_UP_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    /** How the resolver arrived at this location.  Affects downstream policy. */
    public enum Kind {
        /** Caller passed an explicit path. */
        EXPLICIT,
        /** Found via walk-up search; project-local. */
        WALK_UP,
        /** Default persistent shape under XDG_DATA_HOME. */
        DEFAULT_PERSISTENT,
        /** Ephemeral shape under XDG_RUNTIME_DIR (dies at reboot). */
        EPHEMERAL
    }

    /**
     * The result of a resolution: the chosen path, its provenance, and small
     * helpers for downstream policy.  Does not load, validate, or mint —
     * callers do that.
     */
    public static final class Resolved {

        private final Path path;
        private final Kind kind;

        Resolved(Path path, Kind kind) {
            this.path = path;
            this.kind = kind;
        }

        /** The resolved absolute path to the session's materialization root. */
        public Path path() { return path; }

        /** How this path was chosen. */
        public Kind kind() { return kind; }

        /** True if the resolved directory currently exists on disk. */
        public boolean dirExists() {
            return Files.isDirectory(path);
        }

        /**
         * True if the directory exists AND has a {@code .item/} subdirectory —
         * the substrate's marker that this is a materialized item, not just
         * an empty directory.  Per [[project_materialization_pattern_2026_06_05]].
         */
        public boolean isMaterialized() {
            return Files.isDirectory(path.resolve(".item"));
        }

        /** Convenience: true if this resolution targets the ephemeral runtime location. */
        public boolean isEphemeral() {
            return kind == Kind.EPHEMERAL;
        }

        @Override
        public String toString() {
            return "Resolved[path=" + path + ", kind=" + kind
                    + ", dirExists=" + dirExists()
                    + ", materialized=" + isMaterialized() + "]";
        }
    }
}
