package dev.everydaythings.graph.ui.paragraph;

import com.ibm.icu.text.BreakIterator;
import dev.everydaythings.filament.text.ColrGlyphInfo;
import dev.everydaythings.filament.text.MsdfAtlas;
import dev.everydaythings.graph.ui.filament.MsdfFontManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@link ParagraphFactory} backed by MSDF glyph metrics and ICU4J line breaking.
 *
 * <p>Provides word-wrapping, multi-line layout, hit-testing, and selection
 * rectangles for the Filament/MSDF text rendering path. Uses ICU4J's
 * {@link BreakIterator#getLineInstance()} for Unicode-correct break opportunities.
 *
 * <p>The {@link MsdfParagraph#paint(Object, float, float)} method expects the
 * canvas argument to be a {@link dev.everydaythings.graph.ui.filament.FilamentSurfacePainter}
 * and calls its glyph-emission methods.
 */
public class MsdfParagraphFactory implements ParagraphFactory {

    private final MsdfFontManager fontManager;

    public MsdfParagraphFactory(MsdfFontManager fontManager) {
        this.fontManager = fontManager;
    }

    @Override
    public ParagraphBuilder builder(List<String> paragraphStyles) {
        return new MsdfParagraphBuilder(fontManager, fontManager.baseFontSize());
    }

    /**
     * Create a builder with an explicit font size (from resolved node).
     */
    public ParagraphBuilder builder(float fontSize) {
        return new MsdfParagraphBuilder(fontManager, fontSize);
    }

    /**
     * Build a paragraph from spans using an explicit resolved font size.
     */
    public Paragraph fromSpans(List<dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan> spans,
                               float fontSize, float maxWidth) {
        ParagraphBuilder b = builder(fontSize);
        for (var span : spans) {
            b.pushStyle(span.styles());
            b.addText(span.text());
            b.popStyle();
        }
        Paragraph p = b.build();
        p.layout(maxWidth);
        return p;
    }

    /**
     * Build a paragraph from plain text using an explicit resolved font size.
     */
    public Paragraph fromText(String text, float fontSize, float maxWidth) {
        ParagraphBuilder b = builder(fontSize);
        b.addText(text);
        Paragraph p = b.build();
        p.layout(maxWidth);
        return p;
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    static class MsdfParagraphBuilder implements ParagraphBuilder {
        private final MsdfFontManager fontManager;
        private final float fontSize;
        private final List<StyledRun> runs = new ArrayList<>();
        private List<String> currentStyles = List.of();

        MsdfParagraphBuilder(MsdfFontManager fontManager, float fontSize) {
            this.fontManager = fontManager;
            this.fontSize = fontSize;
        }

        @Override
        public ParagraphBuilder pushStyle(List<String> styles) {
            this.currentStyles = styles != null ? styles : List.of();
            return this;
        }

        @Override
        public ParagraphBuilder popStyle() {
            this.currentStyles = List.of();
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
            return new MsdfParagraph(fontManager, fontSize, List.copyOf(runs));
        }
    }

    record StyledRun(String text, List<String> styles) {}

    // ==================================================================================
    // Paragraph
    // ==================================================================================

    static class MsdfParagraph implements Paragraph {
        private final MsdfFontManager fontManager;
        private final float fontSize;
        private final List<StyledRun> runs;
        private final String fullText;

        // Layout results
        private float width;
        private float height;
        private float maxIntrinsicWidth;
        private List<LineMetrics> lines;
        private List<PositionedGlyph> glyphs;

        MsdfParagraph(MsdfFontManager fontManager, float fontSize, List<StyledRun> runs) {
            this.fontManager = fontManager;
            this.fontSize = fontSize;
            this.runs = runs;

            // Flatten text for line breaking
            StringBuilder sb = new StringBuilder();
            for (StyledRun run : runs) sb.append(run.text());
            this.fullText = sb.toString();

            // Ensure all glyphs are loaded
            fontManager.ensureGlyphs(fullText);
        }

        @Override
        public void layout(float maxWidth) {
            if (fullText.isEmpty()) {
                MsdfAtlas primary = fontManager.defaultAtlas();
                float lineH = primary != null ? (float)(primary.hbLineHeight() * fontSize) : fontSize;
                this.width = 0;
                this.height = lineH;
                this.maxIntrinsicWidth = 0;
                this.lines = List.of();
                this.glyphs = List.of();
                return;
            }

            MsdfAtlas primary = fontManager.defaultAtlas();
            float ascent = primary != null ? (float) primary.hbAscent() : 0.8f;
            float lineH = primary != null ? (float)(primary.hbLineHeight() * fontSize) : fontSize;

            // Measure the ellipsis character for truncation
            var ellipsisRg = fontManager.resolveGlyph('\u2026');
            float ellipsisAdv = ellipsisRg != null ? (float)(ellipsisRg.metrics().advance() * fontSize) : 0;

            // Get line break opportunities from ICU4J
            BreakIterator bi = BreakIterator.getLineInstance(Locale.getDefault());
            bi.setText(fullText);

            // Measure using HarfBuzz shaping when available
            int len = fullText.length();
            float[] advances = new float[len]; // advance per Java char (0 for surrogates)
            MsdfAtlas.ShapedRun shaped = primary != null ? primary.shape(fullText) : null;

            if (shaped != null) {
                // Build advances array from shaped glyphs.
                // Each shaped glyph maps back via cluster index to the original text position.
                for (MsdfAtlas.ShapedGlyph sg : shaped.glyphs) {
                    int cluster = sg.cluster;
                    if (cluster < 0 || cluster >= len) continue;

                    if (sg.glyphIndex == 0) {
                        // .notdef in primary font — use fallback chain per-codepoint
                        int cp = fullText.codePointAt(cluster);
                        var rg = fontManager.resolveGlyph(cp);
                        // Use += to accumulate: HarfBuzz may emit multiple glyphs
                        // for the same cluster (e.g. base + variation selector).
                        // A later non-.notdef glyph with xAdvance=0 must not
                        // overwrite a valid fallback-resolved advance.
                        advances[cluster] += rg != null ? (float)(rg.metrics().advance() * fontSize) : 0;
                    } else {
                        advances[cluster] += (float)(sg.xAdvance * fontSize);
                    }
                }
            } else {
                // HarfBuzz unavailable — fall back to per-codepoint measurement
                for (int i = 0; i < len; ) {
                    int cp = fullText.codePointAt(i);
                    var rg = fontManager.resolveGlyph(cp);
                    float adv = rg != null ? (float)(rg.metrics().advance() * fontSize) : 0;
                    advances[i] = adv;
                    int cc = Character.charCount(cp);
                    for (int j = 1; j < cc; j++) advances[i + j] = 0;
                    i += cc;
                }
            }

            // Compute max intrinsic width (no wrapping)
            float intrinsic = 0;
            for (float a : advances) intrinsic += a;
            this.maxIntrinsicWidth = intrinsic;

            // Greedy line-breaking
            // Each entry: [start, end, ellipsis] where ellipsis=1 means append "…"
            List<int[]> lineRanges = new ArrayList<>();
            int lineStart = 0;
            float lineWidth = 0;
            int lastBreak = 0;
            float widthAtLastBreak = 0;

            for (int i = 0; i < len; ) {
                int cp = fullText.codePointAt(i);
                int cc = Character.charCount(cp);

                // Check for hard line break
                if (cp == '\n') {
                    lineRanges.add(new int[]{lineStart, i, 0});
                    lineStart = i + cc;
                    lineWidth = 0;
                    lastBreak = lineStart;
                    widthAtLastBreak = 0;
                    i += cc;
                    continue;
                }

                float charAdv = advances[i];

                // Check if this is a break opportunity (beginning of next word/segment)
                if (bi.isBoundary(i) && i > lineStart) {
                    lastBreak = i;
                    widthAtLastBreak = lineWidth;
                }

                if (lineWidth + charAdv > maxWidth && lineWidth > 0) {
                    // Need to break
                    if (lastBreak > lineStart) {
                        // Break at last opportunity
                        lineRanges.add(new int[]{lineStart, lastBreak, 0});
                        lineStart = lastBreak;
                        lineWidth = lineWidth - widthAtLastBreak;
                        lastBreak = lineStart;
                        widthAtLastBreak = 0;
                    } else {
                        // No break opportunity — truncate with ellipsis
                        int truncI = i;
                        float truncW = lineWidth;
                        while (truncI > lineStart) {
                            int prevCp = fullText.codePointBefore(truncI);
                            int prevCC = Character.charCount(prevCp);
                            truncI -= prevCC;
                            truncW -= advances[truncI];
                            if (truncW + ellipsisAdv <= maxWidth) break;
                        }
                        lineRanges.add(new int[]{lineStart, truncI, 1});
                        // Skip rest of the unbreakable segment
                        while (i < len) {
                            int nextCp = fullText.codePointAt(i);
                            if (bi.isBoundary(i) && i > truncI) break;
                            i += Character.charCount(nextCp);
                        }
                        lineStart = i;
                        lineWidth = 0;
                        lastBreak = lineStart;
                        widthAtLastBreak = 0;
                        continue;
                    }
                }

                lineWidth += charAdv;
                i += cc;
            }
            // Final line
            if (lineStart < len) {
                lineRanges.add(new int[]{lineStart, len, 0});
            }
            // Edge case: text ends with \n
            if (!fullText.isEmpty() && fullText.charAt(len - 1) == '\n') {
                lineRanges.add(new int[]{len, len, 0});
            }

            // Build line metrics and positioned glyphs
            this.lines = new ArrayList<>(lineRanges.size());
            this.glyphs = new ArrayList<>();
            float maxLineWidth = 0;
            float y = 0;

            for (int[] range : lineRanges) {
                int start = range[0], end = range[1];
                boolean hasEllipsis = range[2] == 1;
                float x = 0;

                for (int i = start; i < end; ) {
                    int cp = fullText.codePointAt(i);
                    int cc = Character.charCount(cp);
                    var rg = fontManager.resolveGlyph(cp);

                    if (rg != null && rg.metrics().advance() > 0) {
                        glyphs.add(new PositionedGlyph(
                                cp, x, y, advances[i], rg, i));
                    }
                    x += advances[i];
                    i += cc;
                }

                // Append ellipsis glyph if this line was truncated
                if (hasEllipsis && ellipsisRg != null) {
                    glyphs.add(new PositionedGlyph(
                            '\u2026', x, y, ellipsisAdv, ellipsisRg, end));
                    x += ellipsisAdv;
                }

                float lw = x;
                maxLineWidth = Math.max(maxLineWidth, lw);
                float descentPx = primary != null ? (float)(-primary.hbDescent() * fontSize) : (1 - ascent) * fontSize;
                lines.add(new LineMetrics(start, end, 0, y, lw, ascent * fontSize, descentPx));
                y += lineH;
            }

            this.width = maxLineWidth;
            this.height = y > 0 ? y : lineH;
        }

        @Override
        public float width() { return width; }

        @Override
        public float height() { return height; }

        @Override
        public float maxIntrinsicWidth() { return maxIntrinsicWidth; }

        @Override
        public List<LineMetrics> lines() { return lines != null ? lines : List.of(); }

        @Override
        public int glyphPositionAt(float x, float y) {
            if (lines == null || lines.isEmpty()) return 0;

            // Find line by Y
            int lineIdx = 0;
            for (int i = 0; i < lines.size(); i++) {
                LineMetrics lm = lines.get(i);
                if (y < lm.y() + lm.ascent() + lm.descent()) {
                    lineIdx = i;
                    break;
                }
                lineIdx = i;
            }

            LineMetrics line = lines.get(lineIdx);
            // Find glyph by X within this line
            for (PositionedGlyph g : glyphs) {
                if (g.charIndex >= line.startIndex() && g.charIndex < line.endIndex()) {
                    if (x < g.x + g.advance / 2f) {
                        return g.charIndex;
                    }
                }
            }
            return line.endIndex();
        }

        @Override
        public List<Rect> rectsForRange(int start, int end) {
            if (lines == null || lines.isEmpty()) return List.of();

            List<Rect> rects = new ArrayList<>();
            for (LineMetrics line : lines) {
                int ls = line.startIndex(), le = line.endIndex();
                if (end <= ls || start >= le) continue;

                int rangeStart = Math.max(start, ls);
                int rangeEnd = Math.min(end, le);

                float x0 = Float.MAX_VALUE, x1 = 0;
                for (PositionedGlyph g : glyphs) {
                    if (g.charIndex >= rangeStart && g.charIndex < rangeEnd) {
                        var rg = g.resolved;
                        if (rg == null) continue;
                        var gm = rg.metrics();
                        if (rg.isColor() && rg.colorInfo() != null && rg.atlas() != null) {
                            float lx0 = Float.MAX_VALUE, lx1 = -Float.MAX_VALUE;
                            for (var layer : rg.colorInfo().layers()) {
                                var lm = rg.atlas().glyphByIndex(layer.glyphIndex());
                                if (lm == null) continue;
                                lx0 = Math.min(lx0, g.x + (float) (lm.planeLeft() * fontSize));
                                lx1 = Math.max(lx1, g.x + (float) (lm.planeRight() * fontSize));
                            }
                            if (lx0 < Float.MAX_VALUE && lx1 > -Float.MAX_VALUE) {
                                x0 = Math.min(x0, lx0);
                                x1 = Math.max(x1, lx1);
                                continue;
                            }
                        }
                        x0 = Math.min(x0, g.x + (float) (gm.planeLeft() * fontSize));
                        x1 = Math.max(x1, g.x + (float) (gm.planeRight() * fontSize));
                    }
                }
                if (x0 < Float.MAX_VALUE) {
                    rects.add(new Rect(x0, line.y(), x1 - x0, line.ascent() + line.descent()));
                }
            }
            return rects;
        }

        @Override
        public void paint(Object canvas, float x, float y) {
            // canvas is expected to be a PaintContext providing glyph emission
            if (!(canvas instanceof GlyphPainter painter)) return;
            if (glyphs == null) return;

            MsdfAtlas primary = fontManager.defaultAtlas();
            float ascent = primary != null ? (float) primary.hbAscent() : 0.8f;

            for (PositionedGlyph pg : glyphs) {
                var rg = pg.resolved;
                if (rg == null) continue;
                var g = rg.metrics();

                float baselineY = y + pg.y + ascent * fontSize;

                // COLRv0 color emoji: base glyph has empty outline (zero UVs),
                // but the layer glyphs have valid UVs — dispatch before UV check
                if (rg.isColor()) {
                    painter.emitColorGlyph(0, 0, 0, 0,
                            rg.colorInfo(), rg.atlas(), fontSize, baselineY, x + pg.x);
                    continue;
                }

                if (g.uvRight() <= g.uvLeft() || g.uvBottom() <= g.uvTop()) continue;

                float quadX = x + pg.x + (float)(g.planeLeft() * fontSize);
                float quadY = baselineY - (float)(g.planeTop() * fontSize);
                float quadW = (float)((g.planeRight() - g.planeLeft()) * fontSize);
                float quadH = (float)((g.planeTop() - g.planeBottom()) * fontSize);

                painter.emitGlyph(quadX, quadY, quadW, quadH, g, rg.atlas());
            }
        }

        @Override
        public void close() {
            // No native resources to free
        }
    }

    /**
     * A positioned glyph in the laid-out paragraph.
     */
    record PositionedGlyph(
            int codepoint,
            float x, float y,
            float advance,
            dev.everydaythings.filament.text.MsdfFontManager.ResolvedGlyph resolved,
            int charIndex
    ) {}

    /**
     * Interface for receiving positioned glyph quads during painting.
     *
     * <p>FilamentSurfacePainter implements this to emit MSDF quads.
     */
    public interface GlyphPainter {
        void emitGlyph(float x, float y, float w, float h,
                       MsdfAtlas.GlyphMetrics metrics, MsdfAtlas atlas);

        /**
         * Emit a COLRv0 color glyph with multiple colored layers.
         * Each layer is a separate glyph outline with its own palette color.
         *
         * @param x        quad X (from the base glyph's plane bounds)
         * @param y        quad Y
         * @param w        quad width
         * @param h        quad height
         * @param colorInfo COLRv0 layer decomposition
         * @param atlas    the atlas containing all layer glyphs
         * @param fontSize current font size in pixels
         * @param baselineY baseline Y position for computing layer quad positions
         * @param cursorX  cursor X position for computing layer quad positions
         */
        default void emitColorGlyph(float x, float y, float w, float h,
                                    ColrGlyphInfo colorInfo, MsdfAtlas atlas,
                                    float fontSize, float baselineY, float cursorX) {
            // Default: fall back to first layer as monochrome
            if (colorInfo.isColor()) {
                var first = colorInfo.layers().getFirst();
                var metrics = atlas.glyphByIndex(first.glyphIndex());
                if (metrics != null) emitGlyph(x, y, w, h, metrics, atlas);
            }
        }
    }
}
