package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.scene.SceneContainer;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.VariableResolver;
import dev.everydaythings.graph.scene.Viewport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Presenter — Variable substitution on scene trees")
class PresenterTest {

    private final Viewport viewport = new Viewport(80, 24);

    @Nested
    @DisplayName("Variable substitution")
    class VariableSubstitution {

        @Test
        @DisplayName("SceneText with CurrentTime reference is substituted to the resolved value")
        void substitutesCurrentTimeInText() {
            ItemRef currentTimeRef = ItemRef.iid(CoreVocabulary.CurrentTime.KEY);
            Body unresolved = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.literal(
                            ItemRef.iid(SceneVocabulary.Text.KEY),
                            currentTimeRef)));

            VariableResolver resolver = ref ->
                    currentTimeRef.equals(ref) ? Optional.of("12:34:56") : Optional.empty();

            Presenter presenter = new Presenter(viewport, resolver);
            SceneNode presented = presenter.presentNode(unresolved);

            assertThat(presented).isInstanceOf(SceneText.class);
            String textTarget = findBindingTarget(presented, SceneVocabulary.Text.KEY, String.class);
            assertThat(textTarget).isEqualTo("12:34:56");
        }

        @Test
        @DisplayName("Variable substitution recurses into Container children")
        void substitutesInsideContainerChildren() {
            ItemRef currentTimeRef = ItemRef.iid(CoreVocabulary.CurrentTime.KEY);
            Body clockText = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.literal(
                            ItemRef.iid(SceneVocabulary.Text.KEY),
                            currentTimeRef)));
            Body container = Body.of(
                    ItemRef.iid(SceneContainer.KEY),
                    List.of(new Binding(
                            ItemRef.iid(SceneVocabulary.Children.KEY),
                            List.of(), clockText, 0L)));

            Map<ItemRef, Object> values = Map.of(currentTimeRef, "23:00:00");
            VariableResolver resolver = ref -> Optional.ofNullable(values.get(ref));
            Presenter presenter = new Presenter(viewport, resolver);
            SceneNode presented = presenter.presentNode(container);

            assertThat(presented).isInstanceOf(SceneContainer.class);
            SceneContainer presentedContainer = (SceneContainer) presented;

            Body childBody = findChildBody(presentedContainer);
            String childText = findBindingTarget(SceneNode.from(childBody),
                    SceneVocabulary.Text.KEY, String.class);
            assertThat(childText).isEqualTo("23:00:00");
        }

        @Test
        @DisplayName("Variable not known to the resolver passes through unchanged")
        void unknownVariablePassesThrough() {
            ItemRef unknownRef = ItemRef.iid(CoreVocabulary.CurrentTime.KEY);
            Body unresolved = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.literal(
                            ItemRef.iid(SceneVocabulary.Text.KEY),
                            unknownRef)));

            Presenter presenter = new Presenter(viewport, VariableResolver.NONE);
            Body presented = presenter.present(unresolved);

            ItemRef target = (ItemRef) presented.bindings().stream()
                    .filter(b -> ItemRef.iid(SceneVocabulary.Text.KEY).equals(b.role()))
                    .findFirst().orElseThrow().target();
            assertThat(target).isEqualTo(unknownRef);
        }
    }

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("Tree with no Variable references comes through with the same data")
        void treeWithoutVariablesIsUnchanged() {
            Body original = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.literal(
                            ItemRef.iid(SceneVocabulary.Text.KEY),
                            "static text")));

            Presenter presenter = new Presenter(viewport, VariableResolver.NONE);
            SceneNode presented = presenter.presentNode(original);

            String target = findBindingTarget(presented, SceneVocabulary.Text.KEY, String.class);
            assertThat(target).isEqualTo("static text");
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private <T> T findBindingTarget(SceneNode node, String roleKey, Class<T> type) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : node.bindings()) {
            if (role.equals(b.role()) && type.isInstance(b.target())) {
                return type.cast(b.target());
            }
        }
        throw new AssertionError(
                "No binding found on node for role " + roleKey + " with target type " + type.getSimpleName());
    }

    private Body findChildBody(SceneContainer container) {
        ItemRef childrenRole = ItemRef.iid(SceneVocabulary.Children.KEY);
        for (Binding b : container.bindings()) {
            if (childrenRole.equals(b.role()) && b.target() instanceof Body body) {
                return body;
            }
        }
        throw new AssertionError("No child body found on container");
    }
}
