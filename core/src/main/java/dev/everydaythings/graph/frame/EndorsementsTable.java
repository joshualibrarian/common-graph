package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.item.Factory;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.mount.Mount;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Table of endorsed frames within an Item — implements {@code Map<FrameKey, Frame>}.
 *
 * <p>Endorsed frames are those included in the item's manifest — they compose
 * the item's identity and behavior. Mounts (path assignments) are stored
 * separately from frames in a parallel map.
 *
 * <p>Also implements {@link Iterable} of {@link FrameOld} for convenient for-each loops.
 */
public class EndorsementsTable extends AbstractMap<CompoundKey, FrameOld>
        implements Canonical, Iterable<FrameOld> {

    // ==================================================================================
    // Internal Storage
    // ==================================================================================

    /** Frame storage (insertion-ordered). */
    private final Map<CompoundKey, FrameOld> frames = new LinkedHashMap<>();

    /** Mount metadata — separate from frames. */
    private final Map<CompoundKey, List<Mount>> mounts = new LinkedHashMap<>();

    /** Owner item (transient). */
    private transient ItemOld owningItem;

    /** Change listener — notified when frames are added or removed. */
    private transient Runnable onChanged;

    // ==================================================================================
    // Owner Tracking
    // ==================================================================================

    public void setOwner(ItemOld owner) {
        this.owningItem = owner;
        for (FrameOld frame : frames.values()) {
            frame.setOwner(owner);
        }
    }

    /** Subscribe to frame changes (add/remove). Only one listener at a time. */
    public void onChanged(Runnable listener) {
        this.onChanged = listener;
    }

    private void notifyChanged() {
        if (onChanged != null) onChanged.run();
    }

    // ==================================================================================
    // Map<FrameKey, Frame> Implementation
    // ==================================================================================

    @Override
    public Set<Entry<CompoundKey, FrameOld>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<CompoundKey, FrameOld>> iterator() {
                return frames.entrySet().iterator();
            }
            @Override public int size() { return frames.size(); }
        };
    }

    @Override
    public FrameOld get(Object key) {
        return frames.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return frames.containsKey(key);
    }

    @Override
    public int size() {
        return frames.size();
    }

    @Override
    public boolean isEmpty() {
        return frames.isEmpty();
    }

    @Override
    public void clear() {
        boolean wasNonEmpty = !frames.isEmpty();
        frames.clear();
        mounts.clear();
        if (wasNonEmpty) notifyChanged();
    }

    @Override
    public FrameOld remove(Object key) {
        if (key instanceof CompoundKey fk) {
            mounts.remove(fk);
        }
        FrameOld removed = frames.remove(key);
        if (removed != null) notifyChanged();
        return removed;
    }

    // ==================================================================================
    // Iterable<Frame>
    // ==================================================================================

    @Override
    public Iterator<FrameOld> iterator() {
        return frames.values().iterator();
    }

    /** Stream over all frames. */
    public Stream<FrameOld> stream() {
        return frames.values().stream();
    }

    // ==================================================================================
    // Frame Management
    // ==================================================================================

    /** Add a frame (with optional mounts). */
    public void add(FrameOld frame) {
        frames.put(frame.frameKey(), frame);
        if (owningItem != null) {
            frame.setOwner(owningItem);
        }
        notifyChanged();
    }

    /** Add a frame with mounts. */
    public void add(FrameOld frame, List<Mount> frameMounts) {
        frames.put(frame.frameKey(), frame);
        if (owningItem != null) {
            frame.setOwner(owningItem);
        }
        if (frameMounts != null && !frameMounts.isEmpty()) {
            mounts.put(frame.frameKey(), List.copyOf(frameMounts));
        }
        notifyChanged();
    }

    /** Get frame by key. */
    public Optional<FrameOld> getFrame(CompoundKey key) {
        return Optional.ofNullable(frames.get(key));
    }

    /** Remove by key. */
    public boolean removeByKey(CompoundKey key) {
        mounts.remove(key);
        boolean removed = frames.remove(key) != null;
        if (removed) notifyChanged();
        return removed;
    }

    // ==================================================================================
    // Mount Management
    // ==================================================================================

    /** Add a mount for a frame key. */
    public void addMount(CompoundKey key, Mount mount) {
        mounts.computeIfAbsent(key, k -> new ArrayList<>()).add(mount);
    }

    /** Get all mounts for a frame key. */
    public List<Mount> mountsFor(CompoundKey key) {
        return mounts.getOrDefault(key, List.of());
    }

    /** Get path mounts for a frame key. */
    public List<Mount.PathMount> pathMountsFor(CompoundKey key) {
        return FrameOld.filterPathMounts(mountsFor(key));
    }

    /** Does the given frame have any path mounts? */
    public boolean hasPathMount(CompoundKey key) {
        return FrameOld.hasPathMount(mountsFor(key));
    }

    /** Get the primary path mount for a frame key. */
    public Mount.PathMount primaryPathMount(CompoundKey key) {
        return FrameOld.primaryPathMount(mountsFor(key));
    }

    // ==================================================================================
    // Live Instance Operations (delegates to Frame.instance)
    // ==================================================================================

    /** Store a live decoded instance. */
    public void setLive(CompoundKey key, Object instance) {
        FrameOld frame = frames.get(key);
        if (frame == null) {
            throw new IllegalArgumentException("No frame for key: " + key);
        }
        frame.setInstance(instance);
    }


    /** Get a live decoded instance by key (typed). */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getLive(CompoundKey key, Class<T> type) {
        FrameOld frame = frames.get(key);
        if (frame == null) return Optional.empty();
        Object instance = frame.instance();
        if (instance != null && (type.isInstance(instance) || primitiveMatches(type, instance))) {
            return Optional.of((T) instance);
        }
        return Optional.empty();
    }

    private static boolean primitiveMatches(Class<?> type, Object value) {
        if (!type.isPrimitive()) return false;
        return (type == int.class && value instanceof Integer)
                || (type == long.class && value instanceof Long)
                || (type == float.class && value instanceof Float)
                || (type == double.class && value instanceof Double)
                || (type == boolean.class && value instanceof Boolean)
                || (type == byte.class && value instanceof Byte)
                || (type == short.class && value instanceof Short)
                || (type == char.class && value instanceof Character);
    }

    /** Get a live decoded instance by key (untyped). */
    public Optional<Object> getLive(CompoundKey key) {
        FrameOld frame = frames.get(key);
        if (frame == null) return Optional.empty();
        return Optional.ofNullable(frame.instance());
    }

    /** Iterate over all live instances. */
    public void forEachLive(Consumer<Object> action) {
        for (FrameOld frame : frames.values()) {
            Object instance = frame.instance();
            if (instance != null) action.accept(instance);
        }
    }

    /** Iterate over live instances of a specific type. */
    @SuppressWarnings("unchecked")
    public <T> void forEachLive(Class<T> filter, Consumer<T> action) {
        for (FrameOld frame : frames.values()) {
            Object instance = frame.instance();
            if (instance == null) continue;
            if (filter.isInstance(instance)) {
                action.accept((T) instance);
            }
        }
    }

    /** Check if a live instance exists for the given key. */
    public boolean hasLive(CompoundKey key) {
        FrameOld frame = frames.get(key);
        return frame != null && frame.instance() != null;
    }

    // ==================================================================================
    // Frame Filtering
    // ==================================================================================

    /** Get all bare frames (type == FrameBody.TYPE_ID — semantic assertions without component wrapper). */
    public Stream<FrameOld> bareFrames() {
        return frames.values().stream().filter(FrameOld::isBareFrame);
    }

    /** Remove all bare frames. */
    public void removeBareFrames() {
        var toRemove = frames.values().stream()
                .filter(FrameOld::isBareFrame)
                .map(FrameOld::frameKey)
                .toList();
        for (var key : toRemove) {
            removeByKey(key);
        }
    }

    /** Get frames matching a predicate filter. */
    public Stream<FrameOld> framesWithPredicate(ItemID predicate) {
        return frames.values().stream()
                .filter(f -> predicate.equals(f.type()));
    }

    // ==================================================================================
    // Mount-Based Tree Navigation
    // ==================================================================================

    /** Get all frames that have at least one PathMount. */
    public Stream<FrameOld> mounted() {
        return frames.values().stream()
                .filter(f -> hasPathMount(f.frameKey()));
    }

    /** Get the frame mounted at the exact path. */
    public Optional<FrameOld> atPath(String path) {
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(path);
        return frames.values().stream()
                .filter(f -> pathMountsFor(f.frameKey()).stream()
                        .anyMatch(pm -> pm.path().equals(canonical)))
                .findFirst();
    }

    /** Get root-level frames (depth-1 path mounts). */
    public List<FrameOld> roots() {
        return frames.values().stream()
                .filter(f -> pathMountsFor(f.frameKey()).stream()
                        .anyMatch(pm -> dev.everydaythings.graph.item.mount.PathUtil.depth(pm.path()) == 1))
                .toList();
    }

    /** Get frames that are immediate children of the given path. */
    public List<FrameOld> children(String parentPath) {
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(parentPath);
        return frames.values().stream()
                .filter(f -> pathMountsFor(f.frameKey()).stream()
                        .anyMatch(pm -> pm.isChildOf(canonical)))
                .toList();
    }

    /** Get all frames that are descendants of the given path. */
    public Stream<FrameOld> descendants(String path) {
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(path);
        String prefix = canonical.equals("/") ? "/" : canonical + "/";
        return frames.values().stream()
                .filter(f -> pathMountsFor(f.frameKey()).stream()
                        .anyMatch(pm -> pm.path().startsWith(prefix) && !pm.path().equals(canonical)));
    }

    /** Check if any frames have path mounts under the given path. */
    public boolean hasChildren(String path) {
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(path);
        String prefix = canonical.equals("/") ? "/" : canonical + "/";
        return frames.values().stream()
                .anyMatch(f -> pathMountsFor(f.frameKey()).stream()
                        .anyMatch(pm -> pm.path().startsWith(prefix) && !pm.path().equals(canonical)));
    }

    // ==================================================================================
    // Virtual Directory Support
    // ==================================================================================

    /** A child at a path — real mounted component or virtual directory. */
    public record PathChild(String segment, String fullPath, FrameOld frame) {
        public boolean isVirtual() { return frame == null; }
    }

    /** Get immediate children at a path, including virtual directories. */
    public List<PathChild> childrenAt(String parentPath) {
        String canon = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(parentPath);
        int targetDepth = dev.everydaythings.graph.item.mount.PathUtil.depth(canon) + 1;
        String prefix = canon.equals("/") ? "/" : canon + "/";

        Map<String, FrameOld> childMap = new LinkedHashMap<>();

        for (FrameOld frame : frames.values()) {
            for (Mount.PathMount pm : pathMountsFor(frame.frameKey())) {
                String mountPath = pm.path();

                if (canon.equals("/")) {
                    if (mountPath.equals("/")) continue;
                } else {
                    if (!mountPath.startsWith(prefix)) continue;
                }

                String[] segments = mountPath.substring(1).split("/");
                if (segments.length < targetDepth) continue;

                String childSegment = segments[targetDepth - 1];
                String childPath = canon.equals("/")
                        ? "/" + childSegment
                        : canon + "/" + childSegment;

                if (segments.length == targetDepth) {
                    childMap.put(childPath, frame);
                } else if (!childMap.containsKey(childPath)) {
                    childMap.put(childPath, null);
                }
            }
        }

        return childMap.entrySet().stream()
                .map(e -> {
                    String path = e.getKey();
                    String segment = path.substring(path.lastIndexOf('/') + 1);
                    return new PathChild(segment, path, e.getValue());
                })
                .toList();
    }

    /** Reverse-lookup: find the primary presentation path for a frame key. */
    public Optional<String> pathForKey(CompoundKey key) {
        Mount.PathMount pm = primaryPathMount(key);
        return pm != null ? Optional.of(pm.path()) : Optional.empty();
    }

    // ==================================================================================
    // Endorsement Building
    // ==================================================================================

    /** Build endorsements from all frames for manifest serialization. */
    public List<FrameEndorsement> buildEndorsements() {
        List<FrameEndorsement> result = new ArrayList<>();
        for (FrameOld frame : frames.values()) {
            result.add(frame.toEndorsement(mountsFor(frame.frameKey())));
        }
        return Collections.unmodifiableList(result);
    }

    // ==================================================================================
    // Canonical Implementation
    // ==================================================================================

    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject array = CBORObject.NewArray();
        for (FrameOld frame : frames.values()) {
            array.Add(frame.toCborTree(mountsFor(frame.frameKey())));
        }
        return array;
    }

    @Factory
    public static EndorsementsTable fromCborTree(CBORObject node) {
        EndorsementsTable table = new EndorsementsTable();
        if (node != null && node.getType() == com.upokecenter.cbor.CBORType.Array) {
            for (CBORObject entryNode : node.getValues()) {
                FrameOld frame = Canonical.fromCborTree(entryNode, FrameOld.class, Scope.RECORD);
                if (frame != null) {
                    table.add(frame, frame.decodedMounts());
                }
            }
        }
        return table;
    }

    /** Display helper. */
    public String displayToken() {
        return "Frames";
    }

    public String emoji() {
        return "📦";
    }

    public String displaySubtitle() {
        int count = size();
        return count + " frame" + (count == 1 ? "" : "s");
    }
}
