package dev.everydaythings.graph.ui.clock;

import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ClockFace;
import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.LayoutNode;
import dev.everydaythings.graph.ui.skia.SkiaSurfaceRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that @Scene.Repeat generates tick marks for the clock widget.
 */
class ClockRepeatTest {

    private static SurfaceSchema<ClockFace> clockSurface(ClockFace model) {
        SurfaceSchema<ClockFace> surface = new SurfaceSchema<>() {};
        surface.value(model);
        surface.structureClass(ClockFace.class);
        return surface;
    }

    @Test
    void repeatWithClockModelDirectly() {
        SurfaceSchema<ClockFace> surface = clockSurface(new ClockFace(10, 10, 30, true));

        SkiaSurfaceRenderer renderer = new SkiaSurfaceRenderer();
        surface.render(renderer);
        LayoutNode.BoxNode root = renderer.result();

        LayoutNode.BoxNode stack = (LayoutNode.BoxNode) root.children().get(0);
        System.out.println("[ClockFace] STACK children: " + stack.children().size());
        assertThat(stack.children().size()).as("12 ticks + 3 hands + 1 center dot").isEqualTo(16);
    }

    @Test
    void repeatWithFreshClockFace() {
        ClockFace clock = ClockFace.now();

        SurfaceSchema<ClockFace> surface = clockSurface(clock);

        SkiaSurfaceRenderer renderer = new SkiaSurfaceRenderer();
        surface.render(renderer);
        LayoutNode.BoxNode root = renderer.result();

        LayoutNode.BoxNode stack = (LayoutNode.BoxNode) root.children().get(0);
        System.out.println("[ClockFace.now()] STACK children: " + stack.children().size());

        for (int i = 0; i < stack.children().size(); i++) {
            LayoutNode child = stack.children().get(i);
            if (child instanceof LayoutNode.BoxNode box) {
                System.out.println("  Child " + i + ": rot=" + box.rotation()
                        + " styles=" + box.styles() + " children=" + box.children().size());
            }
        }

        assertThat(stack.children().size()).as("12 ticks + 3 hands + 1 center dot").isEqualTo(16);
    }

    /**
     * Full pipeline test: renderer -> layout engine -> check positions/sizes.
     * This reproduces what the app does at 400x400.
     */
    @Test
    void fullPipelineLayout() {
        ClockFace clock = ClockFace.now();
        SurfaceSchema<ClockFace> surface = clockSurface(clock);

        SkiaSurfaceRenderer renderer = new SkiaSurfaceRenderer();
        surface.render(renderer);
        LayoutNode.BoxNode root = renderer.result();

        // Run layout at 400x400 (typical detail pane size)
        LayoutEngine engine = new LayoutEngine((node, maxWidth) -> {
            node.measuredSize(maxWidth > 0 ? Math.min(100, maxWidth) : 100, 20);
        });
        engine.layout(root, 400, 400);

        LayoutNode.BoxNode stack = (LayoutNode.BoxNode) root.children().get(0);
        System.out.println("=== FULL PIPELINE at 400x400 ===");
        System.out.println("ROOT: " + bounds(root) + " widthSpec=" + root.widthSpec() + " heightSpec=" + root.heightSpec());
        System.out.println("STACK: " + bounds(stack) + " widthSpec=" + stack.widthSpec() + " heightSpec=" + stack.heightSpec()
                + " bg=" + stack.background() + " shapeType=" + stack.shapeType());

        for (int i = 0; i < stack.children().size(); i++) {
            LayoutNode child = stack.children().get(i);
            if (child instanceof LayoutNode.BoxNode box) {
                System.out.println("  Child[" + i + "]: " + bounds(box)
                        + " rot=" + box.rotation()
                        + " paddingSpec=" + box.paddingSpec()
                        + " pad=[" + box.paddingTop() + "," + box.paddingRight()
                        + "," + box.paddingBottom() + "," + box.paddingLeft() + "]"
                        + " styles=" + box.styles()
                        + " elev=" + box.elevation());
                // Dump child shapes
                for (int j = 0; j < box.children().size(); j++) {
                    LayoutNode grandchild = box.children().get(j);
                    if (grandchild instanceof LayoutNode.ShapeNode shape) {
                        System.out.println("    Shape[" + j + "]: " + bounds(shape)
                                + " type=" + shape.shapeType()
                                + " fill=" + shape.fill()
                                + " wSpec=" + shape.widthSpec() + " hSpec=" + shape.heightSpec()
                                + " explW=" + shape.explicitWidth() + " explH=" + shape.explicitHeight());
                    } else if (grandchild instanceof LayoutNode.BoxNode inner) {
                        System.out.println("    Box[" + j + "]: " + bounds(inner)
                                + " styles=" + inner.styles() + " children=" + inner.children().size());
                    }
                }
            }
        }

        // Verify first tick has non-zero shape
        LayoutNode.BoxNode firstTick = (LayoutNode.BoxNode) stack.children().get(0);
        assertThat(firstTick.styles()).contains("tick");
        assertThat(firstTick.width()).as("tick container width").isGreaterThan(0);
        assertThat(firstTick.height()).as("tick container height").isGreaterThan(0);

        // Verify tick mark shape has non-zero size and is positioned correctly
        LayoutNode tickChild = firstTick.children().get(0);
        assertThat(tickChild.width()).as("tick mark width").isGreaterThan(0);
        assertThat(tickChild.height()).as("tick mark height").isGreaterThan(0);
        System.out.println("\nFirst tick mark at: " + bounds(tickChild));
    }

    @Test
    void digitalModeUsesSegmentedDigits() {
        ClockFace clock = new ClockFace(9, 5, 37, false);

        assertThat(clock.digitalTime()).isEqualTo("🯰🯹:🯰🯵:🯳🯷");
    }

    @Test
    void digitalTimeFormatsAllDigits() {
        // Verify all 10 segmented digits are reachable
        ClockFace clock = new ClockFace(12, 34, 56, false);
        assertThat(clock.digitalTime()).isEqualTo("🯱🯲:🯳🯴:🯵🯶");

        clock.setTime(0, 7, 8);
        assertThat(clock.digitalTime()).isEqualTo("🯰🯰:🯰🯷:🯰🯸");

        clock.setTime(19, 0, 9);
        assertThat(clock.digitalTime()).isEqualTo("🯱🯹:🯰🯰:🯰🯹");
    }

    @Test
    void digitalTimeMidnight() {
        ClockFace clock = new ClockFace(0, 0, 0, false);
        assertThat(clock.digitalTime()).isEqualTo("🯰🯰:🯰🯰:🯰🯰");
    }

    @Test
    void digitalTimeMaxValues() {
        ClockFace clock = new ClockFace(23, 59, 59, false);
        assertThat(clock.digitalTime()).isEqualTo("🯲🯳:🯵🯹:🯵🯹");
    }

    private static String bounds(LayoutNode node) {
        return String.format("(%.1f,%.1f %.1fx%.1f)", node.x(), node.y(), node.width(), node.height());
    }
}
