package dev.everydaythings.graph.item;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.item.component.Component;
import dev.everydaythings.graph.item.component.Components;
import dev.everydaythings.graph.item.component.ComponentFieldSpec;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.item.relation.Relation;
import lombok.extern.log4j.Log4j2;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.action.ActionContext;
import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.item.component.ComponentEntry;
import dev.everydaythings.graph.item.component.ComponentTable;
import dev.everydaythings.graph.item.component.ComponentType;
import dev.everydaythings.graph.item.mount.Mount;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.VersionID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.workingtree.WorkingTreeStore;
import dev.everydaythings.graph.policy.PolicySet;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.ValueType;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.ItemSurface;
import dev.everydaythings.graph.ui.scene.SceneMode;
import dev.everydaythings.graph.ui.scene.View;
import lombok.Getter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * The fundamental unit of Common Graph - versioned containers with identity,
 * content components, relations, and actions. Think of them as "little git repos."
 *
 * <p>This is a self-describing type. The class IS the definition.
 *
 * <p>State:
 * <ul>
 *   <li>base: current version (VersionID) or null if fresh/uncommitted</li>
 *   <li>componentTable: functional building blocks (content, streams)</li>
 *   <li>actionTable: declared actions</li>
 * </ul>
 *
 * <p>Dirty tracking: items can be created/modified without committing.
 * Call commit() when ready to persist a new version.
 */
@Log4j2
@Scene.Rule(match = ".heading", color = "#89B4FA", fontSize = "1.33")
@Scene.Rule(match = ".muted", color = "#6C7086", fontSize = "0.87")
@Scene.Rule(match = ".small", fontSize = "0.87")
@Scene.Rule(match = ".selected", background = "#313244")
@Scene.Rule(match = ":selected", background = "reverse")
@Scene.Rule(match = ":hover", opacity = "bright")
@Type(value = Item.KEY, glyph = "📦")
@Scene(as = ItemSurface.class)
public class Item implements Property {

    // === TYPE DEFINITION ===
    public static final String KEY = "cg:type/item";

    // === WELL-KNOWN HANDLES ===
    // Lazy-initialized via holder class to break circular clinit:
    // HandleID → HashID.<clinit> → Unit.<clinit> → Dimension.<clinit> → Item(ItemID) → vocabulary()
    private static class BuiltinHandles {
        static final HandleID POLICY = HandleID.of("policy");
    }

    // Type seed instance is auto-created by SeedStore from @Type annotation

    // ==================================================================================
    // Instance Fields
    // ==================================================================================

    /** Runtime context - null for siloed items and seed items. Set by Librarian to itself. */
    protected Librarian librarian;

    @Getter
    protected final ItemID iid;

    /** Store for materialized items (null if not materialized). Typically a WorkingTreeStore. */
    @Getter
    protected transient ItemStore store;

    /** True if this is a fresh creation (not loaded from disk). */
    @Getter
    protected transient boolean freshBoot;

    /** Current version (base) - null if fresh/uncommitted. */
    @Getter
    protected VersionID base;

    /** Currently loaded manifest (if any). */
    @Getter
    protected Manifest current;

    /**
     * Unified state: components, actions, mounts, relations, and policy.
     *
     * <p>ItemState holds all the versioned state of an Item, providing a
     * unified abstraction that can be shared with Manifest for serialization.
     */
    protected final ItemState state = new ItemState();

    /** Version history (for items with history loaded). */
    @Getter
    protected List<VersionID> versions = new ArrayList<>();

    /**
     * Intrinsic runtime vocabulary derived from item + component verbs.
     *
     * <p>This is not stored as a component entry; it's rebuilt from schema/content.
     */
    private final transient Vocabulary runtimeVocabulary = new Vocabulary();

    /** True when in "edit mode" - editing a new version. */
    private boolean editing;

    /** Tracks whether item has uncommitted changes. */
    @Getter
    private boolean dirty;

    // ==================================================================================
    // Schema Access (cached per class via ItemScanner)
    // ==================================================================================

    /**
     * Get the cached schema for this item's class.
     *
     * <p>The schema contains all annotation-derived metadata (component fields,
     * relation fields, verbs) and is computed once per class.
     */
    @SuppressWarnings("unchecked")
    protected ItemSchema schema() {
        return ItemScanner.schemaFor(getClass());
    }

    // ==================================================================================
    // State Accessors (delegate to ItemState)
    // ==================================================================================

    /** Component table - content building blocks. */
    public ComponentTable content() {
        return state.content();
    }

    /**
     * Endorsed relations — filtered view from the component table.
     *
     * <p>Returns relations that have been brought into this item as endorsed content.
     * These are stored as ComponentEntries with type == Relation.TYPE_ID, and
     * their live instances are the Relation objects.
     *
     * @return stream of endorsed Relation objects
     */
    public java.util.stream.Stream<Relation> endorsedRelations() {
        return content().relationEntries()
                .map(entry -> content().getLive(entry.handle(), Relation.class))
                .flatMap(java.util.Optional::stream);
    }


    /** Per-item policies — stored in ComponentTable under well-known handle. */
    public PolicySet policy() {
        return content().getLive(BuiltinHandles.POLICY, PolicySet.class).orElse(null);
    }

    /** Intrinsic vocabulary: semantic actions available on this item. */
    public Vocabulary vocabulary() {
        return runtimeVocabulary;
    }

    // ==================================================================================
    // Display & Navigation
    // ==================================================================================

    public Link link() {
        return Link.of(iid);
    }

    public String displayToken() {
        return getClass().getSimpleName();
    }

    /**
     * Resolve a path within this item to get display token.
     *
     * <p>Paths like "/componentHandle" are resolved through the content table.
     *
     * @param path the path to resolve (e.g., "/readme")
     * @return display token for the component, or empty if not found
     */
    public Optional<String> resolvePathDisplayToken(String path) {
        if (path == null || path.isEmpty()) {
            return Optional.of(displayToken());
        }

        // Strip leading slash
        String handle = path.startsWith("/") ? path.substring(1) : path;

        // Check ComponentTable for content()
        if (handle.equals("content")) {
            return Optional.of(content().displayToken());
        }

        // Resolve HandleID: encoded form (hid:...) or plain text name
        HandleID hid = handle.startsWith(HandleID.HID_PREFIX)
                ? HandleID.parse(handle)
                : HandleID.of(handle);

        // Prefer stable entry metadata to avoid label mutation when a component
        // gets lazily hydrated after first render.
        Optional<String> entryLabel = content().get(hid).map(ComponentEntry::displayToken);
        if (entryLabel.isPresent()) {
            return entryLabel;
        }

        // Fall back to live payload display token when no entry metadata exists.
        Optional<Object> live = content().getLive(hid);
        if (live.isPresent()) {
            return Optional.of(resolvePayloadDisplayToken(live.get()));
        }
        return Optional.empty();
    }

    /**
     * Resolve a path within this item to get emoji.
     *
     * @param path the path to resolve (e.g., "/readme")
     * @return emoji for the component, or empty if not found
     */
    public Optional<String> resolvePathEmoji(String path) {
        if (path == null || path.isEmpty()) {
            return Optional.of(emoji());
        }

        // Strip leading slash
        String handle = path.startsWith("/") ? path.substring(1) : path;

        // Check ComponentTable for content()
        if (handle.equals("content")) {
            return Optional.of(content().emoji());
        }

        // Resolve HandleID: encoded form (hid:...) or plain text name
        HandleID hid = handle.startsWith(HandleID.HID_PREFIX)
                ? HandleID.parse(handle)
                : HandleID.of(handle);

        // Prefer stable entry metadata to avoid icon mutation when a component
        // gets lazily hydrated after first render.
        Optional<String> entryEmoji = content().get(hid).map(this::resolveEntryEmoji);
        if (entryEmoji.isPresent()) {
            return entryEmoji;
        }

        // Fall back to live payload emoji when no entry metadata exists.
        Optional<Object> live = content().getLive(hid);
        if (live.isPresent()) {
            return Optional.of(resolvePayloadEmoji(live.get()));
        }
        return Optional.empty();
    }

    /**
     * Resolve emoji from entry metadata with semantic type fallback.
     */
    private String resolveEntryEmoji(ComponentEntry entry) {
        if (entry == null) return "📦";
        String typeGlyph = resolveTypeGlyph(entry.type());
        if (typeGlyph != null && !typeGlyph.isBlank()) {
            return typeGlyph;
        }
        return entry.emoji();
    }

    /**
     * Resolve the semantic glyph for a type ID from type metadata.
     */
    private String resolveTypeGlyph(ItemID typeId) {
        if (typeId == null) return null;

        // 1) Type item's SurfaceTemplateComponent glyph (authoritative display metadata)
        if (librarian != null) {
            Optional<Item> typeItem = librarian.get(typeId, Item.class);
            if (typeItem.isPresent()) {
                var st = typeItem.get().content().getLive(
                        dev.everydaythings.graph.item.component.SurfaceTemplateComponent.HANDLE,
                        dev.everydaythings.graph.item.component.SurfaceTemplateComponent.class
                ).orElse(null);
                if (st != null && st.glyph() != null && !st.glyph().isBlank()) {
                    return st.glyph();
                }
            }
        }

        // 2) Implementation class annotations (works even without hydrated type item)
        Optional<Class<?>> impl = findImplementation(typeId);
        if (impl.isPresent()) {
            Class<?> cls = impl.get();
            dev.everydaythings.graph.item.component.Type type =
                    cls.getAnnotation(dev.everydaythings.graph.item.component.Type.class);
            if (type != null && !type.glyph().isEmpty()) {
                return type.glyph();
            }
            dev.everydaythings.graph.value.Value.Type valueType =
                    cls.getAnnotation(dev.everydaythings.graph.value.Value.Type.class);
            if (valueType != null && !valueType.glyph().isEmpty()) {
                return valueType.glyph();
            }
        }

        return null;
    }

    /**
     * Resolve an emoji/glyph for an arbitrary live payload object.
     *
     * <p>ComponentTable payloads are open-ended and not required to implement
     * {@link Component}, so this must not rely on a single interface.
     */
    private static String resolvePayloadEmoji(Object payload) {
        if (payload == null) return "📦";
        if (payload instanceof Component component) {
            return component.emoji();
        }
        if (payload instanceof dev.everydaythings.graph.value.Value value) {
            return value.emoji();
        }
        dev.everydaythings.graph.item.component.Type type =
                payload.getClass().getAnnotation(dev.everydaythings.graph.item.component.Type.class);
        if (type != null && !type.glyph().isEmpty()) {
            return type.glyph();
        }
        dev.everydaythings.graph.value.Value.Type valueType =
                payload.getClass().getAnnotation(dev.everydaythings.graph.value.Value.Type.class);
        if (valueType != null && !valueType.glyph().isEmpty()) {
            return valueType.glyph();
        }
        return "📦";
    }

    private static String resolvePayloadDisplayToken(Object payload) {
        if (payload == null) return "(unnamed)";
        if (payload instanceof Component component) {
            return component.displayToken();
        }
        if (payload instanceof dev.everydaythings.graph.value.Value value) {
            return value.displayToken();
        }
        dev.everydaythings.graph.item.component.Type type =
                payload.getClass().getAnnotation(dev.everydaythings.graph.item.component.Type.class);
        if (type != null) {
            String key = type.value();
            int slash = key.lastIndexOf('/');
            if (slash >= 0 && slash < key.length() - 1) {
                String shortName = key.substring(slash + 1);
                if (!shortName.isEmpty()) {
                    return Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1);
                }
            }
        }
        return payload.getClass().getSimpleName();
    }

    /**
     * Resolve a path to a 2D icon resource path, if the component has one.
     *
     * <p>Checks the component's {@code @Type(icon=...)} annotation.
     */
    public Optional<String> resolvePathIconResource(String path) {
        if (path == null || path.isEmpty()) return Optional.empty();

        String handle = path.startsWith("/") ? path.substring(1) : path;

        HandleID hid = handle.startsWith(HandleID.HID_PREFIX)
                ? HandleID.parse(handle)
                : HandleID.of(handle);

        return content().getLive(hid)
                .map(o -> {
                    Type typeAnno = o.getClass().getAnnotation(Type.class);
                    if (typeAnno != null && !typeAnno.icon().isEmpty()) {
                        return typeAnno.icon();
                    }
                    return null;
                });
    }

    /**
     * Resolve a path to the component's type color (from annotation).
     */
    public Optional<dev.everydaythings.graph.value.Color> resolvePathTypeColor(String path) {
        if (path == null || path.isEmpty()) return Optional.empty();

        String handle = path.startsWith("/") ? path.substring(1) : path;

        HandleID hid = handle.startsWith(HandleID.HID_PREFIX)
                ? HandleID.parse(handle)
                : HandleID.of(handle);

        return content().getLive(hid)
                .map(o -> {
                    Type typeAnno = o.getClass().getAnnotation(Type.class);
                    if (typeAnno != null && typeAnno.color() != 0) {
                        return dev.everydaythings.graph.value.Color.fromPacked(typeAnno.color());
                    }
                    return null;
                });
    }

    public boolean isExpandable() {
        return true; // Items always have structure to explore
    }

    // ==================================================================================
    // Tree Navigation (Link-based)
    // ==================================================================================

    /**
     * Check if this item has children in the given mode.
     *
     * @param mode PRESENTATION (mount tree) or INSPECT (raw tables)
     * @return true if there are children to show
     */
    public boolean isExpandable(TreeLink.ChildMode mode) {
        return switch (mode) {
            case PRESENTATION -> !content().childrenAt("/").isEmpty();
            case INSPECT -> !content().isEmpty();
        };
    }

    /**
     * Get children as Links for tree navigation.
     *
     * <p>In PRESENTATION mode, returns the mount table roots as navigable links.
     * <p>In INSPECT mode, returns links to content components and tables.
     *
     * @param mode which view of the item's structure
     * @return list of child Links
     */
    public List<Link> children(TreeLink.ChildMode mode) {
        return switch (mode) {
            case PRESENTATION -> childrenPresentation();
            case INSPECT -> childrenInspect();
        };
    }

    /**
     * Presentation mode: mount tree children at root, including virtual directories.
     */
    private List<Link> childrenPresentation() {
        return childrenAtPath("/");
    }

    /**
     * Get children at a specific path in presentation mode.
     *
     * <p>Returns Links for both real mounted components and virtual
     * directories implied by deeper mounts.
     *
     * @param path the parent path to list children of
     * @return list of child Links
     */
    public List<Link> childrenAtPath(String path) {
        List<Link> children = new ArrayList<>();
        for (var child : content().childrenAt(path)) {
            children.add(Link.of(iid(), child.fullPath()));
        }
        return children;
    }

    /**
     * Inspect mode: raw content + tables.
     */
    private List<Link> childrenInspect() {
        List<Link> children = new ArrayList<>();

        // All non-built-in component entries (content components, relations).
        // Policy now lives under component config metadata and should not appear
        // as a standalone inspect tree component.
        for (ComponentEntry entry : content()) {
            if (BuiltinHandles.POLICY.equals(entry.handle())) {
                continue;
            }
            Link link = entry.link();
            if (link != null) {
                children.add(link);
            }
        }

        return children;
    }

    public ItemID icon() {
        // Return this Item's type ID - UI will find the icon for that type
        Type typeAnnotation = getClass().getAnnotation(Type.class);
        if (typeAnnotation != null && !typeAnnotation.value().isBlank()) {
            return ItemID.fromString(typeAnnotation.value());
        }
        return ItemID.fromString(KEY); // Default to base Item type
    }

    public String colorCategory() {
        // Fallback coloring by name pattern
        String name = getClass().getSimpleName().toLowerCase();
        if (name.contains("librarian")) return "librarian";
        if (name.contains("principal")) return "principal";
        if (name.contains("workspace")) return "workspace";
        return "item";
    }

    public String displaySubtitle() {
        return iid != null ? iid.toString().substring(0, Math.min(20, iid.toString().length())) + "..." : "";
    }

    /**
     * Get display information for this item.
     *
     * <p>Base implementation provides defaults based on the class.
     * Type subclasses should override with their own static TYPE_DISPLAY.
     *
     * @return display information for rendering this item
     */
    /**
     * Get the display information for this item.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Check for a SurfaceTemplateComponent on this item (instance override)</li>
     *   <li>Look up the type's SurfaceTemplateComponent from the library</li>
     *   <li>Fall back to basic defaults (with "&#x2753;" glyph to indicate missing type info)</li>
     * </ol>
     *
     * <p>If you see "&#x2753;" in the UI, the type's SurfaceTemplateComponent is missing from
     * the type bootstrap. Types are loaded first, so this indicates a problem.
     */
    public DisplayInfo displayInfo() {
        // 1. Check for instance-level SurfaceTemplateComponent
        var stcOpt = content().getLive(
                dev.everydaythings.graph.item.component.SurfaceTemplateComponent.HANDLE,
                dev.everydaythings.graph.item.component.SurfaceTemplateComponent.class);
        if (stcOpt.isPresent()) {
            return stcOpt.get().toDisplayInfo(findDisplayName());
        }

        // 2. Look up type's SurfaceTemplateComponent from library
        if (librarian != null) {
            var typeSurface = getTypeSurfaceTemplate();
            if (typeSurface != null) {
                return typeSurface.toDisplayInfo(findDisplayName());
            }
        }

        // 3. Fall back to annotation-based display info
        return DisplayInfo.builder()
                .name(findDisplayName())
                .typeName(findTypeName())
                .color(findTypeColor())
                .iconText(findIconText())
                .build();
    }

    /**
     * Get the SurfaceTemplateComponent from this item's type (if available in the library).
     */
    protected dev.everydaythings.graph.item.component.SurfaceTemplateComponent getTypeSurfaceTemplate() {
        if (librarian == null) return null;

        // Get this item's type ID
        Type typeAnnotation = getClass().getAnnotation(Type.class);
        if (typeAnnotation == null || typeAnnotation.value().isBlank()) return null;

        ItemID typeId = ItemID.fromString(typeAnnotation.value());

        // Look up the type item
        var typeItemOpt = librarian.get(typeId, Item.class);
        if (typeItemOpt.isEmpty()) return null;

        Item typeItem = typeItemOpt.get();

        // Get the type's SurfaceTemplateComponent
        return typeItem.content().getLive(
                dev.everydaythings.graph.item.component.SurfaceTemplateComponent.HANDLE,
                dev.everydaythings.graph.item.component.SurfaceTemplateComponent.class
        ).orElse(null);
    }

    /**
     * Find a human-readable display name for this item.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Check for a "name" field/component</li>
     *   <li>Check for a "title" field/component</li>
     *   <li>Check for a "label" field/component</li>
     *   <li>Use the class simple name</li>
     * </ol>
     */
    protected String findDisplayName() {
        // Try common name fields via content table
        for (String handle : new String[]{"name", "title", "label", "canonicalKey"}) {
            var opt = content().getLive(HandleID.of(handle), Object.class);
            if (opt.isPresent()) {
                Object value = opt.get();
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }
        // Fallback to class name
        return getClass().getSimpleName();
    }

    /**
     * Find the type name from the @Type annotation.
     */
    protected String findTypeName() {
        Type typeAnnotation = getClass().getAnnotation(Type.class);
        if (typeAnnotation != null && !typeAnnotation.value().isBlank()) {
            String key = typeAnnotation.value();
            // Extract last segment: "cg:type/librarian" -> "Librarian"
            int lastSlash = key.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < key.length() - 1) {
                String shortName = key.substring(lastSlash + 1);
                return shortName.substring(0, 1).toUpperCase() + shortName.substring(1);
            }
            return key;
        }
        return getClass().getSimpleName();
    }

    /**
     * Find the type color from SurfaceTemplateComponent or annotation.
     */
    protected dev.everydaythings.graph.value.Color findTypeColor() {
        // Try to get from type's SurfaceTemplateComponent
        var typeSurface = getTypeSurfaceTemplate();
        if (typeSurface != null) {
            return typeSurface.toColor();
        }

        // Fall back to annotation color
        Type typeAnnotation = getClass().getAnnotation(Type.class);
        if (typeAnnotation != null) {
            return dev.everydaythings.graph.value.Color.fromPacked(typeAnnotation.color());
        }

        return dev.everydaythings.graph.value.Color.rgb(120, 120, 140); // Default gray
    }

    /**
     * Find the icon text (glyph) from SurfaceTemplateComponent or annotation.
     */
    protected String findIconText() {
        // Try to get from type's SurfaceTemplateComponent
        var typeSurface = getTypeSurfaceTemplate();
        if (typeSurface != null && typeSurface.glyph() != null) {
            return typeSurface.glyph();
        }

        // Fall back to @Type annotation glyph
        Type typeAnnotation = getClass().getAnnotation(Type.class);
        if (typeAnnotation != null && !typeAnnotation.glyph().isEmpty()) {
            return typeAnnotation.glyph();
        }

        return "\uD83D\uDCE6";  // Default item glyph
    }

    public String emoji() {
        return findIconText();
    }

    public ItemID targetId() {
        return iid;
    }

    public String displayDetail() {
        return iid != null ? iid.encodeText() : null;
    }

    // ==================================================================================
    // CBOR View Rendering
    // ==================================================================================

    /**
     * Render this item as a compact handle view (CBOR-serializable).
     *
     * <p>Uses ItemSurface at COMPACT mode for consistent rendering.
     */
    public View renderHandle() {
        return View.of(ItemSurface.from(this, SceneMode.COMPACT));
    }

    /**
     * Render this item as an expanded detail view (CBOR-serializable).
     *
     * <p>Uses ItemSurface at FULL mode to show all content.
     */
    public View renderDetail() {
        return View.of(ItemSurface.from(this, SceneMode.FULL));
    }

    // ==================================================================================
    // Token Extraction (for indexing)
    // ==================================================================================

    /**
     * A token entry for indexing this item.
     *
     * @param token the token string (will be normalized by the index)
     * @param weight relevance weight (1.0 = primary name, 0.9 = alias, etc.)
     */
    public record TokenEntry(String token, float weight) {}

    /**
     * Extract tokens for indexing this item.
     *
     * <p>Scans the actual content and relations stored in this item, not just
     * class metadata. Classes define the schema; items hold the data.
     *
     * <p>Token sources:
     * <ul>
     *   <li>Content components - string values from handles like "name", "symbol", "label"</li>
     *   <li>Relations - can contribute tokens from predicates/objects</li>
     *   <li>DisplayInfo - the computed display name and type</li>
     *   <li>Path mounts - mounted component paths (highest weight, always first in lookups)</li>
     * </ul>
     *
     * @return stream of tokens for this item
     */
    public Stream<TokenEntry> extractTokens() {
        List<TokenEntry> tokens = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Helper to add token only if not already seen
        BiConsumer<String, Float> addToken = (token, weight) -> {
            if (token != null && !token.isBlank()) {
                String normalized = token.toLowerCase().trim();
                if (seen.add(normalized)) {
                    tokens.add(new TokenEntry(token, weight));
                }
            }
        };

        // 1. Scan content table for string values
        var content = content();
        if (content != null) {
            for (var entry : content) {
                var handleId = entry.handle();
                var opt = content.getLive(handleId);
                if (opt.isPresent()) {
                    Object value = opt.get();
                    extractTokensFromValue(value, handleId.toString(), addToken);
                }
            }
        }

        // 2. Add display info tokens (may overlap with content, but addToken dedupes)
        var info = displayInfo();
        if (info != null) {
            addToken.accept(info.name(), 1.0f);
            addToken.accept(info.typeName(), 0.5f);
        }

        // 3. Add displayToken if different from just class name
        String label = displayToken();
        if (label != null && !label.equals(getClass().getSimpleName())) {
            addToken.accept(label, 0.9f);
        }

        // 4. Scan path mounts — mounted components get high-weight tokens
        if (content != null) {
            for (var entry : content) {
                for (Mount.PathMount pm : entry.pathMounts()) {
                    String mountPath = pm.path();
                    if (mountPath != null && !mountPath.isBlank()) {
                        // Leaf segment of the mount path
                        String clean = mountPath.startsWith("/") ? mountPath.substring(1) : mountPath;
                        String[] segments = clean.split("/");
                        if (segments.length > 0) {
                            String leaf = segments[segments.length - 1];
                            if (!leaf.isBlank()) {
                                addToken.accept(leaf, 1.5f);
                            }
                        }
                    }
                }
            }
        }

        return tokens.stream();
    }

    /**
     * Extract tokens from a component value.
     *
     * <p>Handles common patterns: String, Map<String,String> for multilingual names, etc.
     */
    private void extractTokensFromValue(Object value, String handle,
            BiConsumer<String, Float> addToken) {
        // High-value handles get higher weight
        float weight = switch (handle.toLowerCase()) {
            case "name", "symbol", "label", "title" -> 1.0f;
            case "names", "labels", "aliases" -> 0.9f;
            case "description", "descriptions" -> 0.3f;
            default -> 0.5f;
        };

        if (value instanceof String s) {
            // Don't index very long strings (descriptions, etc.)
            if (s.length() <= 100) {
                addToken.accept(s, weight);
            }
        } else if (value instanceof Map<?, ?> map) {
            // Multilingual maps like names = {en: "meter", de: "Meter"}
            for (Object v : map.values()) {
                if (v instanceof String s && s.length() <= 100) {
                    addToken.accept(s, weight * 0.9f);
                }
            }
        } else if (value instanceof Iterable<?> iter) {
            // Lists of aliases, tokens, etc.
            for (Object v : iter) {
                if (v instanceof String s && s.length() <= 100) {
                    addToken.accept(s, weight * 0.8f);
                }
            }
        }
        // Components themselves might have their own tokens - we could recurse
        // but for now, we're scanning the raw data stored in the content table
    }

    // ==================================================================================
    // Property Implementation
    // ==================================================================================

    /**
     * Item's top-level properties: components, actions, relations, policy.
     */
    private static final List<String> TOP_LEVEL_PROPERTIES = List.of(
            "components"
    );

    @Override
    public Property property(String name) {
        return switch (name) {
            case "components" -> content();
            case "vocabulary" -> vocabulary();
            default -> {
                // Try to resolve as a component handle (includes policy)
                HandleID hid = HandleID.of(name);
                Object live = content().getLive(hid).orElse(null);
                if (live instanceof Property prop) {
                    yield prop;
                }
                // Try alias resolution
                Object comp = component(name);
                if (comp instanceof Property prop) {
                    yield prop;
                }
                yield null;
            }
        };
    }

    @Override
    public Stream<String> properties() {
        return TOP_LEVEL_PROPERTIES.stream();
    }

    @Override
    public Object get() {
        return this; // Item itself is the value
    }

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Create a seed item with a deterministic IID.
     *
     * <p>Seed items are created without a librarian. They exist in the SeedStore,
     * which is an in-memory store populated at startup. This constructor is used
     * for statically-defined vocabulary items (Sememes, types, units, etc.).
     *
     * @param iid The item's identity (deterministic, derived from canonical key)
     */
    protected Item(ItemID iid) {
        this.librarian = null;
        this.iid = Objects.requireNonNull(iid, "iid");
        this.dirty = false;  // Seed items are immutable
        state.setOwner(this);
        initBuiltinComponents();
        populateVocabulary();
    }

    /**
     * Create a fresh new item.
     */
    protected Item(Librarian librarian) {
        this.librarian = librarian;
        this.iid = ItemID.random();
        this.dirty = true;
        state.setOwner(this);
        initBuiltinComponents();
        populateVocabulary();
    }

    /**
     * Create a fresh item with a specific IID.
     */
    protected Item(Librarian librarian, ItemID iid) {
        this.librarian = librarian;
        this.iid = Objects.requireNonNull(iid, "iid");
        this.dirty = true;
        state.setOwner(this);
        initBuiltinComponents();
        populateVocabulary();
    }

    /**
     * Hydrate an existing item from a manifest.
     *
     * <p>This constructor populates the ComponentTable from the manifest,
     * then calls hydrate() to decode all components and bind fields.
     *
     * @param librarian The librarian (provides store access for content fetching)
     * @param manifest  The manifest describing this item's state
     */
    protected Item(Librarian librarian, Manifest manifest) {
        this.librarian = librarian;
        this.store = librarian.library().primaryStore().orElse(null);
        this.iid = manifest.iid();
        this.current = manifest;
        this.base = manifest.vid();
        this.dirty = false;

        // Set owner on state tables before populating them
        state.setOwner(this);

        // Populate component table from manifest
        for (ComponentEntry entry : manifest.components()) {
            content().add(entry);
        }

        // Hydrate: decode all, bind fields, invoke callbacks
        hydrate();
        initBuiltinComponents();
        populateVocabulary();
    }

    /**
     * Path-based constructor for materialized items.
     *
     * <p>If path exists (.item/ structure): loads existing item (IID from disk, components loaded)
     * <p>If path doesn't exist: creates new item (random IID, components initialized)
     *
     * <p>This is the preferred constructor for filesystem-backed items like Librarian.
     * It handles both "create at path" and "load from path" automatically.
     *
     * @param path    The filesystem path for this item
     * @param fallbackStore Store to fall back on for at least type lookups
     */
    protected Item(Path path, ItemStore fallbackStore) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(fallbackStore, "fallbackStore");

        this.librarian = null;  // Path-based items don't have a librarian

        // Set owner on state tables before populating them
        state.setOwner(this);

        if (WorkingTreeStore.exists(path)) {
            // LOAD existing
            WorkingTreeStore wts = WorkingTreeStore.open(path, fallbackStore);
            this.store = wts;
            this.iid = wts.iid();
            this.freshBoot = false;
            this.dirty = false;

            // Load component entries from store into ComponentTable
            for (ComponentEntry entry : wts.loadHeadComponents()) {
                content().add(entry);
            }

            // Hydrate: decode all, bind fields, invoke callbacks
            hydrate();
        } else {
            // CREATE new
            this.iid = ItemID.random();
            this.store = WorkingTreeStore.materialize(path, iid, fallbackStore);
            this.freshBoot = true;
            this.dirty = true;

            // Initialize fresh components (creates defaults, populates ComponentTable)
            initializeFreshComponents();

            // Hydrate: bind fields and invoke callbacks (components already in table)
            hydrate();
        }

        // Call hook after all components are initialized
        onFullyInitialized();
    }

    /**
     * In-memory constructor for ephemeral items.
     *
     * <p>Creates a fresh item with in-memory storage. The item is fully functional
     * but data is lost when the JVM exits. Used for testing, demos, and temporary sessions.
     *
     * <p>This constructor:
     * <ul>
     *   <li>Creates a random IID</li>
     *   <li>Uses the provided store for type lookups and storage</li>
     *   <li>Initializes fresh components</li>
     *   <li>Calls hydrate() and onFullyInitialized()</li>
     * </ul>
     *
     * @param store In-memory store for type lookups and content storage
     * @param inMemoryMarker Marker parameter to distinguish from path-based constructor
     */
    protected Item(ItemStore store, InMemoryMarker inMemoryMarker) {
        Objects.requireNonNull(store, "store");

        this.librarian = null;  // In-memory items don't have a librarian reference
        this.store = store;
        this.iid = ItemID.random();
        this.freshBoot = true;
        this.dirty = true;

        // Set owner on state tables before populating them
        state.setOwner(this);

        // Initialize fresh components (creates defaults, populates ComponentTable)
        initializeFreshComponents();

        // Hydrate: bind fields and invoke callbacks (components already in table)
        hydrate();

        // Call hook after all components are initialized
        onFullyInitialized();
    }

    /**
     * Marker class to distinguish in-memory constructor from path-based constructor.
     * This avoids signature collision with Item(Path, ItemStore).
     */
    protected static final class InMemoryMarker {
        public static final InMemoryMarker INSTANCE = new InMemoryMarker();
        private InMemoryMarker() {}
    }

    /**
     * Path-based constructor with librarian reference.
     *
     * <p>Combines librarian reference (for principal tracking, library access) with
     * full path-based initialization (vault creation, key generation, component init).
     * Used by User and other items that need both a librarian and a home directory.
     *
     * <p>If path exists (.item/ structure): loads existing item.
     * <p>If path doesn't exist: creates new item with full component initialization.
     *
     * @param librarian The librarian (provides store access and library)
     * @param path      The filesystem path for this item's home directory
     */
    protected Item(Librarian librarian, Path path) {
        Objects.requireNonNull(librarian, "librarian");
        Objects.requireNonNull(path, "path");

        this.librarian = librarian;
        ItemStore fallbackStore = librarian.library().primaryStore().orElse(null);

        // Set owner on state tables before populating them
        state.setOwner(this);

        if (WorkingTreeStore.exists(path)) {
            // LOAD existing
            WorkingTreeStore wts = WorkingTreeStore.open(path, fallbackStore);
            this.store = wts;
            this.iid = wts.iid();
            this.freshBoot = false;
            this.dirty = false;

            // Load component entries from store into ComponentTable
            for (ComponentEntry entry : wts.loadHeadComponents()) {
                content().add(entry);
            }

            // Hydrate: decode all, bind fields, invoke callbacks
            hydrate();
        } else {
            // CREATE new
            this.iid = ItemID.random();
            this.store = WorkingTreeStore.materialize(path, iid, fallbackStore);
            this.freshBoot = true;
            this.dirty = true;

            // Initialize fresh components (creates defaults, populates ComponentTable)
            initializeFreshComponents();

            // Hydrate: bind fields and invoke callbacks (components already in table)
            hydrate();
        }

        // Call hook after all components are initialized
        onFullyInitialized();
    }

    /**
     * In-memory constructor with librarian reference.
     *
     * <p>Combines librarian reference with full in-memory initialization
     * (vault creation, key generation, component init). Used for creating
     * items that need both a librarian and full component initialization
     * but don't need a filesystem path (testing, ephemeral users).
     *
     * @param librarian The librarian (provides store access and library)
     * @param marker    Marker to distinguish from other constructors
     */
    protected Item(Librarian librarian, InMemoryMarker marker) {
        Objects.requireNonNull(librarian, "librarian");

        this.librarian = librarian;
        this.store = librarian.library().primaryStore().orElse(null);
        this.iid = ItemID.random();
        this.freshBoot = true;
        this.dirty = true;

        // Set owner on state tables before populating them
        state.setOwner(this);

        // Initialize fresh components (creates defaults, populates ComponentTable)
        initializeFreshComponents();

        // Hydrate: bind fields and invoke callbacks (components already in table)
        hydrate();

        // Call hook after all components are initialized
        onFullyInitialized();
    }

    /**
     * Called after all components are initialized but before the constructor completes.
     *
     * <p>Override in subclasses for post-initialization logic that needs all components
     * ready. This is called at the end of the path-based constructor, after
     * {@link #hydrate()} has decoded components and invoked initComponent() callbacks.
     *
     * <p>Typical uses:
     * <ul>
     *   <li>First-boot initialization (generate keys, commit initial version)</li>
     *   <li>Reload verification (check integrity, refresh state)</li>
     * </ul>
     *
     * <p>Note: Subclass constructor body has NOT yet run when this is called.
     * Only access fields set via superclass constructors or component initialization.
     *
     * <p><b>Important:</b> Subclasses MUST call {@code super.onFullyInitialized()} first
     * to ensure the vocabulary is populated.
     */
    protected void onFullyInitialized() {
        initBuiltinComponents();
        populateVocabulary();
        populateRelationTable();
        // Sync pre-initialized field values to ComponentTable (handles subclass field initializers)
        syncFieldValuesToTable();
    }

    /**
     * Sync pre-initialized field values to the ComponentTable.
     *
     * <p>This handles the case where a subclass has field initializers like:
     * {@code ExpressionComponent typesExpr = ExpressionComponent.subjects(...)}
     *
     * <p>Since superclass constructor runs before subclass field initializers,
     * the ComponentTable may have a default instance while the field has the
     * actual desired value. This method syncs them.
     */
    private void syncFieldValuesToTable() {
        if (!freshBoot) return; // Only needed for fresh creation

        for (ComponentFieldSpec spec : schema().componentFields()) {
            Object fieldValue = spec.getValue(this);
            if (fieldValue == null) continue;

            // Check if ComponentTable has a different instance
            var tableValue = content().getLive(spec.handle(), Object.class);
            if (tableValue.isPresent() && tableValue.get() != fieldValue) {
                // Field has a different value - sync it to the table
                content().setLive(spec.handle(), spec.handleKey(), fieldValue);
            }
        }
    }

    /**
     * Populate relation entries in the component table from the cached schema.
     *
     * <p>Uses {@link ItemSchema#populateRelationEntries(ComponentTable, Item)} to add
     * all relations from {@code @Item.RelationField} annotations as ComponentEntries.
     *
     * <p>Called automatically from {@link #onFullyInitialized()} for path-based items.
     */
    protected void populateRelationTable() {
        schema().populateRelationEntries(content(), this);
    }

    /**
     * Populate the vocabulary from the cached schema.
     *
     * <p>Uses {@link ItemSchema#populateVocabulary(Vocabulary, Item)} to add
     * all verbs discovered during class scanning:
     * <ul>
     *   <li>{@code @Verb} methods on this class hierarchy</li>
     *   <li>{@code @Verb} methods on all components (future)</li>
     * </ul>
     *
     * <p>Called automatically from {@link #onFullyInitialized()} for path-based items.
     * Other item types should call this explicitly if they need verb dispatch.
     */
    protected void populateVocabulary() {
        // Clear any existing verbs (in case called multiple times)
        vocabulary().clear();

        // Code layer: @Verb annotations from class hierarchy
        schema().populateVocabulary(vocabulary(), this);

        // User/data layer: EntryVocabulary contributions from ComponentEntries
        if (content() != null) {
            for (var entry : content()) {
                var entryVocab = entry.vocabulary();
                if (entryVocab.contributions() == null) continue;
                for (var term : entryVocab.contributions()) {
                    if (term.isExpression() && term.token() != null) {
                        vocabulary().addExpression(term.token(), term.expression());
                    } else if (term.termRef() != null && term.token() != null) {
                        vocabulary().addAlias(term.token(), term.termRef());
                    }
                }
            }
        }
    }

    /**
     * Get a component instance by alias, HID text, or handleKey.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Alias index (human-facing names)</li>
     *   <li>Raw "hid:..." reference</li>
     *   <li>Backward compat: hash as handleKey</li>
     * </ol>
     *
     * @param ref The component reference (alias, hid:..., or handleKey)
     * @return The component, or null if not found
     */
    public Object component(String ref) {
        // 1. Try alias index
        Optional<HandleID> aliased = content().resolveAlias(ref);
        if (aliased.isPresent()) return component(aliased.get());
        // 2. Try "hid:..." raw reference
        if (ref.startsWith("hid:")) return component(HandleID.parse(ref));
        // 3. Backward compat: hash as handleKey
        return component(HandleID.of(ref));
    }

    /**
     * Get a component instance by handle ID.
     *
     * @param handle The component handle
     * @return The component, or null if not found
     */
    public Object component(HandleID handle) {
        Optional<Object> live = content().getLive(handle);
        if (live.isPresent()) return live.get();

        // Lazy decode from metadata entry when live cache is cold.
        Optional<ComponentEntry> entryOpt = content().get(handle);
        if (entryOpt.isEmpty()) return null;

        try {
            Object decoded = decodeComponent(entryOpt.get());
            if (decoded != null) {
                content().setLive(handle, decoded);
                return decoded;
            }
        } catch (Exception e) {
            logger.debug("Lazy component decode failed for {}: {}", handle, e.getMessage());
        }

        // Final fallback: read the bound schema field directly.
        // This keeps simple @ContentField values visible even if no live decode path exists.
        ComponentFieldSpec spec = schema().getComponentField(handle);
        if (spec != null) {
            Object fieldValue = spec.getValue(this);
            if (fieldValue != null) {
                content().setLive(handle, fieldValue);
                return fieldValue;
            }
        }
        return null;
    }

    /**
     * Find the ComponentEntry that owns a given live component instance.
     *
     * <p>This is useful for component-level logic that needs to update
     * entry facets (config/presentation/vocabulary) for itself.
     *
     * @param componentInstance live component instance
     * @return matching entry, if present
     */
    public Optional<ComponentEntry> componentEntry(Object componentInstance) {
        if (componentInstance == null) return Optional.empty();
        for (ComponentEntry entry : content()) {
            if (entry.instance() == componentInstance) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * Get a component instance by alias/handleKey, cast to a specific type.
     *
     * @param ref The component reference (alias, hid:..., or handleKey)
     * @param type The expected component type
     * @return The component, or empty if not found or wrong type
     */
    public <T> Optional<T> component(String ref, Class<T> type) {
        Object value = component(ref);
        if (value == null) return Optional.empty();
        if (type.isInstance(value)) return Optional.of(type.cast(value));
        return Optional.empty();
    }

    /**
     * Dynamically add a component to this item.
     *
     * <p>This enables the core pattern: any Item can host any component.
     * The component's verbs are scanned and added to this item's vocabulary,
     * so they "bubble up" and become dispatchable through this item.
     *
     * <p>The component can be any object with a {@code @Type}
     * annotation — it does not need to implement Component.
     *
     * @param handle    The component handle (e.g., "chess")
     * @param component The component instance
     */
    public void addComponent(String handle, Object component) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(component, "component");

        HandleID hid = HandleID.of(handle);
        ItemID typeId = Components.typeId(component.getClass());

        // 1. Add metadata entry
        ComponentEntry entry = ComponentEntry.builder()
                .handle(hid)
                .alias(handle)
                .type(typeId)
                .identity(true)
                .build();
        content().add(entry);

        // 2. Register live instance
        content().setLive(hid, handle, component);

        // 3. Call lifecycle hook (only if Component)
        if (component instanceof Component c) {
            c.initComponent(this);
        }

        // 4. Scan component class for verbs and register them
        List<VerbSpec> verbs = ItemScanner.scanComponentVerbs(component.getClass(), handle);
        for (VerbSpec spec : verbs) {
            vocabulary().add(VerbEntry.componentVerb(spec, handle, component));
        }
    }

    // ==================================================================================
    // Verb Dispatch
    // ==================================================================================

    /**
     * Dispatch a command to this item via vocabulary lookup.
     *
     * <p>Resolves the command token to a verb using the item's {@link Vocabulary}:
     * <ul>
     *   <li>With librarian: token → {@link dev.everydaythings.graph.library.dictionary.TokenDictionary}
     *       → Sememe ID → Vocabulary → VerbEntry</li>
     *   <li>Without librarian (seed items): tries direct Sememe ID lookup</li>
     * </ul>
     *
     * <p>This enables language-agnostic dispatch: the same verb can be
     * invoked via "create", "crear", "新建", etc.
     *
     * @param caller  The identity of who is invoking this verb
     * @param command The command token (resolved via TokenDictionary)
     * @param args    The arguments as strings
     * @return The invocation result
     */
    public ActionResult dispatch(ItemID caller, String command, List<String> args) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(args, "args");

        // Build context — resolve caller to Signer if available
        dev.everydaythings.graph.item.user.Signer callerSigner = null;
        if (librarian != null && caller != null) {
            callerSigner = librarian.get(caller, dev.everydaythings.graph.item.user.Signer.class).orElse(null);
        }
        ActionContext ctx = ActionContext.of(caller, callerSigner, this, librarian);

        // Vocabulary dispatch (language-agnostic via TokenDictionary)
        Optional<VerbEntry> verbOpt;
        if (librarian != null) {
            verbOpt = vocabulary().lookupToken(command, librarian);
        } else {
            // Without librarian (seed items), try direct sememe ID lookup
            verbOpt = vocabulary().lookup(ItemID.fromString(command));
        }

        if (verbOpt.isEmpty()) {
            return ActionResult.failure(
                    new IllegalArgumentException("Unknown command: " + command));
        }

        VerbInvoker invoker = new VerbInvoker();
        return invoker.invokeWithStrings(verbOpt.get(), ctx, args);
    }

    /**
     * Dispatch a command to this item (anonymous caller).
     *
     * <p>Convenience method for local dispatch where caller identity isn't needed.
     *
     * @param command The action name
     * @param args    The arguments
     * @return The action result
     */
    public ActionResult dispatch(String command, List<String> args) {
        return dispatch(null, command, args);
    }

    // ==================================================================================
    // Relations
    // ==================================================================================

    /**
     * Get all relations where this item is the subject.
     *
     * <p>Returns a stream of relations that can be filtered by predicate:
     * <pre>{@code
     * // All relations from this item
     * item.relations().forEach(r -> ...);
     *
     * // Filter by predicate
     * item.relations()
     *     .filter(r -> r.predicate().equals(Sememe.WROTE.iid()))
     *     .forEach(r -> ...);
     * }</pre>
     *
     * <p>For more complex queries, use {@code librarian.library().find().from(this)}.
     *Our work is fundamentally to support and empower this local community.  We want to see our small town and its people healthy, productive, and abundant.

This public non- profit land trust’s top founding principle is to promote and provide education to the community regarding permaculture, sustainable agricultural practices, alternatives forms of building, and social organization.
     * @return Stream of relations where this item is the subject
     */
    public Stream<Relation> relations() {
        if (librarian == null) {
            return Stream.empty();
        }
        return librarian.library().find().from(this).relations();
    }

    /**
     * Get relations where this item is the subject with a specific predicate.
     *
     * <p>Convenience method for common pattern:
     * <pre>{@code
     * // Get all "authored by" relations
     * item.relations(Sememe.AUTHORED_BY).forEach(r -> ...);
     * }</pre>
     *
     * @param predicate The predicate to filter by
     * @return Stream of relations matching subject=this and the predicate
     */
    public Stream<Relation> relations(ItemID predicate) {
        if (librarian == null) {
            return Stream.empty();
        }
        return librarian.library().find().from(this).via(predicate).relations();
    }

    /**
     * Get all relations where this item is the object (reverse lookup).
     *
     * <p>Returns relations that point TO this item:
     * <pre>{@code
     * // Who wrote this book?
     * book.relationsTo().filter(r -> r.predicate().equals(wroteId)).forEach(...);
     * }</pre>
     *
     * @return Stream of relations where this item is the object
     */
    public Stream<Relation> relationsTo() {
        if (librarian == null) {
            return Stream.empty();
        }
        return librarian.library().find().to(this).relations();
    }

    /**
     * Create a relation from this item to another.
     *
     * <p>Creates, signs (if signer available), and stores the relation:
     * <pre>{@code
     * // This author wrote this book
     * author.relate(Sememe.WROTE.iid(), book.iid());
     *
     * // With a literal object
     * item.relate(predicateId, Literal.ofText("some value"));
     * }</pre>
     *
     * @param predicate The predicate (relationship type)
     * @param object The object (target of the relation)
     * @return The created relation
     */
    public Relation relate(ItemID predicate, Relation.Target object) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(object, "object");

        Relation relation = Relation.builder()
                .subject(iid)
                .predicate(predicate)
                .object(object)
                .build();

        // Sign if we have a signer
        if (this instanceof dev.everydaythings.graph.item.user.Signer signer) {
            relation.sign(signer);
        }

        // Store if we have a librarian
        if (librarian != null) {
            librarian.relation(relation);
        }

        return relation;
    }

    /**
     * Create a relation from this item to another item.
     *
     * <p>Convenience overload for item-to-item relations:
     * <pre>{@code
     * author.relate(Sememe.WROTE.iid(), book);
     * }</pre>
     *
     * @param predicate The predicate (relationship type)
     * @param object The target item
     * @return The created relation
     */
    public Relation relate(ItemID predicate, Item object) {
        return relate(predicate, Relation.iid(object.iid()));
    }

    /**
     * Create a relation from this item to another item by ID.
     *
     * <p>Convenience overload:
     * <pre>{@code
     * author.relate(Sememe.WROTE.iid(), bookId);
     * }</pre>
     *
     * @param predicate The predicate (relationship type)
     * @param objectId The target item ID
     * @return The created relation
     */
    public Relation relate(ItemID predicate, ItemID objectId) {
        return relate(predicate, Relation.iid(objectId));
    }

    // ==================================================================================
    // Path-Based Component Management
    // ==================================================================================

    /**
     * Ensure built-in components (PolicySet) exist in the ComponentTable.
     *
     * <p>Policy is persisted as an intrinsic component. Vocabulary is intrinsic runtime
     * state derived from schema/content and is not stored as a component entry.
     *
     * <p>Called in every constructor after {@code state.setOwner(this)}, before
     * {@code populateVocabulary()}.
     */
    private void initBuiltinComponents() {
        if (!content().hasLive(BuiltinHandles.POLICY)) {
            addBuiltinComponent("policy", new PolicySet());
        }
    }

    /**
     * Add a built-in component (Vocabulary or PolicySet) to the ComponentTable.
     */
    private void addBuiltinComponent(String handle, Object component) {
        HandleID hid = HandleID.of(handle);
        ItemID typeId = Components.typeId(component.getClass());
        ComponentEntry entry = ComponentEntry.builder()
                .handle(hid).alias(handle).type(typeId).identity(true).build();
        content().add(entry);
        content().setLive(hid, handle, component);
    }

    /**
     * Initialize fresh components for a newly created item.
     *
     * <p>Uses the cached {@link ItemSchema} to iterate over all @Item.ComponentField
     * annotations and create each component. For local resource components, opens at
     * the mount path. For other components, creates a default instance.
     *
     * <p>This method populates the ComponentTable with both entries (metadata) and
     * live instances. Field binding and initComponent() callbacks are handled
     * by hydrate() which is called afterward.
     */
    private void initializeFreshComponents() {
        ItemSchema itemSchema = schema();
        List<ComponentEntry> entries = new ArrayList<>();

        // Create instances for all Component-typed @ComponentField fields
        // (Non-Component fields like SigningPublicKey are handled during commit)
        for (ComponentFieldSpec spec : itemSchema.componentFields()) {
            // Skip fields that don't have @Type annotation
            if (!spec.fieldType().isAnnotationPresent(Type.class)) {
                continue;
            }

            // Use the field's declared type directly (not lookup by type ID)
            // This avoids issues with @Inherited annotations where subclasses
            // share the same type ID as their abstract parent
            Class<?> type = spec.fieldType();

            Object instance;
            ComponentEntry entry;

            // Determine alias: use spec.alias() if set, otherwise fall back to handleKey
            String alias = spec.alias().isEmpty() ? spec.handleKey() : spec.alias();

            if (spec.localOnly() && store != null && store.root() != null) {
                // Local resource with filesystem: open at mount path
                Path componentPath = store.root().resolve(spec.path());
                instance = Components.openPathBased(type, componentPath);
                entry = ComponentEntry.builder()
                        .handle(spec.handle()).alias(alias)
                        .type(spec.type()).identity(spec.identity()).build();
            } else if (spec.localOnly()) {
                // Local resource but in-memory mode: create default in-memory instance
                instance = Components.createDefault(type)
                        .orElseThrow(() -> new IllegalStateException(
                                "Cannot create default in-memory instance of local resource: " + type.getName()));
                entry = ComponentEntry.builder()
                        .handle(spec.handle()).alias(alias)
                        .type(spec.type()).identity(spec.identity()).build();
            } else {
                // Regular component: use pre-initialized field value if present, else create default
                Object existingValue = spec.getValue(this);
                if (existingValue != null && type.isInstance(existingValue)) {
                    // Field was pre-initialized (e.g., ExpressionComponent with pattern)
                    instance = existingValue;
                } else {
                    // Try createDefault first, fall back to instantiateComponent
                    Optional<?> defaultOpt = Components.createDefault(type);
                    instance = defaultOpt.isPresent()
                            ? defaultOpt.get()
                            : ComponentType.instantiateComponent(type);
                }
                // Create appropriate entry type based on component kind
                if (spec.stream()) {
                    // Stream component: starts with empty heads, content added via append
                    entry = ComponentEntry.builder()
                            .handle(spec.handle()).alias(alias)
                            .type(spec.type()).identity(spec.identity())
                            .payload(ComponentEntry.EntryPayload.builder().streamBased(true).build())
                            .build();
                } else {
                    // Snapshot component: CID computed during commit, use placeholder for now
                    // Note: The actual CID is computed in scanAndBindFields() during commit
                    entry = ComponentEntry.builder()
                            .handle(spec.handle()).alias(alias)
                            .type(spec.type()).identity(spec.identity()).build();
                }
            }

            // Add mount to ComponentEntry for path-based components
            if (spec.hasMountPath()) {
                String mountPath = "/" + spec.path();  // Convert filesystem path to presentation path
                entry.addMount(new Mount.PathMount(mountPath));
            }

            // Add to ComponentTable: both metadata and live instance
            content().add(entry);
            content().setLive(spec.handle(), alias, instance);

            entries.add(entry);

        }

        // Save component metadata to store and materialize mount directories
        if (store != null) {
            store.runInWriteTransaction(tx -> {
                store.saveHeadComponents(entries, tx);
            });
            if (store instanceof WorkingTreeStore wts) {
                wts.materializeMountPaths(content());
            }
        }
    }

    // ==================================================================================
    // Edit / Commit Lifecycle
    // ==================================================================================

    /**
     * Begin editing a new version. Call commit() when done.
     */
    public void edit() {
        if (editing) {
            throw new IllegalStateException("Already in edit mode");
        }
        editing = true;
        dirty = true;
    }

    /**
     * Commit the current state as a new version.
     *
     * @param signer The signer to sign the manifest (required)
     * @return The new VersionID
     */
    public VersionID commit(Signer signer) {
        Objects.requireNonNull(signer, "signer required for commit");
        logger.debug("Committing item {} (type={})", iid, getClass().getSimpleName());

        // Start edit mode if not already
        if (!editing) {
            edit();
        }

        // Populate state tables from annotated fields
        scanAndBindFields();

        // Build manifest with the item's state
        Manifest manifest = Manifest.builder()
                .iid(iid)
                .type(Item.idOf(this.getClass()))
                .parents(base != null ? List.of(base) : List.of())
                .state(state)
                .build();
        manifest.sign(signer);

        // Store manifest and content
        if (librarian != null) {
            storeManifest(manifest);
        }

        // Materialize mount directories for working tree items
        if (store instanceof WorkingTreeStore wts) {
            wts.materializeMountPaths(content());
        }

        // Update state
        this.current = manifest;
        this.base = manifest.vid();
        this.versions.add(manifest.vid());
        this.dirty = false;
        this.editing = false;

        logger.debug("Committed item {} -> vid={}", iid, manifest.vid());
        return manifest.vid();
    }

    /**
     * Persist the current item state without creating a new version.
     *
     * <p>This saves the working state of the item - component content, metadata,
     * and mounts - without creating a signed version. Use this for:
     * <ul>
     *   <li>Auto-save / periodic persistence</li>
     *   <li>Saving work-in-progress before committing</li>
     *   <li>Items that don't need version history</li>
     * </ul>
     *
     * <p>Unlike {@link #commit(Signer)}, this method:
     * <ul>
     *   <li>Does NOT require a signer</li>
     *   <li>Does NOT create a new VersionID</li>
     *   <li>Does NOT update the manifest</li>
     *   <li>DOES save all component content to the store</li>
     *   <li>DOES update component and mount metadata</li>
     * </ul>
     *
     * <p>Requires a store - items without a backing store cannot persist.
     *
     * @throws IllegalStateException if no store is available
     */
    public void persist() {
        if (store == null) {
            throw new IllegalStateException("Cannot persist: no store available");
        }

        logger.debug("Persisting item {} (type={})", iid, getClass().getSimpleName());

        List<ComponentEntry> entries = new ArrayList<>();

        store.runInWriteTransaction(tx -> {
            // Persist each component
            for (ComponentEntry entry : content()) {
                ComponentEntry updatedEntry = persistComponent(entry, store, tx);
                entries.add(updatedEntry);
            }

            // Save component metadata
            store.saveHeadComponents(entries, tx);
        });

        // Materialize mount directories for working tree items
        if (store instanceof WorkingTreeStore wts) {
            wts.materializeMountPaths(content());
        }

        // Clear dirty flag
        this.dirty = false;

        logger.debug("Persisted item {}", iid);
    }

    /**
     * Persist a single component, returning an updated entry with CID if applicable.
     */
    private ComponentEntry persistComponent(ComponentEntry entry, ItemStore targetStore,
                                          dev.everydaythings.graph.library.WriteTransaction tx) {
        // Local resources are managed by the component itself - nothing to persist
        if (entry.isLocalResource()) {
            return entry;
        }

        // References point to another item - no content bytes to persist
        if (entry.isReference()) {
            return entry;
        }

        // Get live instance
        Optional<?> liveOpt = content().getLive(entry.handle(), Object.class);
        if (liveOpt.isEmpty()) {
            return entry; // No live instance, keep existing entry
        }

        Object live = liveOpt.get();
        byte[] bytes;

        // Encode based on type
        if (live.getClass().isAnnotationPresent(Type.class)) {
            bytes = Components.encode(live);
        } else if (live instanceof Canonical canonical) {
            bytes = canonical.encodeBinary(Canonical.Scope.RECORD);
        } else if (ItemSchema.isSimpleSerializableType(live)) {
            bytes = ItemSchema.encodeSimpleValue(live);
        } else {
            return entry; // Unknown type, keep existing entry
        }

        // Store and get CID
        ContentID cid = targetStore.persistContent(bytes, tx);

        // Return updated entry with new CID
        return ComponentEntry.snapshot(entry.handle(), entry.type(), cid, entry.identity());
    }

    /**
     * Generate a deterministic manifest for seed items.
     *
     * <p>Seed items need manifests that are identical across all machines.
     * This is achieved by:
     * <ul>
     *   <li>Fixed timestamp of 0 (epoch)</li>
     *   <li>No signature (seed items are code-defined, not user-signed)</li>
     *   <li>Deterministic IID (derived from canonical key)</li>
     *   <li>Deterministic content hashes (same fields → same hash)</li>
     * </ul>
     *
     * <p>Note: This does NOT update the item's state (current, base, versions).
     * It just generates the manifest for storage during bootstrap.
     *
     * @return A deterministic manifest for this seed item
     */
    public Manifest generateSeedManifest() {
        // Start edit mode if not already
        if (!editing) {
            edit();
        }

        // Populate state tables from annotated fields
        scanAndBindFields();

        // Build without signature (seed items are code-defined, deterministic)
        Manifest manifest = Manifest.builder()
                .iid(iid)
                .type(Item.idOf(this.getClass()))
                .parents(base != null ? List.of(base) : List.of())
                .state(state)
                .build();

        // Reset edit mode (don't persist state changes)
        this.editing = false;

        return manifest;
    }

    /**
     * Abort current edit, discarding uncommitted changes.
     */
    public void abortEdit() {
        editing = false;
        // Note: in-memory field changes are not reverted
    }

    /**
     * Encode a component field value by handle.
     *
     * <p>Used during seed import to get the encoded bytes for storage.
     * This re-encodes the field value (deterministic, same hash as manifest).
     *
     * @param handle The component handle to encode
     * @return Encoded bytes, or null if handle not found
     */
    public byte[] encodeComponentValue(HandleID handle) {
        // Find the field spec with this handle from cached schema
        for (ComponentFieldSpec spec : schema().componentFields()) {
            if (!spec.handle().equals(handle)) continue;

            Object value = spec.getValue(this);
            if (value == null) return null;

            // Encode based on type
            if (value.getClass().isAnnotationPresent(Type.class)) {
                return Components.encode(value);
            } else if (value instanceof Canonical canonical) {
                return canonical.encodeBinary(Canonical.Scope.RECORD);
            } else if (ItemSchema.isSimpleSerializableType(value)) {
                return ItemSchema.encodeSimpleValue(value);
            }
            return null;
        }
        return null;
    }

    /**
     * Mark item as dirty (has uncommitted changes).
     */
    protected void markDirty() {
        this.dirty = true;
    }

    // ==================================================================================
    // Field Binding (Annotation Processing)
    // ==================================================================================

    /**
     * Populate state tables from annotated fields for commit.
     *
     * <p>Since Manifest now embeds ItemState directly, we just populate the tables.
     * Delegates to {@link ItemSchema} for the actual field processing.
     */
    private void scanAndBindFields() {
        // Payload storage function - stores bytes via librarian and returns CID
        java.util.function.Function<byte[], ContentID> storePayload = (librarian != null)
                ? bytes -> { librarian.storePayload(bytes); return ContentID.of(bytes); }
                : null;

        // Payload storage as Consumer for component fields (legacy signature)
        java.util.function.Consumer<byte[]> storePayloadConsumer = (librarian != null) ? librarian::storePayload : null;

        // Relation storage function - stores canonical relations via librarian (DB RELATION column)
        java.util.function.Consumer<Relation> storeRelation = (librarian != null) ? librarian::relation : null;

        // Bind component fields (encode and add to content table)
        schema().bindComponentFieldsForCommit(this, content(), storePayloadConsumer);

        // Bind relation fields (create relations, store in DB, add as ComponentEntries)
        schema().bindRelationFieldsForCommit(this, content(), storePayload, storeRelation);

    }

    // ==================================================================================
    // Storage Operations
    // ==================================================================================

    private void storeManifest(Manifest manifest) {
        byte[] body = manifest.encodeBinary(Canonical.Scope.RECORD);
        librarian.storeManifest(body);
    }

    // ==================================================================================
    // Hydration (Loading from Manifest)
    // ==================================================================================

    /**
     * Unified hydration: decode components from store, populate ComponentTable, bind fields.
     *
     * <p>The ComponentTable is the source of truth for what an item contains.
     * Fields (@ComponentField) are optional developer ergonomics that bind to
     * entries in the table.
     *
     * <p>Flow:
     * <ol>
     *   <li>For each ComponentEntry in the table, decode the content from the store</li>
     *   <li>Store the live instance in ComponentTable</li>
     *   <li>Bind matching @ComponentField fields</li>
     *   <li>Invoke initComponent() callbacks on all Component instances</li>
     * </ol>
     *
     * <p>Components are the ONLY non-Canonical things:
     * <ul>
     *   <li>Component types → Component.decode() or Component.openPathBased()</li>
     *   <li>Everything else → Canonical.decodeBinary()</li>
     * </ul>
     */
    protected void hydrate() {
        // Phase 1: Decode components that don't already have live instances
        // (Fresh items may already have live instances from initializeFreshComponents())
        for (ComponentEntry entry : content()) {
            if (content().hasLive(entry.handle())) {
                continue;  // Already decoded/created
            }
            try {
                Object instance = decodeComponent(entry);
                if (instance != null) {
                    content().setLive(entry.handle(), instance);
                }
            } catch (Exception e) {
                logger.warn("Failed to decode component {} (type {}): {}",
                        entry.alias() != null ? entry.alias() : entry.handle(),
                        entry.type(), e.getMessage());
            }
        }

        // Phase 2: Bind @ComponentField fields from ComponentTable
        bindFieldsFromTable();

        // Phase 3: Invoke initComponent() callbacks on Component instances
        content().forEachLive(Component.class, comp -> comp.initComponent(this));
    }

    /**
     * Decode a component from its ComponentEntry.
     *
     * @param entry The component metadata
     * @return The decoded instance, or null if content unavailable
     */
    @SuppressWarnings("unchecked")
    private Object decodeComponent(ComponentEntry entry) {
        // Reference → resolve the target item via librarian
        if (entry.isReference()) {
            return resolveReference(entry);
        }

        // Local resource → open at mount path (requires filesystem)
        if (entry.isLocalResource()) {
            return openLocalResource(entry);
        }

        // Snapshot → fetch content by CID
        if (entry.hasSnapshot()) {
            Optional<byte[]> bytesOpt = fetchContent(entry.payload().snapshotCid());
            if (bytesOpt.isEmpty()) {
                return null;
            }
            return decodeContent(entry, bytesOpt.get());
        }

        // Stream → create a fresh instance via factory
        // Stream components (KeyLog, CertLog, etc.) are append-only logs whose heads
        // contain individual entries, not the full component state. We can't decode
        // the component from a single head. Instead, create a fresh instance and let
        // it replay entries from the store when needed.
        if (entry.hasStream()) {
            return createStreamComponent(entry);
        }

        return null;
    }

    /**
     * Create a fresh instance of a stream component.
     *
     * <p>Stream components (KeyLog, CertLog, etc.) are append-only logs whose
     * heads contain individual entries, not the full component state. Instead of
     * trying to decode the component from a single entry, we create a fresh
     * instance via the component's factory method. The component can replay
     * entries from the store later when needed.
     *
     * @param entry The stream component entry
     * @return A fresh component instance, or null if the type can't be created
     */
    @SuppressWarnings("unchecked")
    private Object createStreamComponent(ComponentEntry entry) {
        Optional<Class<?>> impl = findImplementation(entry.type());
        if (impl.isEmpty()) {
            logger.debug("createStreamComponent() - no implementation for type {}", entry.type());
            return null;
        }
        Class<?> cls = impl.get();
        Optional<?> instance = Components.createDefault(cls);
        if (instance.isPresent()) {
            return instance.get();
        }
        // Fall back to instantiate via factory/constructor
        return ComponentType.instantiateComponent(cls);
    }

    /**
     * Resolve a reference component entry to the target item.
     *
     * <p>Uses the librarian to look up the referenced item by its ItemID.
     * Returns the resolved Item as the live instance, or null if the
     * reference cannot be resolved (no librarian, item not found).
     *
     * @param entry The reference component entry
     * @return The resolved Item, or null if unavailable
     */
    private Object resolveReference(ComponentEntry entry) {
        if (librarian == null) {
            return null;
        }
        Optional<Item> resolved = librarian.get(entry.payload().referenceTarget(), Item.class);
        if (resolved.isEmpty()) {
            logger.debug("Reference target not found: {} (handle={})",
                    entry.payload().referenceTarget(), entry.displayToken());
        }
        return resolved.orElse(null);
    }

    /**
     * Open a local resource component at its mount path.
     *
     * @param entry The component entry (must be local resource)
     * @return The opened component, or null if no filesystem access
     */
    @SuppressWarnings("unchecked")
    private Object openLocalResource(ComponentEntry entry) {
        // Need filesystem access to open local resources
        Path root = (store != null) ? store.root() : null;
        if (root == null) {
            // No filesystem context - local resources stay null
            // (e.g., loading someone else's Signer via Librarian.get())
            return null;
        }

        // Find mount path for this handle
        Path mountPath = resolveMountPath(entry.handle());
        if (mountPath == null) {
            return null;
        }

        // Find implementation class
        Optional<Class<?>> implOpt = findImplementation(entry.type());
        if (implOpt.isEmpty()) {
            return null;
        }

        return Components.openPathBased(implOpt.get(), mountPath);
    }

    /**
     * Fetch content bytes by CID from the store or librarian.
     */
    private Optional<byte[]> fetchContent(ContentID cid) {
        // Try store first (for path-based items)
        if (store != null) {
            Optional<byte[]> bytes = store.content(cid);
            if (bytes.isPresent()) {
                return bytes;
            }
        }

        // Fall back to librarian (for manifest-based items)
        if (librarian != null) {
            return librarian.content(cid);
        }

        return Optional.empty();
    }

    /**
     * Decode content bytes into an instance based on type.
     *
     * <p>Priority: Relation, then primitive types, then Canonical types via universal decoder.
     */
    @SuppressWarnings("unchecked")
    private Object decodeContent(ComponentEntry entry, byte[] bytes) {
        ItemID typeId = entry.type();
        // Relation entries → decode directly (Relation is Canonical, not a Component)
        if (Relation.TYPE_ID.equals(typeId)) {
            return Relation.decode(bytes);
        }

        // Primary path: typeId -> IMPLEMENTED_BY -> Java class
        Optional<Class<?>> impl = findImplementation(typeId);
        if (impl.isPresent()) {
            Class<?> cls = impl.get();
            CBORObject node = CBORObject.DecodeFromBytes(bytes);
            return Canonical.decodeIntoType(cls, cls, node, Canonical.Scope.RECORD);
        }

        // Fallback for intrinsic schema-backed fields:
        // decode using the declared field type.
        ComponentFieldSpec spec = schema().getComponentField(entry.handle());
        if (spec != null) {
            CBORObject node = CBORObject.DecodeFromBytes(bytes);
            return Canonical.decodeIntoType(spec.fieldType(), spec.fieldType(), node, Canonical.Scope.RECORD);
        }

        return null;
    }

    /**
     * Find the implementing Java class for a type ID.
     *
     * <p>Delegates to store or librarian's unified findImplementation method.
     */
    private Optional<Class<?>> findImplementation(ItemID typeId) {
        if (store != null) {
            return store.findImplementation(typeId);
        }
        if (librarian != null) {
            return librarian.library().findImplementation(typeId);
        }
        return Optional.empty();
    }

    /**
     * Find Canonical implementation class for a type ID.
     */
    @SuppressWarnings("unchecked")
    private Optional<Class<? extends Canonical>> findCanonicalImplementation(ItemID typeId) {
        return findImplementation(typeId)
                .filter(Canonical.class::isAssignableFrom)
                .map(c -> (Class<? extends Canonical>) c);
    }

    /**
     * Resolve the mount path for a component handle.
     *
     * <p>Looks up the path from cached schema or mount table.
     * Priority: schema path > mount table > null
     */
    private Path resolveMountPath(HandleID handle) {
        Path root = (store != null) ? store.root() : null;
        if (root == null) {
            return null;
        }

        // Check cached schema for path
        for (ComponentFieldSpec spec : schema().componentFields()) {
            if (spec.handle().equals(handle) && spec.hasMountPath()) {
                return root.resolve(spec.path());
            }
        }

        // Check content table for runtime mounts
        return content().pathForHandle(handle)
                .map(mountPath -> {
                    // Convert presentation path to filesystem path
                    // e.g., "/documents" -> ".documents" (leading dot for hidden)
                    String fsPath = mountPath.equals("/") ? "" : mountPath.substring(1);
                    return root.resolve(fsPath);
                })
                .orElse(null);
    }

    /**
     * Bind @ComponentField fields from the ComponentTable's live instances.
     *
     * <p>Uses {@link ItemSchema#bindFieldsFromTable(Item, ComponentTable)} to inject
     * live instances into their corresponding fields.
     *
     * <p>For simple types (String, int, etc.) that weren't decoded during hydrate(),
     * fetch and decode inline using the field's declared type.
     */
    private void bindFieldsFromTable() {
        // Use schema for efficient field binding
        schema().bindFieldsFromTable(this, content());

        // Handle simple types that weren't decoded - decode and cache
        for (ComponentFieldSpec spec : schema().componentFields()) {
            HandleID handle = spec.handle();

            // Skip if already has a live instance in the table
            if (content().hasLive(handle)) continue;

            // Try to decode from stored content
            Optional<ComponentEntry> entryOpt = content().get(handle);
            if (entryOpt.isPresent() && entryOpt.get().hasSnapshot()) {
                Optional<byte[]> bytesOpt = fetchContent(entryOpt.get().payload().snapshotCid());
                if (bytesOpt.isPresent()) {
                    Object value = ItemSchema.decodeSimpleValue(spec.field(), bytesOpt.get());
                    if (value != null) {
                        spec.setValue(this, value);
                        content().setLive(handle, value);
                    }
                }
            }
        }
    }

    // ==================================================================================
    // Actions
    // ==================================================================================

    /**
     * Create a new instance of this item's type.
     *
     * <p>This action treats every item as a potential "template" for creating
     * new instances. When invoked on a type item (seed), it creates a new
     * instance of that type. When invoked on a regular item, it creates
     * another item of the same type.
     *
     * <p>The new item is created with a random IID, marked as dirty, and
     * returned without being saved. The caller is responsible for adding
     * components, setting relations, and saving the item.
     *
     * <p>Subclasses can override to provide custom initialization logic.
     *
     * @param ctx The action context (provides librarian reference)
     * @return A new instance of this type
     * @throws IllegalStateException if no librarian is available
     * @throws IllegalArgumentException if the type is abstract or has no suitable constructor
     */
    @Verb(value = dev.everydaythings.graph.language.Sememe.CREATE, doc = "Create a new instance of this type")
    public Item actionNew(
            ActionContext ctx,
            @dev.everydaythings.graph.item.component.Param(
                    value = "name", required = false, role = "NAME") String name) {
        Librarian lib = ctx.librarian();
        if (lib == null) {
            throw new IllegalStateException("Cannot create item without librarian");
        }

        @SuppressWarnings("unchecked")
        Class<? extends Item> itemClass = (Class<? extends Item>) this.getClass();

        // Check if instantiable (not abstract, not interface)
        if (Modifier.isAbstract(itemClass.getModifiers())) {
            throw new IllegalArgumentException(
                    "Cannot instantiate abstract type: " + itemClass.getSimpleName());
        }

        // Find and invoke constructor(Librarian)
        Item newItem;
        try {
            Constructor<? extends Item> ctor = itemClass.getDeclaredConstructor(Librarian.class);
            ctor.setAccessible(true);
            newItem = ctor.newInstance(lib);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Type " + itemClass.getSimpleName() + " has no Librarian constructor");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create " + itemClass.getSimpleName(), e);
        }

        // Apply title if a name was provided
        if (name != null && !name.isBlank()) {
            newItem.relate(
                    dev.everydaythings.graph.language.Sememe.TITLE.iid(),
                    Literal.ofText(name));
        }

        return newItem;
    }

    /**
     * Show available verbs and their documentation.
     *
     * <p>Returns the vocabulary itself — it's a Component with a Surface,
     * so the rendering pipeline handles display.
     */
    @Verb(value = dev.everydaythings.graph.language.Sememe.HELP, doc = "Show available verbs and their documentation")
    public Object actionHelp(ActionContext ctx) {
        return vocabulary();
    }

    /**
     * Navigate to a path within this item's mount tree.
     *
     * <p>Resolves the target path and returns a {@link Link} that the session
     * can use for navigation. Supports:
     * <ul>
     *   <li>{@code ".."} — navigate to parent path (or back to root)</li>
     *   <li>{@code "/path"} — absolute path within this item's mounts</li>
     *   <li>{@code "path"} — relative path (treated as absolute)</li>
     * </ul>
     *
     * <p>The path must resolve to either a real mounted component or a virtual
     * directory implied by deeper mounts. Returns a failure if the path doesn't exist.
     *
     * @param target The path to navigate to
     * @return A Link for the session to navigate to
     */
    @Verb(value = dev.everydaythings.graph.language.Sememe.CD, doc = "Navigate to path within item")
    public Link actionCd(
            @Param(value = "target", doc = "Path or '..' to go back") String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("cd requires a target path");
        }

        // ".." — navigate to parent
        if ("..".equals(target.trim())) {
            return Link.of(iid());  // Back to item root
        }

        // Canonicalize path
        String path = target.startsWith("/") ? target : "/" + target;
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(path);

        // Check if path exists as a real component
        if (content().atPath(canonical).isPresent()) {
            return Link.of(iid(), canonical);
        }

        // Check if path exists as a virtual directory (has children under it)
        if (content().hasChildren(canonical)) {
            return Link.of(iid(), canonical);
        }

        throw new IllegalArgumentException("No such path: " + target);
    }

    // ==================================================================================
    // Convenience Methods
    // ==================================================================================

    /**
     * Start building a relation with this item as subject.
     */
    public Relation.RelationBuilder relate() {
        return Relation.builder().subject(iid);
    }

    /**
     * Resolve the item type from @Type annotation.
     */
    protected String resolveItemType() {
        Type ann = getClass().getAnnotation(Type.class);
        return (ann != null && !ann.value().isBlank())
                ? ann.value()
                : getClass().getName();
    }

    // ==================================================================================
    // Static Factory Methods
    // ==================================================================================

    /**
     * Create a new basic Item.
     *
     * <p>This is the factory method for creating plain Items. For typed items,
     * use the {@code new} action on the appropriate type item.
     *
     * @param librarian The librarian for this item
     * @return A new Item
     */
    public static Item create(Librarian librarian) {
        return new Item(librarian);
    }

    // ==================================================================================
    // Static Type Utilities
    // ==================================================================================

    /**
     * Get the type key from an Item class.
     */
    public static String keyOf(Class<? extends Item> type) {
        return Components.typeKey(type);
    }

    /**
     * Get the type ID from an Item class.
     */
    public static ItemID idOf(Class<? extends Item> type) {
        return Components.typeId(type);
    }

    /**
     * Marks a static Item field as a seed instance for the SeedStore.
     *
     * <p>Seed items are bootstrap vocabulary: types, predicates, dimensions, units, etc.
     * They have deterministic IIDs derived from their canonical key.
     *
     * <p>Usage:
     * <pre>{@code
     * @Item.Seed
     * public static final Dimension LENGTH = new Dimension("cg.dim:length", "L", "length");
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Seed {}

    // ==================================================================================
    // Field Annotations for Components & Relations
    // ==================================================================================

    /**
     * Declares a component field on this Item.
     *
     * <p>Components are the building blocks of Items. They can be:
     * <ul>
     *   <li><b>Snapshot</b> - immutable content-addressed bytes</li>
     *   <li><b>Stream</b> - mutable CRDT-based content</li>
     *   <li><b>Local-only</b> - path-based, never synced (e.g., vaults, databases)</li>
     * </ul>
     *
     * <p>The component's type (via {@code @Type}) provides defaults for
     * snapshot/stream/localOnly. These can be overridden in the annotation.
     *
     * <p>If {@code path} is specified, the component lives at that path relative
     * to the item root, and a mount entry is created. For localOnly components,
     * path is required.
     *
     * <p>Usage:
     * <pre>{@code
     * @Item.ContentField(handleKey = "key")
     * private SigningPublicKey publicKey;
     *
     * @Item.ContentField(path = ".vault", localOnly = true)
     * private Vault vault;
     *
     * @Item.ContentField(handleKey = "keys", path = ".keys", stream = true)
     * private KeyLog keyLog;
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ContentField {
        /** HandleID seed. Empty = use field name (deterministic for hydration binding). */
        String handleKey() default "";

        /** Human-facing name (sememe token or literal). Empty = no alias. */
        String alias() default "";

        /** Mount path relative to item root. Required for localOnly, optional otherwise. */
        String path() default EMPTY;

        /** Override: store as snapshot content. Default from type. */
        boolean snapshot() default true;

        /** Override: store as stream content. Default from type. */
        boolean stream() default false;

        /** Override: local-only (no sync). Default from type's @Type. */
        boolean localOnly() default false;

        /** Contributes to version identity (VID). Default true for snapshots. */
        boolean identity() default true;
    }

    /**
     * Declares a relation field on this Item.
     *
     * <p>Relations are RDF-like triples with this item as the subject:
     * {@code (this item) —[predicate]→ (object)}
     *
     * <p>The field value becomes the object. Supported types:
     * <ul>
     *   <li>{@code ItemID} or {@code Item} - relation to another item</li>
     *   <li>{@code String} - text literal</li>
     *   <li>{@code Number} - numeric literal</li>
     *   <li>{@code AddressSpace.ParsedAddress} - address literal</li>
     *   <li>{@code Iterable} - multiple relations with same predicate</li>
     * </ul>
     *
     * <p>Usage:
     * <pre>{@code
     * @Item.RelationField(predicate = "cg.address:at-domain")
     * private List<AtDomain.Parsed> addresses;
     *
     * @Item.RelationField(predicate = "cg.predicate:author")
     * private ItemID author;
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface RelationField {
        /** Predicate for this relation (required). */
        String predicate();

        /** Include in manifest's relation list. Default true. */
        boolean canonical() default true;
    }
}
