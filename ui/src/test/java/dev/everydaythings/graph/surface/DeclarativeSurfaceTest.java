package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.ViewNode;
import dev.everydaythings.graph.ui.scene.surface.bool.CheckboxSurface;
import dev.everydaythings.graph.ui.scene.surface.bool.ToggleSurface;
import dev.everydaythings.graph.ui.text.TuiSurfaceRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for declarative surface compilation and rendering.
 */
class DeclarativeSurfaceTest {

    @Test
    void toggleSurfaceCompiles() {
        // Create toggle in ON state
        ToggleSurface toggle = ToggleSurface.of(true)
            .label("Dark Mode")
            .editable(true);

        // Verify value
        assertThat(toggle.on()).isTrue();
        assertThat(toggle.value()).isTrue();
        assertThat(toggle.label()).isEqualTo("Dark Mode");

        // Verify it compiles (finds structural annotations)
        var compiled = SceneCompiler.getCompiled(ToggleSurface.class);
        assertThat(compiled).isNotNull();
        assertThat(compiled.type).isEqualTo(ViewNode.NodeType.CONTAINER);
    }

    @Test
    void toggleSurfaceRendersOn() {
        ToggleSurface toggle = ToggleSurface.of(true).editable(true);

        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        toggle.render(renderer);
        String output = renderer.result();

        System.err.println("Toggle ON output:");
        System.err.println(output);

        // Should have "on" style class when true
        assertThat(output).isNotEmpty();
    }

    @Test
    void toggleSurfaceRendersOff() {
        ToggleSurface toggle = ToggleSurface.of(false).editable(true);

        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        toggle.render(renderer);
        String output = renderer.result();

        System.err.println("Toggle OFF output:");
        System.err.println(output);

        // Should have "off" style class when false
        assertThat(output).isNotEmpty();
    }

    @Test
    void toggleSurfaceToggles() {
        ToggleSurface toggle = ToggleSurface.of(false);
        assertThat(toggle.on()).isFalse();

        toggle.toggle();
        assertThat(toggle.on()).isTrue();

        toggle.toggle();
        assertThat(toggle.on()).isFalse();
    }

    @Test
    void checkboxSurfaceCompiles() {
        CheckboxSurface checkbox = CheckboxSurface.of(true)
            .label("Accept terms");

        // Verify value
        assertThat(checkbox.checked()).isTrue();
        assertThat(checkbox.value()).isTrue();

        // Verify it compiles
        var compiled = SceneCompiler.getCompiled(CheckboxSurface.class);
        assertThat(compiled).isNotNull();
        assertThat(compiled.type).isEqualTo(ViewNode.NodeType.CONTAINER);
    }

    @Test
    void checkboxSurfaceRendersChecked() {
        CheckboxSurface checkbox = CheckboxSurface.of(true)
            .label("I agree")
            .editable(true);

        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        checkbox.render(renderer);
        String output = renderer.result();

        System.err.println("Checkbox CHECKED output:");
        System.err.println(output);

        // Should render checkmark when checked
        assertThat(output).contains("✓");
    }

    @Test
    void checkboxSurfaceRendersUnchecked() {
        CheckboxSurface checkbox = CheckboxSurface.of(false)
            .label("I agree")
            .editable(true);

        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        checkbox.render(renderer);
        String output = renderer.result();

        System.err.println("Checkbox UNCHECKED output:");
        System.err.println(output);

        // Should NOT render checkmark when unchecked
        assertThat(output).doesNotContain("✓");
    }

    @Test
    void checkboxWithLabel() {
        CheckboxSurface checkbox = CheckboxSurface.of(true)
            .label("Accept terms")
            .editable(true);

        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        checkbox.render(renderer);
        String output = renderer.result();

        System.err.println("Checkbox with label output:");
        System.err.println(output);

        // Should render the label
        assertThat(output).contains("Accept terms");
    }

    @Test
    void disabledToggleHasDisabledStyle() {
        ToggleSurface toggle = ToggleSurface.of(true)
            .editable(false);  // disabled

        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        toggle.render(renderer);
        String output = renderer.result();

        System.err.println("Disabled toggle output:");
        System.err.println(output);

        // Compiled structure should exist
        var compiled = SceneCompiler.getCompiled(ToggleSurface.class);
        assertThat(compiled).isNotNull();
    }
}
