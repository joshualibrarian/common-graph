/**
 * Unified Surface presentation system.
 *
 * <p>This package defines the core abstractions for the Common Graph
 * rendering pipeline:
 *
 * <pre>
 * DATA + SURFACE → VIEW → RENDER
 * (CBOR)  (CBOR)    (CBOR)  (Platform)
 * </pre>
 *
 * <h2>Key Concepts</h2>
 * <ul>
 *   <li><b>Surface</b> - Annotation defining how to present data</li>
 *   <li><b>SurfaceSchema</b> - Base class for surface patterns (Text, List, Tree, etc.)</li>
 *   <li><b>SceneMode</b> - Rendering mode (FULL, COMPACT, CHIP, PREVIEW)</li>
 *   <li><b>View</b> - Populated surface tree (result of applying Surface to Data)</li>
 * </ul>
 *
 * <h2>There Is Only Surface</h2>
 * <p>Everything that can be displayed has a Surface. Surfaces are defined
 * via annotations on model classes, or attached to Items at runtime.
 * The "widgets" of traditional UI toolkits are simply Surface patterns.
 *
 * @see dev.everydaythings.graph.ui.scene.surface.Surface
 * @see dev.everydaythings.graph.ui.scene.surface.SurfaceSchema
 * @see dev.everydaythings.graph.ui.scene.SceneMode
 */
package dev.everydaythings.graph.ui.scene.surface;
