package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.scene.FontMetrics;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * SkiaFontMetrics — {@link FontMetrics} implementation backed by Skija.
 * Measures text width and reports line height using a Skia {@link Font}
 * built from a {@link Typeface}.
 *
 * <p>Single default typeface for the first cut — system sans-serif via
 * {@link Typeface#makeDefault()}.  Per-size Font instances are cached so
 * repeated measurements at the same size don't recreate the metric data.
 * Font-family selection and full typography binding resolution (style /
 * weight / variant) lands when scenes start carrying real typography
 * declarations.
 *
 * <p>This class doesn't touch the GL context — measurement is pure CPU
 * shaping.  That makes it safe to call from any thread (the layout pass
 * runs on the render thread in {@link dev.everydaythings.graph.ui.Presenter
 * Presenter}, which is fine), and a natural candidate to share with the
 * Filament 2D painter when it lands: both raster paths want identical
 * font measurement to produce pixel-identical output, so the eventual
 * home for this class is probably a shared {@code :ui} or
 * {@code :ui:raster} module, with both Skia and Filament 2D consuming it.
 */
public final class SkiaFontMetrics implements FontMetrics, AutoCloseable {

    private final Typeface typeface;
    private final boolean ownsTypeface;
    private final Map<Float, Font> fontCache = new HashMap<>();

    /** Default constructor — uses the system default sans-serif typeface. */
    public SkiaFontMetrics() {
        this(FontMgr.getDefault().matchFamilyStyle(null, FontStyle.NORMAL), true);
    }

    /**
     * Construct with a specific typeface.  The caller retains ownership;
     * this instance does not close it on {@link #close()}.
     */
    public SkiaFontMetrics(Typeface typeface) {
        this(typeface, false);
    }

    private SkiaFontMetrics(Typeface typeface, boolean ownsTypeface) {
        this.typeface = typeface;
        this.ownsTypeface = ownsTypeface;
    }

    @Override
    public float measureWidth(String text, float fontSize) {
        if (text == null || text.isEmpty()) return 0f;
        return fontFor(fontSize).measureTextWidth(text);
    }

    @Override
    public float lineHeight(float fontSize) {
        io.github.humbleui.skija.FontMetrics m = fontFor(fontSize).getMetrics();
        // Total line height = ascent (negative) + descent (positive) + leading.
        // Skija's ascent is negative-up by convention, so subtract.
        return -m.getAscent() + m.getDescent() + m.getLeading();
    }

    /** The Typeface this metric uses.  Visible to the painter for draw-time font reuse. */
    public Typeface typeface() {
        return typeface;
    }

    /** Build-or-fetch a Font at the requested size, cached per-instance. */
    public Font fontFor(float fontSize) {
        return fontCache.computeIfAbsent(fontSize, sz -> new Font(typeface, sz));
    }

    @Override
    public void close() {
        for (Font font : fontCache.values()) {
            font.close();
        }
        fontCache.clear();
        if (ownsTypeface) typeface.close();
    }
}
