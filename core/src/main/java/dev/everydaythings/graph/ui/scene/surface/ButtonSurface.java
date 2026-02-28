package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import java.util.ArrayList;
import java.util.List;

/**
 * Clickable container that triggers an action.
 *
 * <p>ButtonSurface is a container - it can hold arbitrary content.
 * For simple text/icon buttons, use the convenience factory methods.
 *
 * <pre>{@code
 * // Simple text button
 * ButtonSurface.of("Click me", "doAction")
 *
 * // Icon button
 * ButtonSurface.icon("🗑️", "delete")
 *
 * // Rich content button
 * ButtonSurface.action("submit")
 *     .add(TextSurface.of("📤"))
 *     .add(TextSurface.of("Submit").style("bold"))
 *     .primary()
 * }</pre>
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * ┌─────────────────────┐
 * │ [icon] [text] ...   │  ← click triggers action
 * └─────────────────────┘
 * </pre>
 */
@Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"button"})
@Scene.On(event = "click", action = "$action")
@Scene.State(when = "!enabled", style = {"disabled"})
public class ButtonSurface extends ContainerSurface {

    // Children are added via add() and rendered by ContainerSurface

    /**
     * Action handle to dispatch when clicked.
     */
    @Canon(order = 10)
    private String action;

    /**
     * Whether the button is enabled.
     */
    @Canon(order = 11)
    private boolean enabled = true;

    public ButtonSurface() {}

    /**
     * Create a button with just an action (add content separately).
     */
    public static ButtonSurface action(String action) {
        ButtonSurface b = new ButtonSurface();
        b.action = action;
        return b;
    }

    /**
     * Create a simple text button.
     */
    public static ButtonSurface of(String text, String action) {
        ButtonSurface b = new ButtonSurface();
        b.action = action;
        b.add(TextSurface.of(text));
        return b;
    }

    /**
     * Create an icon-only button.
     */
    public static ButtonSurface icon(String icon, String action) {
        ButtonSurface b = new ButtonSurface();
        b.action = action;
        b.add(TextSurface.of(icon).style("icon"));
        return b;
    }

    /**
     * Create a button with icon and text.
     */
    public static ButtonSurface of(String icon, String text, String action) {
        ButtonSurface b = new ButtonSurface();
        b.action = action;
        b.add(TextSurface.of(icon).style("icon"));
        b.add(TextSurface.of(text));
        return b;
    }

    public ButtonSurface enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /** Style as primary button. */
    public ButtonSurface primary() {
        return style("primary");
    }

    /** Style as danger/destructive button. */
    public ButtonSurface danger() {
        return style("danger");
    }

    /** Style as ghost/minimal button. */
    public ButtonSurface ghost() {
        return style("ghost");
    }

    public String action() { return action; }
    public boolean enabled() { return enabled; }

    // TODO: Remove procedural render() once declarative children rendering works
    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        // Attach click handler - button is just a box with an event
        if (action != null && enabled) {
            out.event("click", action, "");
        }

        // Build styles: button base + user styles + disabled state
        List<String> buttonStyles = new ArrayList<>();
        buttonStyles.add("button");
        buttonStyles.addAll(style());
        if (!enabled) {
            buttonStyles.add("disabled");
        }

        // Render as horizontal box
        out.beginBox(Scene.Direction.HORIZONTAL, buttonStyles);
        renderChildren(out);
        out.endBox();
    }
}
