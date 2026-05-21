package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.scene.SceneNode;
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

    /**
     * Construct a Presenter bound to a viewport and a resolver.  The
     * viewport is currently unused but will be consulted when dimensional
     * unit resolution lands.
     */
    public Presenter(Viewport viewport, VariableResolver resolver) {
        this.viewport = viewport;
        this.resolver = resolver == null ? VariableResolver.NONE : resolver;
    }

    public Viewport viewport()         { return viewport; }
    public VariableResolver resolver() { return resolver; }

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

    /** Convenience: present and wrap the result as a {@link SceneNode}. */
    public SceneNode presentNode(Body tree) {
        return SceneNode.from(present(tree));
    }

    // ==================================================================================
    // Internal walk
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
}
