package dev.everydaythings.graph.library.rocksdb;


import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for {@link RocksDataStore} / {@link RocksRefIndexStore} /
 * {@link RocksTokenIndexStore} via the {@link Library#rocksDb(Path)} factory.
 *
 * <p>Each test opens a fresh RocksDB at a {@code @TempDir} path, exercises a
 * minimal flow, and closes. Verifies that the RocksDB-backed Library composes
 * correctly with the new abstractions.
 */
class RocksDbLibraryTest {

    @Test
    @DisplayName("Library.rocksDb(path) opens three RocksDB instances and round-trips a Datum")
    void roundtripDatum(@TempDir Path root) {
        try (Library lib = Library.rocksDb(root)) {
            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("test.predicate:authored")),
                    List.of(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), ItemRef.fromString("test.item:book"))));

            DatumRef id = lib.put(body);
            assertThat(id).isEqualTo(body.datumId());

            Optional<Datum> fetched = lib.fetchDatum(id);
            assertThat(fetched).isPresent();
            assertThat(fetched.get().datumId()).isEqualTo(body.datumId());
        }
    }

    @Test
    @DisplayName("RocksDB Library creates the library subdirectory on first open")
    void libraryDirCreated(@TempDir Path root) {
        try (Library ignored = Library.rocksDb(root)) {
            assertThat(root.resolve(Library.LIBRARY_SUBDIR)).isDirectory();
        }
    }

    @Test
    @DisplayName("Reopen on the same path validates marker and persists data")
    void reopenPersists(@TempDir Path root) {
        DatumRef id;
        Body body = Body.of(
                ItemRef.of(ItemRef.fromString("test.predicate:authored")),
                List.of(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), ItemRef.fromString("test.item:book"))));
        try (Library first = Library.rocksDb(root)) {
            id = first.put(body);
        }
        // Second open against the same dir: marker validates, data is still there.
        try (Library second = Library.rocksDb(root)) {
            assertThat(second.fetchDatum(id)).isPresent();
        }
    }

    @Test
    @DisplayName("Index queries work over RocksDB-backed RefIndexStore")
    void refIndexQueries(@TempDir Path root) {
        try (Library lib = Library.rocksDb(root)) {
            ItemRef role = ItemRef.iid(ThematicRole.Theme.KEY);
            ItemRef target = ItemRef.fromString("test.item:target-x");
            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("test.predicate:test")),
                    List.of(Binding.ref(role, target)));
            DatumRef id = lib.put(body);

            List<DatumRef> matches = lib.bodyCidsForReferenceBinding(role, target);
            assertThat(matches).containsExactly(id);
        }
    }
}
