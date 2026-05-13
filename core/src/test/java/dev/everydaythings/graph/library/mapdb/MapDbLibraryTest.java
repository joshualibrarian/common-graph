package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.item.id.DatumID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.semantics.ThematicRole;
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
                    ItemRef.of(ItemID.fromString("test.predicate:authored")),
                    List.of(Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("test.item:book"))));

            DatumID id = lib.put(body);
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
        DatumID id;
        Body body = Body.of(
                ItemRef.of(ItemID.fromString("test.predicate:authored")),
                List.of(Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("test.item:book"))));
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
            ItemID role = ThematicRole.Theme.IID;
            ItemID target = ItemID.fromString("test.item:target-x");
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("test.predicate:test")),
                    List.of(Binding.ref(role, target)));
            DatumID id = lib.put(body);

            List<DatumID> matches = lib.bodyCidsForReferenceBinding(role, target);
            assertThat(matches).containsExactly(id);
        }
    }
}
