package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.identity.Signer;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatumBuilderTest {

    static final ItemID LOOKUP = ItemID.fromString("cg.predicate:lookup");
    static final ItemID AUTHORED = ItemID.fromString("cg.predicate:authored");
    static final ItemID TOLKIEN = ItemID.fromString("person.tolkien");
    static final ItemID HOBBIT = ItemID.fromString("book.hobbit");
    static final ItemID LEXEME = ItemID.fromString("cg.predicate:lexeme");
    static final ItemID ADD = ItemID.fromString("cg.operator:add");
    static final ItemID ENGLISH = ItemID.fromString("cg.lang:eng");
    static final ItemID VERB = ItemID.fromString("cg.pos:verb");
    static final ItemID LEMMA = ItemID.fromString("cg.feat:lemma");

    @Nested
    @DisplayName("Frame.compose — basic shapes")
    class BasicFrame {

        @Test
        @DisplayName("ephemeral frame: predicate + one binding, no records")
        void ephemeral() {
            Frame f = Frame.compose(LOOKUP)
                    .theme("create")
                    .build();

            assertThat(f.body().head()).isEqualTo(ItemRef.of(LOOKUP));
            assertThat(f.body().bindings()).hasSize(1);
            assertThat(f.records()).isEmpty();

            Binding b = f.body().bindings().get(0);
            assertThat(b.role()).isEqualTo(ThematicRole.Theme.IID);
            assertThat(b.target()).isInstanceOf(Literal.class);
            assertThat(((Literal) b.target()).asText()).isEqualTo("create");
        }

        @Test
        @DisplayName("multiple role helpers")
        void multipleRoles() {
            Frame f = Frame.compose(AUTHORED)
                    .agent(TOLKIEN)
                    .theme(HOBBIT)
                    .build();

            assertThat(f.body().bindings()).hasSize(2);
            assertThat(f.records()).isEmpty();
            // Sort-order is by role-IID bytes (canonical), so we look up by role.
            assertThat(f.body().binding(CompoundKey.of(ThematicRole.Agent.IID)))
                    .isPresent();
            assertThat(f.body().binding(CompoundKey.of(ThematicRole.Theme.IID)))
                    .isPresent();
        }

        @Test
        @DisplayName("generic .with(role, target) for arbitrary roles")
        void genericWith() {
            ItemID custom = ItemID.fromString("custom.role:x");
            Frame f = Frame.compose(LOOKUP)
                    .with(custom, "value")
                    .build();

            assertThat(f.body().bindings()).hasSize(1);
            assertThat(f.body().bindings().get(0).role()).isEqualTo(custom);
        }
    }

    @Nested
    @DisplayName("Frame.compose — qualified bindings")
    class QualifiedBindings {

        @Test
        @DisplayName("binding with qualifiers — closed by .target()")
        void qualifiersThenTargetThenBuild() {
            Frame f = (Frame) Frame.compose(LEXEME)
                    .theme(ADD)
                    .binding(ThematicRole.Value.IID)
                        .qualifier(ENGLISH)
                        .qualifier(VERB)
                        .qualifier(LEMMA)
                        .target("add")
                    .build();

            assertThat(f.body().bindings()).hasSize(2);
            Binding lex = f.body().binding(CompoundKey.of(
                    ThematicRole.Value.IID, ENGLISH, VERB, LEMMA)).orElseThrow();
            assertThat(((Literal) lex.target()).asText()).isEqualTo("add");
        }

        @Test
        @DisplayName("binding auto-closes when next .binding() opens")
        void autoCloseOnNextBinding() {
            Frame f = (Frame) Frame.compose(LEXEME)
                    .theme(ADD)
                    .binding(ThematicRole.Value.IID)
                        .qualifier(ENGLISH)
                        .target("add")
                    .binding(ThematicRole.Value.IID)        // auto-closes prev
                        .qualifier(ItemID.fromString("cg.lang:deu"))
                        .target("addieren")
                    .build();

            assertThat(f.body().bindings()).hasSize(3);
            // Both English and German bindings should be present.
            ItemID german = ItemID.fromString("cg.lang:deu");
            assertThat(f.body().binding(CompoundKey.of(ThematicRole.Value.IID, ENGLISH)))
                    .isPresent();
            assertThat(f.body().binding(CompoundKey.of(ThematicRole.Value.IID, german)))
                    .isPresent();
        }

        @Test
        @DisplayName("binding auto-closes when a role helper is called next")
        void autoCloseOnRoleHelper() {
            Frame f = Frame.compose(LEXEME)
                    .binding(ThematicRole.Value.IID)
                        .qualifier(ENGLISH)
                        .target("add")
                    .theme(ADD)                              // forwards through binding
                    .build();

            assertThat(f.body().bindings()).hasSize(2);
            assertThat(f.body().binding(CompoundKey.of(ThematicRole.Theme.IID))).isPresent();
            assertThat(f.body().binding(CompoundKey.of(ThematicRole.Value.IID, ENGLISH)))
                    .isPresent();
        }
    }

    @Nested
    @DisplayName("Records and signing")
    class Signing {

        @Test
        @DisplayName(".record(signer).build() — one record with default AGENT/TIME")
        void simpleSigned() {
            Signer alice = Signer.inMemory();

            Frame f = Frame.compose(AUTHORED)
                    .agent(TOLKIEN)
                    .theme(HOBBIT)
                    .record(alice)
                    .build();

            assertThat(f.records()).hasSize(1);
            Record r = f.records().get(0);

            // AGENT auto-added → alice's IID
            Binding agent = r.binding(CompoundKey.of(ThematicRole.Agent.IID)).orElseThrow();
            assertThat(((BindingTarget.RefTarget) agent.target()).asItemId()).isEqualTo(alice.iid());

            // TIME auto-added → some Instant literal
            Binding time = r.binding(CompoundKey.of(ThematicRole.Time.IID)).orElseThrow();
            assertThat(time.target()).isInstanceOf(Literal.class);

            // Signature is real bytes
            assertThat(r.signature()).isNotEmpty();
        }

        @Test
        @DisplayName(".record(signer).with(...).build() — record-level bindings preserved")
        void recordWithBindings() {
            Signer alice = Signer.inMemory();
            ItemID confidence = ItemID.fromString("attr.confidence");

            Frame f = Frame.compose(AUTHORED)
                    .theme(HOBBIT)
                    .record(alice)
                        .with(confidence, "high")
                    .build();

            assertThat(f.records()).hasSize(1);
            Record r = f.records().get(0);

            // Confidence binding preserved on the record
            assertThat(r.binding(CompoundKey.of(confidence)))
                    .hasValueSatisfying(b ->
                            assertThat(((Literal) b.target()).asText()).isEqualTo("high"));
        }

        @Test
        @DisplayName("multi-sig: two records on one body")
        void multiSig() {
            Signer alice = Signer.inMemory();
            Signer bob = Signer.inMemory();

            Frame f = Frame.compose(AUTHORED)
                    .theme(HOBBIT)
                    .record(alice)
                    .record(bob)
                    .build();

            assertThat(f.records()).hasSize(2);
            // Both records reference the same body
            assertThat(f.records().get(0).headRef().bodyId())
                    .isEqualTo(f.body().datumId());
            assertThat(f.records().get(1).headRef().bodyId())
                    .isEqualTo(f.body().datumId());
            // Distinct AGENT bindings
            assertThat(f.records().get(0).bindings()).anySatisfy(b ->
                    assertThat(b.targetId()).isEqualTo(alice.iid()));
            assertThat(f.records().get(1).bindings()).anySatisfy(b ->
                    assertThat(b.targetId()).isEqualTo(bob.iid()));
        }
    }

    @Nested
    @DisplayName("Manifest.compose")
    class Manifests {

        @Test
        @DisplayName("manifest auto-injects ITEM_ID")
        void autoItemId() {
            ItemID iid = ItemID.fromString("test.item:doc");
            ItemID archetype = ItemID.fromString("cg.archetype:document");

            Manifest m = Manifest.compose(archetype, iid).build();

            assertThat(m.itemId()).isEqualTo(iid);
            assertThat(m.body().head()).isEqualTo(ItemRef.of(archetype));
        }

        @Test
        @DisplayName("manifest can be signed")
        void signedManifest() {
            Signer librarian = Librarian.inMemory();
            ItemID iid = ItemID.fromString("test.item:doc");
            ItemID archetype = ItemID.fromString("cg.archetype:document");

            Manifest m = Manifest.compose(archetype, iid)
                    .with(Manifest.ENDORSES, ItemID.fromString("test.frame:cid1"))
                    .record(librarian)
                    .build();

            assertThat(m.records()).hasSize(1);
            assertThat(m.endorses()).hasSize(1);
        }
    }
}
