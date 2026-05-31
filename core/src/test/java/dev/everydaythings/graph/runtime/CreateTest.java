package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.runtime.Implementations;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.librarian.LibrarianVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the CREATE spine: a CREATE frame, folded against the archetype's
 * EXPECTS via {@code ItemSketch}, mints a fresh instance with a random IID and
 * returns the committed manifest as a frame whose record is the creation
 * receipt (head → the new item, CAUSE → the CREATE command).
 *
 * <p>Uses a hand-built test archetype (direct IMPLEMENTATION binding + one
 * {@code !}-roled EXPECTS) so the spine is tested without depending on any
 * particular seeded archetype's embodiment wiring.
 */
@DisplayName("CREATE spine (ItemSketch-driven)")
class CreateTest {

    private static final ItemRef GADGET = ItemRef.fromString("cg.archetype:gadget-test");

    /**
     * Public so {@code Class.forName} can hydrate it from the IMPLEMENTATION
     * binding.  Like every real implementation class, it overrides
     * {@link #archetype()} to report the archetype it implements, so its
     * committed manifest's head is the archetype.
     */
    public static class Gadget extends Item {
        public Gadget(ItemRef iid, Librarian lib) {
            super(iid, lib);
        }

        @Override
        public ItemRef archetype() {
            return GADGET;
        }
    }
    private static final ItemRef COLOR = ItemRef.fromString("cg.role:color-test");

    /** Persist a Gadget archetype manifest: IMPLEMENTATION → Gadget, EXPECTS !COLOR. */
    private static void seedGadgetArchetype(Librarian lib) {
        Body archetypeManifest = Body.of(
                ItemRef.of(GADGET),
                List.of(
                        Binding.ref(Manifest.ITEM_ID, GADGET),
                        Implementations.forJava(Gadget.class),
                        new Binding(SchemaRef.of(COLOR), TypeRef.iid("cg.value:color-test"))));
        lib.persist(archetypeManifest);
    }

    /** A CREATE for a Gadget with no color — leaves the !COLOR expectation unfilled. */
    private static Frame createGadget() {
        Body body = Body.of(
                ItemRef.of(ItemRef.iid(LibrarianVocabulary.Create.KEY)),
                List.of(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), GADGET)));
        return Frame.of(body, List.of());
    }

    private static Frame createGadgetWithColor(String color) {
        // Command-frame compound-key shape: Attribute[Color] → color.  Color isn't
        // a thematic role, so it belongs in the qualifier slot, not the role slot.
        // The manifest realization is the simpler Color → color, since manifests
        // are free shape.
        Binding colorBinding = Binding.qualified(
                ItemRef.iid(ThematicRole.Attribute.KEY),
                List.of(new CompoundKey.Sememe(COLOR)),
                color);
        Body body = Body.of(
                ItemRef.of(ItemRef.iid(LibrarianVocabulary.Create.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), GADGET),
                        colorBinding));
        return Frame.of(body, List.of());
    }

    private static ItemRef newIidOf(Frame receipt) {
        return (ItemRef) receipt.body()
                .binding(CompoundKey.of(Manifest.ITEM_ID))
                .map(Binding::target)
                .orElseThrow();
    }

    @Nested
    @DisplayName("complete CREATE")
    class Complete {

        @Test
        @DisplayName("mints the instance and returns the committed manifest as a frame")
        void mintsAndReturnsManifest() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();
            seedGadgetArchetype(lib);

            List<Frame> result = lib.createItem(createGadgetWithColor("red"));

            assertThat(result).hasSize(1);
            Body manifestBody = result.get(0).body();
            assertThat(manifestBody.headRef()).isEqualTo(GADGET);

            ItemRef newIid = newIidOf(result.get(0));
            assertThat(newIid).isNotNull();
        }

        @Test
        @DisplayName("the filled field lands on the new item's manifest")
        void filledFieldOnManifest() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();
            seedGadgetArchetype(lib);

            ItemRef newIid = newIidOf(lib.createItem(createGadgetWithColor("red")).get(0));

            Item created = lib.fetchItem(newIid).orElseThrow();
            assertThat(created).isInstanceOf(Gadget.class);
            Object color = created.current().body()
                    .binding(CompoundKey.of(COLOR))
                    .map(Binding::target)
                    .orElse(null);
            assertThat(color).isEqualTo("red");
        }

        @Test
        @DisplayName("the manifest record is a creation receipt: head → item, CAUSE → the CREATE command")
        void recordIsReceipt() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();
            seedGadgetArchetype(lib);

            Frame createFrame = createGadgetWithColor("red");
            Frame receipt = lib.createItem(createFrame).get(0);

            assertThat(receipt.records()).hasSize(1);
            Record record = receipt.records().get(0);

            // head → the new item's manifest body
            assertThat(record.headRef()).isEqualTo(receipt.body().datumId());

            // CAUSE → the CREATE command's body
            List<Binding> cause = record.bindings(
                    CompoundKey.of(ItemRef.iid(ThematicRole.Cause.KEY)));
            assertThat(cause).hasSize(1);
            assertThat(cause.get(0).target()).isEqualTo(createFrame.body().datumId());
        }

        @Test
        @DisplayName("two identical CREATEs mint two distinct items (random IID, no aliasing)")
        void randomDistinctIids() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();
            seedGadgetArchetype(lib);

            ItemRef iid1 = newIidOf(lib.createItem(createGadgetWithColor("red")).get(0));
            ItemRef iid2 = newIidOf(lib.createItem(createGadgetWithColor("red")).get(0));

            assertThat(iid1).isNotEqualTo(iid2);
        }
    }

    @Nested
    @DisplayName("incomplete CREATE")
    class Incomplete {

        @Test
        @DisplayName("a CREATE missing an expected field is rejected, naming the missing role")
        void missingExpectedFieldRejected() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();
            seedGadgetArchetype(lib);

            assertThatThrownBy(() -> lib.createItem(createGadget()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing expected fields")
                    .hasMessageContaining(COLOR.encodeText());
        }

        @Test
        @DisplayName("nothing is minted when the CREATE is incomplete")
        void nothingMintedOnIncomplete() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();
            seedGadgetArchetype(lib);

            long before = lib.library().manifestCidsForItem(GADGET).size();
            try {
                lib.createItem(createGadget());
            } catch (IllegalStateException ignored) {
                // expected
            }
            // No new Gadget manifests beyond the archetype's own.
            assertThat(lib.library().manifestCidsForItem(GADGET)).hasSize((int) before);
        }
    }

    @Nested
    @DisplayName("missing THEME")
    class MissingTheme {

        @Test
        @DisplayName("a CREATE with no THEME is rejected")
        void noThemeRejected() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            Body body = Body.of(ItemRef.of(ItemRef.iid(LibrarianVocabulary.Create.KEY)), List.of());
            assertThatThrownBy(() -> lib.createItem(Frame.of(body, List.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("THEME");
        }
    }
}
