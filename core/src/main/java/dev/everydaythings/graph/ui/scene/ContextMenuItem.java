package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
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
@Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
public class ContextMenuItem implements Canonical {

    @Canon(order = 0) private String label;
    @Canon(order = 1) private String action;
    @Canon(order = 2) private String target;
    @Canon(order = 3) private String when;
    @Canon(order = 4) private String icon;
    @Canon(order = 5) private String group;
    @Canon(order = 6) private int order;
}
