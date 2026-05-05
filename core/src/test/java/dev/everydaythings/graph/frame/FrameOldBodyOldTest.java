package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FrameBody")
class FrameOldBodyOldTest {

    static final ItemID TITLE = ItemID.fromString("cg:pred/title");
    static final ItemID AUTHOR = ItemID.fromString("cg:pred/author");
    static final ItemID THE_HOBBIT = ItemID.fromString("cg:book/the-hobbit");
    static final ItemID TOLKIEN = ItemID.fromString("cg:person/tolkien");
    static final ItemID THEME_ROLE = ItemID.fromString("cg.role:theme");
    static final ItemID GOAL_ROLE = ItemID.fromString("cg.role:goal");

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("minimal body with predicate and theme")
        void minimalBody() {
            FrameBodyOld body = new FrameBodyOld(TITLE, THE_HOBBIT);
            assertThat(body.predicate()).isEqualTo(TITLE);
            assertThat(body.homeId()).isEqualTo(THE_HOBBIT);
            // THEME is now a regular binding
            assertThat(body.frameBindings()).hasSize(1);
        }

        @Test
        @DisplayName("body with bindings")
        void withBindings() {
            Map<ItemID, BindingTarget> bindings = Map.of(
                    GOAL_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBodyOld body = new FrameBodyOld(AUTHOR, THE_HOBBIT, bindings);
            assertThat(body.predicate()).isEqualTo(AUTHOR);
            assertThat(body.homeId()).isEqualTo(THE_HOBBIT);
            // THEME binding + GOAL binding
            assertThat(body.frameBindings()).hasSize(2);
            assertThat(body.bindings().get(GOAL_ROLE))
                    .isInstanceOf(BindingTarget.IidTarget.class);
        }

        @Test
        @DisplayName("primary constructor with predicate and bindings only")
        void primaryConstructor() {
            List<Binding> bindings = List.of(
                    FrameBodyOld.homeBinding(THE_HOBBIT),
                    new Binding(GOAL_ROLE, BindingTarget.iid(TOLKIEN))
            );
            FrameBodyOld body = new FrameBodyOld(AUTHOR, bindings);
            assertThat(body.predicate()).isEqualTo(AUTHOR);
            assertThat(body.homeId()).isEqualTo(THE_HOBBIT);
            assertThat(body.frameBindings()).hasSize(2);
        }

        @Test
        @DisplayName("null predicate rejected")
        void nullPredicate() {
            assertThatThrownBy(() -> new FrameBodyOld(null, THE_HOBBIT))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null theme rejected")
        void nullTheme() {
            assertThatThrownBy(() -> new FrameBodyOld(TITLE, (ItemID) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null bindings treated as empty")
        void nullBindings() {
            FrameBodyOld body = new FrameBodyOld(TITLE, THE_HOBBIT, (Map<ItemID, BindingTarget>) null);
            // Still has THEME binding from convenience constructor
            assertThat(body.frameBindings()).hasSize(1);
            assertThat(body.homeId()).isEqualTo(THE_HOBBIT);
        }
    }

    @Nested
    @DisplayName("Home binding")
    class HomeBinding {

        @Test
        @DisplayName("home() returns THEME binding")
        void homeReturnsThemeBinding() {
            FrameBodyOld body = new FrameBodyOld(TITLE, THE_HOBBIT);
            Binding home = body.home();
            assertThat(home).isNotNull();
            assertThat(home.targetId()).isEqualTo(THE_HOBBIT);
        }

        @Test
        @DisplayName("homeId() returns owning item IID")
        void homeIdReturnsIid() {
            FrameBodyOld body = new FrameBodyOld(TITLE, THE_HOBBIT);
            assertThat(body.homeId()).isEqualTo(THE_HOBBIT);
        }

        @Test
        @DisplayName("homeBinding() creates THEME binding")
        void homeBindingFactory() {
            Binding b = FrameBodyOld.homeBinding(THE_HOBBIT);
            assertThat(b.targetId()).isEqualTo(THE_HOBBIT);
            assertThat(b.identity()).isTrue();
        }

        @Test
        @DisplayName("homeId() returns the owning item's IID")
        void homeIdReturnsOwner() {
            FrameBodyOld body = new FrameBodyOld(TITLE, THE_HOBBIT);
            assertThat(body.homeId()).isEqualTo(THE_HOBBIT);
        }
    }

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("hash is deterministic")
        void deterministicHash() {
            FrameBodyOld a = new FrameBodyOld(TITLE, THE_HOBBIT);
            FrameBodyOld b = new FrameBodyOld(TITLE, THE_HOBBIT);
            assertThat(a.hash()).isEqualTo(b.hash());
        }

        @Test
        @DisplayName("different predicates produce different hashes")
        void differentPredicates() {
            FrameBodyOld a = new FrameBodyOld(TITLE, THE_HOBBIT);
            FrameBodyOld b = new FrameBodyOld(AUTHOR, THE_HOBBIT);
            assertThat(a.hash()).isNotEqualTo(b.hash());
        }

        @Test
        @DisplayName("different themes produce different hashes")
        void differentThemes() {
            FrameBodyOld a = new FrameBodyOld(TITLE, THE_HOBBIT);
            FrameBodyOld b = new FrameBodyOld(TITLE, TOLKIEN);
            assertThat(a.hash()).isNotEqualTo(b.hash());
        }

        @Test
        @DisplayName("same assertion from different callers = same hash")
        void sameAssertionSameHash() {
            Map<ItemID, BindingTarget> bindings = Map.of(
                    GOAL_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBodyOld alice = FrameBodyOld.of(AUTHOR, THE_HOBBIT, bindings);
            FrameBodyOld bob = FrameBodyOld.of(AUTHOR, THE_HOBBIT, bindings);
            assertThat(alice.hash()).isEqualTo(bob.hash());
            assertThat(alice.bodyBytes()).isEqualTo(bob.bodyBytes());
        }

        @Test
        @DisplayName("hash is a ContentID")
        void hashIsContentID() {
            FrameBodyOld body = new FrameBodyOld(TITLE, THE_HOBBIT);
            ContentID hash = body.hash();
            assertThat(hash).isNotNull();
            assertThat(hash.encodeBinary()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("CBOR Round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("minimal body round-trips via new 2-element format")
        void minimalRoundTrip() {
            FrameBodyOld original = new FrameBodyOld(TITLE, THE_HOBBIT);
            byte[] bytes = original.encodeBinary(Canonical.Scope.BODY);
            FrameBodyOld decoded = Canonical.decodeBinary(bytes, FrameBodyOld.class, Canonical.Scope.BODY);
            assertThat(decoded.predicate()).isEqualTo(original.predicate());
            assertThat(decoded.homeId()).isEqualTo(original.homeId());
        }

        @Test
        @DisplayName("body with bindings round-trips")
        void bindingsRoundTrip() {
            Map<ItemID, BindingTarget> bindings = Map.of(
                    GOAL_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBodyOld original = new FrameBodyOld(AUTHOR, THE_HOBBIT, bindings);
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            FrameBodyOld decoded = Canonical.decodeBinary(bytes, FrameBodyOld.class, Canonical.Scope.RECORD);
            assertThat(decoded.predicate()).isEqualTo(original.predicate());
            assertThat(decoded.homeId()).isEqualTo(original.homeId());
            // THEME binding + GOAL binding
            assertThat(decoded.frameBindings()).hasSize(2);
        }

        @Test
        @DisplayName("round-tripped body produces same hash")
        void roundTripPreservesHash() {
            Map<ItemID, BindingTarget> bindings = Map.of(
                    GOAL_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBodyOld original = new FrameBodyOld(AUTHOR, THE_HOBBIT, bindings);
            byte[] bytes = original.encodeBinary(Canonical.Scope.BODY);
            FrameBodyOld decoded = Canonical.decodeBinary(bytes, FrameBodyOld.class, Canonical.Scope.BODY);
            assertThat(decoded.hash()).isEqualTo(original.hash());
        }

        @Test
        @DisplayName("primary constructor body round-trips")
        void primaryConstructorRoundTrip() {
            List<Binding> bindings = List.of(
                    FrameBodyOld.homeBinding(THE_HOBBIT),
                    new Binding(GOAL_ROLE, BindingTarget.iid(TOLKIEN))
            );
            FrameBodyOld original = new FrameBodyOld(AUTHOR, bindings);
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            FrameBodyOld decoded = Canonical.decodeBinary(bytes, FrameBodyOld.class, Canonical.Scope.RECORD);
            assertThat(decoded.predicate()).isEqualTo(AUTHOR);
            assertThat(decoded.homeId()).isEqualTo(THE_HOBBIT);
            assertThat(decoded.frameBindings()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equal bodies are equal")
        void equalBodies() {
            FrameBodyOld a = new FrameBodyOld(TITLE, THE_HOBBIT);
            FrameBodyOld b = new FrameBodyOld(TITLE, THE_HOBBIT);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different bodies are not equal")
        void differentBodies() {
            FrameBodyOld a = new FrameBodyOld(TITLE, THE_HOBBIT);
            FrameBodyOld b = new FrameBodyOld(AUTHOR, THE_HOBBIT);
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
                    GOAL_ROLE, BindingTarget.iid(TOLKIEN)
            );
            FrameBodyOld body = FrameBodyOld.of(AUTHOR, THE_HOBBIT, bindings);
            assertThat(body.predicate()).isEqualTo(AUTHOR);
            assertThat(body.homeId()).isEqualTo(THE_HOBBIT);
        }

        @Test
        @DisplayName("of() without bindings")
        void ofWithoutBindings() {
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            assertThat(body.predicate()).isEqualTo(TITLE);
            assertThat(body.homeId()).isEqualTo(THE_HOBBIT);
        }
    }
}
