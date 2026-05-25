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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the scene cascade walks via {@code manifest.body().head()} from
 * any iid up through the archetype chain.  Tests cover both the default
 * Scene cascade and the qualifier-aware variants ({@code Scene[Handle]},
 * {@code Scene[Aura]}).  Archetypes that declare their own scene short-
 * circuit the walk; archetypes that don't fall through to
 * {@link CoreVocabulary.Archetype}'s terminal defaults.
 */
@DisplayName("SceneCascade — qualifier-aware archetype-chain walk")
class SceneCascadeTest {

    @Nested
    @DisplayName("Default Scene cascade (no qualifier)")
    class DefaultScene {

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
        @DisplayName("An iid with no own scene falls through to Archetype's default")
        void sessionFallsThroughToArchetype() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            // Session declares no Scene of its own anymore, so the cascade
            // walks Session-archetype's manifest (no match) then up to
            // Archetype (match) and returns the terminal placeholder.
            Body scene = SceneCascade.sceneFor(ItemRef.iid(Session.KEY), lib);

            assertThat(scene.headRef()).isEqualTo(ItemRef.iid(SceneText.KEY));
            assertThat(readText(scene)).contains("Common Graph item");
        }

        @Test
        @DisplayName("Unknown iid that has no manifest at all throws loudly")
        void unknownIidThrows() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            ItemRef ghost = ItemRef.iid("cg.test:ghost-with-no-manifest");
            assertThatThrownBy(() -> SceneCascade.sceneFor(ghost, lib))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Scene")
                    .hasMessageContaining("declared in archetype chain");
        }
    }

    @Nested
    @DisplayName("Scene[Handle] cascade")
    class HandleScene {

        @Test
        @DisplayName("Archetype's terminal Handle is reachable directly")
        void archetypeHandle() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            Body scene = SceneCascade.sceneFor(
                    ItemRef.iid(CoreVocabulary.Archetype.KEY), lib,
                    SceneVocabulary.Handle.KEY);

            assertThat(scene.headRef()).isEqualTo(ItemRef.iid(SceneText.KEY));
            assertThat(readText(scene)).contains("[item]");
        }

        @Test
        @DisplayName("Session falls through to Archetype's terminal Handle")
        void sessionFallsThroughForHandle() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            Body scene = SceneCascade.sceneFor(
                    ItemRef.iid(Session.KEY), lib,
                    SceneVocabulary.Handle.KEY);

            assertThat(scene.headRef()).isEqualTo(ItemRef.iid(SceneText.KEY));
            assertThat(readText(scene)).contains("[item]");
        }

        @Test
        @DisplayName("Default Scene and Handle Scene are independent on the same archetype")
        void defaultAndHandleAreIndependent() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            ItemRef archetype = ItemRef.iid(CoreVocabulary.Archetype.KEY);
            Body def    = SceneCascade.sceneFor(archetype, lib);
            Body handle = SceneCascade.sceneFor(archetype, lib, SceneVocabulary.Handle.KEY);

            assertThat(readText(def)).contains("Common Graph item");
            assertThat(readText(handle)).contains("[item]");
        }
    }

    @Nested
    @DisplayName("Scene[Aura] cascade")
    class AuraScene {

        @Test
        @DisplayName("Archetype's terminal Aura is reachable directly (empty container)")
        void archetypeAura() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            Body scene = SceneCascade.sceneFor(
                    ItemRef.iid(CoreVocabulary.Archetype.KEY), lib,
                    SceneVocabulary.Aura.KEY);

            assertThat(scene.headRef()).isEqualTo(ItemRef.iid(SceneContainer.KEY));
            assertThat(scene.bindings()).isEmpty();
        }

        @Test
        @DisplayName("Session falls through to Archetype's terminal Aura")
        void sessionFallsThroughForAura() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            Body scene = SceneCascade.sceneFor(
                    ItemRef.iid(Session.KEY), lib,
                    SceneVocabulary.Aura.KEY);

            assertThat(scene.headRef()).isEqualTo(ItemRef.iid(SceneContainer.KEY));
        }
    }

    @Nested
    @DisplayName("Error reporting")
    class Errors {

        @Test
        @DisplayName("Unknown qualifier on an item with no matching Scene throws with the qualifier named")
        void unknownQualifierMentionedInError() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            String bogus = "cg.qualifier:not-a-real-qualifier";
            assertThatThrownBy(() -> SceneCascade.sceneFor(
                    ItemRef.iid(CoreVocabulary.Archetype.KEY), lib, bogus))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Scene[" + bogus + "]");
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /** Read the literal text out of a SceneText body, if present. */
    private static Optional<String> readText(Body sceneTextBody) {
        return sceneTextBody.binding(CompoundKey.of(ItemRef.iid(SceneVocabulary.Text.KEY)))
                .map(Binding::target)
                .filter(t -> t instanceof String)
                .map(String.class::cast);
    }
}
