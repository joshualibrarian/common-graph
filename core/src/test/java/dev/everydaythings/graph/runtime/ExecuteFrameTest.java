package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.librarian.LibrarianVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Item#execute(Frame)} — a frame bubbling up the handler chain
 * (orchestrator → librarian).  Exercised with CREATE: an orchestrator executes
 * a CREATE frame, it reaches the librarian's create handler, an item is minted,
 * and the committed manifest comes back as the response.
 */
@DisplayName("Item.execute (bubble-up)")
class ExecuteFrameTest {

    private static final ItemRef WIDGET = ItemRef.fromString("cg.archetype:widget-test");

    /** Public so {@code Class.forName} can hydrate it; reports its archetype. */
    public static class Widget extends Item {
        public Widget(ItemRef iid, Librarian lib) {
            super(iid, lib);
        }

        @Override
        public ItemRef archetype() {
            return WIDGET;
        }
    }

    /** Seed a Widget archetype with an IMPLEMENTATION binding and no EXPECTS — "create widget" is complete. */
    private static void seedWidget(Librarian lib) {
        Body archetypeManifest = Body.of(
                ItemRef.of(WIDGET),
                List.of(
                        Binding.ref(Manifest.ITEM_ID, WIDGET),
                        Implementations.forJava(Widget.class)));
        lib.persist(archetypeManifest);
    }

    @Test
    @DisplayName("executing a CREATE frame mints the item and returns its manifest")
    void executeCreateMintsItem() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();
        seedWidget(lib);

        Item orchestrator = new Item(ItemRef.fromString("test.orchestrator"), lib);
        Body createBody = Body.of(
                ItemRef.of(ItemRef.iid(LibrarianVocabulary.Create.KEY)),
                List.of(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), WIDGET)));

        SubmitResult result = orchestrator.execute(Frame.of(createBody, List.of()));

        assertThat(result.responses()).hasSize(1);
        Frame receipt = result.responses().get(0);

        ItemRef newIid = (ItemRef) receipt.body()
                .binding(CompoundKey.of(Manifest.ITEM_ID))
                .map(Binding::target)
                .orElseThrow();
        assertThat(lib.fetchItem(newIid)).get().isInstanceOf(Widget.class);
    }

    @Test
    @DisplayName("execute persists the frame first, so the receipt's CAUSE resolves")
    void executePersistsCauseFrame() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();
        seedWidget(lib);

        Item orchestrator = new Item(ItemRef.fromString("test.orchestrator"), lib);
        Body createBody = Body.of(
                ItemRef.of(ItemRef.iid(LibrarianVocabulary.Create.KEY)),
                List.of(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), WIDGET)));

        SubmitResult result = orchestrator.execute(Frame.of(createBody, List.of()));

        Record record = result.responses().get(0).records().get(0);
        List<Binding> cause = record.bindings(CompoundKey.of(ItemRef.iid(ThematicRole.Cause.KEY)));
        assertThat(cause).hasSize(1);
        assertThat(cause.get(0).target()).isEqualTo(createBody.datumId());
        // The CREATE frame body was persisted on the way through, so the CAUSE resolves.
        assertThat(lib.has(createBody.datumId())).isTrue();
    }
}
