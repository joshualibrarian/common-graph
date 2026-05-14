package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.canonical.Order;
import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.canonical.Layout;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Compiled representation of a {@code @Scene.ContextMenu} annotation.
 *
 * <p>Stored on {@link ViewNode#contextMenu} and used by
 * renderers to display floating menus on right-click. Each item describes
 * a single menu entry: its label, the action to dispatch, optional icon,
 * grouping, and a condition for visibility.
 *
 * @see Scene.ContextMenu
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Layout(Layout.Kind.MAP)
public class ContextMenuItem implements Canonical {

    @Order(0) private String label;
    @Order(1) private String action;
    @Order(2) private String target;
    @Order(3) private String when;
    @Order(4) private String icon;
    @Order(5) private String group;
    @Order(6) private int order;
}
