package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.session.SessionVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link Window#fromBody(Body, Supplier)} builds a Window from an
 * ITEM_VIEW frame body's Theme binding, defaulting position/size/expanded
 * for the bindings whose presenter-side translators aren't built yet.
 */
@DisplayName("Window.fromBody — runtime Window from an ITEM_VIEW frame body")
class WindowFromBodyTest {

    private static final Supplier<Body> EMPTY_SCENE = () -> Body.of(
            ItemRef.iid(CoreVocabulary.Predicate.KEY), List.of());

    @Test
    @DisplayName("Reads Theme into the Window's itemRef")
    void readsTheme() {
        ItemRef target = ItemRef.iid("cg.test:viewed-item");
        Body body = itemViewBodyWithTheme(target);

        Window window = Window.fromBody(body, EMPTY_SCENE);

        assertThat(window.itemRef()).isEqualTo(target);
    }

    @Test
    @DisplayName("Defaults position, expanded, and size when no bindings present")
    void defaultsForBootstrapShape() {
        Body body = itemViewBodyWithTheme(ItemRef.iid("cg.test:viewed-item"));

        Window window = Window.fromBody(body, EMPTY_SCENE);

        assertThat(window.position()).isEqualTo(Window.Position.ORIGIN);
        assertThat(window.expanded()).isTrue();
        assertThat(window.size()).isEqualTo(Window.Size.UNBOUNDED);
    }

    @Test
    @DisplayName("Uses the provided sceneSupplier as-is")
    void preservesSceneSupplier() {
        Body body = itemViewBodyWithTheme(ItemRef.iid("cg.test:viewed-item"));

        Window window = Window.fromBody(body, EMPTY_SCENE);

        assertThat(window.sceneSupplier()).isSameAs(EMPTY_SCENE);
    }

    @Test
    @DisplayName("Throws if the body's head isn't ITEM_VIEW")
    void rejectsNonItemViewHead() {
        Body wrongHead = Body.of(
                ItemRef.iid("cg.test:not-item-view"),
                List.of(Binding.ref(
                        ItemRef.iid(ThematicRole.Theme.KEY),
                        ItemRef.iid("cg.test:whatever"))));

        assertThatThrownBy(() -> Window.fromBody(wrongHead, EMPTY_SCENE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ITEM_VIEW");
    }

    @Test
    @DisplayName("Throws if the body has no Theme binding")
    void rejectsMissingTheme() {
        Body noTheme = Body.of(
                ItemRef.iid(SessionVocabulary.ItemView.KEY),
                List.of(Binding.ref(
                        ItemRef.iid(ThematicRole.Location.KEY),
                        ItemRef.iid("cg.test:session"))));

        assertThatThrownBy(() -> Window.fromBody(noTheme, EMPTY_SCENE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Theme");
    }

    private static Body itemViewBodyWithTheme(ItemRef theme) {
        return Body.of(
                ItemRef.iid(SessionVocabulary.ItemView.KEY),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), theme),
                        Binding.ref(ItemRef.iid(ThematicRole.Location.KEY),
                                ItemRef.iid("cg.test:session"))));
    }
}
