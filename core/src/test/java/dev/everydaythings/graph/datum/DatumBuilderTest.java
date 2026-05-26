package dev.everydaythings.graph.datum;


import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.Signer;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatumBuilderTest {

    static final ItemRef LOOKUP = ItemRef.fromString("cg.predicate:lookup");
    static final ItemRef AUTHORED = ItemRef.fromString("cg.predicate:authored");
    static final ItemRef TOLKIEN = ItemRef.fromString("person.tolkien");
    static final ItemRef HOBBIT = ItemRef.fromString("book.hobbit");
    static final ItemRef LEXEME = ItemRef.fromString("cg.predicate:lexeme");
    static final ItemRef ADD = ItemRef.fromString("cg.operator:add");
    static final ItemRef ENGLISH = ItemRef.fromString("cg.lang:eng");
    static final ItemRef VERB = ItemRef.fromString("cg.pos:verb");
    static final ItemRef LEMMA = ItemRef.fromString("cg.feat:lemma");

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
            assertThat(b.role()).isEqualTo(ItemRef.iid(ThematicRole.Theme.KEY));
            assertThat(b.target()).isEqualTo("create");
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
            assertThat(f.body().binding(CompoundKey.of(ItemRef.iid(ThematicRole.Agent.KEY))))
                    .isPresent();
            assertThat(f.body().binding(CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY))))
                    .isPresent();
        }

        @Test
        @DisplayName("generic .with(role, target) for arbitrary roles")
        void genericWith() {
            ItemRef custom = ItemRef.fromString("custom.role:x");
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
                    .binding(ItemRef.iid(ThematicRole.Value.KEY))
                        .qualifier(ENGLISH)
                        .qualifier(VERB)
                        .qualifier(LEMMA)
                        .target("add")
                    .build();

            assertThat(f.body().bindings()).hasSize(2);
            Binding lex = f.body().binding(CompoundKey.of(
                    ItemRef.iid(ThematicRole.Value.KEY), ENGLISH, VERB, LEMMA)).orElseThrow();
            assertThat(lex.target()).isEqualTo("add");
        }

        @Test
        @DisplayName("binding auto-closes when next .binding() opens")
        void autoCloseOnNextBinding() {
            Frame f = (Frame) Frame.compose(LEXEME)
                    .theme(ADD)
                    .binding(ItemRef.iid(ThematicRole.Value.KEY))
                        .qualifier(ENGLISH)
                        .target("add")
                    .binding(ItemRef.iid(ThematicRole.Value.KEY))        // auto-closes prev
                        .qualifier(ItemRef.fromString("cg.lang:deu"))
                        .target("addieren")
                    .build();

            assertThat(f.body().bindings()).hasSize(3);
            // Both English and German bindings should be present.
            ItemRef german = ItemRef.fromString("cg.lang:deu");
            assertThat(f.body().binding(CompoundKey.of(ItemRef.iid(ThematicRole.Value.KEY), ENGLISH)))
                    .isPresent();
            assertThat(f.body().binding(CompoundKey.of(ItemRef.iid(ThematicRole.Value.KEY), german)))
                    .isPresent();
        }

        @Test
        @DisplayName("binding closes via .done() and the parent continues normally")
        void doneEscapesToParent() {
            Frame f = Frame.compose(LEXEME)
                    .binding(ItemRef.iid(ThematicRole.Value.KEY))
                        .qualifier(ENGLISH)
                        .target("add")
                        .done()                              // close binding, back on parent
                    .theme(ADD)
                    .build();

            assertThat(f.body().bindings()).hasSize(2);
            assertThat(f.body().binding(CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))).isPresent();
            assertThat(f.body().binding(CompoundKey.of(ItemRef.iid(ThematicRole.Value.KEY), ENGLISH)))
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
            Binding agent = r.binding(CompoundKey.of(ItemRef.iid(ThematicRole.Agent.KEY))).orElseThrow();
            assertThat(agent.target()).isEqualTo(alice.iid());

            // TIME auto-added → an Instant
            Binding time = r.binding(CompoundKey.of(ItemRef.iid(ThematicRole.Time.KEY))).orElseThrow();
            assertThat(time.target()).isInstanceOf(java.time.Instant.class);

            // Signature is real bytes
            assertThat(r.signature()).isNotEmpty();
        }

        @Test
        @DisplayName(".record(signer).with(...).build() — record-level bindings preserved")
        void recordWithBindings() {
            Signer alice = Signer.inMemory();
            ItemRef confidence = ItemRef.fromString("attr.confidence");

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
                            assertThat(b.target()).isEqualTo("high"));
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
            ItemRef iid = ItemRef.fromString("test.item:doc");
            ItemRef archetype = ItemRef.fromString("cg.archetype:document");

            Manifest m = Manifest.compose(archetype, iid).build();

            assertThat(m.itemId()).isEqualTo(iid);
            assertThat(m.body().head()).isEqualTo(ItemRef.of(archetype));
        }

        @Test
        @DisplayName("manifest can be signed")
        void signedManifest() {
            Signer librarian = Librarian.inMemory();
            ItemRef iid = ItemRef.fromString("test.item:doc");
            ItemRef archetype = ItemRef.fromString("cg.archetype:document");

            Manifest m = Manifest.compose(archetype, iid)
                    .with(Manifest.ENDORSES, ItemRef.fromString("test.frame:cid1"))
                    .record(librarian)
                    .build();

            assertThat(m.records()).hasSize(1);
            assertThat(m.endorses()).hasSize(1);
        }
    }
}
