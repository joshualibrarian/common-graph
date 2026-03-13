package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Factory;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.mount.Mount;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Table of frames within an Item.
 *
 * <p>Frames are the single primitive — endorsed semantic assertions that
 * compose an Item's identity and behavior. This table holds both frame
 * metadata (entries) and live decoded instances.
 *
 * <p>Extends {@link AbstractSet} with keyed lookup by {@link FrameKey}.
 * Entries are {@link FrameEntry}.
 *
 * <p>The FrameTable is the source of truth for what an item contains.
 * Field bindings ({@code @Item.Frame}) are optional developer ergonomics
 * that bind to entries in this table.
 */
public class FrameTable extends AbstractSet<FrameEntry> implements Canonical {

    // ==================================================================================
    // Owner Tracking
    // ==================================================================================

    /** The Item that owns this FrameTable. */
    private transient Item owningItem;

    /**
     * Set the owning Item. Called during Item initialization.
     */
    public void setOwner(Item owner) {
        this.owningItem = owner;
        // Propagate to existing entries so entry.ref() works
        for (FrameEntry entry : entries.values()) {
            entry.setOwner(owner);
        }
    }

    // ==================================================================================
    // Component Display
    // ==================================================================================

    public String displayToken() {
        return "Frames";
    }

    public boolean isExpandable() {
        return !isEmpty();
    }

    public String colorCategory() {
        return "frames";
    }

    public String displaySubtitle() {
        int count = size();
        return count + " frame" + (count == 1 ? "" : "s");
    }

    public String emoji() {
        return "📦";  // Box for content/components
    }


    // ==================================================================================
    // Property Implementation
    // ==================================================================================

    /**
     * Resolve a string token to a FrameKey.
     *
     * @param token The string name (e.g., "vault", "policy")
     * @return The FrameKey if an entry matches, or empty
     */
    public Optional<FrameKey> resolveAlias(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        // 1) Try literal key match
        FrameKey literalKey = FrameKey.literal(token);
        if (entries.containsKey(literalKey)) {
            return Optional.of(literalKey);
        }

        // 2) Alias / aliasRef lookup (exact match)
        for (Map.Entry<FrameKey, FrameEntry> e : entries.entrySet()) {
            FrameEntry entry = e.getValue();
            if (token.equals(entry.alias())) {
                return Optional.of(e.getKey());
            }
            if (entry.aliasRef() != null && token.equals(entry.aliasRef().encodeText())) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    /** Metadata entries (FrameKey -> FrameEntry with type, CID, etc.) */
    private final Map<FrameKey, FrameEntry> entries = new LinkedHashMap<>();

    // ==================================================================================
    // Collection Implementation
    // ==================================================================================

    public String collectionName() {
        return "frames";
    }

    public Class<FrameEntry> entryType() {
        return FrameEntry.class;
    }

    public FrameKey keyOf(FrameEntry entry) {
        return entry.frameKey();
    }

    public Optional<FrameEntry> get(FrameKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    public boolean removeByKey(FrameKey key) {
        FrameEntry entry = entries.get(key);
        boolean had = entry != null;
        if (had) {
            entries.remove(key);
        }
        return had;
    }

    @Override
    public boolean add(FrameEntry entry) {
        FrameKey key = entry.frameKey();
        boolean isNew = !entries.containsKey(key);
        entries.put(key, entry);
        // Set owner so entry.ref() works
        if (owningItem != null) {
            entry.setOwner(owningItem);
        }
        return isNew;
    }

    @Override
    public Iterator<FrameEntry> iterator() {
        return entries.values().iterator();
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof FrameEntry entry) {
            return removeByKey(keyOf(entry));
        }
        return false;
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof FrameEntry entry) {
            return get(keyOf(entry)).isPresent();
        }
        return false;
    }

    // ==================================================================================
    // Live Instance Operations
    // ==================================================================================

    /**
     * Store a live decoded instance for a component.
     *
     * @param key      The frame key
     * @param instance The decoded instance
     */
    public void setLive(FrameKey key, Object instance) {
        setLive(key, null, instance);
    }

    /**
     * Store a live decoded instance for a component.
     *
     * @param key      The frame key
     * @param alias    The human-facing alias (indexed on the entry, not the instance)
     * @param instance The decoded instance
     */
    public void setLive(FrameKey key, String alias, Object instance) {
        FrameEntry entry = entries.get(key);
        if (entry == null) {
            throw new IllegalArgumentException("No FrameEntry for key: " + key);
        }
        entry.setInstance(instance);
        if (alias != null && !alias.isBlank()) {
            entry.setAlias(alias);
        }
    }

    /**
     * Get a live decoded instance by key.
     *
     * @param key  The frame key
     * @param type The expected type
     * @return The instance if present and assignable to type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getLive(FrameKey key, Class<T> type) {
        FrameEntry entry = entries.get(key);
        if (entry == null) return Optional.empty();
        Object instance = entry.instance();
        if (instance != null && (type.isInstance(instance) || primitiveMatches(type, instance))) {
            return Optional.of((T) instance);
        }
        return Optional.empty();
    }

    /**
     * Check if a primitive type class matches a boxed value.
     * e.g., int.class matches Integer, long.class matches Long, etc.
     */
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

    /**
     * Get a live decoded instance by key (untyped).
     */
    public Optional<Object> getLive(FrameKey key) {
        return Optional.ofNullable(entries.get(key)).map(FrameEntry::instance);
    }

    /**
     * Iterate over all live instances.
     */
    public void forEachLive(Consumer<Object> action) {
        for (FrameEntry entry : entries.values()) {
            Object instance = entry.instance();
            if (instance != null) action.accept(instance);
        }
    }

    /**
     * Iterate over live instances of a specific type.
     *
     * @param filter Only instances assignable to this type
     * @param action Consumer for each matching instance
     */
    @SuppressWarnings("unchecked")
    public <T> void forEachLive(Class<T> filter, Consumer<T> action) {
        for (FrameEntry entry : entries.values()) {
            Object instance = entry.instance();
            if (instance == null) continue;
            if (filter.isInstance(instance)) {
                action.accept((T) instance);
            }
        }
    }

    /**
     * Check if a live instance exists for the given key.
     */
    public boolean hasLive(FrameKey key) {
        FrameEntry entry = entries.get(key);
        return entry != null && entry.instance() != null;
    }

    /**
     * Get count of live instances.
     */
    public int liveCount() {
        int count = 0;
        for (FrameEntry entry : entries.values()) {
            if (entry.instance() != null) count++;
        }
        return count;
    }

    // ==================================================================================
    // Relation Entries
    // ==================================================================================

    /**
     * Get all relation entries in this table.
     *
     * @return Stream of entries where type == FrameBody.TYPE_ID
     */
    public java.util.stream.Stream<FrameEntry> relationEntries() {
        return entries.values().stream().filter(FrameEntry::isRelation);
    }

    /**
     * Count of relation entries.
     */
    public int relationCount() {
        return (int) entries.values().stream().filter(FrameEntry::isRelation).count();
    }

    /**
     * Remove all relation entries from this table.
     */
    public void removeRelationEntries() {
        var toRemove = entries.values().stream()
                .filter(FrameEntry::isRelation)
                .map(FrameEntry::frameKey)
                .toList();
        for (var key : toRemove) {
            removeByKey(key);
        }
    }

    // ==================================================================================
    // Mount-Based Tree Navigation
    // ==================================================================================

    /**
     * Get all entries that have at least one PathMount.
     *
     * @return Stream of entries with path mounts
     */
    public java.util.stream.Stream<FrameEntry> mounted() {
        return entries.values().stream()
                .filter(FrameEntry::hasPathMount);
    }

    /**
     * Get the entry mounted at the exact path.
     *
     * @param path The path to look up (e.g., "/vault")
     * @return The entry if found at that path
     */
    public Optional<FrameEntry> atPath(String path) {
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(path);
        return entries.values().stream()
                .filter(e -> e.pathMounts().stream()
                        .anyMatch(pm -> pm.path().equals(canonical)))
                .findFirst();
    }

    /**
     * Get root-level entries (those with PathMounts at depth 1, i.e., direct children of "/").
     *
     * @return List of entries mounted at the root level
     */
    public java.util.List<FrameEntry> roots() {
        return entries.values().stream()
                .filter(e -> e.pathMounts().stream()
                        .anyMatch(pm -> dev.everydaythings.graph.item.mount.PathUtil.depth(pm.path()) == 1))
                .toList();
    }

    /**
     * Get entries that are immediate children of the given path.
     *
     * @param parentPath The parent path
     * @return List of entries whose PathMount is a direct child of parentPath
     */
    public java.util.List<FrameEntry> children(String parentPath) {
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(parentPath);
        return entries.values().stream()
                .filter(e -> e.pathMounts().stream()
                        .anyMatch(pm -> pm.isChildOf(canonical)))
                .toList();
    }

    /**
     * Get all entries that are descendants of the given path.
     *
     * @param path The ancestor path
     * @return Stream of entries mounted under the given path
     */
    public java.util.stream.Stream<FrameEntry> descendants(String path) {
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(path);
        String prefix = canonical.equals("/") ? "/" : canonical + "/";
        return entries.values().stream()
                .filter(e -> e.pathMounts().stream()
                        .anyMatch(pm -> pm.path().startsWith(prefix) && !pm.path().equals(canonical)));
    }

    /**
     * Check if any entries have path mounts under the given path.
     */
    public boolean hasChildren(String path) {
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(path);
        String prefix = canonical.equals("/") ? "/" : canonical + "/";
        return entries.values().stream()
                .anyMatch(e -> e.pathMounts().stream()
                        .anyMatch(pm -> pm.path().startsWith(prefix) && !pm.path().equals(canonical)));
    }

    // ==================================================================================
    // Virtual Directory Support
    // ==================================================================================

    /**
     * A child at a path — either a real mounted component or a virtual directory
     * implied by deeper mounts.
     *
     * @param segment the child's name (last path segment)
     * @param fullPath the full canonical path (e.g., "/documents")
     * @param entry the backing FrameEntry, or null for virtual directories
     */
    public record PathChild(String segment, String fullPath, FrameEntry entry) {
        /** True if this is a virtual directory (no backing component). */
        public boolean isVirtual() { return entry == null; }
    }

    /**
     * Get immediate children at a path, including virtual directories.
     *
     * <p>Unlike {@link #children(String)} which only returns entries with mounts
     * at exactly the right depth, this method also synthesizes virtual directory
     * nodes implied by deeper mounts.
     *
     * <p>Example: if mounts exist at "/a/b/c" and "/a/b/d", calling
     * {@code childrenAt("/a")} returns one PathChild for "b" (virtual).
     * Calling {@code childrenAt("/a/b")} returns "c" and "d" (real).
     *
     * @param parentPath the parent path to list children of
     * @return list of immediate children (real and virtual), never null
     */
    public java.util.List<PathChild> childrenAt(String parentPath) {
        String canon = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(parentPath);
        int targetDepth = dev.everydaythings.graph.item.mount.PathUtil.depth(canon) + 1;
        String prefix = canon.equals("/") ? "/" : canon + "/";

        // Track: fullPath → FrameEntry (null for virtual)
        Map<String, FrameEntry> childMap = new LinkedHashMap<>();

        for (FrameEntry entry : entries.values()) {
            for (Mount.PathMount pm : entry.pathMounts()) {
                String mountPath = pm.path();

                // Must be under our parent
                if (canon.equals("/")) {
                    // Everything is under root
                    if (mountPath.equals("/")) continue;
                } else {
                    if (!mountPath.startsWith(prefix)) continue;
                }

                // Extract segment at target depth
                String[] segments = mountPath.substring(1).split("/");
                if (segments.length < targetDepth) continue;

                String childSegment = segments[targetDepth - 1];
                String childPath = canon.equals("/")
                        ? "/" + childSegment
                        : canon + "/" + childSegment;

                if (segments.length == targetDepth) {
                    // Exact depth match — real mounted child (overrides virtual)
                    childMap.put(childPath, entry);
                } else if (!childMap.containsKey(childPath)) {
                    // Deeper mount — virtual directory (don't override real)
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

    /**
     * Reverse-lookup: find the primary presentation path for a frame key.
     *
     * @param key The frame key
     * @return The primary path mount's path, if the entry exists and has a PathMount
     */
    public Optional<String> pathForKey(FrameKey key) {
        return get(key)
                .map(FrameEntry::primaryPathMount)
                .map(Mount.PathMount::path);
    }

    // ==================================================================================
    // Canonical Implementation
    // ==================================================================================

    /**
     * Serialize the content table to CBOR.
     *
     * <p>Serializes as an array of FrameEntry records.
     * Live instances are not serialized - they are decoded on hydration.
     */
    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject array = CBORObject.NewArray();
        for (FrameEntry entry : entries.values()) {
            array.Add(entry.toCborTree(scope));
        }
        return array;
    }

    /**
     * Deserialize a content table from CBOR.
     *
     * <p>Expects a CBOR array of FrameEntry records.
     */
    @Factory
    public static FrameTable fromCborTree(CBORObject node) {
        FrameTable table = new FrameTable();
        if (node != null && node.getType() == com.upokecenter.cbor.CBORType.Array) {
            for (CBORObject entryNode : node.getValues()) {
                FrameEntry entry = Canonical.fromCborTree(entryNode, FrameEntry.class, Scope.RECORD);
                if (entry != null) {
                    table.add(entry);
                }
            }
        }
        return table;
    }
}
