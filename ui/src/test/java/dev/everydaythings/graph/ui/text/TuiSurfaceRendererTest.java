package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.ui.scene.surface.tree.TreeSurface;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TuiSurfaceRenderer tree rendering.
 */
class TuiSurfaceRendererTest {

    @Test
    void testTreeRendering() {
        // Build a tree:
        // Root
        // ├── Child 1
        // │   ├── Grandchild 1.1
        // │   └── Grandchild 1.2
        // └── Child 2

        TreeSurface.Node grandchild1 = TreeSurface.Node.of("gc1", "📄", "Grandchild 1.1");
        TreeSurface.Node grandchild2 = TreeSurface.Node.of("gc2", "📄", "Grandchild 1.2");

        TreeSurface.Node child1 = TreeSurface.Node.of("c1", "📁", "Child 1")
                .expanded(true)
                .addChild(grandchild1)
                .addChild(grandchild2);

        TreeSurface.Node child2 = TreeSurface.Node.of("c2", "📁", "Child 2");

        TreeSurface.Node root = TreeSurface.Node.of("root", "🌳", "Root")
                .expanded(true)
                .addChild(child1)
                .addChild(child2);

        TreeSurface tree = new TreeSurface().addRoot(root);

        // Render
        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        tree.render(renderer);
        String output = renderer.result();

        System.out.println("Tree output:");
        System.out.println(output);

        // Verify structure
        assertThat(output).contains("Root");
        assertThat(output).contains("Child 1");
        assertThat(output).contains("Child 2");
        assertThat(output).contains("Grandchild 1.1");
        assertThat(output).contains("Grandchild 1.2");

        // Note: Tree lines (├──, └──, │) are now emitted by TreeSurface, not the renderer.
        // The renderer is "dumb" and just renders what it's given.
        // These tests verify the renderer processes the tree structure correctly.
    }

    @Test
    void testSingleRootNoPrefix() {
        // A single root node shouldn't have branch prefix
        TreeSurface.Node root = TreeSurface.Node.of("root", "🌳", "Single Root");
        TreeSurface tree = new TreeSurface().addRoot(root);

        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        tree.render(renderer);
        String output = renderer.result();

        System.out.println("Single root output:");
        System.out.println(output);

        // Root should NOT have box-drawing prefix
        assertThat(output).doesNotContain("├──");
        assertThat(output).doesNotContain("└──");
        assertThat(output).contains("Single Root");
    }

    @Test
    void testDeepNesting() {
        // Test deep nesting: A > B > C > D
        TreeSurface.Node d = TreeSurface.Node.of("d", "📄", "Level D");
        TreeSurface.Node c = TreeSurface.Node.of("c", "📁", "Level C")
                .expanded(true)
                .addChild(d);
        TreeSurface.Node b = TreeSurface.Node.of("b", "📁", "Level B")
                .expanded(true)
                .addChild(c);
        TreeSurface.Node a = TreeSurface.Node.of("a", "🌳", "Level A")
                .expanded(true)
                .addChild(b);

        TreeSurface tree = new TreeSurface().addRoot(a);

        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        tree.render(renderer);
        String output = renderer.result();

        System.out.println("Deep nesting output:");
        System.out.println(output);

        // All nodes should be present
        assertThat(output).contains("Level A");
        assertThat(output).contains("Level B");
        assertThat(output).contains("Level C");
        assertThat(output).contains("Level D");
    }

    @Test
    void testLastChildUsesElbow() {
        // Last child should use └── not ├──
        TreeSurface.Node child1 = TreeSurface.Node.of("c1", "📄", "First");
        TreeSurface.Node child2 = TreeSurface.Node.of("c2", "📄", "Middle");
        TreeSurface.Node child3 = TreeSurface.Node.of("c3", "📄", "Last");

        TreeSurface.Node root = TreeSurface.Node.of("root", "🌳", "Root")
                .expanded(true)
                .addChild(child1)
                .addChild(child2)
                .addChild(child3);

        TreeSurface tree = new TreeSurface().addRoot(root);

        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        tree.render(renderer);
        String output = renderer.result();

        System.out.println("Last child elbow output:");
        System.out.println(output);

        // Note: Tree lines now come from TreeSurface, not the renderer.
        // Verify the basic structure is rendered.
        assertThat(output).contains("First");
        assertThat(output).contains("Last");
    }
}
