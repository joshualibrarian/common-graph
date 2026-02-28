package dev.everydaythings.graph.ui.scene.surface.primitive;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Progress bar composite widget.
 *
 * <p>Renders a horizontal track with a colored fill portion whose width
 * is computed dynamically from the bound value (0.0 to 1.0). A proper
 * DSL citizen — Skia paints colored rectangles, TUI renders proportional
 * box-drawing characters.
 *
 * <p>The fill width is computed in {@link #render} and passed as a percentage
 * string to {@link SurfaceRenderer#beginBox}. The LayoutEngine resolves it
 * as a fraction of the track container's width.
 *
 * @see SurfaceSchema
 */
@NoArgsConstructor
@Canonical.Canonization
public class ProgressBarSurface extends SurfaceSchema<Double> {

    @Canon(order = 10) private String color = "#A6E3A1";
    @Canon(order = 11) private String trackColor = "#313244";
    @Canon(order = 12) private String barLabel;

    private ProgressBarSurface(double value, String color, String barLabel) {
        this.value = value;
        this.color = color;
        this.barLabel = barLabel;
    }

    // ==================================================================================
    // Factory methods
    // ==================================================================================

    public static ProgressBarSurface of(double value) {
        return new ProgressBarSurface(value, "#A6E3A1", null);
    }

    public static ProgressBarSurface of(double value, String color) {
        return new ProgressBarSurface(value, color, null);
    }

    public static ProgressBarSurface of(double value, String color, String barLabel) {
        return new ProgressBarSurface(value, color, barLabel);
    }

    // ==================================================================================
    // Rendering
    // ==================================================================================

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        double ratio = value != null ? Math.clamp(value, 0.0, 1.0) : 0.0;
        String fillWidth = String.format("%.0f%%", ratio * 100);

        // Track (background bar with pill shape)
        out.shape("rectangle", "pill", trackColor, "", "", "");
        out.beginBox(Scene.Direction.HORIZONTAL, List.of("progress-track"),
                BoxBorder.NONE, trackColor, "100%", "0.75em", "");

        // Fill (colored portion — width is the computed percentage)
        out.shape("rectangle", "pill", color, "", "", "");
        out.beginBox(Scene.Direction.HORIZONTAL, List.of("progress-fill"),
                BoxBorder.NONE, color, fillWidth, "100%", "");
        out.endBox();

        out.endBox();

        // Optional label below the bar
        if (barLabel != null) {
            out.text(barLabel, List.of("progress-label", "monospace", "small"));
        }
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    public String color() { return color; }
    public String trackColor() { return trackColor; }
    public String barLabel() { return barLabel; }

    public ProgressBarSurface color(String color) {
        this.color = color;
        return this;
    }

    public ProgressBarSurface trackColor(String trackColor) {
        this.trackColor = trackColor;
        return this;
    }

    public ProgressBarSurface barLabel(String barLabel) {
        this.barLabel = barLabel;
        return this;
    }
}
