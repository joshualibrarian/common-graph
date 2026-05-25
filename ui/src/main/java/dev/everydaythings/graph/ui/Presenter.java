package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.scene.Bounds;
import dev.everydaythings.graph.scene.FontMetrics;
import dev.everydaythings.graph.scene.SceneBody;
import dev.everydaythings.graph.scene.SceneContainer;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.VariableResolver;
import dev.everydaythings.graph.scene.Viewport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Presenter — the window-side stage of the scene pipeline.  Takes a
 * resolved scene tree and produces a positioned tree the painter can
 * consume.
 *
 * <p>First-cut responsibility: substitute Variable references in binding
 * targets via a {@link VariableResolver} supplied by the session.  A
 * binding whose target is an {@link ItemRef} matching a Variable the
 * resolver knows is rewritten to carry the variable's current value.
 * Bindings whose targets aren't Variable references pass through
 * unchanged.
 *
 * <p>Pipeline shape (per docs/scene.md): the presenter clones the tree
 * stage-by-stage rather than mutating in place.  Scene Datums are
 * ephemeral and never hashed for local renders, so cloning is cheap
 * (lazy ids), and immutability stays intact for everything else.
 *
 * <p>What's <i>not</i> yet here:
 *
 * <ul>
 *   <li>Dimensional unit resolution ({@code "50%"} → pixel float).  Lands
 *       when we wire up a graphical painter; TUI doesn't need it.</li>
 *   <li>Two-phase layout solving (measure → position).  Same — lands when
 *       we have a painter that needs absolute placement.</li>
 *   <li>Text measurement via {@link dev.everydaythings.graph.scene.FontMetrics
 *       FontMetrics}.  Same.</li>
 *   <li>Expression evaluation (operator bodies as binding targets).  The
 *       operator pipeline already exists in :core; the presenter will
 *       evaluate expressions once their resolver-side path is wired.</li>
 * </ul>
 *
 * <p>The minimal current implementation is enough to drive a clock: a
 * {@link dev.everydaythings.graph.scene.SceneText SceneText} whose
 * {@code text} binding targets {@link
 * dev.everydaythings.graph.CoreVocabulary.CurrentTime CurrentTime}
 * resolves to whatever the session's resolver returns for "now."
 */
public final class Presenter {

    private final Viewport viewport;
    private final VariableResolver resolver;
    private final FontMetrics fontMetrics;

    /**
     * Construct a Presenter bound to a viewport, a resolver, and a font
     * metrics source.  The layout pass measures {@link
     * dev.everydaythings.graph.scene.SceneText SceneText} nodes through
     * the metrics; graphical painters supply real measurements,
     * {@link FontMetrics#NONE} works for tests and TUI parity.
     */
    public Presenter(Viewport viewport, VariableResolver resolver, FontMetrics fontMetrics) {
        this.viewport = viewport;
        this.resolver = resolver == null ? VariableResolver.NONE : resolver;
        this.fontMetrics = fontMetrics == null ? FontMetrics.NONE : fontMetrics;
    }

    /**
     * Construct a Presenter with no font metrics — layout measurements fall
     * back to {@link FontMetrics#NONE} (reports zero-width text).  The TUI
     * doesn't need text measurement to render, and tests / fixtures that
     * exercise the presenter without a painter don't either.  When a real
     * painter is in the loop, prefer {@link #Presenter(Viewport,
     * VariableResolver, FontMetrics)} so layout gets real measurements.
     */
    public Presenter(Viewport viewport, VariableResolver resolver) {
        this(viewport, resolver, FontMetrics.NONE);
    }

    public Viewport viewport()         { return viewport; }
    public VariableResolver resolver() { return resolver; }
    public FontMetrics fontMetrics()   { return fontMetrics; }

    /**
     * Walk the tree, substitute Variable references, return a fresh
     * positioned tree.  When no binding references a Variable the
     * resolver knows, the result is structurally identical to the input.
     *
     * <p>Operates at the raw {@link Body} level: pre-substitution scene
     * trees may carry {@link ItemRef} targets in slots whose typed view
     * (e.g., {@code SceneText.text} is {@code String}) wouldn't accept
     * them.  After substitution, the result is well-formed for wrapping
     * via {@link SceneNode#from(Body)} — call that at the painter
     * boundary if you need the typed view.
     */
    public Body present(Body tree) {
        return presentBody(tree);
    }

    /**
     * Convenience: present and wrap the result as a {@link SceneNode}, then
     * run the layout pass so every node has {@link SceneNode#bounds() bounds}
     * populated.  This is what the {@link RenderLoop} calls each tick.
     */
    public SceneNode presentNode(Body tree) {
        SceneNode wrapped = SceneNode.from(present(tree));
        layout(wrapped, 0, 0, viewport.width());
        return wrapped;
    }

    // ==================================================================================
    // Pass 1 — variable substitution
    // ==================================================================================

    private Body presentBody(Body body) {
        List<Binding> rewritten = new ArrayList<>(body.bindings().size());
        for (Binding b : body.bindings()) {
            rewritten.add(presentBinding(b));
        }
        return Body.of(body.headRef(), rewritten);
    }

    private Binding presentBinding(Binding binding) {
        Object target = binding.target();
        Object resolved = target;

        if (target instanceof ItemRef ref) {
            Optional<Object> substituted = resolver.resolve(ref);
            if (substituted.isPresent()) {
                resolved = substituted.get();
            }
        } else if (target instanceof Body childBody) {
            resolved = presentBody(childBody);
        }

        if (resolved == target) {
            return binding;
        }
        return new Binding(binding.role(), binding.qualifiers(), resolved, binding.index());
    }

    // ==================================================================================
    // Pass 2 — layout
    //
    // First-cut algorithm: vertical stack.  Each container takes the full
    // available width and stacks its children top-to-bottom; each child
    // gets a Bounds reflecting its measured size.  Text is measured via
    // {@link FontMetrics}; body uses a placeholder intrinsic size until
    // fidelity-aware sizing lands.
    //
    // Deferred: padding, margin, gap, flex / grid / absolute positioning,
    // alignment, text wrapping, viewport-relative units, min/max
    // constraints, transforms, aspect-ratio enforcement.  All of these
    // are real scene-vocabulary bindings; they just don't influence
    // layout yet.
    // ==================================================================================

    private static final float DEFAULT_FONT_SIZE = 16f;
    private static final float DEFAULT_BODY_WIDTH = 100f;
    private static final float DEFAULT_BODY_HEIGHT = 100f;

    /**
     * Assign bounds to {@code node} and its descendants.  Returns the
     * total height consumed by the subtree at {@code (x, y)} with the
     * given available width.
     */
    private float layout(SceneNode node, float x, float y, float availableWidth) {
        if (node instanceof SceneText text) {
            String content = readLiteralBinding(text, SceneVocabulary.Text.KEY);
            float fontSize = DEFAULT_FONT_SIZE;
            float w = content == null ? 0 : fontMetrics.measureWidth(content, fontSize);
            float h = fontMetrics.lineHeight(fontSize);
            float clamped = Math.min(w, availableWidth);
            text.bounds(new Bounds(x, y, clamped, h));
            return h;
        }
        if (node instanceof SceneContainer container) {
            float runningY = y;
            for (SceneNode child : container.children()) {
                float childHeight = layout(child, x, runningY, availableWidth);
                runningY += childHeight;
            }
            float containerHeight = runningY - y;
            container.bounds(new Bounds(x, y, availableWidth, containerHeight));
            return containerHeight;
        }
        if (node instanceof SceneBody body) {
            // Placeholder size until SceneBody declares fidelity-aware
            // intrinsic dimensions (image natural size, model bounding box,
            // glyph metrics, etc.).  Keeps layout total finite so the
            // pipeline produces deterministic output even on body nodes.
            body.bounds(new Bounds(x, y, DEFAULT_BODY_WIDTH, DEFAULT_BODY_HEIGHT));
            return DEFAULT_BODY_HEIGHT;
        }
        node.bounds(new Bounds(x, y, 0, 0));
        return 0;
    }

    /**
     * Read a single literal binding from {@code node} whose role is
     * {@code roleKey} and whose target is a string-like literal.  Returns
     * {@code null} when the binding is absent or has a non-string target.
     */
    private static String readLiteralBinding(SceneNode node, String roleKey) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : node.bindings()) {
            if (role.equals(b.role()) && b.target() instanceof String s) {
                return s;
            }
        }
        return null;
    }
}
