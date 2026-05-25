package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.DatumNode;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordered chain of entities the {@link SceneResolver} consults when resolving
 * a variable reference (a {@code ?}-mode target) in a scene binding.
 *
 * <p>The chain runs from most-specific to most-general.  For a Window
 * rendering item X on session S held by librarian L (with a user U
 * eventually), the chain is:
 *
 * <pre>
 *   contextItem (= X) → user → session → librarian
 * </pre>
 *
 * <p>The first chain entry whose manifest-record carries a binding with the
 * looked-up role wins; the binding's target is returned.  If no entry matches,
 * the lookup returns {@link Optional#empty()} and the resolver leaves the
 * original {@code ?}-mode target in place (unresolved — surfacing the missing
 * binding loud-but-rendering).
 *
 * <p>Variables are just bindings whose role IS the variable sememe.  No
 * CONFIG wrapper; no separate registry.  The chain walks records' top-level
 * bindings by role with no qualifiers — variables that need qualifiers can
 * use {@link #lookupByCompoundKey(CompoundKey)} instead.
 */
public final class ContextChain {

    /**
     * Default fuel budget for resolution — caps the number of resolver steps
     * a single top-level {@link #resolveBody(Body)} call may consume.
     * Defensive guard against runaway recursive operators (a Transform whose
     * template references itself, for instance) so render-time evaluation
     * stays bounded even with the Turing-complete operator vocabulary.
     */
    public static final int DEFAULT_RESOLUTION_FUEL = 10_000;

    private final List<Item> entries;
    private final List<Body> pushedBodies; // most-recent first; checked before entries in lookups

    /**
     * Build a chain from a list of entries in resolution order (most-specific
     * first).  Null entries are dropped — convenient for callers that pass a
     * potentially-empty user slot.
     */
    public ContextChain(List<Item> entries) {
        this(entries, List.of());
    }

    private ContextChain(List<Item> entries, List<Body> pushedBodies) {
        Objects.requireNonNull(entries, "entries");
        List<Item> filtered = new ArrayList<>(entries.size());
        for (Item item : entries) {
            if (item != null) filtered.add(item);
        }
        this.entries = List.copyOf(filtered);
        this.pushedBodies = List.copyOf(pushedBodies);
    }

    /**
     * A single-entry chain.  Used as the minimal chain when a caller dispatches
     * a frame without any surrounding scene-render context — typically a plain
     * {@link Librarian#submit(dev.everydaythings.graph.datum.Frame)
     * librarian.submit(frame)} call that wants the librarian itself in the
     * chain so librarian-implemented handlers (Lookup, etc.) remain reachable.
     */
    public static ContextChain singleton(Item item) {
        return new ContextChain(List.of(item));
    }

    /**
     * Return a new chain with {@code body} pushed to the most-specific
     * position.  Used by operators that establish per-iteration scope, like
     * Transform: each iteration's source item is pushed so {@code ?}-mode
     * variable lookups inside the template find that item's bindings first.
     *
     * <p>Pushed bodies are checked before {@link #entries() Item entries} in
     * role lookups (role-binding scan on the body's own bindings).  They do
     * not participate in archetype-chain walks, since they're typically
     * value-bodies without manifests.  The original chain is unchanged.
     */
    public ContextChain pushing(Body body) {
        Objects.requireNonNull(body, "body");
        List<Body> ext = new ArrayList<>(pushedBodies.size() + 1);
        ext.add(body);
        ext.addAll(pushedBodies);
        return new ContextChain(entries, ext);
    }

    /** The entries in resolution order; most-specific first.  Read-only view. */
    public List<Item> entries() {
        return entries;
    }

    /**
     * Walk the chain looking for a record binding whose role is {@code role}
     * and which has no qualifiers.  Returns the binding's target on the first
     * match; empty when no entry has the binding.
     */
    public Optional<Object> lookupByRole(ItemRef role) {
        Objects.requireNonNull(role, "role");
        return lookupByCompoundKey(CompoundKey.of(role));
    }

    /**
     * Walk the chain looking for a record binding whose compound key
     * (role + qualifiers) matches {@code key}.
     *
     * <p>Walk order, most-specific first:
     * <ol>
     *   <li>Pushed bodies (from {@link #pushing(Body)}): each body's own
     *       bindings are scanned directly.  No archetype-chain walk —
     *       pushed bodies are typically transient value-bodies without
     *       manifests (per-iteration source items in Transform, etc.).</li>
     *   <li>Item entries: each item's manifest records + its archetype
     *       chain walked via {@code manifest.body().head()} upward, so
     *       archetype-declared bindings reach instance-level lookups.</li>
     * </ol>
     *
     * <p>Returns the binding's target on the first match anywhere.
     */
    public Optional<Object> lookupByCompoundKey(CompoundKey key) {
        Objects.requireNonNull(key, "key");
        for (Body pushed : pushedBodies) {
            Optional<Binding> hit = pushed.binding(key);
            if (hit.isPresent()) return Optional.ofNullable(hit.get().target());
        }
        for (Item item : entries) {
            Librarian lib = item.librarian();
            Optional<Object> hit = walkArchetypeChainForBinding(item, lib, key);
            if (hit.isPresent()) return hit;
        }
        return Optional.empty();
    }

    /**
     * Walk an item's manifest record bindings, then its archetype's, then
     * its archetype's archetype's, etc., looking for a binding matching
     * {@code key}.  Same walk as {@code SceneCascade.sceneFor} — same
     * archetype-chain semantics, applied to a different role.
     */
    private static Optional<Object> walkArchetypeChainForBinding(
            Item start, Librarian lib, CompoundKey key) {
        Item current = start;
        ItemRef currentIid = start.iid();
        java.util.Set<ItemRef> visited = new java.util.HashSet<>();
        while (current != null && currentIid != null && visited.add(currentIid)) {
            Manifest manifest = current.current();
            if (manifest != null) {
                for (Record record : manifest.records()) {
                    Optional<Binding> hit = record.binding(key);
                    if (hit.isPresent()) {
                        return Optional.ofNullable(hit.get().target());
                    }
                }
                ItemRef nextHead = manifest.body().headRef();
                if (nextHead == null || nextHead.equals(currentIid)) break;
                currentIid = nextHead;
                current = lib != null ? lib.fetchItem(currentIid).orElse(null) : null;
            } else {
                // No manifest on this instance — fall to its archetype via the
                // Java archetype() override (mirrors what body.head() would be).
                ItemRef archetype = current.archetype();
                if (archetype == null || archetype.equals(currentIid)) break;
                currentIid = archetype;
                current = lib != null ? lib.fetchItem(currentIid).orElse(null) : null;
            }
        }
        return Optional.empty();
    }

    /**
     * Find the first chain entry whose archetype equals {@code archetype}.
     * Used by the dispatcher for chain-routed handlers: when the handler's
     * {@code @Seed.Handler} declares no {@code role=}, dispatch walks the
     * chain (most-specific first) for a live instance of the handler's
     * archetype rather than reading a routing role off the incoming frame.
     *
     * <p>Direct archetype-IID equality only.  Archetype-chain walking (so a
     * Chess instance matches a Game-archetype handler) is deliberately
     * deferred — no real handler stack currently exercises it.
     */
    public Optional<Item> findInstanceOf(ItemRef archetype) {
        Objects.requireNonNull(archetype, "archetype");
        for (Item item : entries) {
            if (archetype.equals(item.archetype())) return Optional.of(item);
        }
        return Optional.empty();
    }

    /**
     * The first {@link Librarian} reachable through any chain entry.
     * Used by the resolver to dispatch operator-frame targets during
     * resolution.  Empty when no chain entry has a librarian (shouldn't
     * happen in real usage — the chain always includes the librarian — but
     * keeps the API honest for unit-test contexts).
     */
    public Optional<Librarian> librarian() {
        for (Item item : entries) {
            Librarian lib = item.librarian();
            if (lib != null) return Optional.of(lib);
        }
        return Optional.empty();
    }

    /**
     * Dispatch {@code frame} through the librarian with this chain in scope.
     * The universal entry point for chain-aware dispatch: handlers declared
     * with no {@code role=} on {@code @Seed.Handler} reach their target by
     * the librarian walking this chain for an instance of the handler's
     * archetype.
     *
     * <p>Unlike {@link Librarian#submit(Frame)}, this path does not persist
     * the frame and does not fire referenced-item notifications — it is pure
     * dispatch.  Callers that want the persist-and-dispatch combo should use
     * {@code librarian.submit(frame)}; that builds its own
     * {@link #singleton(Item) singleton} chain wrapping the librarian.
     *
     * <p>Returns an empty list when the chain has no librarian, or when no
     * handler consumes the frame.
     */
    public List<Frame> dispatch(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        Librarian lib = librarian().orElse(null);
        if (lib == null) return List.of();
        return lib.dispatch(frame, this);
    }

    /**
     * Collect every default {@link SceneVocabulary.Style Style} record
     * binding seen along the chain — convenience for the no-qualifier
     * case (rendering the default Scene's style cascade).  Equivalent to
     * {@link #collectStyles(String...)} with no qualifier keys.
     */
    public List<Body> collectStyles() {
        return collectStyles(new String[0]);
    }

    /**
     * Collect every {@code Style[qualifierKeys...]} record binding seen
     * along the chain.  For each chain entry, walks its archetype chain
     * collecting matching Style bindings at every level.  Returns the
     * targets (the style bodies themselves), in chain-order (most-specific
     * first), then within each entry / archetype-level, in binding-index
     * order.
     *
     * <p>When rendering the {@code Scene[Aura]} form of an item, pass
     * {@code SceneVocabulary.Aura.KEY} so the cascade picks up only the
     * Aura-flavored styles; default-Scene styles aren't applied to the
     * Aura render.  (A future refinement could let default styles
     * cascade into qualified forms as a more-general base; for first
     * cut, qualifier match is strict.)
     */
    public List<Body> collectStyles(String... qualifierKeys) {
        CompoundKey styleKey = compoundStyleKey(qualifierKeys);
        List<Body> out = new ArrayList<>();
        for (Item item : entries) {
            collectStylesAlongArchetypeChain(item, item.librarian(), styleKey, out);
        }
        return out;
    }

    private static CompoundKey compoundStyleKey(String[] qualifierKeys) {
        ItemRef styleRole = ItemRef.iid(SceneVocabulary.Style.KEY);
        if (qualifierKeys == null || qualifierKeys.length == 0) {
            return CompoundKey.of(styleRole);
        }
        Object[] qualifiers = new Object[qualifierKeys.length];
        for (int i = 0; i < qualifierKeys.length; i++) {
            qualifiers[i] = ItemRef.fromString(qualifierKeys[i]);
        }
        return CompoundKey.of(styleRole, qualifiers);
    }

    /**
     * Walk an item's archetype chain (same head-walk as
     * {@link #walkArchetypeChainForBinding}), accumulating every Style
     * binding seen at each level, in declaration-index order within each
     * level.
     */
    private static void collectStylesAlongArchetypeChain(
            Item start, Librarian lib, CompoundKey styleKey, List<Body> out) {
        Item current = start;
        ItemRef currentIid = start.iid();
        java.util.Set<ItemRef> visited = new java.util.HashSet<>();
        while (current != null && currentIid != null && visited.add(currentIid)) {
            Manifest manifest = current.current();
            if (manifest != null) {
                for (Record record : manifest.records()) {
                    List<Binding> matches = new ArrayList<>(record.bindings(styleKey));
                    matches.sort((a, b) -> Long.compare(
                            a.index() == null ? 0L : a.index(),
                            b.index() == null ? 0L : b.index()));
                    for (Binding b : matches) {
                        if (b.target() instanceof Body body) out.add(body);
                    }
                }
                ItemRef nextHead = manifest.body().headRef();
                if (nextHead == null || nextHead.equals(currentIid)) break;
                currentIid = nextHead;
                current = lib != null ? lib.fetchItem(currentIid).orElse(null) : null;
            } else {
                ItemRef archetype = current.archetype();
                if (archetype == null || archetype.equals(currentIid)) break;
                currentIid = archetype;
                current = lib != null ? lib.fetchItem(currentIid).orElse(null) : null;
            }
        }
    }

    // ==================================================================================
    // Resolution — the general expression-evaluation surface operators can call.
    //
    // resolveBody is the top-level entry: walk a body, substituting TypeRefs from
    // the context chain and dispatching operator-headed bodies along the way.
    // resolveAllBindings is the helper for operators' default eager evaluate
    // (resolve every binding, but don't redispatch the body itself — we're
    // already at the operator boundary).  resolveOne resolves a single
    // binding's target.
    //
    // The walk threads a Fuel counter through recursive calls so a runaway
    // operator (self-referential template, etc.) can't lock the renderer up.
    // Default budget is DEFAULT_RESOLUTION_FUEL per top-level call; the limit
    // shows up as a clear IllegalStateException, not a stack overflow.
    // ==================================================================================

    /**
     * Resolve a body in this chain's context.  TypeRef-targeted bindings are
     * substituted from chain lookups; operator-headed nested bodies are
     * dispatched to their {@link dev.everydaythings.graph.operator.Operator
     * Operator} via {@code evaluate(Frame, ContextChain)}; structural bodies
     * (SceneText, SceneContainer, SceneBody, SceneStyle, value-bodies) recurse
     * into their bindings.  A binding target that evaluates to a
     * {@link Collection} expands into multiple indexed bindings under the same
     * role (the lazy-eval splat that lets Transform emit N children from one
     * template binding).
     */
    public Body resolveBody(Body body) {
        Objects.requireNonNull(body, "body");
        return resolveStructuralBody(body, new Fuel(DEFAULT_RESOLUTION_FUEL));
    }

    /**
     * Resolve every binding of {@code body}, returning a body whose targets
     * are fully resolved.  Same logic as {@link #resolveBody(Body)} — both
     * recurse into structural bindings and dispatch operator-headed binding
     * targets.  Used by {@code Operator.evaluate(Frame, ContextChain)}'s
     * default eager path; the named alias signals intent at the call site
     * (we're at the operator boundary, resolving our arguments).
     */
    public Body resolveAllBindings(Body body) {
        Objects.requireNonNull(body, "body");
        return resolveStructuralBody(body, new Fuel(DEFAULT_RESOLUTION_FUEL));
    }

    /**
     * Resolve a single binding's target in this chain's context.  Convenience
     * for operators that need to resolve one argument selectively (Transform
     * resolves only its Source; If resolves only its condition first; ...).
     * Returns the raw resolved value — primitive, Body, Collection, ItemRef,
     * whatever — for the operator to consume directly.
     */
    public Object resolveOne(Binding binding) {
        Objects.requireNonNull(binding, "binding");
        return resolveTarget(binding.target(), new Fuel(DEFAULT_RESOLUTION_FUEL));
    }

    // -------- internals --------

    private Body resolveStructuralBody(Body body, Fuel fuel) {
        fuel.consume();
        if (body.isAtomic()) return body;
        List<DatumNode> resolved = new ArrayList<>(body.bindings().size());
        long autoIndex = 0L;
        boolean anyChange = false;
        for (Binding b : body.bindings()) {
            Object originalTarget = b.target();
            Object newTarget = resolveTarget(originalTarget, fuel);
            long bindingIndex = b.index() == null ? autoIndex : b.index();
            if (newTarget instanceof Collection<?> coll) {
                // Splat: one binding becomes N indexed bindings under the same role.
                anyChange = true;
                long i = bindingIndex;
                for (Object elem : coll) {
                    resolved.add(new Binding(b.role(), b.qualifiers(), elem, i++));
                }
                autoIndex = Math.max(autoIndex, i);
            } else {
                if (newTarget != originalTarget) {
                    anyChange = true;
                    resolved.add(new Binding(b.role(), b.qualifiers(), newTarget, b.index()));
                } else {
                    resolved.add(b);
                }
                autoIndex = Math.max(autoIndex, bindingIndex + 1);
            }
        }
        if (!anyChange) return body;
        return Body.of(body.head(), resolved);
    }

    /**
     * Resolve a single binding target.  Returns the raw resolved value, which
     * may be: a literal pass-through, a TypeRef-substituted value, a resolved
     * structural Body, an operator's raw return (primitive / Body / Collection /
     * whatever), or the original target if nothing was resolvable.
     */
    private Object resolveTarget(Object target, Fuel fuel) {
        if (target instanceof TypeRef typeRef) {
            Object resolved = lookupByRole(typeRef.iid()).orElse(target);
            return finishResolution(resolved, fuel);
        }
        if (target instanceof Body subBody) {
            return finishResolution(subBody, fuel);
        }
        return target;
    }

    /**
     * Once a binding-target has been variable-resolved, decide what to do with
     * it: dispatch as operator (raw return), recurse as structural body, or
     * pass through as a primitive.
     */
    private Object finishResolution(Object value, Fuel fuel) {
        if (!(value instanceof Body subBody)) return value;
        if (subBody.head() instanceof ItemRef headRef && !isStructuralSceneBody(headRef)) {
            dev.everydaythings.graph.operator.Operator op = lookupOperator(headRef);
            if (op != null) {
                fuel.consume();
                return op.evaluate(Frame.of(subBody, List.of()), this);
            }
        }
        return resolveStructuralBody(subBody, fuel);
    }

    private dev.everydaythings.graph.operator.Operator lookupOperator(ItemRef headRef) {
        Librarian lib = librarian().orElse(null);
        if (lib == null) return null;
        return lib.fetchItem(headRef)
                .filter(dev.everydaythings.graph.operator.Operator.class::isInstance)
                .map(dev.everydaythings.graph.operator.Operator.class::cast)
                .orElse(null);
    }

    private static boolean isStructuralSceneBody(ItemRef headIid) {
        return ItemRef.iid(SceneContainer.KEY).equals(headIid)
                || ItemRef.iid(SceneText.KEY).equals(headIid)
                || ItemRef.iid(SceneBody.KEY).equals(headIid)
                || ItemRef.iid(SceneVocabulary.SceneStyle.KEY).equals(headIid);
    }

    /**
     * Bounded-fuel counter shared across one top-level resolution.  Decrements
     * on each {@code resolveBody} step (recursive sub-resolutions consume the
     * same budget) and throws when exhausted.  Defensive against operators
     * with runaway recursion in the Turing-complete expression vocabulary.
     */
    private static final class Fuel {
        private int remaining;
        Fuel(int initial) { this.remaining = initial; }
        void consume() {
            if (--remaining < 0) {
                throw new IllegalStateException(
                        "Expression too complex: resolution exceeded "
                                + DEFAULT_RESOLUTION_FUEL
                                + " steps.  Possible runaway recursive operator "
                                + "(a Transform whose template references itself, "
                                + "an If chain that diverges, ...).  Raise "
                                + "ContextChain.DEFAULT_RESOLUTION_FUEL if the "
                                + "expression is genuinely large.");
            }
        }
    }
}
