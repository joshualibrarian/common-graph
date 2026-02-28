package dev.everydaythings.graph.ui.scene.surface.primitive;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

/**
 * Visual content with text fallback.
 *
 * <p>ImageSurface is one of the three primitives. It displays visual content
 * with a required text fallback, following the chain: solid → image → alt.
 *
 * <ul>
 *   <li><b>alt</b> (required) - Text/emoji fallback, always works</li>
 *   <li><b>image</b> (optional) - 2D image from content store</li>
 *   <li><b>solid</b> (optional) - 3D geometry for immersive UIs (future)</li>
 * </ul>
 *
 * <p>This is similar to HTML's {@code <img alt="...">} but with 3D support.
 * Text-mode renderers use the alt text. GUI renderers prefer image/solid.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Simple emoji/glyph icon
 * ImageSurface.of("📁")
 *
 * // Icon with image for GUI
 * ImageSurface.of("📁").image(folderImageId)
 *
 * // Full specification with 3D
 * ImageSurface.of("🎮")
 *     .image(gamepadImageId)
 *     .solid(gamepad3dModelId)
 *
 * // Photo with alt text
 * ImageSurface.of("Photo of sunset").image(photoId)
 * }</pre>
 */
public class ImageSurface extends SurfaceSchema {

    /**
     * Text fallback - always required.
     * Can be an emoji ("📁"), glyph, or descriptive text ("Photo of sunset").
     * Used in text mode and when image/solid unavailable.
     */
    @Canon(order = 10)
    private String alt;

    /**
     * 2D image from content-addressed storage.
     * Takes precedence over alt in graphical UIs.
     */
    @Canon(order = 11)
    private ContentID image;

    /**
     * 3D geometry from content-addressed storage.
     * Takes precedence over image in immersive UIs (future).
     */
    @Canon(order = 12)
    private ContentID solid;

    /**
     * Classpath resource path for a 2D icon (e.g., "icons/key.png").
     * Takes precedence over alt in graphical UIs that support image loading.
     * Text renderers fall back to alt.
     */
    @Canon(order = 15)
    private String resource;

    /**
     * Size hint: "small", "medium", "large", or explicit like "24px", "100%".
     */
    @Canon(order = 13)
    private String size;

    /**
     * How to fit the image: "contain", "cover", "fill", "none".
     */
    @Canon(order = 14)
    private String fit;

    /**
     * Binding expression for dynamic image content.
     *
     * <p>When set, the expression is resolved against the root value at render time.
     * The result's {@code symbol()}, {@code imageKey()}, or {@code toString()} is used
     * as the alt text. Takes precedence over static {@code alt}.
     *
     * <p>Examples: {@code "value.state.pieces.a1.symbol"}.
     */
    @Canon(order = 16)
    private String bind;

    /**
     * Shape to draw behind the image: "circle", "rounded-rect", or null (bare).
     */
    @Canon(order = 17)
    private String shape;

    /**
     * Background fill color for the shape (e.g., "#3C3C4E", "dark").
     */
    @Canon(order = 18)
    private String backgroundColor;

    /**
     * Classpath path to a 3D model (GLB/glTF) for immersive UIs.
     *
     * <p>Completes the fidelity chain: alt (emoji) → resource (SVG) → modelResource (GLB) → solid (content-addressed 3D).
     * When present, 3D renderers use this to place a mesh at the image's layout position.
     * 2D renderers ignore this field.
     */
    @Canon(order = 19)
    private String modelResource;

    /**
     * Material color override for the 3D model (-1 = use model default).
     */
    @Canon(order = 20)
    private int modelColor = -1;

    /**
     * Foreground tint color for the glyph/emoji (0xAARRGGBB, 0 = use renderer default).
     *
     * <p>In MSDF (3D) rendering, emojis are monochrome outlines tinted by this color.
     * Typically set to the item's type color so tree icons appear colored.
     */
    @Canon(order = 21)
    private int color = 0;

    public ImageSurface() {}

    public ImageSurface(String alt) {
        this.alt = alt;
    }

    // ==================== Factory Methods ====================

    /**
     * Create an image surface with alt text.
     * The alt is required - it's the universal fallback.
     */
    public static ImageSurface of(String alt) {
        return new ImageSurface(alt);
    }

    /**
     * Create an image surface with alt and image.
     */
    public static ImageSurface of(String alt, ContentID image) {
        ImageSurface s = new ImageSurface(alt);
        s.image = image;
        return s;
    }

    /**
     * Create an image surface with a classpath resource and alt text fallback.
     */
    public static ImageSurface ofResource(String resource, String alt) {
        ImageSurface s = new ImageSurface(alt);
        s.resource = resource;
        return s;
    }

    // ==================== Fluent Setters ====================

    public ImageSurface alt(String alt) {
        this.alt = alt;
        return this;
    }

    public ImageSurface image(ContentID image) {
        this.image = image;
        return this;
    }

    public ImageSurface solid(ContentID solid) {
        this.solid = solid;
        return this;
    }

    public ImageSurface size(String size) {
        this.size = size;
        return this;
    }

    public ImageSurface fit(String fit) {
        this.fit = fit;
        return this;
    }

    public ImageSurface resource(String resource) {
        this.resource = resource;
        return this;
    }

    // Size convenience methods
    public ImageSurface small() {
        this.size = "small";
        return this;
    }

    public ImageSurface medium() {
        this.size = "medium";
        return this;
    }

    public ImageSurface large() {
        this.size = "large";
        return this;
    }

    public ImageSurface bind(String bindExpr) {
        this.bind = bindExpr;
        return this;
    }

    public ImageSurface shape(String shape) {
        this.shape = shape;
        return this;
    }

    public ImageSurface backgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public ImageSurface modelResource(String modelResource) {
        this.modelResource = modelResource;
        return this;
    }

    public ImageSurface modelColor(int modelColor) {
        this.modelColor = modelColor;
        return this;
    }

    public ImageSurface color(int color) {
        this.color = color;
        return this;
    }

    /**
     * Convenience: set shape to "circle".
     */
    public ImageSurface circle() {
        this.shape = "circle";
        return this;
    }

    // ==================== Getters ====================

    public String alt() { return alt; }
    public ContentID image() { return image; }
    public ContentID solid() { return solid; }
    public String resource() { return resource; }
    public String size() { return size; }
    public String fit() { return fit; }
    public String bind() { return bind; }
    public String shape() { return shape; }
    public String backgroundColor() { return backgroundColor; }

    public boolean hasImage() { return image != null; }
    public boolean hasSolid() { return solid != null; }
    public boolean hasResource() { return resource != null && !resource.isEmpty(); }
    public boolean hasShape() { return shape != null && !shape.isEmpty(); }
    public String modelResource() { return modelResource; }
    public int modelColor() { return modelColor; }
    public boolean hasModelResource() { return modelResource != null && !modelResource.isEmpty(); }
    public int color() { return color; }
    public boolean hasColor() { return color != 0; }

    // ==================== Rendering ====================

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);
        if (shape != null) {
            out.shape(shape, "circle".equals(shape) ? "circle" : "medium",
                    backgroundColor != null ? backgroundColor : "",
                    "", "", "");
        }
        if (modelResource != null) {
            out.model(modelResource, modelColor);
        }
        if (color != 0) {
            out.tint(color);
        }
        out.image(alt, image, solid, resource, size, fit, style());
    }
}
