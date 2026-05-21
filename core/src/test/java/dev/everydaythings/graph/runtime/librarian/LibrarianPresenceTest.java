package dev.everydaythings.graph.runtime.librarian;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link LibrarianPresence} — the pidfile + flock layer that
 * guarantees one librarian per data dir.
 */
class LibrarianPresenceTest {

    @Test
    @DisplayName("acquire() writes parley.pid containing the current PID")
    void writesPid(@TempDir Path root) throws Exception {
        try (LibrarianPresence presence = LibrarianPresence.acquire(root)) {
            assertThat(Files.isRegularFile(root.resolve("parley.pid"))).isTrue();
            String contents = Files.readString(root.resolve("parley.pid")).trim();
            assertThat(Long.parseLong(contents))
                    .isEqualTo(ProcessHandle.current().pid())
                    .isEqualTo(presence.pid());
        }
    }

    @Test
    @DisplayName("close() removes the pidfile")
    void closeRemovesPidfile(@TempDir Path root) {
        LibrarianPresence presence = LibrarianPresence.acquire(root);
        assertThat(Files.exists(root.resolve("parley.pid"))).isTrue();
        presence.close();
        assertThat(Files.exists(root.resolve("parley.pid"))).isFalse();
    }

    @Test
    @DisplayName("close() is idempotent")
    void closeIdempotent(@TempDir Path root) {
        LibrarianPresence presence = LibrarianPresence.acquire(root);
        presence.close();
        presence.close();   // second close must not throw
    }

    @Test
    @DisplayName("acquire() throws when a presence is already held in the same JVM")
    void acquireRefusesSameJvm(@TempDir Path root) {
        LibrarianPresence first = LibrarianPresence.acquire(root);
        try {
            assertThatThrownBy(() -> LibrarianPresence.acquire(root))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already running");
        } finally {
            first.close();
        }
    }

    @Test
    @DisplayName("acquire() succeeds after a prior presence is released")
    void acquireAfterRelease(@TempDir Path root) {
        LibrarianPresence first = LibrarianPresence.acquire(root);
        first.close();

        // Second acquire should succeed.
        try (LibrarianPresence second = LibrarianPresence.acquire(root)) {
            assertThat(second.pid()).isEqualTo(ProcessHandle.current().pid());
        }
    }

    @Test
    @DisplayName("isAlive() returns false on an empty directory")
    void isAliveEmptyDir(@TempDir Path root) {
        assertThat(LibrarianPresence.isAlive(root)).isFalse();
    }

    @Test
    @DisplayName("isAlive() returns true while a presence is held in this JVM")
    void isAliveWhileHeld(@TempDir Path root) {
        try (LibrarianPresence presence = LibrarianPresence.acquire(root)) {
            assertThat(LibrarianPresence.isAlive(root)).isTrue();
        }
    }

    @Test
    @DisplayName("isAlive() returns false after a clean release")
    void isAliveAfterRelease(@TempDir Path root) {
        LibrarianPresence presence = LibrarianPresence.acquire(root);
        presence.close();
        assertThat(LibrarianPresence.isAlive(root)).isFalse();
    }

    @Test
    @DisplayName("isAlive() returns false when only a stale pidfile remains (no live process)")
    void isAliveStalePidfile(@TempDir Path root) throws Exception {
        // Simulate a crashed previous librarian: pidfile present, no lock.
        Files.writeString(root.resolve("parley.pid"), "99999");
        assertThat(LibrarianPresence.isAlive(root)).isFalse();
    }

    @Test
    @DisplayName("acquire() overwrites a stale pidfile and succeeds")
    void acquireOverwritesStale(@TempDir Path root) throws Exception {
        // Stale pidfile with a fake PID, no flock held.
        Files.writeString(root.resolve("parley.pid"), "99999");
        try (LibrarianPresence presence = LibrarianPresence.acquire(root)) {
            String contents = Files.readString(root.resolve("parley.pid")).trim();
            assertThat(Long.parseLong(contents))
                    .isEqualTo(ProcessHandle.current().pid());
        }
    }

    @Test
    @DisplayName("readPid() reads the stored pid")
    void readPid(@TempDir Path root) {
        try (LibrarianPresence presence = LibrarianPresence.acquire(root)) {
            assertThat(LibrarianPresence.readPid(root))
                    .isPresent()
                    .hasValue(presence.pid());
        }
    }
}
