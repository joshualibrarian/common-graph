package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.DatumNode;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.TypeRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a declared scene {@link Body} against a {@link ContextChain}.
 *
 * <p>Two passes:
 * <ol>
 *   <li><b>Variable substitution</b> — {@code ?}-mode reference targets
 *       ({@link TypeRef}) get substituted with their chain-resolved values.
 *       Recurses into nested Body targets.</li>
 *   <li><b>Style cascade</b> — every {@link SceneVocabulary.Style Style}
 *       record binding visible along the chain is collected.  The scene tree
 *       is walked; for each node, every Style whose {@link SceneVocabulary.Pattern
 *       Pattern} query matches the node has its (non-Pattern) bindings
 *       merged onto the node.  Inline bindings on the node win over
 *       cascaded styles (same precedence as CSS: inline beats class beats
 *       inherited).</li>
 * </ol>
 *
 * <p>Operator-frame dispatch (evaluating Now() / Add() / etc. at render-time)
 * is a follow-on slice; until then, expression-valued targets pass through
 * as their literal frame form.
 */
public final class SceneResolver {

    private SceneResolver() {}

    private static final ItemRef PATTERN_ROLE = ItemRef.iid(SceneVocabulary.Pattern.KEY);
    private static final ItemRef CHILDREN_ROLE = ItemRef.iid(SceneVocabulary.Children.KEY);
    private static final ItemRef SCENE_NODE_ARCHETYPE = ItemRef.iid(SceneNode.KEY);

    public static Body resolve(Body declaredScene, ContextChain chain) {
        return resolve(declaredScene, chain, new String[0]);
    }

    /**
     * Resolve a declared scene against this chain, applying the
     * {@code Style[qualifierKeys...]} cascade.  Empty qualifier list
     * (default) applies the unqualified Style cascade — the right thing
     * when rendering the default Scene of an item.  Pass
     * {@code SceneVocabulary.Aura.KEY} when rendering an Aura form so
     * Style[Aura] declarations apply.
     */
    public static Body resolve(Body declaredScene, ContextChain chain, String... qualifierKeys) {
        Objects.requireNonNull(declaredScene, "declaredScene");
        Objects.requireNonNull(chain, "chain");

        // Pass 1 — variable substitution + operator dispatch + collection-splat,
        // all unified in ContextChain.resolveBody.
        Body afterVariables = chain.resolveBody(declaredScene);

        // Pass 2 — style cascade.
        List<Body> styles = chain.collectStyles(qualifierKeys);
        if (styles.isEmpty()) return afterVariables;
        return applyStyles(afterVariables, styles);
    }

    // ==================================================================================
    // Pass 2 — style cascade
    // ==================================================================================

    private static Body applyStyles(Body body, List<Body> styles) {
        if (body.isAtomic()) return body;
        // Recurse into children first so cascade applies bottom-up.  Order
        // doesn't actually matter for the merge (siblings are independent),
        // but bottom-up makes intent clear: a node's resolved bindings reflect
        // its content's resolved bindings before its own style overlay.
        List<DatumNode> recursedBindings = new ArrayList<>();
        for (Binding b : body.bindings()) {
            if (b.target() instanceof Body subBody) {
                Body styledSub = applyStyles(subBody, styles);
                if (styledSub == subBody) {
                    recursedBindings.add(b);
                } else {
                    recursedBindings.add(new Binding(b.key(), styledSub, b.index()));
                }
            } else {
                recursedBindings.add(b);
            }
        }
        Body recursed = Body.of(body.head(), recursedBindings);

        // Match each style against this node.  Cascade order = chain order;
        // for a given binding key, the first matching style sets the value
        // (so later styles don't override earlier ones — and existing inline
        // bindings always win).
        List<Binding> withCascade = new ArrayList<>(recursed.bindings());
        Set<CompoundKey> alreadyHave = new HashSet<>();
        for (Binding b : withCascade) alreadyHave.add(b.key());

        for (Body style : styles) {
            if (!styleMatches(style, recursed)) continue;
            for (Binding apply : style.bindings()) {
                if (PATTERN_ROLE.equals(apply.role())) continue;   // skip the Pattern binding
                if (alreadyHave.contains(apply.key())) continue;   // inline / earlier-cascade wins
                withCascade.add(apply);
                alreadyHave.add(apply.key());
            }
        }
        if (withCascade.size() == recursed.bindings().size()) {
            return recursed;
        }
        return Body.of(recursed.head(), withCascade);
    }

    /**
     * True if {@code style}'s {@link SceneVocabulary.Pattern Pattern} query
     * matches {@code node}.  Head match uses archetype-hierarchy equivalence
     * (a {@code ?SceneNode} pattern matches SceneText / SceneContainer /
     * SceneBody nodes); binding match requires every pattern binding to find
     * a same-key, same-target binding on the node.
     */
    private static boolean styleMatches(Body style, Body node) {
        Body pattern = style.binding(CompoundKey.of(PATTERN_ROLE))
                .map(Binding::target)
                .filter(t -> t instanceof Body)
                .map(Body.class::cast)
                .orElse(null);
        if (pattern == null) return false;

        if (!headMatches(pattern, node)) return false;

        for (Binding patternBinding : pattern.bindings()) {
            if (!nodeHasMatchingBinding(node, patternBinding)) return false;
        }
        return true;
    }

    /**
     * Head-match: pattern head is a {@link TypeRef} (query-mode).  Match if
     * the node's head iid equals the pattern's iid, OR the pattern names
     * {@link SceneNode#KEY SceneNode} (the universal scene-node parent —
     * matches every structural scene-node archetype).
     *
     * <p>Future: walk the node's archetype chain via fetched manifests so
     * arbitrary intermediate archetypes match too.  First cut hard-codes
     * the SceneNode-as-wildcard case since that's what the matchClass /
     * matchId shortcuts emit.
     */
    private static boolean headMatches(Body pattern, Body node) {
        if (!(pattern.head() instanceof TypeRef typeRef)) return false;
        ItemRef wantedIid = typeRef.iid();
        if (wantedIid.equals(node.headRef())) return true;
        if (SCENE_NODE_ARCHETYPE.equals(wantedIid) && isStructuralSceneNode(node.headRef())) {
            return true;
        }
        return false;
    }

    private static boolean isStructuralSceneNode(ItemRef headIid) {
        return ItemRef.iid(SceneContainer.KEY).equals(headIid)
                || ItemRef.iid(SceneText.KEY).equals(headIid)
                || ItemRef.iid(SceneBody.KEY).equals(headIid);
    }

    /**
     * True if {@code node} carries a binding with the same compound key AND
     * the same target as {@code patternBinding}.  Equality on target uses
     * {@link Object#equals(Object)} — ItemRef-equality for references,
     * String-equality for literals, etc.
     *
     * <p>Note: pattern bindings on multi-valued roles (like Classes, which is
     * typically several bindings) match against ANY occurrence of that key
     * on the node — we walk all bindings under the role and look for one
     * whose target equals the pattern's.
     */
    private static boolean nodeHasMatchingBinding(Body node, Binding patternBinding) {
        Object wanted = patternBinding.target();
        for (Binding nb : node.bindings(patternBinding.key())) {
            if (Objects.equals(nb.target(), wanted)) return true;
        }
        return false;
    }
}
