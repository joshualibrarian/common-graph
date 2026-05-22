package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@code CONFIG[Presentation]} cascade walks via
 * {@code manifest.body().head()} from any iid up through the archetype chain
 * to {@link CoreVocabulary.Archetype}'s terminal default scene.
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

        assertThat(scene.headRef())
                .as("Archetype's default scene is a SceneText body")
                .isEqualTo(ItemRef.iid(SceneText.KEY));
    }

    @Test
    @DisplayName("A Session-archetype iid cascades up to Archetype's default")
    void sessionArchetypeFallsThrough() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        Body scene = SceneCascade.sceneFor(ItemRef.iid(Session.KEY), lib);

        // No Session-specific CONFIG[Presentation] declared yet, so the walk
        // climbs Session-archetype → Archetype and picks up Archetype's
        // terminal default.
        assertThat(scene.headRef()).isEqualTo(ItemRef.iid(SceneText.KEY));
    }

    @Test
    @DisplayName("Unknown iid that has no manifest at all throws loudly")
    void unknownIidThrows() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        ItemRef ghost = ItemRef.iid("cg.test:ghost-with-no-manifest");
        assertThatThrownBy(() -> SceneCascade.sceneFor(ghost, lib))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No CONFIG[Presentation]");
    }
}
