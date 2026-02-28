package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.item.Link;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.item.ItemModel;
import dev.everydaythings.graph.ui.scene.surface.layout.ConstraintSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SurfaceLayoutCompilerTest {

    @Scene(as = ConstraintSurface.class)
    static class TestModel {

        @Scene(id = "header")
        @Scene.Constraint(top = "0", height = "fit")
        public SurfaceSchema header() {
            return TextSurface.of("Header");
        }

        @Scene(id = "body")
        @Scene.Constraint(topTo = "header.bottom", bottom = "0")
        public SurfaceSchema body() {
            return TextSurface.of("Body");
        }
    }

    @Scene(as = ConstraintSurface.class)
    static class PlaceModel {

        @Scene(id = "header")
        @Scene.Place(top = "0", height = "fit")
        public SurfaceSchema header() {
            return TextSurface.of("Header");
        }

        @Scene(id = "body")
        @Scene.Place(topTo = "header.bottom", bottom = "0")
        public SurfaceSchema body() {
            return TextSurface.of("Body");
        }
    }


    @Test
    void compilesConstraintLayout() {
        TestModel model = new TestModel();
        SurfaceSchema result = SceneCompiler.compile(model).root();

        assertThat(result).isInstanceOf(ConstraintSurface.class);
        ConstraintSurface constraint = (ConstraintSurface) result;
        assertThat(constraint.children()).hasSize(2);

        // Check we got both elements
        var ids = constraint.children().stream()
                .map(ConstraintSurface.ConstrainedChild::id)
                .toList();
        assertThat(ids).containsExactlyInAnyOrder("header", "body");
    }

    @Test
    void compilesPlaceLayoutViaConstraintPipeline() {
        PlaceModel model = new PlaceModel();
        SurfaceSchema result = SceneCompiler.compile(model).root();

        assertThat(result).isInstanceOf(ConstraintSurface.class);
        ConstraintSurface constraint = (ConstraintSurface) result;
        assertThat(constraint.children()).hasSize(2);

        var byId = constraint.children().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ConstraintSurface.ConstrainedChild::id, c -> c));
        assertThat(byId).containsKeys("header", "body");
        assertThat(byId.get("header").constraints().top()).isEqualTo("0");
        assertThat(byId.get("header").constraints().height()).isEqualTo("fit");
        assertThat(byId.get("body").constraints().topTo()).isEqualTo("header.bottom");
        assertThat(byId.get("body").constraints().bottom()).isEqualTo("0");
    }


    @Test
    void itemModelHasCorrectAnnotations() {
        // Check that ItemModel has the correct class-level annotation
        Scene classSurface = ItemModel.class.getAnnotation(Scene.class);
        assertThat(classSurface).isNotNull();
        assertThat(classSurface.as()).isEqualTo(ConstraintSurface.class);

        // Check that methods have the required annotations
        var methodCount = 0;
        for (var method : ItemModel.class.getDeclaredMethods()) {
            Scene surface = method.getAnnotation(Scene.class);
            Scene.Constraint constraint = method.getAnnotation(Scene.Constraint.class);

            if (surface != null && constraint != null) {
                methodCount++;
                System.err.println("Found annotated method: " + method.getName() +
                    " id=" + surface.id());
            }
        }

        System.err.println("Total annotated methods: " + methodCount);
        assertThat(methodCount).isEqualTo(4);  // header, tree, detail, prompt
    }

    @Test
    void itemModelCompilesWithMockResolver() {
        // Create a mock Item for testing
        ItemID testIid = ItemID.random();
        Link root = Link.of(testIid);

        // Create ItemModel with a resolver that returns empty
        // This tests that even with no resolved items, the structure is built
        ItemModel itemModel = new ItemModel(root, iid -> Optional.empty());

        // Compile to surface
        SurfaceSchema surface = itemModel.toSurface();

        assertThat(surface).isInstanceOf(ConstraintSurface.class);
        ConstraintSurface constraint = (ConstraintSurface) surface;

        // Log what was compiled
        System.err.println("=== ItemModel compiled to ConstraintSurface ===");
        System.err.println("Number of children: " + constraint.children().size());

        for (var child : constraint.children()) {
            System.err.println("  Child id='" + child.id() + "' surface=" +
                (child.surface() != null ? child.surface().getClass().getSimpleName() : "null"));
        }

        // With an empty resolver, header/detail return null so only prompt renders
        assertThat(constraint.children()).isNotEmpty();
        var ids = constraint.children().stream()
            .map(ConstraintSurface.ConstrainedChild::id)
            .toList();
        assertThat(ids).contains("prompt");
    }
}
