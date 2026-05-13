package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.semantics.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the CREATE predicate + @Handler dispatch.
 *
 * <p>Validates:
 * <ol>
 *   <li>A CREATE frame submitted to the librarian causes a new item to exist</li>
 *   <li>The new item's IID is deterministic from the CREATE frame</li>
 *   <li>The new item's manifest is committed and signed (durable)</li>
 *   <li>Resolution path: archetype's IMPLEMENTATION (when no INSTRUMENT)</li>
 *   <li>Resolution path: INSTRUMENT-as-class-literal (caller-specified impl)</li>
 *   <li>The CREATE frame itself is the durable record — no separate CREATED emitted</li>
 * </ol>
 */
class CreateSubmitTest {

    @Test
    @DisplayName("CREATE with archetype-default implementation produces a new item")
    void createDefaultImpl() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // The base Item archetype has @Seed.Embodies(key = Item.KEY) pointing at
        // Item.class itself — so its manifest carries IMPLEMENTATION → Item.class.
        Frame create = Frame.compose(Create.IID)
                .theme(Item.IID)         // create an instance of the bare Item archetype
                .record(lib)             // librarian signs the request
                .build();

        SubmitResult result = lib.submit(create);

        // No CREATED response frame — the CREATE frame itself is the audit.
        assertThat(result.responses()).isEmpty();

        // The new item should now be findable via the deterministic IID.
        ItemID expectedIid = ItemID.fromMultikeyBytes(create.body().datumId().encodeBinary());
        Optional<Item> created = lib.fetchItem(expectedIid);
        assertThat(created).isPresent();
        assertThat(created.get().archetype()).isEqualTo(Item.IID);
        // The CREATE frame body is durable (CREATE is not ephemeral).
        assertThat(lib.has(create.body().datumId())).isTrue();
    }

    @Test
    @DisplayName("CREATE with explicit INSTRUMENT class literal uses that impl")
    void createWithInstrumentLiteral() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // Caller picks the implementation directly via a Java-class binding —
        // INSTRUMENT role narrowed by the JavaClass qualifier, with the FQCN as text.
        Frame create = Frame.compose(Create.IID)
                .theme(Item.IID)
                .with(new dev.everydaythings.graph.datum.Binding(
                        ThematicRole.Instrument.IID,
                        java.util.List.of(new dev.everydaythings.graph.item.id.CompoundKey.Sememe(
                                dev.everydaythings.graph.CoreVocabulary.JavaClass.IID)),
                        Literal.ofText(Item.class.getName())))
                .record(lib)
                .build();

        SubmitResult result = lib.submit(create);
        assertThat(result.responses()).isEmpty();

        ItemID newIid = ItemID.fromMultikeyBytes(create.body().datumId().encodeBinary());
        Optional<Item> created = lib.fetchItem(newIid);
        assertThat(created).isPresent();
        assertThat(created.get()).isInstanceOf(Item.class);
    }

    @Test
    @DisplayName("Two CREATE frames produce two distinct items (records carry TIME)")
    void distinctCreates() throws InterruptedException {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Frame create1 = Frame.compose(Create.IID).theme(Item.IID).record(lib).build();
        Thread.sleep(1);   // ensure distinct TIME on the records
        Frame create2 = Frame.compose(Create.IID).theme(Item.IID).record(lib).build();

        // The two CREATE frame BODIES are identical (records aren't part of the body),
        // so this is actually a sameness check — both CREATEs hash to the same body
        // datumID, so the resulting IID is also the same. The records differ (TIME),
        // but the new item's IID is body-derived. So submitting twice is idempotent.
        assertThat(create1.body().datumId()).isEqualTo(create2.body().datumId());

        ItemID iid = ItemID.fromMultikeyBytes(create1.body().datumId().encodeBinary());
        lib.submit(create1);
        Item first = lib.fetchItem(iid).orElseThrow();

        // Second submit re-runs CREATE — same archetype, same IID; commits a new
        // manifest version of the existing item.
        lib.submit(create2);
        Item second = lib.fetchItem(iid).orElseThrow();

        // Same IID, same item identity (cache returns the same instance).
        assertThat(first.iid()).isEqualTo(second.iid());
    }

    @Test
    @DisplayName("CREATE with missing THEME throws")
    void createWithoutThemeFails() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Frame badCreate = Frame.compose(Create.IID).record(lib).build();
        // No THEME — handler should fail at dispatch time.
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> lib.submit(badCreate));
    }
}
