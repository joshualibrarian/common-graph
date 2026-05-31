package dev.everydaythings.graph.text;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FrameMap#toBody()} — the bridge from a settled parse to a submittable
 * Body.  Head from the predicate, one Binding per BindingMap, nested
 * FrameMapTargets recursed into sub-Bodies.
 */
@DisplayName("FrameMap.toBody")
class FrameMapToBodyTest {

    private static final ItemRef PRED = ItemRef.fromString("cg.predicate:test-pred");
    private static final ItemRef THEME = ItemRef.iid(ThematicRole.Theme.KEY);
    private static final ItemRef GOAL = ItemRef.iid(ThematicRole.Goal.KEY);

    private static <T> Part<T> part(T value) {
        return new Part<>(value, BigDecimal.ONE, List.of());
    }

    @Test
    @DisplayName("predicate becomes the head; bindings become Bindings")
    void flatFrame() {
        ItemRef target = ItemRef.fromString("cg.archetype:thing");
        FrameMap fm = new FrameMap(
                "create thing",
                part(PRED),
                List.of(new BindingMap(part(THEME), List.of(), part(target))),
                List.of());

        Body body = fm.toBody();

        assertThat(body.headRef()).isEqualTo(PRED);
        assertThat(body.binding(CompoundKey.of(THEME)).map(Binding::target))
                .contains(target);
    }

    @Test
    @DisplayName("a literal target carries through unchanged")
    void literalTarget() {
        FrameMap fm = new FrameMap(
                "x",
                part(PRED),
                List.of(new BindingMap(part(THEME), List.of(), part(5L))),
                List.of());

        Body body = fm.toBody();
        assertThat(body.binding(CompoundKey.of(THEME)).map(Binding::target)).contains(5L);
    }

    @Test
    @DisplayName("a nested FrameMapTarget recurses into a sub-Body")
    void nestedFrame() {
        FrameMap inner = new FrameMap(
                null,
                part(ItemRef.fromString("cg.predicate:inner")),
                List.of(new BindingMap(part(GOAL), List.of(), part(3L))),
                List.of());
        FrameMap outer = new FrameMap(
                null,
                part(PRED),
                List.of(new BindingMap(part(THEME), List.of(),
                        new Part<>(new FrameMapTarget(inner), BigDecimal.ONE, List.of()))),
                List.of());

        Body body = outer.toBody();

        Object themeTarget = body.binding(CompoundKey.of(THEME)).map(Binding::target).orElseThrow();
        assertThat(themeTarget).isInstanceOf(Body.class);
        assertThat(((Body) themeTarget).headRef())
                .isEqualTo(ItemRef.fromString("cg.predicate:inner"));
    }

    @Test
    @DisplayName("no settled predicate → throws")
    void noPredicate() {
        FrameMap fm = FrameMap.empty();
        assertThatThrownBy(fm::toBody)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("predicate");
    }
}
