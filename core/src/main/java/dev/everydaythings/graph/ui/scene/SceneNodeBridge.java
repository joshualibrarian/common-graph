package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.ui.scene.node.Body;
import dev.everydaythings.graph.ui.scene.node.Container;
import dev.everydaythings.graph.ui.scene.node.Node;
import dev.everydaythings.graph.ui.scene.node.Text;

/**
 * Temporary bridge for converting between old Node trees and SceneNode trees.
 *
 * <p>This class exists only during the migration from the old Node/LayoutNode
 * hierarchies to SceneNode. It will be deleted in Phase 5 of the refactor.
 */
public final class SceneNodeBridge {

    private SceneNodeBridge() {}

    // =================================================================================
    // Node → SceneNode
    // =================================================================================

    /**
     * Convert an old Node tree to a SceneNode tree.
     */
    public static SceneNode fromNode(Node node) {
        if (node == null) return null;

        SceneNode sn = new SceneNode();

        // Copy base Node properties
        copyBaseProperties(node, sn);

        if (node instanceof Container c) {
            sn.type(SceneNode.NodeType.CONTAINER);
            sn.layout(c.layout());
            sn.gap(c.gap());
            sn.align(c.align());
            sn.justify(c.justify());
            sn.wrap(c.wrap());
            sn.columns(c.columns());
            sn.rows(c.rows());
            sn.aspectRatio(c.aspectRatio());
            sn.repeat(c.repeat());
            if (c.childTemplate() != null) {
                sn.childTemplate(fromNode(c.childTemplate()));
            }
            if (c.children() != null) {
                for (Node child : c.children()) {
                    sn.add(fromNode(child));
                }
            }
        } else if (node instanceof Text t) {
            sn.type(SceneNode.NodeType.TEXT);
            sn.text(t.text());
            sn.format(t.format());
            sn.tokens(t.tokens());
        } else if (node instanceof Body b) {
            sn.type(SceneNode.NodeType.BODY);
            sn.shape(b.shape());
            sn.image(b.image());
            sn.model(b.model());
            sn.glyph(b.glyph());
            sn.alt(b.alt());
            sn.fill(b.fill());
            sn.stroke(b.stroke());
            sn.strokeWidth(b.strokeWidth());
            sn.radius(b.radius());
            sn.material(b.material());
            if (b.surfaces() != null) {
                for (var entry : b.surfaces().entrySet()) {
                    sn.surface(entry.getKey(), fromNode(entry.getValue()));
                }
            }
        }

        return sn;
    }

    private static void copyBaseProperties(Node src, SceneNode dst) {
        dst.id(src.id());
        if (src.classes() != null) dst.classes(src.classes());
        dst.width(src.width());
        dst.height(src.height());
        dst.margin(src.margin());
        dst.padding(src.padding());
        dst.border(src.border());
        dst.corner(src.corner());
        dst.background(src.background());
        dst.overflow(src.overflow());
        dst.fontFamily(src.fontFamily());
        dst.fontSize(src.fontSize());
        dst.fontWeight(src.fontWeight());
        dst.foreground(src.foreground());
        dst.opacity(src.opacity());
        dst.rotation(src.rotation());
        dst.scale(src.scale());
        dst.elevation(src.elevation());
        dst.position(src.posX(), src.posY(), src.posZ());
        dst.cursor(src.cursor());
        dst.capturesFocus(src.capturesFocus());
        dst.editable(src.editable());
        dst.bind(src.bind());
        dst.visible(src.visible());

        if (src.events() != null) {
            for (SceneEvent event : src.events()) {
                dst.on(event);
            }
        }

        if (src.state() != null) {
            for (Node.StateDecl decl : src.state()) {
                dst.declareState(decl.key(), decl.defaultValue());
            }
        }

        if (src.when() != null) {
            for (var entry : src.when().entrySet()) {
                for (var prop : entry.getValue().entrySet()) {
                    dst.when(entry.getKey(), prop.getKey(), prop.getValue());
                }
            }
        }
    }
}
