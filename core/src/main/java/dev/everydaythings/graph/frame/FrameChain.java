package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.datum.*;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.SignerOld;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.library_old.LibraryOld;
import dev.everydaythings.graph.library_old.LibraryIndex;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * A causally-ordered chain of frames on an item — the frame-native replacement
 * for {@link Dag} and {@link Log}.
 *
 * <p>FrameChain queries frames from {@link LibraryIndex#framesByItemPredicate},
 * topologically sorts them by {@link ThematicRole.Follows FOLLOWS} bindings,
 * and provides ordered iteration and fold.
 *
 * <p>The owning item is the LOCATION of event frames. A chess move is:
 * {@code MOVE { LOCATION→game, AGENT→white, THEME→knight, SOURCE→g1, GOAL→f3, FOLLOWS→prev }}.
 * The FOLLOWS binding creates causal ordering.
 *
 * <p>Usage:
 * <pre>{@code
 * FrameChain chain = new FrameChain(library, gameIid, movePredicate);
 *
 * // Stream in causal order
 * chain.stream().forEach(body -> ...);
 *
 * // Fold to materialize state
 * BoardState board = chain.fold(BoardState.initial(), (state, body) -> state.apply(body));
 *
 * // Append a new frame
 * chain.append(movePredicate, moveBindings, signer);
 * }</pre>
 *
 * @see Dag
 * @see Log
 */
public class FrameChain {

    private static final ItemID FOLLOWS_ROLE = ItemID.fromString(ThematicRole.Follows.KEY);

    private final LibraryOld library;
    private final ItemID item;       // LOCATION of frames
    private final ItemID predicate;  // frame type to query

    /**
     * Create a FrameChain for the given item and predicate.
     *
     * @param library   the library for storage and index access
     * @param item      the item ID these frames belong to (LOCATION role)
     * @param predicate the predicate (frame type) to query
     */
    public FrameChain(LibraryOld library, ItemID item, ItemID predicate) {
        this.library = Objects.requireNonNull(library, "library");
        this.item = Objects.requireNonNull(item, "item");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    public LibraryOld library() { return library; }
    public ItemID item() { return item; }
    public ItemID predicate() { return predicate; }

    // ==================================================================================
    // Query
    // ==================================================================================

    /**
     * Load all frames for this item+predicate, topologically sorted by FOLLOWS.
     *
     * <p>Frames with no FOLLOWS binding come first (roots). Frames that follow
     * other frames come after their predecessors. Within a topological level,
     * order is deterministic (sorted by body hash).
     *
     * @return stream of frame bodies in causal order
     */
    public Stream<FrameBodyOld> stream() {
        List<FrameBodyOld> bodies = loadAllBodies();
        if (bodies.isEmpty()) return Stream.empty();
        return topoSort(bodies).stream();
    }

    /**
     * Get the current heads — frames not referenced as FOLLOWS target by any other frame.
     *
     * <p>A single head means a linear chain. Multiple heads indicate concurrent branches
     * (fork). An empty list means no frames exist.
     *
     * @return list of head frame bodies (frontier)
     */
    public List<FrameBodyOld> heads() {
        List<FrameBodyOld> bodies = loadAllBodies();
        if (bodies.isEmpty()) return List.of();

        // Collect all body hashes referenced as FOLLOWS targets
        Set<ContentID> referenced = new HashSet<>();
        for (FrameBodyOld body : bodies) {
            List<Binding> followsBindings = body.getAllBindings(FOLLOWS_ROLE);
            for (Binding b : followsBindings) {
                ContentID targetCid = extractFollowsCid(b);
                if (targetCid != null) referenced.add(targetCid);
            }
        }

        // Heads are bodies whose hash is NOT referenced by anyone else
        List<FrameBodyOld> heads = new ArrayList<>();
        for (FrameBodyOld body : bodies) {
            if (!referenced.contains(body.hash())) {
                heads.add(body);
            }
        }

        // Deterministic order by body hash
        heads.sort(Comparator.comparing(b -> b.hash().encodeText()));
        return heads;
    }

    /**
     * Check if this chain is empty (no frames exist).
     */
    public boolean isEmpty() {
        return library.index()
                .map(idx -> idx.framesByItemPredicate(item, predicate).findAny().isEmpty())
                .orElse(true);
    }

    /**
     * Count of frames in this chain.
     */
    public long count() {
        return library.index()
                .map(idx -> idx.framesByItemPredicate(item, predicate).count())
                .orElse(0L);
    }

    // ==================================================================================
    // Fold
    // ==================================================================================

    /**
     * Fold over frames in causal order to materialize state.
     *
     * <p>Replaces Dag's {@code fold(op, event)} and Log's read-then-replay pattern.
     *
     * @param initial the initial state
     * @param reducer function that applies a frame body to the current state
     * @return the final state after all frames are applied
     */
    public <S> S fold(S initial, BiFunction<S, FrameBodyOld, S> reducer) {
        S state = initial;
        for (FrameBodyOld body : topoSort(loadAllBodies())) {
            state = reducer.apply(state, body);
        }
        return state;
    }

    // ==================================================================================
    // Append
    // ==================================================================================

    /**
     * Append a new frame with FOLLOWS pointing to current heads.
     *
     * <p>Creates a FrameBody with the given predicate and bindings, adds
     * FOLLOWS bindings to each current head, signs as a FrameRecord,
     * and stores in the library.
     *
     * @param bindings additional bindings (LOCATION is prepended automatically)
     * @param signer   who signs this frame
     * @return the stored FrameBody
     */
    public FrameBodyOld append(List<Binding> bindings, SignerOld signer) {
        Objects.requireNonNull(signer, "signer");

        List<FrameBodyOld> currentHeads = heads();

        // Build complete binding list
        List<Binding> allBindings = new ArrayList<>();

        // LOCATION → item (home binding for event frames)
        allBindings.add(new Binding(
                ThematicRole.Location.IID,
                BindingTarget.iid(item)));

        // FOLLOWS → each current head's body hash (identity — causal position IS part of event identity)
        for (FrameBodyOld head : currentHeads) {
            allBindings.add(new Binding(
                    FOLLOWS_ROLE,
                    BindingTarget.ref(head.hash())));
        }

        // Caller-provided bindings
        if (bindings != null) allBindings.addAll(bindings);

        // Create body and record
        FrameBodyOld body = new FrameBodyOld(predicate, allBindings);
        FrameRecordOld record = FrameRecordOld.create(body, signer);

        // Store
        library.storeFrame(body, record);

        return body;
    }

    /**
     * Append a frame with a single predicate and bindings map (convenience).
     */
    public FrameBodyOld append(Map<ItemID, BindingTarget> bindings, SignerOld signer) {
        List<Binding> bindingList = new ArrayList<>();
        if (bindings != null) {
            for (var entry : bindings.entrySet()) {
                bindingList.add(new Binding(entry.getKey(), entry.getValue()));
            }
        }
        return append(bindingList, signer);
    }

    // ==================================================================================
    // Internal
    // ==================================================================================

    /**
     * Load all frame bodies from the index.
     */
    private List<FrameBodyOld> loadAllBodies() {
        return library.index()
                .map(idx -> idx.framesByItemPredicate(item, predicate)
                        .map(ref -> library.loadFrameBody(ref.bodyHash()).orElse(null))
                        .filter(Objects::nonNull)
                        .toList())
                .orElse(List.of());
    }

    /**
     * Topologically sort frame bodies by FOLLOWS relationships.
     *
     * <p>Uses Kahn's algorithm. Roots (no FOLLOWS) come first.
     * Within a level, bodies are sorted by body hash for determinism.
     */
    private static List<FrameBodyOld> topoSort(List<FrameBodyOld> bodies) {
        if (bodies.isEmpty()) return List.of();
        if (bodies.size() == 1) return List.copyOf(bodies);

        // Index by body hash
        Map<ContentID, FrameBodyOld> byHash = new LinkedHashMap<>();
        for (FrameBodyOld b : bodies) byHash.put(b.hash(), b);

        // Build adjacency: child → parents (FOLLOWS targets)
        Map<ContentID, List<ContentID>> parents = new HashMap<>();
        // Build reverse adjacency: parent → children
        Map<ContentID, List<ContentID>> children = new HashMap<>();
        Map<ContentID, Integer> inDegree = new HashMap<>();

        for (FrameBodyOld b : bodies) {
            ContentID hash = b.hash();
            parents.putIfAbsent(hash, new ArrayList<>());
            children.putIfAbsent(hash, new ArrayList<>());
            inDegree.putIfAbsent(hash, 0);

            List<Binding> followsBindings = b.getAllBindings(FOLLOWS_ROLE);
            for (Binding fb : followsBindings) {
                ContentID parentHash = extractFollowsCid(fb);
                if (parentHash != null && byHash.containsKey(parentHash)) {
                    parents.get(hash).add(parentHash);
                    children.computeIfAbsent(parentHash, k -> new ArrayList<>()).add(hash);
                    inDegree.merge(hash, 1, Integer::sum);
                }
            }
        }

        // Kahn's algorithm
        List<FrameBodyOld> result = new ArrayList<>(bodies.size());
        Deque<ContentID> queue = new ArrayDeque<>();

        // Start with roots (in-degree 0), sorted for determinism
        List<ContentID> roots = new ArrayList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) roots.add(entry.getKey());
        }
        roots.sort(Comparator.comparing(ContentID::encodeText));
        queue.addAll(roots);

        while (!queue.isEmpty()) {
            ContentID current = queue.poll();
            FrameBodyOld body = byHash.get(current);
            if (body != null) result.add(body);

            List<ContentID> kids = children.getOrDefault(current, List.of());
            List<ContentID> readyKids = new ArrayList<>();
            for (ContentID kid : kids) {
                int newDegree = inDegree.merge(kid, -1, Integer::sum);
                if (newDegree == 0) readyKids.add(kid);
            }
            readyKids.sort(Comparator.comparing(ContentID::encodeText));
            queue.addAll(readyKids);
        }

        // If there were cycles or missing parents, append any remaining
        if (result.size() < bodies.size()) {
            Set<ContentID> included = new HashSet<>();
            for (FrameBodyOld b : result) included.add(b.hash());
            for (FrameBodyOld b : bodies) {
                if (!included.contains(b.hash())) result.add(b);
            }
        }

        return result;
    }

    /**
     * Extract a ContentID from a FOLLOWS binding target.
     *
     * <p>FOLLOWS targets are CIDs (body hashes of predecessor frames),
     * stored as RefTarget.
     */
    private static ContentID extractFollowsCid(Binding b) {
        if (b.target() instanceof BindingTarget.RefTarget ref) {
            return ref.asCid();
        }
        return null;
    }
}
