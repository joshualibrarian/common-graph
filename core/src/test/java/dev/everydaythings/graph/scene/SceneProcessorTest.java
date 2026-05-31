package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.TypeRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SceneProcessor}'s record-binding construction.
 * Each inner archetype class exercises one shape: bare scene-structure root,
 * container with children, child-ordering overrides, style declarations
 * (matchClass / matchId / matchType), and the no-scene fallthrough.
 */
@DisplayName("SceneProcessor — @Scene annotations → record bindings")
class SceneProcessorTest {

    private static final ItemRef SCENE_ROLE   = ItemRef.iid(SceneVocabulary.Scene.KEY);
    private static final ItemRef STYLE_ROLE   = ItemRef.iid(SceneVocabulary.Style.KEY);
    private static final ItemRef PATTERN_ROLE = ItemRef.iid(SceneVocabulary.Pattern.KEY);
    private static final ItemRef TEXT_ROLE    = ItemRef.iid(SceneVocabulary.Text.KEY);
    private static final ItemRef CHILDREN_ROLE = ItemRef.iid(SceneVocabulary.Children.KEY);
    private static final ItemRef CLASSES_ROLE = ItemRef.iid(SceneVocabulary.Classes.KEY);
    private static final ItemRef ID_ROLE      = ItemRef.iid(SceneVocabulary.Id.KEY);

    private static Body sceneBodyOf(Class<?> cls) {
        return SceneProcessor.sceneRecordBindingsFor(cls).stream()
                .filter(b -> SCENE_ROLE.equals(b.role()))
                .map(b -> (Body) b.target())
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("Scene root resolution")
    class RootResolution {

        @Seed.Item(key = "cg.test:simple-text-scene")
        @Scene.Text
        static class TextOnTheSeedClass {
            @Scene.Property(role = SceneVocabulary.Text.KEY)
            static String text = "hello";
        }

        @Test
        @DisplayName("Class with structure-type annotation on itself IS the scene root")
        void seedClassAsRoot() {
            List<Binding> bindings = SceneProcessor.sceneRecordBindingsFor(TextOnTheSeedClass.class);
            assertThat(bindings).hasSize(1);
            assertThat(bindings.get(0).role()).isEqualTo(SCENE_ROLE);

            Body body = (Body) bindings.get(0).target();
            assertThat(body.headRef()).isEqualTo(ItemRef.iid(SceneText.KEY));
            assertThat(textOf(body)).isEqualTo("hello");
        }

        @Seed.Item(key = "cg.test:nested-scene")
        static class HasNestedSceneClass {
            @Scene.Text
            public static class Inside {
                @Scene.Property(role = SceneVocabulary.Text.KEY)
                static String text = "nested";
            }
        }

        @Test
        @DisplayName("Single nested static structure-type class IS the scene root")
        void nestedClassAsRoot() {
            Body body = sceneBodyOf(HasNestedSceneClass.class);
            assertThat(body.headRef()).isEqualTo(ItemRef.iid(SceneText.KEY));
            assertThat(textOf(body)).isEqualTo("nested");
        }

        @Seed.Item(key = "cg.test:no-scene")
        static class NoSceneDeclared {
            // No @Scene.* anywhere.
        }

        @Test
        @DisplayName("Class with no scene declaration returns empty list")
        void noSceneDeclared() {
            assertThat(SceneProcessor.sceneRecordBindingsFor(NoSceneDeclared.class)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Container with children")
    class ContainerWithChildren {

        @Seed.Item(key = "cg.test:container-scene")
        @Scene.Container
        static class ContainerArchetype {

            @Scene.Text
            public static class First {
                @Scene.Property(role = SceneVocabulary.Text.KEY)
                static String text = "first";
            }

            @Scene.Text
            public static class Second {
                @Scene.Property(role = SceneVocabulary.Text.KEY)
                static String text = "second";
            }
        }

        @Test
        @DisplayName("Container body has one Children binding per nested scene child, indexed 0..n-1")
        void containerHasChildren() {
            Body body = sceneBodyOf(ContainerArchetype.class);

            assertThat(body.headRef()).isEqualTo(ItemRef.iid(SceneContainer.KEY));
            List<Binding> children = body.bindingsByRole(CHILDREN_ROLE);
            assertThat(children).hasSize(2);
            assertThat(children).extracting(Binding::index)
                    .containsExactlyInAnyOrder(0L, 1L);
        }

        @Test
        @DisplayName("Each child binding's target is a SceneText body with one of the declared texts")
        void childTargetsAreBodies() {
            Body body = sceneBodyOf(ContainerArchetype.class);

            List<Binding> children = body.bindingsByRole(CHILDREN_ROLE);
            assertThat(children).extracting(b -> textOf((Body) b.target()))
                    .containsExactlyInAnyOrder("first", "second");
        }
    }

    @Nested
    @DisplayName("Explicit ordering via order= override")
    class ExplicitOrdering {

        @Seed.Item(key = "cg.test:ordered-children")
        @Scene.Container
        static class WithExplicitOrder {

            @Scene.Text(order = 5)
            public static class Later {
                @Scene.Property(role = SceneVocabulary.Text.KEY)
                static String text = "late";
            }

            @Scene.Text(order = 1)
            public static class Earlier {
                @Scene.Property(role = SceneVocabulary.Text.KEY)
                static String text = "early";
            }
        }

        @Test
        @DisplayName("order= on child pins its index regardless of source order")
        void explicitOrderWins() {
            Body body = sceneBodyOf(WithExplicitOrder.class);
            List<Binding> children = body.bindingsByRole(CHILDREN_ROLE);

            assertThat(children).hasSize(2);
            assertThat(children).extracting(Binding::index)
                    .containsExactlyInAnyOrder(1L, 5L);
        }
    }

    @Nested
    @DisplayName("Style declarations")
    class StyleDeclarations {

        @Seed.Item(key = "cg.test:style-archetype")
        @Scene.Container
        static class WithStyles {

            @Scene.Style(matchClass = "muted")
            public static class MutedStyle {
                @Scene.Property(role = SceneVocabulary.Text.KEY)
                static String text = "muted-prop";
            }

            @Scene.Style(matchId = "title")
            public static class TitleStyle {
                @Scene.Property(role = SceneVocabulary.Text.KEY)
                static String text = "title-prop";
            }

            @Scene.Style(matchType = "cg.archetype:scene-text")
            public static class TextTypeStyle {
                @Scene.Property(role = SceneVocabulary.Text.KEY)
                static String text = "text-type-prop";
            }
        }

        @Test
        @DisplayName("One Style record-binding emitted per @Scene.Style class")
        void oneStylePerClass() {
            List<Binding> styles = SceneProcessor.sceneRecordBindingsFor(WithStyles.class).stream()
                    .filter(b -> STYLE_ROLE.equals(b.role()))
                    .toList();
            assertThat(styles).hasSize(3);
        }

        @Test
        @DisplayName("matchClass produces ?-mode query body with a Classes pattern binding")
        void matchClassShape() {
            Body styleBody = findStyleBodyContainingProperty(WithStyles.class, "muted-prop");
            assertThat(styleBody.headRef()).isEqualTo(ItemRef.iid(SceneVocabulary.SceneStyle.KEY));

            Body pattern = (Body) styleBody.binding(CompoundKey.of(PATTERN_ROLE))
                    .orElseThrow().target();
            assertThat(pattern.head()).isInstanceOf(TypeRef.class);   // ?-mode head
            assertThat(pattern.binding(CompoundKey.of(CLASSES_ROLE)).orElseThrow().target())
                    .isEqualTo("muted");
        }

        @Test
        @DisplayName("matchId produces ?-mode query body with an Id pattern binding")
        void matchIdShape() {
            Body styleBody = findStyleBodyContainingProperty(WithStyles.class, "title-prop");
            Body pattern = (Body) styleBody.binding(CompoundKey.of(PATTERN_ROLE))
                    .orElseThrow().target();
            assertThat(pattern.binding(CompoundKey.of(ID_ROLE)).orElseThrow().target())
                    .isEqualTo("title");
        }

        @Test
        @DisplayName("matchType encodes the matched archetype as the query head, no extra match binding")
        void matchTypeShape() {
            Body styleBody = findStyleBodyContainingProperty(WithStyles.class, "text-type-prop");
            Body pattern = (Body) styleBody.binding(CompoundKey.of(PATTERN_ROLE))
                    .orElseThrow().target();
            TypeRef head = (TypeRef) pattern.head();
            assertThat(head.iid()).isEqualTo(ItemRef.iid(SceneText.KEY));
            // No Classes or Id pattern binding for matchType-only styles.
            assertThat(pattern.binding(CompoundKey.of(CLASSES_ROLE))).isEmpty();
            assertThat(pattern.binding(CompoundKey.of(ID_ROLE))).isEmpty();
        }

        @Test
        @DisplayName("Style's @Scene.Property fields become sibling bindings to Pattern")
        void styleCarriesProperties() {
            Body styleBody = findStyleBodyContainingProperty(WithStyles.class, "muted-prop");
            assertThat(styleBody.binding(CompoundKey.of(TEXT_ROLE)).orElseThrow().target())
                    .isEqualTo("muted-prop");
        }

        /** Find a Style body whose Text property has the given value. */
        private static Body findStyleBodyContainingProperty(Class<?> cls, String expectedText) {
            return SceneProcessor.sceneRecordBindingsFor(cls).stream()
                    .filter(b -> STYLE_ROLE.equals(b.role()))
                    .map(b -> (Body) b.target())
                    .filter(body -> body.binding(CompoundKey.of(TEXT_ROLE))
                            .map(Binding::target)
                            .filter(t -> expectedText.equals(t))
                            .isPresent())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No Style body with Text=" + expectedText));
        }
    }

    /** Helper: read SceneText's Text binding target. */
    private static String textOf(Body sceneTextBody) {
        return sceneTextBody.binding(CompoundKey.of(TEXT_ROLE))
                .map(Binding::target)
                .map(String.class::cast)
                .orElseThrow();
    }
}
