package dev.everydaythings.graph.library.mapdb;


import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the MapDB-backed Library factory.
 */
class MapDbLibraryTest {

    @Test
    @DisplayName("Library.mapDb(path) opens three MapDB files and round-trips a Datum")
    void roundtripDatum(@TempDir Path root) {
        try (Library lib = Library.mapDb(root)) {
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
    @DisplayName("MapDB Library writes format marker on first open")
    void marker(@TempDir Path root) {
        try (Library ignored = Library.mapDb(root)) {
            assertThat(root.resolve(".librarian").resolve("format")).exists();
        }
    }

    @Test
    @DisplayName("Reopen on the same path validates marker and persists data")
    void reopenPersists(@TempDir Path root) {
        DatumRef id;
        Body body = Body.of(
                ItemRef.of(ItemRef.fromString("test.predicate:authored")),
                List.of(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), ItemRef.fromString("test.item:book"))));
        try (Library first = Library.mapDb(root)) {
            id = first.put(body);
        }
        try (Library second = Library.mapDb(root)) {
            assertThat(second.fetchDatum(id)).isPresent();
        }
    }

    @Test
    @DisplayName("Ref-index queries work over MapDB-backed RefIndexStore")
    void refIndexQueries(@TempDir Path root) {
        try (Library lib = Library.mapDb(root)) {
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
