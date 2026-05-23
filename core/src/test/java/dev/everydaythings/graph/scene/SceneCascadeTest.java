package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@code CONFIG[Presentation]} cascade walks via
 * {@code manifest.body().head()} from any iid up through the archetype chain.
 * Archetypes that declare their own scene short-circuit the walk; archetypes
 * that don't fall through to {@link CoreVocabulary.Archetype}'s terminal
 * default scene.
 */
@DisplayName("SceneCascade — CONFIG[Presentation] walk via body.head()")
class SceneCascadeTest {

    @Test
    @DisplayName("Archetype's own default scene is reachable directly")
    void archetypeOwnDefault() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        Body scene = SceneCascade.sceneFor(
                ItemRef.iid(CoreVocabulary.Archetype.KEY), lib);

        assertThat(scene.headRef()).isEqualTo(ItemRef.iid(SceneText.KEY));
        assertThat(readText(scene)).contains("Common Graph item");
    }

    @Test
    @DisplayName("Session's own scene short-circuits the walk before reaching Archetype")
    void sessionDeclaresItsOwnScene() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        Body scene = SceneCascade.sceneFor(ItemRef.iid(Session.KEY), lib);

        // Session declares its own scene on its archetype's record, so the
        // walk stops there rather than climbing up to Archetype's terminal.
        // The Text binding's target is a TypeRef (?-mode variable ref); the
        // resolver substitutes the actual string at render time — the cascade
        // returns the DECLARED form, with the unresolved variable in place.
        assertThat(scene.headRef()).isEqualTo(ItemRef.iid(SceneText.KEY));
        assertThat(textTarget(scene))
                .as("Session's scene references the greeting variable via a ?-mode TypeRef")
                .isInstanceOf(dev.everydaythings.graph.ref.TypeRef.class);
    }

    /** Read SceneText's Text binding target as-is (could be String, TypeRef, etc.). */
    private static Object textTarget(Body sceneTextBody) {
        return sceneTextBody.binding(CompoundKey.of(ItemRef.iid(SceneVocabulary.Text.KEY)))
                .map(Binding::target)
                .orElseThrow();
    }

    @Test
    @DisplayName("Unknown iid that has no manifest at all throws loudly")
    void unknownIidThrows() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        ItemRef ghost = ItemRef.iid("cg.test:ghost-with-no-manifest");
        assertThatThrownBy(() -> SceneCascade.sceneFor(ghost, lib))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Scene role binding");
    }

    /** Read the literal text out of a SceneText body, if present. */
    private static Optional<String> readText(Body sceneTextBody) {
        return sceneTextBody.binding(CompoundKey.of(ItemRef.iid(SceneVocabulary.Text.KEY)))
                .map(Binding::target)
                .filter(t -> t instanceof String)
                .map(String.class::cast);
    }
}
