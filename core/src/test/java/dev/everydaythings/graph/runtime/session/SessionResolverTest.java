package dev.everydaythings.graph.runtime.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SessionResolver}.
 *
 * <p>The resolver is mostly a pure function of inputs.  Filesystem I/O is
 * limited to probing directory existence for walk-up search and
 * materialization checks.  All tests inject {@link SystemPaths} so they
 * are deterministic across machines.
 */
class SessionResolverTest {

    @TempDir Path home;
    @TempDir Path workingDir;

    private SystemPaths paths() {
        return SystemPaths.of(
                home,
                home.resolve(".local").resolve("share"),
                home.resolve(".runtime"),
                home.resolve(".config"));
    }

    @Nested
    @DisplayName("Explicit path resolution")
    class ExplicitPath {

        @Test
        void explicit_path_wins_over_walk_up_and_defaults() {
            Path explicit = home.resolve("custom-session");
            SessionResolver.Resolved r = SessionResolver.resolve(
                    explicit, null, false, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.EXPLICIT);
            assertThat(r.path()).isEqualTo(explicit.toAbsolutePath().normalize());
        }

        @Test
        void explicit_path_does_not_have_to_exist() {
            Path nonExistent = home.resolve("does-not-exist");
            SessionResolver.Resolved r = SessionResolver.resolve(
                    nonExistent, null, false, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.EXPLICIT);
            assertThat(r.dirExists()).isFalse();
            assertThat(r.isMaterialized()).isFalse();
        }
    }

    @Nested
    @DisplayName("Walk-up search")
    class WalkUp {

        @Test
        void finds_dot_session_in_current_dir() throws IOException {
            Path sessionDir = workingDir.resolve(SessionResolver.WALK_UP_DIR);
            Files.createDirectory(sessionDir);
            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, null, false, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.WALK_UP);
            assertThat(r.path()).isEqualTo(sessionDir.toAbsolutePath().normalize());
        }

        @Test
        void finds_dot_session_in_ancestor_dir() throws IOException {
            Path nested = workingDir.resolve("nested").resolve("deeper");
            Files.createDirectories(nested);
            Path sessionDir = workingDir.resolve(SessionResolver.WALK_UP_DIR);
            Files.createDirectory(sessionDir);
            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, null, false, nested, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.WALK_UP);
            assertThat(r.path()).isEqualTo(sessionDir.toAbsolutePath().normalize());
        }

        @Test
        void no_walk_up_falls_through_to_xdg_default() {
            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, null, false, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.DEFAULT_PERSISTENT);
        }

        @Test
        void walk_up_only_matches_directories_not_files() throws IOException {
            // A file named .session in working dir should NOT match.
            Path notADir = workingDir.resolve(SessionResolver.WALK_UP_DIR);
            Files.writeString(notADir, "not a directory");
            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, null, false, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.DEFAULT_PERSISTENT);
        }
    }

    @Nested
    @DisplayName("Default persistent (XDG_DATA_HOME)")
    class DefaultPersistent {

        @Test
        void resolves_to_xdg_data_home_sessions_default_when_no_name() {
            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, null, false, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.DEFAULT_PERSISTENT);
            assertThat(r.path()).isEqualTo(
                    paths().xdgDataHome().resolve("sessions").resolve("default").toAbsolutePath().normalize());
        }

        @Test
        void name_parameter_picks_named_workspace() {
            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, "work", false, workingDir, paths());
            assertThat(r.path()).isEqualTo(
                    paths().xdgDataHome().resolve("sessions").resolve("work").toAbsolutePath().normalize());
        }

        @Test
        void blank_name_defaults_to_default() {
            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, "  ", false, workingDir, paths());
            assertThat(r.path().getFileName().toString()).isEqualTo("default");
        }
    }

    @Nested
    @DisplayName("Ephemeral (XDG_RUNTIME_DIR)")
    class Ephemeral {

        @Test
        void resolves_to_runtime_dir_when_ephemeral_flag_set() {
            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, null, true, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.EPHEMERAL);
            assertThat(r.isEphemeral()).isTrue();
            assertThat(r.path()).isEqualTo(
                    paths().xdgRuntimeDir().resolve("sessions").resolve("default").toAbsolutePath().normalize());
        }

        @Test
        void ephemeral_with_named_workspace() {
            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, "scratch", true, workingDir, paths());
            assertThat(r.path()).isEqualTo(
                    paths().xdgRuntimeDir().resolve("sessions").resolve("scratch").toAbsolutePath().normalize());
        }
    }

    @Nested
    @DisplayName("Materialization probes")
    class MaterializationProbes {

        @Test
        void dirExists_reports_true_for_existing_dir() throws IOException {
            Path explicit = workingDir.resolve("my-session");
            Files.createDirectory(explicit);
            SessionResolver.Resolved r = SessionResolver.resolve(
                    explicit, null, false, workingDir, paths());
            assertThat(r.dirExists()).isTrue();
        }

        @Test
        void isMaterialized_requires_dot_item_subdir() throws IOException {
            Path explicit = workingDir.resolve("my-session");
            Files.createDirectory(explicit);
            SessionResolver.Resolved r = SessionResolver.resolve(
                    explicit, null, false, workingDir, paths());
            assertThat(r.isMaterialized()).isFalse();

            Files.createDirectory(explicit.resolve(".item"));
            SessionResolver.Resolved r2 = SessionResolver.resolve(
                    explicit, null, false, workingDir, paths());
            assertThat(r2.isMaterialized()).isTrue();
        }
    }

    @Nested
    @DisplayName("Precedence")
    class Precedence {

        @Test
        void explicit_beats_walk_up() throws IOException {
            // Set up a walk-up candidate.
            Files.createDirectory(workingDir.resolve(SessionResolver.WALK_UP_DIR));
            Path explicit = home.resolve("explicit-choice");

            SessionResolver.Resolved r = SessionResolver.resolve(
                    explicit, null, false, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.EXPLICIT);
            assertThat(r.path()).isEqualTo(explicit.toAbsolutePath().normalize());
        }

        @Test
        void walk_up_beats_xdg_default() throws IOException {
            Path sessionDir = workingDir.resolve(SessionResolver.WALK_UP_DIR);
            Files.createDirectory(sessionDir);

            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, "work", false, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.WALK_UP);
            assertThat(r.path()).isEqualTo(sessionDir.toAbsolutePath().normalize());
        }

        @Test
        void ephemeral_does_not_override_walk_up() throws IOException {
            // Ephemeral is a "where to put the default" choice, not an
            // override of the walk-up project-local convention.  A user in a
            // project that has a .session/ directory should get the
            // project-local session even with --ephemeral.
            Path sessionDir = workingDir.resolve(SessionResolver.WALK_UP_DIR);
            Files.createDirectory(sessionDir);

            SessionResolver.Resolved r = SessionResolver.resolve(
                    null, null, true, workingDir, paths());
            assertThat(r.kind()).isEqualTo(SessionResolver.Kind.WALK_UP);
        }
    }
}
