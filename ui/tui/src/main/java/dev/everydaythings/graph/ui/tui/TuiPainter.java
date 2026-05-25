package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.quality.MediaVocabulary;
import dev.everydaythings.graph.quality.VisualVocabulary;
import dev.everydaythings.graph.scene.FontMetrics;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.scene.SceneBody;
import dev.everydaythings.graph.scene.SceneContainer;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.Viewport;
import dev.everydaythings.graph.value.Color;

import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * TuiPainter — terminal-cell painter for the TEXT fidelity.  Walks a
 * positioned scene tree depth-first and emits glyphs, alt text, and
 * line-broken container content to a {@link PrintWriter}.
 *
 * <p>First-cut implementation: hand-rolled output, no JLine yet.  The
 * presenter side of the pipeline is still pending, so for now this
 * painter accepts any scene tree (not necessarily "positioned") and
 * walks it naturally — containers introduce newlines, text emits its
 * content inline, body falls back through glyph → alt.
 *
 * <p>Viewport defaults to 80x24; once the painter is wired to a real
 * terminal (JLine), it'll read the actual size.  Font metrics report
 * one cell per character, line height one cell — TUI is fixed-cell.
 *
 * <p>Input event capture lands later; the {@link #onEvent} sink is
 * registerable but no events fire yet.
 */
public final class TuiPainter implements Painter {

    private final PrintWriter out;
    private final boolean ownsWriter;
    private final FontMetrics fontMetrics = new CellFontMetrics();

    private Viewport viewport;
    private Consumer<Body> eventSink = ignored -> {};
    private boolean open = true;

    /** Construct a TuiPainter writing to {@code System.out} with an 80x24 viewport. */
    public TuiPainter() {
        this(new PrintWriter(System.out, true), true, new Viewport(80, 24));
    }

    /** Construct a TuiPainter writing to the given writer with the given viewport. */
    public TuiPainter(PrintWriter out, Viewport viewport) {
        this(out, false, viewport);
    }

    private TuiPainter(PrintWriter out, boolean ownsWriter, Viewport viewport) {
        this.out = out;
        this.ownsWriter = ownsWriter;
        this.viewport = viewport;
    }

    // ==================================================================================
    // Painter SPI
    // ==================================================================================

    @Override
    public void paint(SceneNode tree) {
        if (!open) throw new IllegalStateException("painter is closed");
        Color rootForeground = tree == null
                ? null
                : tree.readColor(VisualVocabulary.Foreground.KEY).orElse(null);
        if (tree != null) paintNode(tree, 0, rootForeground);
        out.flush();
    }

    @Override
    public Viewport viewport() { return viewport; }

    @Override
    public FontMetrics fontMetrics() { return fontMetrics; }

    @Override
    public Fidelity fidelity() { return Fidelity.TEXT; }

    /** Register a sink for input events (none fire in this first cut). */
    public void onEvent(Consumer<Body> sink) {
        this.eventSink = sink == null ? ignored -> {} : sink;
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        if (ownsWriter) out.close();
        else out.flush();
    }

    // ==================================================================================
    // Tree walk
    // ==================================================================================

    private void paintNode(SceneNode node, int depth, Color inheritedFg) {
        Color fg = node.readColor(VisualVocabulary.Foreground.KEY).orElse(inheritedFg);
        switch (node) {
            case SceneText  text      -> paintText(text, depth, fg);
            case SceneBody  body      -> paintBody(body, depth, fg);
            case SceneContainer cont  -> paintContainer(cont, depth, fg);
            default                    -> writeIndented(depth, "<unknown scene node: " + node.getClass().getSimpleName() + ">", fg);
        }
    }

    private void paintText(SceneText text, int depth, Color fg) {
        String content = text.readLiteral(SceneVocabulary.Text.KEY, String.class).orElse(null);
        if (content == null || content.isEmpty()) return;
        writeIndented(depth, content, fg);
    }

    private void paintBody(SceneBody body, int depth, Color fg) {
        String glyph = body.readLiteral(MediaVocabulary.Glyph.KEY, String.class).orElse(null);
        if (glyph != null && !glyph.isEmpty()) {
            writeIndented(depth, glyph, fg);
            return;
        }
        String alt = body.readLiteral(MediaVocabulary.Alt.KEY, String.class).orElse(null);
        if (alt != null && !alt.isEmpty()) {
            writeIndented(depth, "[" + alt + "]", fg);
            return;
        }
        writeIndented(depth, "[body]", fg);
    }

    private void paintContainer(SceneContainer container, int depth, Color fg) {
        for (SceneNode child : container.children()) {
            paintNode(child, depth + 1, fg);
        }
    }

    private void writeIndented(int depth, String text, Color fg) {
        for (int i = 0; i < depth; i++) out.print("  ");
        if (fg != null) {
            out.print(fg.toAnsiForeground());
            out.print(text);
            out.println(Color.ANSI_RESET);
        } else {
            out.println(text);
        }
    }

    // ==================================================================================
    // Font metrics — one cell per character.
    // ==================================================================================

    private static final class CellFontMetrics implements FontMetrics {
        @Override public float measureWidth(String text, float fontSize) {
            return text == null ? 0 : text.length();
        }
        @Override public float lineHeight(float fontSize) {
            return 1;
        }
    }
}
