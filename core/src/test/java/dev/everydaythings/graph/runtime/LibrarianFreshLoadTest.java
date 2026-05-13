package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.encoding.Encoding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Librarian#fresh(Path)} and {@link Librarian#load(Path)} —
 * the on-disk startup paths. Full disk persistence is not yet wired (RocksDB
 * plumbing pending); these tests pin the factory shape and the
 * {@code .librarian/format} marker mechanism.
 */
class LibrarianFreshLoadTest {

    @Test
    @DisplayName("fresh(path) writes the .librarian/format marker file")
    void freshWritesMarker(@TempDir Path root) throws Exception {
        Librarian lib = Librarian.fresh(root);

        Path markerPath = root.resolve(".librarian").resolve("format");
        assertThat(Files.exists(markerPath)).isTrue();
        byte[] bytes = Files.readAllBytes(markerPath);
        assertThat(bytes).containsExactly((byte) Encoding.CgCborV1.FORMAT_CODE);

        assertThat(lib.rootPath()).contains(root);
    }

    @Test
    @DisplayName("fresh(path) creates a full signing identity (vault, iid, inception)")
    void freshHasIdentity(@TempDir Path root) {
        Librarian lib = Librarian.fresh(root);
        assertThat(lib.iid()).isNotNull();
        assertThat(lib.canSign()).isTrue();
        assertThat(lib.vault()).isPresent();
        // selfIncept ran during construction → its own manifest is loaded.
        assertThat(lib.current()).isNotNull();
    }

    @Test
    @DisplayName("fresh(path) uses byte-backed Library (encoder present)")
    void freshHasEncoder(@TempDir Path root) {
        Librarian lib = Librarian.fresh(root);
        assertThat(lib.encoder()).isPresent();
    }

    @Test
    @DisplayName("load(path) throws NotFound when marker is missing")
    void loadRejectsMissingMarker(@TempDir Path root) {
        // No fresh() call → no marker file.
        assertThatThrownBy(() -> Librarian.load(root))
                .isInstanceOf(dev.everydaythings.graph.library.LibraryException.NotFound.class)
                .hasMessageContaining("No format marker");
    }

    @Test
    @DisplayName("load(path) throws Corrupted when marker disagrees with runtime encoder")
    void loadRejectsForeignEncoder(@TempDir Path root) throws Exception {
        Path markerPath = root.resolve(".librarian").resolve("format");
        Files.createDirectories(markerPath.getParent());
        Files.write(markerPath, new byte[]{(byte) 0x99});  // unknown encoder

        assertThatThrownBy(() -> Librarian.load(root))
                .isInstanceOf(dev.everydaythings.graph.library.LibraryException.Corrupted.class)
                .hasMessageContaining("0x99");
    }

    @Test
    @DisplayName("load(path) currently throws UnsupportedOperationException — vault loading pending")
    void loadPendingVaultLoading(@TempDir Path root) {
        Librarian.fresh(root);   // sets up marker + identity
        // load() can verify the marker, but cannot re-acquire the vault yet.
        assertThatThrownBy(() -> Librarian.load(root))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("vault disk-persistence");
    }
}
