package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.runtime.librarian.Librarian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Librarian#fresh(Path)} and {@link Librarian#load(Path)} —
 * the on-disk startup paths.
 */
class LibrarianFreshLoadTest {

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
    @DisplayName("load(path) re-acquires the same signing identity as the original fresh(path)")
    void loadPreservesIdentity(@TempDir Path root) throws Exception {
        Librarian first = Librarian.fresh(root);
        dev.everydaythings.graph.ref.ItemRef firstIid = first.iid();
        first.close();

        Librarian loaded = Librarian.load(root);
        assertThat(loaded.iid()).isEqualTo(firstIid);
        assertThat(loaded.canSign()).isTrue();
        loaded.library().close();
    }

    @Test
    @DisplayName("load(path) throws if no materialized Librarian exists at the path")
    void loadMissingLibrarian(@TempDir Path root) {
        assertThatThrownBy(() -> Librarian.load(root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No materialized Librarian");
    }

    @Test
    @DisplayName("fresh(path) writes .item/head + a MATERIALIZED record + body in .item/objects/")
    void freshWritesMaterializedManifest(@TempDir Path root) throws Exception {
        Librarian lib = Librarian.fresh(root);
        lib.close();

        Path itemDir = root.resolve(".item");
        assertThat(Files.isRegularFile(itemDir.resolve("head"))).isTrue();
        Path objectsDir = itemDir.resolve("objects");
        // At least two objects: the manifest record + the manifest body.
        long objectCount;
        try (java.util.stream.Stream<Path> walk = Files.list(objectsDir)) {
            objectCount = walk.count();
        }
        assertThat(objectCount).isGreaterThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("fresh(path) materializes the librarian at <path>/.item/ (iid, codec, objects/)")
    void freshMaterializesLibrarian(@TempDir Path root) throws Exception {
        Librarian lib = Librarian.fresh(root);

        Path itemDir = root.resolve(".item");
        assertThat(Files.isDirectory(itemDir)).isTrue();
        assertThat(Files.isRegularFile(itemDir.resolve("iid"))).isTrue();
        assertThat(Files.isRegularFile(itemDir.resolve("codec"))).isTrue();
        assertThat(Files.isDirectory(itemDir.resolve("objects"))).isTrue();

        // .item/iid carries the librarian's actual IID.
        byte[] iidBytes = Files.readAllBytes(itemDir.resolve("iid"));
        assertThat(iidBytes).isEqualTo(lib.iid().toRefBytes());

        lib.close();
    }

    @Test
    @DisplayName("fresh(path) refuses to clobber an existing librarian .item/ (use load() instead)")
    void freshRefusesExistingLibrarian(@TempDir Path root) throws Exception {
        Librarian first = Librarian.fresh(root);
        first.close();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> Librarian.fresh(root));
    }

    @Test
    @DisplayName("Bodies persisted by a first Librarian survive into a second Librarian via load(path)")
    void bodiesPersistAcrossRestart(@TempDir Path root) throws Exception {
        Librarian first = Librarian.fresh(root);
        dev.everydaythings.graph.ref.ContentRef cid =
                first.persistContent("hello-persistent".getBytes());
        first.close();

        Librarian second = Librarian.load(root);
        assertThat(second.fetch(cid))
                .isPresent()
                .get()
                .isEqualTo("hello-persistent".getBytes());
        second.close();
    }

    @SuppressWarnings("unused")
    private static void deleteDirectory(Path dir) throws Exception {
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }
}
