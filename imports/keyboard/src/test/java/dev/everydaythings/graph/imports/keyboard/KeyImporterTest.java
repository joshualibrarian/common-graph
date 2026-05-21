package dev.everydaythings.graph.imports.keyboard;

import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.quality.InputVocabulary;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KeyImporter — verifies that the W3C UI Events `code` TSV bootstraps the
 * Key vocabulary correctly: every row produces a manifest, IIDs match the
 * canonical {@code cg.key:<code>} derivation, category head archetypes
 * match the TSV's category column.
 */
class KeyImporterTest {

    @Test
    @DisplayName("bootstrap seeds every TSV row and returns the count")
    void bootstrapsAllRows() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        int count = KeyImporter.bootstrap(librarian);

        // The committed TSV has ~150 W3C codes.  Exact count may grow
        // over time but should stay above 100.
        assertThat(count).isGreaterThanOrEqualTo(100);
    }

    @Test
    @DisplayName("KeyA seeds with letter category head and the right IID")
    void keyAHeadAndIid() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();
        KeyImporter.bootstrap(librarian);

        ItemRef keyAIid = ItemRef.fromString("cg.key:KeyA");
        List<DatumRef> manifestCids = librarian.library().manifestCidsForItem(keyAIid);
        assertThat(manifestCids).as("manifest for KeyA").isNotEmpty();

        Optional<Manifest> manifestOpt = librarian.fetchManifest(manifestCids.get(0));
        assertThat(manifestOpt).isPresent();
        Manifest manifest = manifestOpt.get();

        assertThat(manifest.body().headRef())
                .isEqualTo(ItemRef.iid(InputVocabulary.Letter.KEY));
    }

    @Test
    @DisplayName("MetaLeft has Alias lexemes for the legacy OSLeft / SuperLeft names")
    void metaLeftHasAliases() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();
        KeyImporter.bootstrap(librarian);

        ItemRef metaLeftIid = ItemRef.fromString("cg.key:MetaLeft");
        List<DatumRef> manifestCids = librarian.library().manifestCidsForItem(metaLeftIid);
        assertThat(manifestCids).isNotEmpty();

        Manifest manifest = librarian.fetchManifest(manifestCids.get(0)).orElseThrow();
        assertThat(manifest.body().headRef())
                .isEqualTo(ItemRef.iid(InputVocabulary.Modifier.KEY));

        // ENDORSES bindings point at lexeme frames (lemma "Left Meta", aliases
        // "OSLeft" and "SuperLeft").  Verify at least three endorsements
        // exist — the test stays robust if additional lexemes get added later.
        long endorsementCount = manifest.body().bindings().stream()
                .filter(b -> Manifest.ENDORSES.equals(b.role()))
                .count();
        assertThat(endorsementCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Numpad0 is classified as a digit, not a numpad-specific category")
    void numpadDigitsAreDigits() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();
        KeyImporter.bootstrap(librarian);

        ItemRef numpad0Iid = ItemRef.fromString("cg.key:Numpad0");
        Manifest manifest = librarian.fetchManifest(
                librarian.library().manifestCidsForItem(numpad0Iid).get(0)).orElseThrow();
        assertThat(manifest.body().headRef())
                .isEqualTo(ItemRef.iid(InputVocabulary.Digit.KEY));
    }
}
