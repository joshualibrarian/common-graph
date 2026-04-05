package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.Engine;
import dev.everydaythings.filament.text.MsdfAtlas;
import dev.everydaythings.graph.ui.paragraph.MsdfParagraphFactory;
import dev.everydaythings.graph.ui.paragraph.Paragraph;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.RenderMetrics;
import dev.everydaythings.graph.ui.scene.SizeValue;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan;
import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.LayoutNode;
import dev.everydaythings.graph.ui.text.FontRegistry;

import java.util.List;
import java.util.logging.Logger;

/**
 * Adapter that bridges filament-java-text's {@link dev.everydaythings.filament.text.MsdfFontManager}
 * to common-graph's {@link LayoutEngine.TextMeasurer} and {@link LayoutEngine.ImageMeasurer}.
 *
 * <p>Font discovery is delegated to {@link FontRegistry}. This class registers
 * the discovered fonts (those with byte data) into the MSDF font manager's
 * fallback chain, ensuring identical font ordering with the Skia renderer.
 */
public class MsdfFontManager implements LayoutEngine.TextMeasurer, LayoutEngine.ImageMeasurer {

    private static final Logger LOG = Logger.getLogger(MsdfFontManager.class.getName());

    private static final float DEFAULT_BASE_FONT_SIZE = 15f;

    private final FontRegistry registry;
    private float baseFontSize = DEFAULT_BASE_FONT_SIZE;
    private final dev.everydaythings.filament.text.MsdfFontManager delegate;
    private MsdfParagraphFactory paragraphFactory;
    private int registeredFontCount;
    private boolean registrationEnabled = true;

    private enum Mode {
        AUTO,
        OFF,
        FORCE
    }

    public MsdfFontManager(Engine engine, FontRegistry registry) {
        this.registry = registry;
        this.delegate = new dev.everydaythings.filament.text.MsdfFontManager(engine);

        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean macArm = os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64"));
        Mode mode = resolveMode(macArm);
        LOG.info("MSDF mode=" + mode + " os=" + os + " arch=" + arch + " macArm=" + macArm);

        if (mode == Mode.OFF) {
            registrationEnabled = false;
            LOG.warning("MSDF font registration disabled (graph.msdf.mode=off)");
            return;
        }

        // Register all file-backed fonts from the shared registry chain.
        // This keeps Filament glyph coverage aligned with Skia's discovered fonts.
        int registered = 0;
        for (FontRegistry.ResolvedFont font : registry.fonts()) {
            if (font.data() != null) {
                try {
                    delegate.registerFont(font.familyName(), font.data());
                    registered++;
                } catch (Throwable t) {
                    LOG.warning("MSDF failed to register font '" + font.familyName() + "': " + t.getMessage());
                }
            }
        }

        this.registeredFontCount = registered;
        LOG.info("MSDF font chain: " + registered + " fonts registered from FontRegistry");
        if (macArm && registered == 0) {
            LOG.warning("MSDF enabled on mac-arm but no fonts registered; fallback renderer will be used");
        }
    }

    private static Mode resolveMode(boolean macArm) {
        // Legacy compatibility knobs
        if (Boolean.getBoolean("graph.msdf.disable")) {
            return Mode.OFF;
        }
        if (macArm && Boolean.getBoolean("graph.msdf.enableOnMacArm")) {
            return Mode.FORCE;
        }

        String raw = System.getProperty("graph.msdf.mode", "auto").trim().toLowerCase();
        return switch (raw) {
            case "off", "false", "0", "disabled" -> Mode.OFF;
            case "force", "on", "true", "1", "enabled" -> Mode.FORCE;
            default -> Mode.AUTO;
        };
    }

    // ==================================================================================
    // Delegation
    // ==================================================================================

    /** The underlying generic font manager. */
    public dev.everydaythings.filament.text.MsdfFontManager delegate() {
        return delegate;
    }

    public MsdfAtlas registerFont(String name, byte[] fontData) {
        return delegate.registerFont(name, fontData);
    }

    public MsdfAtlas registerFont(String name, String resourcePath) {
        return delegate.registerFont(name, resourcePath);
    }

    public dev.everydaythings.filament.text.MsdfFontManager.ResolvedGlyph resolveGlyph(int codepoint) {
        return delegate.resolveGlyph(codepoint);
    }

    public void ensureGlyphs(String text) {
        delegate.ensureGlyphs(text);
    }

    public float measureWidth(String text, float fontSize) {
        return delegate.measureWidth(text, fontSize);
    }

    public MsdfAtlas atlas(String name) {
        return delegate.atlas(name);
    }

    public MsdfAtlas defaultAtlas() {
        return delegate.defaultAtlas();
    }

    /**
     * Resolve font family chain using the shared registry rules.
     */
    public String[] familiesFor(String requestedFamily) {
        return registry.familiesFor(requestedFamily);
    }

    public List<MsdfAtlas> fallbackChain() {
        return delegate.fallbackChain();
    }

    public boolean hasUsableFonts() {
        return registrationEnabled && registeredFontCount > 0;
    }

    public int registeredFontCount() {
        return registeredFontCount;
    }

    // ==================================================================================
    // TextMeasurer Implementation
    // ==================================================================================

    /** Get or lazily create the paragraph factory. */
    public MsdfParagraphFactory paragraphFactory() {
        if (paragraphFactory == null) {
            paragraphFactory = new MsdfParagraphFactory(this);
        }
        return paragraphFactory;
    }

    @Override
    public void measure(LayoutNode.TextNode node, float maxWidth) {
        if (delegate.fallbackChain().isEmpty()) {
            node.measuredSize(0, baseFontSize);
            return;
        }

        // Use paragraph factory for layout (handles wrapping)
        List<TextSpan> spans = node.spans();
        float fontSize = fontSizeFor(node);
        float effectiveMaxWidth = maxWidth > 0 ? maxWidth : Float.MAX_VALUE;

        Paragraph para;
        if (spans != null && !spans.isEmpty()) {
            para = paragraphFactory().fromSpans(spans, fontSize, effectiveMaxWidth);
        } else {
            para = paragraphFactory().fromText(node.content(), fontSize, effectiveMaxWidth);
        }

        node.paragraph(para);
        // Use intrinsic width (actual content width) clamped to maxWidth.
        // para.width() returns the maxWidth constraint, not the content width.
        float w = Math.min(para.maxIntrinsicWidth(), effectiveMaxWidth);
        node.measuredSize(w, para.height());
    }

    // ==================================================================================
    // ImageMeasurer Implementation
    // ==================================================================================

    @Override
    public void measure(LayoutNode.ImageNode image) {
        // Resolve size hint to pixels (mirrors FontCache sizes)
        float px = 0;
        if (image.size() != null) {
            px = switch (image.size()) {
                case "small"  -> 24;
                case "medium" -> 36;
                case "large"  -> 48;
                default -> {
                    SizeValue sv = SizeValue.parse(image.size());
                    if (sv != null) {
                        yield (float) sv.toPixels(RenderContext.gui());
                    }
                    yield 0f;
                }
            };
        }

        // For resource images with a size, use explicit dimensions
        if (image.hasResource() && px > 0) {
            image.setBounds(image.x(), image.y(), px, px);
            return;
        }

        // For shaped images (circle), use size hint as square bounding box
        if (image.shape() != null && px > 0) {
            image.setBounds(image.x(), image.y(), px, px);
            return;
        }

        // Emoji fallback: measure as text using chain
        if (image.alt() != null && !image.alt().isEmpty()) {
            String alt = image.alt();
            delegate.ensureGlyphs(alt);
            float emWidth = delegate.measureWidth(alt, baseFontSize);
            // If shaped, ensure square bounding box with padding
            if (image.shape() != null) {
                float dim = Math.max(emWidth, baseFontSize) + 8;
                image.setBounds(image.x(), image.y(), dim, dim);
            } else {
                image.setBounds(image.x(), image.y(),
                        Math.max(emWidth, baseFontSize), baseFontSize);
            }
        }
    }

    // ==================================================================================
    // Font Size Resolution
    // ==================================================================================

    /**
     * Set the base font size. Invalidates the cached paragraph factory.
     */
    public void setBaseFontSize(float size) {
        this.baseFontSize = size;
        this.paragraphFactory = null;
    }

    public float baseFontSize() {
        return baseFontSize;
    }

    /**
     * Resolve font size in pixels from a text node's resolved style fields.
     */
    public float fontSizeFor(LayoutNode.TextNode node) {
        return node.fontSize() > 0 ? node.fontSize() : baseFontSize;
    }

    /**
     * Build {@link RenderMetrics} from actual MSDF font measurements at the current base font size.
     * Uses HarfBuzz line height for parity with Skia's line height model.
     */
    public RenderMetrics buildMetrics() {
        MsdfAtlas atlas = delegate.defaultAtlas();
        float chWidth = delegate.measureWidth("0", baseFontSize);
        float lineHeight = atlas != null ? (float)(atlas.hbLineHeight() * baseFontSize) : baseFontSize;
        return RenderMetrics.gui(baseFontSize, chWidth, lineHeight, baseFontSize);
    }

    // ==================================================================================
    // Cleanup
    // ==================================================================================

    public void destroy() {
        delegate.destroy();
    }
}
