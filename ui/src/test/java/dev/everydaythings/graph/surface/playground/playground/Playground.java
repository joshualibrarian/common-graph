package dev.everydaythings.graph.surface.playground;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.text.CliSurfaceRenderer;
import dev.everydaythings.graph.ui.text.TuiSurfaceRenderer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Playground for testing surfaces across all renderers.
 *
 * <h2>Usage</h2>
 * <ol>
 *   <li>Modify {@link #surface()} to return whatever you want to test</li>
 *   <li>Run the test</li>
 *   <li>See TUI/CLI output in console</li>
 * </ol>
 *
 * <h2>Two Ways to Build Surfaces</h2>
 *
 * <b>1. Compose primitives (simple, good for testing):</b>
 * <pre>{@code
 * ContainerSurface.vertical()
 *     .add(TextSurface.of("Title").style("heading"))
 *     .add(ToggleSurface.of(true, "Dark Mode"))
 *     .add(ButtonSurface.of("Submit", "submit").primary());
 * }</pre>
 *
 * <b>2. Declarative with annotations (for reusable components):</b>
 * <pre>{@code
 * @Scene.Container(direction = Scene.Direction.VERTICAL, gap = "0.5em")
 * static class MyCard extends SurfaceSchema<MyData> {
 *
 *     @Scene.Text(content = "Card Title", style = "heading")
 *     static class Title {}
 *
 *     @Scene.Text(bind = "value.name")  // binds to MyData.name()
 *     @Scene.If("value")
 *     static class Name {}
 * }
 * }</pre>
 */
@Disabled
public class Playground {

    // ==================================================================================
    // MODIFY THIS METHOD
    // ==================================================================================

    SurfaceSchema<?> surface() {
        // === OPTION 1: Compose primitives ===
//        return ContainerSurface.vertical()
//            .gap("0.5em")
//            .add(TextSurface.of("Settings").style("heading"))
//            .add(ToggleSurface.of(true, "Dark Mode"))
//            .add(ToggleSurface.of(false, "Notifications"))
//            .add(CheckboxSurface.of(true, "Remember Me"))
//            .add(ContainerSurface.horizontal()
//                .gap("0.5em")
//                .add(ButtonSurface.of("Save", "save").primary())
//                .add(ButtonSurface.of("Cancel", "cancel")));

        // === OPTION 2: Use a declarative surface ===
         return new ProfileCard().value(new Profile("Alice", "alice@example.com", true));

        // === OPTION 3: Test individual primitives ===
        // return TextSurface.of("Hello World").style("heading");
        // return ImageSurface.of("Folder");
        // return ToggleSurface.of(true, "Toggle Label");
    }

    // ==================================================================================
    // EXAMPLE: Declarative Surface (correct pattern)
    // ==================================================================================

    /** Example data record */
    record Profile(String name, String email, boolean active) {}

    /**
     * Example declarative surface.
     *
     * Key points:
     * - Extends SurfaceSchema<T> where T is your data type
     * - Uses @Scene.Container at class level
     * - Nested static classes define structure (never instantiated!)
     * - NO render() override - StructureCompiler handles it
     */
    @Scene.Container(direction = Scene.Direction.VERTICAL, style = "profile-card", gap = "0.5em")
    static class ProfileCard extends SurfaceSchema<Profile> {

        @Scene.Text(content = "Profile", style = "heading")
        static class Title {}

        @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "1em")
        @Scene.If("value")  // Only show if we have data
        static class Content {

            @Scene.Image(alt = "avatar", size = "large")
            static class Avatar {}

            @Scene.Container(direction = Scene.Direction.VERTICAL)
            static class Info {

                @Scene.Text(bind = "value.name", style = "bold")
                static class Name {}

                @Scene.Text(bind = "value.email", style = "muted")
                static class Email {}
            }
        }

        @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = "status")
        @Scene.If("value")
        static class Status {

            @Scene.Text(content = "Status: ")
            static class Label {}

            @Scene.Text(content = "Active", style = "success")
            @Scene.If("value.active")
            static class Active {}

            @Scene.Text(content = "Inactive", style = "muted")
            @Scene.If("!value.active")
            static class Inactive {}
        }
    }

    // ==================================================================================
    // THE TEST
    // ==================================================================================

    @Test
    void playground() throws Exception {
        SurfaceSchema<?> surface = surface();

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("SURFACE PLAYGROUND: " + surface.getClass().getSimpleName());
        System.out.println("=".repeat(60));

        // TUI
        System.out.println("\n-- TUI (ANSI) --");
        TuiSurfaceRenderer tui = new TuiSurfaceRenderer();
        surface.render(tui);
        System.out.println(tui.result());

        // CLI
        System.out.println("\n-- CLI (Plain) --");
        CliSurfaceRenderer cli = new CliSurfaceRenderer();
        surface.render(cli);
        System.out.println(cli.result());

        System.out.println("=".repeat(60));
    }
}
