package dev.everydaythings.graph.ui.paragraph;

import dev.everydaythings.graph.ui.skia.FontCache;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.paragraph.RectHeightMode;
import io.github.humbleui.skija.paragraph.RectWidthMode;
import io.github.humbleui.skija.paragraph.TextBox;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ParagraphFactory} backed by Skia's Paragraph API.
 *
 * <p>Wraps {@link io.github.humbleui.skija.paragraph.Paragraph} to provide
 * the unified {@link Paragraph} interface. Delegates all layout, hit-testing,
 * and painting to Skia's native text layout engine.
 *
 * <p>The {@link SkiaParagraph#paint(Object, float, float)} method expects the
 * canvas argument to be a Skia {@link Canvas}.
 */
public class SkiaParagraphFactory implements ParagraphFactory {

    private final FontCache fontCache;

    public SkiaParagraphFactory(FontCache fontCache) {
        this.fontCache = fontCache;
    }

    @Override
    public ParagraphBuilder builder(List<String> paragraphStyles) {
        return new SkiaParagraphBuilder(fontCache, paragraphStyles);
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    static class SkiaParagraphBuilder implements ParagraphBuilder {
        private final FontCache fontCache;
        private final List<String> paragraphStyles;
        private final List<StyledRun> runs = new ArrayList<>();
        private List<String> currentStyles;

        SkiaParagraphBuilder(FontCache fontCache, List<String> paragraphStyles) {
            this.fontCache = fontCache;
            this.paragraphStyles = paragraphStyles;
            this.currentStyles = paragraphStyles;
        }

        @Override
        public ParagraphBuilder pushStyle(List<String> styles) {
            this.currentStyles = styles != null ? styles : List.of();
            return this;
        }

        @Override
        public ParagraphBuilder popStyle() {
            this.currentStyles = paragraphStyles;
            return this;
        }

        @Override
        public ParagraphBuilder addText(String text) {
            if (text != null && !text.isEmpty()) {
                runs.add(new StyledRun(text, currentStyles));
            }
            return this;
        }

        @Override
        public Paragraph build() {
            // Concatenate all runs into a single Skia Paragraph
            // For now, use the paragraph-level style for the whole thing.
            // Multi-style spans will be added when Skia TextStyle mapping is refined.
            StringBuilder sb = new StringBuilder();
            for (StyledRun run : runs) sb.append(run.text());

            String fullText = sb.toString();
            if (fullText.isEmpty()) {
                return new SkiaParagraph(null, fontCache);
            }

            // Use paragraph styles to select profile (determines font, size)
            // Build a synthetic TextNode to reuse FontCache.profileFor logic
            var syntheticNode = new dev.everydaythings.graph.ui.skia.LayoutNode.TextNode(
                    fullText, paragraphStyles);
            FontCache.FontProfile profile = fontCache.profileFor(syntheticNode);
            int color = 0xFF000000; // Black placeholder — actual color applied by painter

            io.github.humbleui.skija.paragraph.Paragraph skiaPara =
                    fontCache.buildParagraph(fullText, profile, color, Float.MAX_VALUE);

            return new SkiaParagraph(skiaPara, fontCache);
        }
    }

    record StyledRun(String text, List<String> styles) {}

    // ==================================================================================
    // Paragraph
    // ==================================================================================

    static class SkiaParagraph implements Paragraph {
        private final io.github.humbleui.skija.paragraph.Paragraph delegate;
        private final FontCache fontCache;
        private boolean laidOut;

        SkiaParagraph(io.github.humbleui.skija.paragraph.Paragraph delegate, FontCache fontCache) {
            this.delegate = delegate;
            this.fontCache = fontCache;
        }

        @Override
        public void layout(float maxWidth) {
            if (delegate != null) {
                delegate.layout(maxWidth > 0 ? maxWidth : Float.MAX_VALUE);
                laidOut = true;
            }
        }

        @Override
        public float width() {
            return delegate != null && laidOut ? delegate.getLongestLine() : 0;
        }

        @Override
        public float height() {
            return delegate != null ? delegate.getHeight() : 0;
        }

        @Override
        public float maxIntrinsicWidth() {
            return delegate != null ? delegate.getMaxIntrinsicWidth() : 0;
        }

        @Override
        public List<LineMetrics> lines() {
            if (delegate == null) return List.of();
            var skiaLines = delegate.getLineMetrics();
            List<LineMetrics> result = new ArrayList<>(skiaLines.length);
            for (var lm : skiaLines) {
                result.add(new LineMetrics(
                        (int) lm.getStartIndex(),
                        (int) lm.getEndIndex(),
                        (float) lm.getLeft(),
                        (float) lm.getBaseline() - (float) lm.getAscent(),
                        (float) lm.getWidth(),
                        (float) lm.getAscent(),
                        (float) lm.getDescent()));
            }
            return result;
        }

        @Override
        public int glyphPositionAt(float x, float y) {
            if (delegate == null) return 0;
            var pos = delegate.getGlyphPositionAtCoordinate(x, y);
            return pos.getPosition();
        }

        @Override
        public List<Rect> rectsForRange(int start, int end) {
            if (delegate == null) return List.of();
            TextBox[] boxes = delegate.getRectsForRange(
                    start, end, RectHeightMode.TIGHT, RectWidthMode.TIGHT);
            List<Rect> result = new ArrayList<>(boxes.length);
            for (TextBox box : boxes) {
                io.github.humbleui.types.Rect r = box.getRect();
                result.add(new Rect(r.getLeft(), r.getTop(), r.getWidth(), r.getHeight()));
            }
            return result;
        }

        @Override
        public void paint(Object canvas, float x, float y) {
            if (delegate != null && canvas instanceof Canvas skiaCanvas) {
                delegate.paint(skiaCanvas, x, y);
            }
        }

        @Override
        public void close() {
            if (delegate != null) {
                delegate.close();
            }
        }
    }
}
