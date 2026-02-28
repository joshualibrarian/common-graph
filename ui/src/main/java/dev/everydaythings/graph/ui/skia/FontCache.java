package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.paragraph.SkiaParagraphFactory;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.RenderMetrics;
import dev.everydaythings.graph.ui.scene.SizeValue;
import dev.everydaythings.graph.ui.text.FontRegistry;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.paragraph.FontCollection;
import io.github.humbleui.skija.paragraph.Paragraph;
import io.github.humbleui.skija.paragraph.ParagraphBuilder;
import io.github.humbleui.skija.paragraph.ParagraphStyle;
import io.github.humbleui.skija.paragraph.TextStyle;
import io.github.humbleui.skija.paragraph.TypefaceFontProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages paragraph-based text rendering for the Skia renderer.
 *
 * <p>Uses the Skia Paragraph API ({@link FontCollection}, {@link ParagraphBuilder})
 * for automatic per-character font fallback across text, emoji, and nerd font icons.
 *
 * <p>Font discovery is delegated to {@link FontRegistry}. This class registers
 * the discovered fonts into Skia's {@link TypefaceFontProvider} and builds
 * {@link FontProfile} records for measurement and painting.
 */
public class FontCache implements LayoutEngine.TextMeasurer, LayoutEngine.ImageMeasurer {

    private static final Logger LOG = Logger.getLogger(FontCache.class.getName());

    private static final float DEFAULT_BASE_FONT_SIZE = 15f;
    private static final String[] EMOJI_PROBES = {"😀", "🧠", "🚀", "✅", "1️⃣"};

    private final FontRegistry registry;
    private final FontCollection fontCollection;
    private final TypefaceFontProvider provider;
    private final Map<String, Integer> emojiScoreCache = new HashMap<>();

    private float baseFontSize = DEFAULT_BASE_FONT_SIZE;
    private FontProfile proportional;
    private FontProfile mono;
    private FontProfile emoji;
    private SkiaParagraphFactory paragraphFactory;

    /**
     * A font fallback chain with a size. Used to build Paragraphs.
     */
    public record FontProfile(String[] families, float size) {}

    public FontCache(FontRegistry registry) {
        this.registry = registry;

        // Register all fonts with byte data as typefaces in the provider
        provider = new TypefaceFontProvider();
        for (FontRegistry.ResolvedFont font : registry.fonts()) {
            if (font.data() != null) {
                try (Data data = Data.makeFromBytes(font.data())) {
                    Typeface face = FontMgr.getDefault().makeFromData(data);
                    if (face != null) {
                        provider.registerTypeface(face, font.familyName());
                    }
                }
            }
        }

        // Build FontCollection: asset (registered bytes) → system (installed fonts)
        fontCollection = new FontCollection();
        fontCollection.setAssetFontManager(provider);
        fontCollection.setDefaultFontManager(FontMgr.getDefault());
        fontCollection.setEnableFallback(true);

        // Build profiles from registry chains
        mono = new FontProfile(orderFamiliesForSkia(registry.monoFamilies()), baseFontSize);
        proportional = new FontProfile(orderFamiliesForSkia(registry.proportionalFamilies()), baseFontSize);
        emoji = new FontProfile(orderFamiliesForSkia(registry.emojiFamilies()), baseFontSize);

        LOG.info("Font profiles ready — mono: " + String.join(", ", mono.families())
                + " | proportional: " + String.join(", ", proportional.families()));
    }

    // ==================== Font Size ====================

    /**
     * Set the base font size and rebuild all profiles.
     */
    public void setBaseFontSize(float size) {
        this.baseFontSize = size;
        rebuildProfiles();
    }

    public float baseFontSize() {
        return baseFontSize;
    }

    private void rebuildProfiles() {
        mono = new FontProfile(mono.families(), baseFontSize);
        proportional = new FontProfile(proportional.families(), baseFontSize);
        emoji = new FontProfile(emoji.families(), baseFontSize);
        paragraphFactory = null;
    }

    /**
     * Measure the actual width of "0" at the current base font size.
     */
    public float measureChWidth() {
        try (Paragraph para = buildParagraph("0", mono, 0xFF000000, Float.MAX_VALUE)) {
            return para.getMaxIntrinsicWidth();
        }
    }

    /**
     * Measure the actual line height at the current base font size.
     */
    public float measureLineHeight() {
        try (Paragraph para = buildParagraph("Xg", proportional, 0xFF000000, Float.MAX_VALUE)) {
            return para.getHeight();
        }
    }

    /**
     * Build {@link RenderMetrics} from actual font measurements at the current base font size.
     */
    public RenderMetrics buildMetrics() {
        return RenderMetrics.gui(baseFontSize, measureChWidth(), measureLineHeight(), baseFontSize);
    }

    /**
     * Select the font profile for a text node based on its resolved style fields.
     *
     * <p>Reads {@code node.fontFamily()} and {@code node.fontSize()} which are
     * set by {@link dev.everydaythings.graph.ui.style.StyleResolver} before layout.
     */
    public FontProfile profileFor(LayoutNode.TextNode node) {
        String[] families = orderFamiliesForSkia(registry.familiesFor(node.fontFamily()));
        float size = node.fontSize() > 0 ? node.fontSize() : baseFontSize;
        return new FontProfile(families, size);
    }

    /**
     * Returns the emoji font profile.
     */
    public FontProfile emojiProfile() {
        return emoji;
    }

    /**
     * Build a laid-out Paragraph for the given text and profile.
     * <p>Caller must close the returned Paragraph when done.
     *
     * @param text     the text content
     * @param profile  font family chain and size
     * @param color    ARGB color for the text
     * @param maxWidth layout width (use {@link Float#MAX_VALUE} for no wrapping)
     * @return a laid-out Paragraph (caller must close)
     */
    public Paragraph buildParagraph(String text, FontProfile profile, int color, float maxWidth) {
        try (TextStyle textStyle = new TextStyle();
             ParagraphStyle paraStyle = new ParagraphStyle()) {

            textStyle.setFontFamilies(profile.families())
                     .setFontSize(profile.size())
                     .setColor(color);

            try (ParagraphBuilder builder = new ParagraphBuilder(paraStyle, fontCollection)) {
                builder.pushStyle(textStyle);
                builder.addText(text);
                Paragraph para = builder.build();
                para.layout(maxWidth > 0 ? maxWidth : Float.MAX_VALUE);
                return para;  // caller must close
            }
        }
    }

    // ==================== ParagraphFactory ====================

    /** Get or lazily create the paragraph factory. */
    public SkiaParagraphFactory paragraphFactory() {
        if (paragraphFactory == null) {
            paragraphFactory = new SkiaParagraphFactory(this);
        }
        return paragraphFactory;
    }

    // ==================== TextMeasurer ====================

    @Override
    public void measure(LayoutNode.TextNode node, float maxWidth) {
        float effectiveMaxWidth = maxWidth > 0 ? maxWidth : Float.MAX_VALUE;

        // Use profileFor(node) so the measurement font size matches the painting
        // font size. Previously, measurement built a synthetic TextNode that lacked
        // the resolved fontSize (from @Scene.Text(fontSize="80%")), causing text
        // to be measured at the default 15px but painted at the resolved size.
        FontProfile profile = profileFor(node);
        try (var para = buildParagraph(node.content(), profile, 0xFF000000, effectiveMaxWidth)) {
            // Use intrinsic width (actual content width) clamped to maxWidth.
            // getMaxIntrinsicWidth() returns the content width, not the constraint,
            // which is essential for shrink-wrap layouts.
            // Ceil to avoid float precision causing last-char line wrapping when
            // the paint path rebuilds the paragraph with this width as maxWidth.
            float w = Math.min((float) Math.ceil(para.getMaxIntrinsicWidth()), effectiveMaxWidth);
            node.measuredSize(w, para.getHeight());
        }
    }

    // ==================== ImageMeasurer ====================

    @Override
    public void measure(LayoutNode.ImageNode image) {
        // Size resolution based on hint
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
            image.setBounds(0, 0, px, px);
            return;
        }

        // For shaped images (circle), use size hint as square bounding box
        if (image.shape() != null && px > 0) {
            image.setBounds(0, 0, px, px);
            return;
        }

        // Fallback: measure based on alt text (emoji size)
        String alt = image.alt();
        try (Paragraph para = buildParagraph(alt, emoji, 0xFF000000, Float.MAX_VALUE)) {
            float w = para.getMaxIntrinsicWidth();
            float h = para.getHeight();
            // If shaped, ensure square bounding box
            if (image.shape() != null) {
                float dim = Math.max(w, h) + 8; // padding inside shape
                image.setBounds(0, 0, dim, dim);
            } else {
                image.setBounds(0, 0, w, h);
            }
        }
    }

    public void close() {
        fontCollection.close();
        provider.close();
    }

    /**
     * Reorder families for Skia text rendering.
     *
     * <p>On macOS, prefer Apple Color Emoji before Twemoji. Twemoji glyphs can
     * be selected first but fail to render correctly in Skia paragraph mode on
     * some setups, preventing fallback to Apple Color Emoji.
     */
    private String[] orderFamiliesForSkia(String[] families) {
        ArrayList<String> ordered = new ArrayList<>(Arrays.asList(families));
        ArrayList<Integer> emojiSlots = new ArrayList<>();
        ArrayList<String> emojiFamilies = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            String family = ordered.get(i);
            if (isEmojiFamily(family)) {
                emojiSlots.add(i);
                emojiFamilies.add(family);
            }
        }
        if (emojiFamilies.size() > 1) {
            emojiFamilies.sort((a, b) -> {
                int sb = emojiRenderScore(b);
                int sa = emojiRenderScore(a);
                return Integer.compare(sb, sa);
            });
            for (int i = 0; i < emojiSlots.size(); i++) {
                ordered.set(emojiSlots.get(i), emojiFamilies.get(i));
            }
        }
        return ordered.toArray(new String[0]);
    }

    private static boolean isEmojiFamily(String family) {
        if (family == null) return false;
        String f = family.toLowerCase();
        return f.contains("emoji") || f.contains("twemoji");
    }

    private int emojiRenderScore(String family) {
        return emojiScoreCache.computeIfAbsent(family, this::computeEmojiRenderScore);
    }

    private int computeEmojiRenderScore(String family) {
        int score = 0;
        for (String probe : EMOJI_PROBES) {
            if (rendersVisibleGlyph(family, probe)) {
                score++;
            }
        }
        return score;
    }

    private boolean rendersVisibleGlyph(String family, String text) {
        try (TextStyle textStyle = new TextStyle();
             ParagraphStyle paraStyle = new ParagraphStyle()) {
            textStyle.setFontFamilies(new String[] {family})
                    .setFontSize(32f)
                    .setColor(0xFFFFFFFF);
            try (ParagraphBuilder builder = new ParagraphBuilder(paraStyle, fontCollection)) {
                builder.pushStyle(textStyle);
                builder.addText(text);
                try (Paragraph para = builder.build()) {
                    para.layout(Float.MAX_VALUE);
                    int w = Math.max(8, (int) Math.ceil(para.getMaxIntrinsicWidth()) + 8);
                    int h = Math.max(8, (int) Math.ceil(para.getHeight()) + 8);
                    ImageInfo info = new ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
                    try (Bitmap bmp = new Bitmap()) {
                        bmp.allocPixels(info);
                        try (Canvas canvas = new Canvas(bmp)) {
                            canvas.clear(0x00000000);
                            para.paint(canvas, 4, 4);
                        }
                        byte[] pixels = bmp.readPixels();
                        for (int i = 3; i < pixels.length; i += 4) {
                            if ((pixels[i] & 0xFF) > 0) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }
}
