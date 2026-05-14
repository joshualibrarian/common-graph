package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.canonical.Canon;
import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.canonical.Canonization;
import lombok.Getter;

/**
 * Multi-fidelity visual identity for an item or element.
 *
 * <p>Three fidelity levels, each optional:
 * <ul>
 *   <li><b>glyph</b> — Unicode character for text contexts (CLI, badges, inline)</li>
 *   <li><b>image</b> — 2D image path or CID for GUI contexts (tree nodes, handles)</li>
 *   <li><b>model</b> — 3D model path or CID for spatial contexts (inventory, 3D scenes)</li>
 * </ul>
 *
 * <p>Renderers pick the appropriate fidelity for their platform.
 * Cascade: missing levels fall back — no model? use image. No image? use glyph.
 */
@Getter
@Canonization
public class SceneIcon implements Canonical {

    @Canon(order = 0)
    private String glyph;

    @Canon(order = 1)
    private String image;

    @Canon(order = 2)
    private String model;

    public SceneIcon() {}

    public SceneIcon(String glyph, String image, String model) {
        this.glyph = glyph;
        this.image = image;
        this.model = model;
    }

    public static SceneIcon ofGlyph(String glyph) {
        return new SceneIcon(glyph, null, null);
    }

    public static SceneIcon ofImage(String image) {
        return new SceneIcon(null, image, null);
    }

    public static SceneIcon of(String glyph, String image) {
        return new SceneIcon(glyph, image, null);
    }

    public static SceneIcon of(String glyph, String image, String model) {
        return new SceneIcon(glyph, image, model);
    }

    /** Resolve the best available representation. Prefers model > image > glyph. */
    public String bestFor3D() {
        if (model != null) return model;
        if (image != null) return image;
        return glyph;
    }

    /** Resolve the best available representation for 2D. Prefers image > glyph. */
    public String bestFor2D() {
        if (image != null) return image;
        return glyph;
    }

    /** Resolve the best available representation for text. Always glyph. */
    public String bestForText() {
        return glyph;
    }

    /** Whether any fidelity level is set. */
    public boolean hasAny() {
        return glyph != null || image != null || model != null;
    }

    @SuppressWarnings("unused")
    private SceneIcon(boolean unused) {
        // no-arg for Canonical
    }
}
