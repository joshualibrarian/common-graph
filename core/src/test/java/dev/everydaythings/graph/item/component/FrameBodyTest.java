package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FrameBody")
class FrameBodyTest {

    static final ItemID TITLE = ItemID.fromString("cg:pred/title");
    static final ItemID AUTHOR = ItemID.fromString("cg:pred/author");
    static final ItemID THE_HOBBIT = ItemID.fromString("cg:book/the-hobbit");
    static final ItemID TOLKIEN = ItemID.fromString("cg:person/tolkien");
    static final ItemID THEME_ROLE = ItemID.fromString("cg.role:theme");
    static final ItemID TARGET_ROLE = ItemID.fromString("cg.role:target");

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("minimal body with predicate and theme")
        void minimalBody() {
            FrameBody body = new FrameBody(TITLE, THE_HOBBIT);
            assertThat(body.predicate()).isEqualTo(TITLE);
            assertThat(body.theme()).isEqualTo(THE_HOBBIT);
            assertThat(body.bindings()).isEmpty();
        }

        @Test
        @DisplayName("body with bindings")
        void withBindings() {
            Map<ItemID, BindingTarget> bindings = Map.of(
                    TARGET_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBody body = new FrameBody(AUTHOR, THE_HOBBIT, bindings);
            assertThat(body.predicate()).isEqualTo(AUTHOR);
            assertThat(body.theme()).isEqualTo(THE_HOBBIT);
            assertThat(body.bindings()).hasSize(1);
            assertThat(body.bindings().get(TARGET_ROLE))
                    .isInstanceOf(BindingTarget.IidTarget.class);
        }

        @Test
        @DisplayName("null predicate rejected")
        void nullPredicate() {
            assertThatThrownBy(() -> new FrameBody(null, THE_HOBBIT))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null theme rejected")
        void nullTheme() {
            assertThatThrownBy(() -> new FrameBody(TITLE, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null bindings treated as empty")
        void nullBindings() {
            FrameBody body = new FrameBody(TITLE, THE_HOBBIT, null);
            assertThat(body.bindings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("hash is deterministic")
        void deterministicHash() {
            FrameBody a = new FrameBody(TITLE, THE_HOBBIT);
            FrameBody b = new FrameBody(TITLE, THE_HOBBIT);
            assertThat(a.hash()).isEqualTo(b.hash());
        }

        @Test
        @DisplayName("different predicates produce different hashes")
        void differentPredicates() {
            FrameBody a = new FrameBody(TITLE, THE_HOBBIT);
            FrameBody b = new FrameBody(AUTHOR, THE_HOBBIT);
            assertThat(a.hash()).isNotEqualTo(b.hash());
        }

        @Test
        @DisplayName("different themes produce different hashes")
        void differentThemes() {
            FrameBody a = new FrameBody(TITLE, THE_HOBBIT);
            FrameBody b = new FrameBody(TITLE, TOLKIEN);
            assertThat(a.hash()).isNotEqualTo(b.hash());
        }

        @Test
        @DisplayName("same assertion from different callers = same hash")
        void sameAssertionSameHash() {
            Map<ItemID, BindingTarget> bindings = Map.of(
                    TARGET_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBody alice = FrameBody.of(AUTHOR, THE_HOBBIT, bindings);
            FrameBody bob = FrameBody.of(AUTHOR, THE_HOBBIT, bindings);
            assertThat(alice.hash()).isEqualTo(bob.hash());
            assertThat(alice.bodyBytes()).isEqualTo(bob.bodyBytes());
        }

        @Test
        @DisplayName("hash is a ContentID")
        void hashIsContentID() {
            FrameBody body = new FrameBody(TITLE, THE_HOBBIT);
            ContentID hash = body.hash();
            assertThat(hash).isNotNull();
            assertThat(hash.encodeBinary()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("CBOR Round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("minimal body round-trips")
        void minimalRoundTrip() {
            FrameBody original = new FrameBody(TITLE, THE_HOBBIT);
            byte[] bytes = original.encodeBinary(Canonical.Scope.BODY);
            FrameBody decoded = Canonical.decodeBinary(bytes, FrameBody.class, Canonical.Scope.BODY);
            assertThat(decoded.predicate()).isEqualTo(original.predicate());
            assertThat(decoded.theme()).isEqualTo(original.theme());
            assertThat(decoded.bindings()).isEmpty();
        }

        @Test
        @DisplayName("body with bindings round-trips")
        void bindingsRoundTrip() {
            Map<ItemID, BindingTarget> bindings = Map.of(
                    TARGET_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBody original = new FrameBody(AUTHOR, THE_HOBBIT, bindings);
            byte[] bytes = original.encodeBinary(Canonical.Scope.BODY);
            FrameBody decoded = Canonical.decodeBinary(bytes, FrameBody.class, Canonical.Scope.BODY);
            assertThat(decoded.predicate()).isEqualTo(original.predicate());
            assertThat(decoded.theme()).isEqualTo(original.theme());
            assertThat(decoded.bindings()).hasSize(1);
        }

        @Test
        @DisplayName("round-tripped body produces same hash")
        void roundTripPreservesHash() {
            Map<ItemID, BindingTarget> bindings = Map.of(
                    TARGET_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBody original = new FrameBody(AUTHOR, THE_HOBBIT, bindings);
            byte[] bytes = original.encodeBinary(Canonical.Scope.BODY);
            FrameBody decoded = Canonical.decodeBinary(bytes, FrameBody.class, Canonical.Scope.BODY);
            assertThat(decoded.hash()).isEqualTo(original.hash());
        }
    }

    @Nested
    @DisplayName("Relation Bridge")
    class RelationBridge {

        @Test
        @DisplayName("fromRelation extracts predicate and bindings")
        void fromRelation() {
            Relation relation = Relation.builder()
                    .predicate(AUTHOR)
                    .bind(THEME_ROLE, BindingTarget.iid(THE_HOBBIT))
                    .bind(TARGET_ROLE, BindingTarget.iid(TOLKIEN))
                    .build();

            FrameBody body = FrameBody.fromRelation(relation, THE_HOBBIT);
            assertThat(body.predicate()).isEqualTo(AUTHOR);
            assertThat(body.theme()).isEqualTo(THE_HOBBIT);
            assertThat(body.bindings()).hasSize(2);
        }

        @Test
        @DisplayName("Relation.toFrameBody() convenience method")
        void toFrameBodyConvenience() {
            Relation relation = Relation.builder()
                    .predicate(AUTHOR)
                    .bind(THEME_ROLE, BindingTarget.iid(THE_HOBBIT))
                    .bind(TARGET_ROLE, BindingTarget.iid(TOLKIEN))
                    .build();

            FrameBody body = relation.toFrameBody(THE_HOBBIT);
            assertThat(body).isNotNull();
            assertThat(body.predicate()).isEqualTo(AUTHOR);
        }

        @Test
        @DisplayName("Relation.toFrameBody() no-arg uses THEME binding")
        void toFrameBodyNoArg() {
            Relation relation = Relation.builder()
                    .predicate(AUTHOR)
                    .bind(THEME_ROLE, BindingTarget.iid(THE_HOBBIT))
                    .bind(TARGET_ROLE, BindingTarget.iid(TOLKIEN))
                    .build();

            FrameBody body = relation.toFrameBody();
            assertThat(body).isNotNull();
            assertThat(body.theme()).isEqualTo(THE_HOBBIT);
        }

        @Test
        @DisplayName("Relation.toFrameBody() returns null when no THEME binding")
        void toFrameBodyNoTheme() {
            Relation relation = Relation.builder()
                    .predicate(AUTHOR)
                    .bind(TARGET_ROLE, BindingTarget.iid(TOLKIEN))
                    .build();

            FrameBody body = relation.toFrameBody();
            assertThat(body).isNull();
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equal bodies are equal")
        void equalBodies() {
            FrameBody a = new FrameBody(TITLE, THE_HOBBIT);
            FrameBody b = new FrameBody(TITLE, THE_HOBBIT);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different bodies are not equal")
        void differentBodies() {
            FrameBody a = new FrameBody(TITLE, THE_HOBBIT);
            FrameBody b = new FrameBody(AUTHOR, THE_HOBBIT);
            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("Factories")
    class Factories {

        @Test
        @DisplayName("of() with bindings")
        void ofWithBindings() {
            Map<ItemID, BindingTarget> bindings = Map.of(
                    TARGET_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBody body = FrameBody.of(AUTHOR, THE_HOBBIT, bindings);
            assertThat(body.predicate()).isEqualTo(AUTHOR);
            assertThat(body.bindings()).hasSize(1);
        }

        @Test
        @DisplayName("of() without bindings")
        void ofWithoutBindings() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            assertThat(body.predicate()).isEqualTo(TITLE);
            assertThat(body.bindings()).isEmpty();
        }
    }
}
