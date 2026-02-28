package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.ui.scene.surface.primitive.ClockFace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for @Scene.ContextMenu compilation.
 */
class SceneContextMenuTest {

    @BeforeEach
    void clearCaches() {
        SceneCompiler.clearCache();
    }

    // ==================================================================================
    // Test Fixtures
    // ==================================================================================

    /** Simple container with context menu items from a nested class. */
    @Scene.Container(direction = Scene.Direction.VERTICAL)
    static class WithContextMenu extends SceneSchema<Void> {

        @Scene.ContextMenu(label = "Action A", action = "doA", icon = "A")
        @Scene.ContextMenu(label = "Action B", action = "doB", group = "other", order = 1)
        static class Menu {}

        @Scene.Text(content = "Hello")
        static class Label {}
    }

    /** Container with conditional context menu items. */
    @Scene.Container(direction = Scene.Direction.VERTICAL)
    static class ConditionalMenu extends SceneSchema<Void> {

        @Scene.ContextMenu(label = "Show X", action = "showX", when = "value.enabled")
        @Scene.ContextMenu(label = "Hide X", action = "hideX", when = "!value.enabled")
        static class Menu {}
    }

    /** Container with no context menu. */
    @Scene.Container(direction = Scene.Direction.HORIZONTAL)
    static class NoContextMenu extends SceneSchema<Void> {

        @Scene.Text(content = "Plain")
        static class Label {}
    }

    /** Context menu with target. */
    @Scene.Container(direction = Scene.Direction.VERTICAL)
    static class TargetedMenu extends SceneSchema<Void> {

        @Scene.ContextMenu(label = "Delete", action = "delete", target = "$item.id")
        static class Menu {}
    }

    /** Multiple menu classes on same parent. */
    @Scene.Container(direction = Scene.Direction.VERTICAL)
    static class MultiMenuClasses extends SceneSchema<Void> {

        @Scene.ContextMenu(label = "Edit", action = "edit")
        static class EditMenu {}

        @Scene.ContextMenu(label = "Share", action = "share")
        static class ShareMenu {}
    }

    // ==================================================================================
    // Compilation Tests
    // ==================================================================================

    @Test
    void compile_contextMenuItemsFromNestedClass() {
        ViewNode root = SceneCompiler.getCompiled(WithContextMenu.class);

        assertThat(root).isNotNull();
        assertThat(root.contextMenu).hasSize(2);

        ContextMenuItem first = root.contextMenu.get(0);
        assertThat(first.label()).isEqualTo("Action A");
        assertThat(first.action()).isEqualTo("doA");
        assertThat(first.icon()).isEqualTo("A");
        assertThat(first.group()).isEmpty();
        assertThat(first.order()).isZero();

        ContextMenuItem second = root.contextMenu.get(1);
        assertThat(second.label()).isEqualTo("Action B");
        assertThat(second.action()).isEqualTo("doB");
        assertThat(second.group()).isEqualTo("other");
        assertThat(second.order()).isEqualTo(1);
    }

    @Test
    void compile_contextMenuPreservesWhenCondition() {
        ViewNode root = SceneCompiler.getCompiled(ConditionalMenu.class);

        assertThat(root).isNotNull();
        assertThat(root.contextMenu).hasSize(2);
        assertThat(root.contextMenu.get(0).when()).isEqualTo("value.enabled");
        assertThat(root.contextMenu.get(1).when()).isEqualTo("!value.enabled");
    }

    @Test
    void compile_noContextMenuWhenAbsent() {
        ViewNode root = SceneCompiler.getCompiled(NoContextMenu.class);

        assertThat(root).isNotNull();
        assertThat(root.contextMenu).isEmpty();
    }

    @Test
    void compile_contextMenuWithTarget() {
        ViewNode root = SceneCompiler.getCompiled(TargetedMenu.class);

        assertThat(root).isNotNull();
        assertThat(root.contextMenu).hasSize(1);
        assertThat(root.contextMenu.get(0).target()).isEqualTo("$item.id");
    }

    @Test
    void compile_multipleMenuClassesMerge() {
        ViewNode root = SceneCompiler.getCompiled(MultiMenuClasses.class);

        assertThat(root).isNotNull();
        assertThat(root.contextMenu).hasSize(2);
        assertThat(root.contextMenu).extracting(ContextMenuItem::label)
                .containsExactlyInAnyOrder("Edit", "Share");
    }

    @Test
    void compile_childrenNotAffectedByContextMenu() {
        ViewNode root = SceneCompiler.getCompiled(WithContextMenu.class);

        assertThat(root).isNotNull();
        // Menu class is not a structural child — only Label is
        assertThat(root.children).hasSize(1);
        assertThat(root.children.get(0).textContent).isEqualTo("Hello");
    }

    // ==================================================================================
    // ClockFace Integration
    // ==================================================================================

    @Test
    void clockFace_hasContextMenuItems() {
        ViewNode root = SceneCompiler.getCompiled(ClockFace.class);

        assertThat(root).isNotNull();
        assertThat(root.contextMenu).hasSize(2);

        ContextMenuItem digital = root.contextMenu.stream()
                .filter(m -> m.label().equals("Digital Mode"))
                .findFirst().orElseThrow();
        assertThat(digital.action()).isEqualTo("toggleMode");
        assertThat(digital.when()).isEqualTo("value.analog");

        ContextMenuItem analog = root.contextMenu.stream()
                .filter(m -> m.label().equals("Analog Mode"))
                .findFirst().orElseThrow();
        assertThat(analog.action()).isEqualTo("toggleMode");
        assertThat(analog.when()).isEqualTo("!value.analog");
    }
}
