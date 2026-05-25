package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.text.MsdfAtlas;
import dev.everydaythings.filament.text.MsdfFontManager;
import dev.everydaythings.graph.scene.FontMetrics;

/**
 * FilamentFontMetrics — {@link FontMetrics} backed by Filament's MSDF font
 * pipeline.  Measures text width via {@link MsdfFontManager#measureWidth}
 * and reports line height from the default atlas's metrics scaled by the
 * font size.
 *
 * <p>Sibling to {@link dev.everydaythings.graph.ui.skia.SkiaFontMetrics}.
 * The two should produce very similar numbers for the same typeface so
 * Presenter layout positions text similarly across renderers, supporting
 * the longer-term pixel-identity goal.  Exact byte-identity isn't expected
 * yet — different glyph rasterization (CPU AA vs MSDF SDF) plus hinting
 * differences mean strict pixel equality is a refinement to converge on,
 * not a property of the first cut.
 */
public final class FilamentFontMetrics implements FontMetrics {

    private final MsdfFontManager fontManager;

    public FilamentFontMetrics(MsdfFontManager fontManager) {
        this.fontManager = fontManager;
    }

    @Override
    public float measureWidth(String text, float fontSize) {
        if (text == null || text.isEmpty()) return 0f;
        return fontManager.measureWidth(text, fontSize);
    }

    @Override
    public float lineHeight(float fontSize) {
        MsdfAtlas atlas = fontManager.defaultAtlas();
        if (atlas == null) return fontSize;
        return (float) atlas.lineHeight() * fontSize;
    }
}
