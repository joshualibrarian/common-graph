package dev.everydaythings.graph.ui.scene.node;

import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Migration bridge: embeds a legacy {@link SurfaceSchema} within a Node tree.
 *
 * <p>When {@link NodeRenderer} encounters an Embedded node, it delegates
 * rendering to the wrapped SurfaceSchema. This allows incremental migration:
 * parts of the UI can move to Node trees while unmigrated parts (TreeSurface,
 * InputSurface) continue working as SurfaceSchemas.
 *
 * <p>Each Embedded node is a concrete migration target — when its wrapped
 * surface is rewritten to produce Nodes, the Embedded wrapper is removed.
 */
@Getter
@Accessors(fluent = true)
public class Embedded extends Node {

    private final SurfaceSchema<?> surface;

    private Embedded(SurfaceSchema<?> surface) {
        this.surface = surface;
    }

    /**
     * Wrap a legacy SurfaceSchema so it can participate in a Node tree.
     */
    public static Embedded of(SurfaceSchema<?> surface) {
        return new Embedded(surface);
    }
}
