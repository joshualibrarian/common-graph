package dev.everydaythings.graph.ui.scene.node;

import dev.everydaythings.graph.Canonical;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * The visual primitive — displays shapes, images, 3D models, or glyphs.
 *
 * <p>A Body is "the thing you see" — rendered at the best available
 * fidelity for the platform:
 * <ul>
 *   <li><b>3D renderer</b>: model → shape → image-as-texture → glyph-as-label</li>
 *   <li><b>2D renderer</b>: image → shape → glyph</li>
 *   <li><b>CLI renderer</b>: glyph → alt → "[body]"</li>
 * </ul>
 *
 * <p>An item's icon IS a Body. A chess piece IS a Body.
 * A chart IS a Body. A geometric shape IS a Body.
 */
@Getter
@Accessors(fluent = true)
@Canonical.Canonization
public class Body extends Node {

    // ==================================================================================
    // Content Sources (fallback chain)
    // ==================================================================================

    /** Geometric shape: "circle", "line", "rect", "sphere", "cone", "cylinder", "box". */
    @Canon(order = 100)
    private String shape;

    /** 2D image path or CID (SVG, PNG). */
    @Canon(order = 101)
    private String image;

    /** 3D model path or CID (GLB, GLTF). */
    @Canon(order = 102)
    private String model;

    /** Unicode glyph (single character or emoji). */
    @Canon(order = 103)
    private String glyph;

    /** Text description fallback. */
    @Canon(order = 104)
    private String alt;

    // ==================================================================================
    // Shape Parameters
    // ==================================================================================

    /** Fill color for shapes. */
    @Canon(order = 110)
    private String fill;

    /** Stroke color for shapes. */
    @Canon(order = 111)
    private String stroke;

    /** Stroke width for shapes. */
    @Canon(order = 112)
    private String strokeWidth;

    /** Radius for circle/sphere shapes. */
    @Canon(order = 113)
    private String radius;

    // ==================================================================================
    // 3D Material
    // ==================================================================================

    /** Material reference (PBR properties — CID or name). */
    @Canon(order = 120)
    private String material;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    public Body() {}

    // ==================================================================================
    // Factories
    // ==================================================================================

    /** Body with a glyph (simplest form — works everywhere). */
    public static Body ofGlyph(String glyph) {
        Body b = new Body();
        b.glyph = glyph;
        return b;
    }

    /** Body with an image. */
    public static Body ofImage(String imagePath) {
        Body b = new Body();
        b.image = imagePath;
        return b;
    }

    /** Body with a 3D model. */
    public static Body ofModel(String modelPath) {
        Body b = new Body();
        b.model = modelPath;
        return b;
    }

    /** Body with a geometric shape. */
    public static Body ofShape(String shape) {
        Body b = new Body();
        b.shape = shape;
        return b;
    }

    /** Body with all fidelity levels. */
    public static Body of(String glyph, String image, String model) {
        Body b = new Body();
        b.glyph = glyph;
        b.image = image;
        b.model = model;
        return b;
    }

    // ==================================================================================
    // Fidelity Resolution
    // ==================================================================================

    /** Best content for 3D rendering. */
    public String bestFor3D() {
        if (model != null) return model;
        if (shape != null) return shape;
        if (image != null) return image;
        return glyph;
    }

    /** Best content for 2D rendering. */
    public String bestFor2D() {
        if (image != null) return image;
        if (shape != null) return shape;
        return glyph;
    }

    /** Best content for text rendering. */
    public String bestForText() {
        if (glyph != null) return glyph;
        return alt;
    }

    /** Whether any content source is set. */
    public boolean hasContent() {
        return shape != null || image != null || model != null || glyph != null;
    }

    // ==================================================================================
    // Fluent Setters
    // ==================================================================================

    public Body shape(String s) { this.shape = s; return this; }
    public Body image(String i) { this.image = i; return this; }
    public Body model(String m) { this.model = m; return this; }
    public Body glyph(String g) { this.glyph = g; return this; }
    public Body alt(String a) { this.alt = a; return this; }
    public Body fill(String f) { this.fill = f; return this; }
    public Body stroke(String s) { this.stroke = s; return this; }
    public Body strokeWidth(String w) { this.strokeWidth = w; return this; }
    public Body radius(String r) { this.radius = r; return this; }
    public Body material(String m) { this.material = m; return this; }
}
