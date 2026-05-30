package dev.everydaythings.graph.item;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.ref.TypeRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ItemSketch} — the transient create-assembly buffer.
 *
 * <p>These exercise the sketch in isolation against a hand-built archetype
 * manifest body, with no librarian.  An archetype manifest carries its EXPECTS
 * as {@code !}-roled ({@link SchemaRef}) bindings; the sketch reads those as the
 * roles instances must fill.
 */
@DisplayName("ItemSketch")
class ItemSketchTest {

    private static final ItemRef ARCHETYPE = ItemRef.fromString("cg.archetype:gadget-test");
    private static final ItemRef COLOR_ROLE = ItemRef.fromString("cg.role:color-test");
    private static final ItemRef SIZE_ROLE = ItemRef.fromString("cg.role:size-test");

    /** A manifest body expecting one role (!Color), plus a literal-roled binding that is NOT an expectation. */
    private static Body manifestExpecting(ItemRef... expectedRoles) {
        java.util.List<Binding> bindings = new java.util.ArrayList<>();
        // A literal-roled binding (the archetype's own metadata) — must be ignored.
        bindings.add(Binding.ref(ItemRef.fromString("cg.structural:item-id"),
                ItemRef.fromString("the-archetype")));
        for (ItemRef role : expectedRoles) {
            bindings.add(new Binding(SchemaRef.of(role), TypeRef.iid("cg.value:some-type")));
        }
        return Body.of(ItemRef.of(ARCHETYPE), bindings);
    }

    @Nested
    @DisplayName("expected roles")
    class Expected {

        @Test
        @DisplayName("reads !-roled bindings as expected roles, ignoring literal-roled ones")
        void readsExpected() {
            ItemSketch sketch = ItemSketch.forArchetype(ARCHETYPE, manifestExpecting(COLOR_ROLE));
            assertThat(sketch.expectedRoles()).containsExactly(COLOR_ROLE);
        }

        @Test
        @DisplayName("null manifest yields no expectations")
        void nullManifest() {
            ItemSketch sketch = ItemSketch.forArchetype(ARCHETYPE, null);
            assertThat(sketch.expectedRoles()).isEmpty();
            assertThat(sketch.isComplete()).isTrue();
        }

        @Test
        @DisplayName("multiple expected roles are all read")
        void multipleExpected() {
            ItemSketch sketch = ItemSketch.forArchetype(ARCHETYPE, manifestExpecting(COLOR_ROLE, SIZE_ROLE));
            assertThat(sketch.expectedRoles()).containsExactly(COLOR_ROLE, SIZE_ROLE);
        }
    }

    @Nested
    @DisplayName("completeness")
    class Completeness {

        @Test
        @DisplayName("an unfilled expected role makes the sketch incomplete")
        void incompleteWhenUnfilled() {
            ItemSketch sketch = ItemSketch.forArchetype(ARCHETYPE, manifestExpecting(COLOR_ROLE));
            assertThat(sketch.isComplete()).isFalse();
            assertThat(sketch.unfilledRoles()).containsExactly(COLOR_ROLE);
        }

        @Test
        @DisplayName("filling the expected role completes the sketch")
        void completeWhenFilled() {
            ItemSketch sketch = ItemSketch.forArchetype(ARCHETYPE, manifestExpecting(COLOR_ROLE));
            sketch.fill(COLOR_ROLE, "red");
            assertThat(sketch.isComplete()).isTrue();
            assertThat(sketch.unfilledRoles()).isEmpty();
        }

        @Test
        @DisplayName("partial fill leaves the remaining role unfilled")
        void partialFill() {
            ItemSketch sketch = ItemSketch.forArchetype(ARCHETYPE, manifestExpecting(COLOR_ROLE, SIZE_ROLE));
            sketch.fill(COLOR_ROLE, "red");
            assertThat(sketch.isComplete()).isFalse();
            assertThat(sketch.unfilledRoles()).containsExactly(SIZE_ROLE);
        }
    }

    @Nested
    @DisplayName("bindings")
    class Bindings {

        @Test
        @DisplayName("filled values surface as content bindings")
        void filledBecomeBindings() {
            ItemSketch sketch = ItemSketch.forArchetype(ARCHETYPE, manifestExpecting(COLOR_ROLE));
            sketch.fill(COLOR_ROLE, "red");

            List<Binding> bindings = sketch.bindings();
            assertThat(bindings).hasSize(1);
            assertThat(bindings.get(0).role()).isEqualTo(COLOR_ROLE);
            assertThat(bindings.get(0).target()).isEqualTo("red");
        }

        @Test
        @DisplayName("a non-expected fill carries forward without affecting completeness")
        void extraFillCarriesForward() {
            ItemSketch sketch = ItemSketch.forArchetype(ARCHETYPE, manifestExpecting(COLOR_ROLE));
            sketch.fill(COLOR_ROLE, "red");
            sketch.fill(ItemRef.fromString("cg.role:nickname-test"), "speedy");

            assertThat(sketch.isComplete()).isTrue();           // extra doesn't break it
            assertThat(sketch.bindings()).hasSize(2);           // but it carries forward
        }

        @Test
        @DisplayName("re-filling a role replaces the prior value")
        void refillReplaces() {
            ItemSketch sketch = ItemSketch.forArchetype(ARCHETYPE, manifestExpecting(COLOR_ROLE));
            sketch.fill(COLOR_ROLE, "red").fill(COLOR_ROLE, "blue");

            assertThat(sketch.bindings()).hasSize(1);
            assertThat(sketch.bindings().get(0).target()).isEqualTo("blue");
        }
    }
}
