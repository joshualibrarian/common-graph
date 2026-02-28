package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.ui.scene.spatial.ItemSpace;
import dev.everydaythings.graph.ui.scene.spatial.SpatialSchema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Unified type annotation for both Item classes and component classes.
 *
 * <p>Declares a type's identity and default display metadata:
 * <ul>
 *   <li>{@code value} - the canonical type key (e.g., "cg:type/librarian")</li>
 *   <li>{@code glyph} - the default emoji/icon for this type</li>
 *   <li>{@code color} - RGB color as hex (e.g., 0x4B6EAF)</li>
 *   <li>{@code shape} - "sphere" (items), "cube" (components), "disc" (values)</li>
 *   <li>{@code icon} - classpath resource path for a 2D icon image</li>
 *   <li>{@code scene} - SpatialSchema class for 3D presentation</li>
 * </ul>
 *
 * <p>This metadata flows through SeedVocabulary into the library as a
 * {@link SurfaceTemplateComponent} on the type item. Instances inherit their type's
 * display defaults but can override with their own SurfaceTemplateComponent.
 *
 * <p>Usage:
 * <pre>{@code
 * @Type(value = "cg:type/librarian", glyph = "📚", color = 0x4B6EAF)
 * public class Librarian extends Signer { ... }
 *
 * @Type(value = "cg:type/vault", glyph = "🔐", shape = "cube")
 * public class Vault implements Component { ... }
 * }</pre>
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Type {
    /** The canonical type key (e.g., "cg:type/item"). */
    String value() default "";

    /** The default glyph (emoji/icon) for this type. Defaults to 📦. */
    String glyph() default "📦";

    /** RGB color as hex int (e.g., 0x4B6EAF). Defaults to neutral gray. */
    int color() default 0x78788C;

    /** Shape kind: "sphere" (items), "cube" (components), "disc" (values). */
    String shape() default "sphere";

    /**
     * Classpath resource path for a 2D icon image (e.g., "/icons/key.png").
     * Used by graphical renderers instead of the emoji glyph.
     * Text renderers fall back to the glyph.
     */
    String icon() default "";

    /** SpatialSchema class for 3D presentation. Defaults to ItemSpace. */
    Class<? extends SpatialSchema> scene() default ItemSpace.class;
}
