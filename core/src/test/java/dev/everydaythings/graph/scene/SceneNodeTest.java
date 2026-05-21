package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.item.BodyBinder;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.quality.LayoutVocabulary;
import dev.everydaythings.graph.quality.SpatialVocabulary;
import dev.everydaythings.graph.quality.VisualVocabulary;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import dev.everydaythings.graph.value.Color;
import dev.everydaythings.graph.value.Length;
import dev.everydaythings.graph.value.Numeric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SceneNode — abstract value-archetype.  Verifies the symmetric
 * {@code @Seed.Property} pattern: instance fields produce EXPECTS on
 * the archetype's seed manifest (type derived from field type) AND
 * populate runtime values at construction via {@link BodyBinder}.
 */
class SceneNodeTest {

    @Nested
    @DisplayName("Seed: instance-field EXPECTS")
    class InstanceFieldExpects {

        @Test
        @DisplayName("SceneNode's manifest has EXPECTS bindings derived from its instance fields")
        void manifestHasExpectsFromInstanceFields() {
            Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
            librarian.bootstrap();

            ItemRef sceneNodeIid = ItemRef.fromString(SceneNode.KEY);
            List<DatumRef> manifestCids = librarian.library().manifestCidsForItem(sceneNodeIid);
            assertThat(manifestCids).isNotEmpty();

            Manifest m = librarian.fetchManifest(manifestCids.get(0)).orElseThrow();

            assertExpects(m, VisualVocabulary.Foreground.KEY,  Color.KEY);
            assertExpects(m, VisualVocabulary.Background.KEY,  Color.KEY);
            assertExpects(m, SpatialVocabulary.Width.KEY,      Length.KEY);
            assertExpects(m, SpatialVocabulary.Height.KEY,     Length.KEY);
            assertExpects(m, SpatialVocabulary.MinWidth.KEY,   Length.KEY);
            assertExpects(m, SpatialVocabulary.MaxWidth.KEY,   Length.KEY);
            assertExpects(m, SpatialVocabulary.MinHeight.KEY,  Length.KEY);
            assertExpects(m, SpatialVocabulary.MaxHeight.KEY,  Length.KEY);
            assertExpects(m, LayoutVocabulary.Gap.KEY,         Length.KEY);
            assertExpects(m, SpatialVocabulary.Elevation.KEY,  Length.KEY);
            assertExpects(m, VisualVocabulary.Opacity.KEY,     Numeric.KEY);
        }

        private static void assertExpects(Manifest m, String roleKey, String expectedTypeKey) {
            SchemaRef expectedRole = SchemaRef.fromString(roleKey);
            TypeRef expectedTypeRef = TypeRef.iid(expectedTypeKey);
            boolean found = m.body().bindings().stream().anyMatch(b ->
                    expectedRole.equals(b.role()) && expectedTypeRef.equals(b.target()));
            assertThat(found)
                    .as("SceneNode manifest should have EXPECTS {!%s → ?%s}",
                            roleKey, expectedTypeKey)
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Bind: BodyBinder populates instance fields")
    class BindAtConstruction {

        /** Minimal concrete SceneNode just for testing. */
        static final class TestNode extends SceneNode {
            static final String KEY = "cg.test:scene-node-test";
            TestNode(Body body) {
                super(ItemRef.iid(KEY), body.bindings());
                BodyBinder.bind(this, body);
            }
        }

        @Test
        @DisplayName("a body's Width / Background bindings populate the inherited fields")
        void widthAndBackgroundPopulated() {
            Color red = Color.rgb(255, 0, 0);
            Length width = Length.pixels(100);

            Body body = Body.of(ItemRef.iid(TestNode.KEY), List.of(
                    Binding.literal(ItemRef.iid(SpatialVocabulary.Width.KEY), width),
                    Binding.literal(ItemRef.iid(VisualVocabulary.Background.KEY), red)));

            TestNode node = new TestNode(body);

            assertThat(node.width).isEqualTo(width);
            assertThat(node.background).isEqualTo(red);
            assertThat(node.height).isNull();  // absent binding → null
            assertThat(node.foreground).isNull();
        }

        @Test
        @DisplayName("missing bindings leave fields at their declared defaults (null)")
        void missingBindingsLeaveDefaults() {
            Body empty = Body.of(ItemRef.iid(TestNode.KEY), List.of());
            TestNode node = new TestNode(empty);
            assertThat(node.width).isNull();
            assertThat(node.height).isNull();
            assertThat(node.background).isNull();
            assertThat(node.foreground).isNull();
            assertThat(node.opacity).isNull();
        }
    }
}
