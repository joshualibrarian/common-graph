package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.quality.MediaVocabulary;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.scene.FontMetrics;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.scene.SceneBody;
import dev.everydaythings.graph.scene.SceneContainer;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.Viewport;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        paintNode(tree, 0);
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

    private void paintNode(SceneNode node, int depth) {
        switch (node) {
            case SceneText  text      -> paintText(text, depth);
            case SceneBody  body      -> paintBody(body, depth);
            case SceneContainer cont  -> paintContainer(cont, depth);
            default                    -> writeIndented(depth, "<unknown scene node: " + node.getClass().getSimpleName() + ">");
        }
    }

    private void paintText(SceneText text, int depth) {
        String content = readLiteral(text, SceneVocabulary.Text.KEY, String.class);
        if (content == null || content.isEmpty()) return;
        writeIndented(depth, content);
    }

    private void paintBody(SceneBody body, int depth) {
        String glyph = readLiteral(body, MediaVocabulary.Glyph.KEY, String.class);
        if (glyph != null && !glyph.isEmpty()) {
            writeIndented(depth, glyph);
            return;
        }
        String alt = readLiteral(body, MediaVocabulary.Alt.KEY, String.class);
        if (alt != null && !alt.isEmpty()) {
            writeIndented(depth, "[" + alt + "]");
            return;
        }
        writeIndented(depth, "[body]");
    }

    private void paintContainer(SceneContainer container, int depth) {
        List<SceneNode> children = readChildren(container);
        for (SceneNode child : children) {
            paintNode(child, depth + 1);
        }
    }

    // ==================================================================================
    // Binding readers
    //
    // The painter walks raw bindings (rather than fielded accessors) because
    // the scene-node classes don't yet expose typed readers for many slots.
    // Switching to fielded reads is a follow-up once the resolver lands.
    // ==================================================================================

    private <T> T readLiteral(SceneNode node, String roleKey, Class<T> type) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : node.bindings()) {
            if (role.equals(b.role())
                    && b.qualifiers().isEmpty()
                    && type.isInstance(b.target())) {
                return type.cast(b.target());
            }
        }
        return null;
    }

    private List<SceneNode> readChildren(SceneContainer container) {
        ItemRef childrenRole = ItemRef.iid(SceneVocabulary.Children.KEY);
        List<Binding> matches = new ArrayList<>();
        for (Binding b : container.bindings()) {
            if (childrenRole.equals(b.role()) && b.target() instanceof Body) {
                matches.add(b);
            }
        }
        matches.sort(Comparator.comparing(
                b -> b.index() == null ? Long.MAX_VALUE : b.index(),
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<SceneNode> children = new ArrayList<>(matches.size());
        for (Binding b : matches) {
            children.add(SceneNode.from((Body) b.target()));
        }
        return children;
    }

    private void writeIndented(int depth, String text) {
        for (int i = 0; i < depth; i++) out.print("  ");
        out.println(text);
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
