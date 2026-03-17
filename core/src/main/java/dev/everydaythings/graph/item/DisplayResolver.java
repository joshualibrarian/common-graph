package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.PresentationConfig;
import dev.everydaythings.graph.frame.SurfaceTemplateComponent;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.network.RoutingVocabulary;
import dev.everydaythings.graph.value.Color;
import dev.everydaythings.graph.value.Value;

import java.util.Optional;

/**
 * Resolves display properties for Items — names, icons, colors, glyphs.
 *
 * <p>Extracted from Item.java to keep Item focused on core semantics.
 * All methods are static and take the Item as their first parameter.
 */
public final class DisplayResolver {
    private DisplayResolver() {}

    // ==================================================================================
    // Path Resolution
    // ==================================================================================

    /**
     * Resolve a path within an item to get display token.
     *
     * <p>Paths like "/componentHandle" are resolved through the content table.
     *
     * @param item the item to resolve within
     * @param path the path to resolve (e.g., "/readme")
     * @return display token for the component, or empty if not found
     */
    public static Optional<String> resolvePathDisplayToken(Item item, String path) {
        if (path == null || path.isEmpty()) {
            return Optional.of(item.displayToken());
        }

        // Strip leading slash
        String handle = path.startsWith("/") ? path.substring(1) : path;

        // Check EndorsementsTable for frames()
        if (handle.equals("content")) {
            return Optional.of(item.frames().displayToken());
        }

        // Resolve FrameKey from path component
        FrameKey key = FrameKey.literal(handle);

        // Prefer stable frame metadata to avoid label mutation when a component
        // gets lazily hydrated after first render.
        Optional<String> entryLabel = item.frames().getFrame(key).map(Frame::displayToken);
        if (entryLabel.isPresent()) {
            return entryLabel;
        }

        // Fall back to live payload display token when no entry metadata exists.
        Optional<Object> live = item.frames().getLive(key);
        if (live.isPresent()) {
            return Optional.of(resolvePayloadDisplayToken(live.get()));
        }
        return Optional.empty();
    }

    /**
     * Resolve a path within an item to get emoji.
     *
     * @param item the item to resolve within
     * @param path the path to resolve (e.g., "/readme")
     * @return emoji for the component, or empty if not found
     */
    public static Optional<String> resolvePathEmoji(Item item, String path) {
        if (path == null || path.isEmpty()) {
            return Optional.of(item.emoji());
        }

        // Strip leading slash
        String handle = path.startsWith("/") ? path.substring(1) : path;

        // Check EndorsementsTable for frames()
        if (handle.equals("content")) {
            return Optional.of(item.frames().emoji());
        }

        // Resolve FrameKey from path component
        FrameKey key = FrameKey.literal(handle);

        // Prefer stable frame metadata to avoid icon mutation when a component
        // gets lazily hydrated after first render.
        Optional<String> entryEmoji = item.frames().getFrame(key).map(f -> resolveFrameEmoji(item, f));
        if (entryEmoji.isPresent()) {
            return entryEmoji;
        }

        // Fall back to live payload emoji when no entry metadata exists.
        Optional<Object> live = item.frames().getLive(key);
        if (live.isPresent()) {
            return Optional.of(resolvePayloadEmoji(live.get()));
        }
        return Optional.empty();
    }

    /**
     * Resolve emoji from frame metadata with semantic type fallback.
     */
    public static String resolveFrameEmoji(Item item, Frame frame) {
        if (frame == null) return "\uD83D\uDCE6";
        String typeGlyph = resolveTypeGlyph(item, frame.type());
        if (typeGlyph != null && !typeGlyph.isBlank()) {
            return typeGlyph;
        }
        return frame.emoji();
    }

    /**
     * Resolve the semantic glyph for a type ID from type metadata.
     */
    public static String resolveTypeGlyph(Item item, ItemID typeId) {
        if (typeId == null) return null;

        // 1) Type item's SurfaceTemplateComponent glyph (authoritative display metadata)
        if (item.librarian != null) {
            Optional<Item> typeItem = item.librarian.get(typeId, Item.class);
            if (typeItem.isPresent()) {
                var st = typeItem.get().frames().getLive(
                        SurfaceTemplateComponent.HANDLE,
                        SurfaceTemplateComponent.class
                ).orElse(null);
                if (st != null && st.glyph() != null && !st.glyph().isBlank()) {
                    return st.glyph();
                }
            }
        }

        // 2) Implementation class annotations (works even without hydrated type item)
        Optional<Class<?>> impl = findImplementation(item, typeId);
        if (impl.isPresent()) {
            Class<?> cls = impl.get();
            Type type = cls.getAnnotation(Type.class);
            if (type != null && !type.glyph().isEmpty()) {
                return type.glyph();
            }
            Value.Type valueType = cls.getAnnotation(Value.Type.class);
            if (valueType != null && !valueType.glyph().isEmpty()) {
                return valueType.glyph();
            }
        }

        return null;
    }

    // ==================================================================================
    // Payload Resolution (static — no item context needed)
    // ==================================================================================

    /**
     * Resolve an emoji/glyph for an arbitrary live payload object.
     *
     * <p>EndorsementsTable payloads are open-ended and not required to implement
     * {@link dev.everydaythings.graph.frame.Component}, so this must not rely on a single interface.
     */
    static String resolvePayloadEmoji(Object payload) {
        if (payload == null) return "\uD83D\uDCE6";
        if (payload instanceof Value value) {
            return value.emoji();
        }
        Type type = payload.getClass().getAnnotation(Type.class);
        if (type != null && !type.glyph().isEmpty()) {
            return type.glyph();
        }
        Value.Type valueType = payload.getClass().getAnnotation(Value.Type.class);
        if (valueType != null && !valueType.glyph().isEmpty()) {
            return valueType.glyph();
        }
        return "\uD83D\uDCE6";
    }

    static String resolvePayloadDisplayToken(Object payload) {
        if (payload == null) return "(unnamed)";
        if (payload instanceof Value value) {
            return value.displayToken();
        }
        Implements impl = payload.getClass().getAnnotation(Implements.class);
        if (impl != null) {
            String key = impl.value();
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

    // ==================================================================================
    // Path Icon & Color Resolution
    // ==================================================================================

    /**
     * Resolve a path to a 2D icon resource path, if the component has one.
     *
     * <p>Checks the component's {@code @Type(icon=...)} annotation.
     */
    public static Optional<String> resolvePathIconResource(Item item, String path) {
        if (path == null || path.isEmpty()) return Optional.empty();

        String handle = path.startsWith("/") ? path.substring(1) : path;

        FrameKey key = FrameKey.literal(handle);

        return item.frames().getLive(key)
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
    public static Optional<Color> resolvePathTypeColor(Item item, String path) {
        if (path == null || path.isEmpty()) return Optional.empty();

        String handle = path.startsWith("/") ? path.substring(1) : path;

        FrameKey key = FrameKey.literal(handle);

        return item.frames().getLive(key)
                .map(o -> {
                    Type typeAnno = o.getClass().getAnnotation(Type.class);
                    if (typeAnno != null && typeAnno.color() != 0) {
                        return Color.fromPacked(typeAnno.color());
                    }
                    return null;
                });
    }

    // ==================================================================================
    // Icon, Color Category, Subtitle
    // ==================================================================================

    public static ItemID icon(Item item) {
        // Return this Item's type ID - UI will find the icon for that type
        Implements impl = item.getClass().getAnnotation(Implements.class);
        if (impl != null) {
            return ItemID.fromString(impl.value());
        }
        return ItemID.fromString(Item.KEY); // Default to base Item type
    }

    public static String colorCategory(Item item) {
        // Fallback coloring by name pattern
        String name = item.getClass().getSimpleName().toLowerCase();
        if (name.contains("librarian")) return "librarian";
        if (name.contains("principal")) return "principal";
        if (name.contains("workspace")) return "workspace";
        return "item";
    }

    public static String displaySubtitle(Item item) {
        return item.iid() != null ? item.iid().toString().substring(0, Math.min(20, item.iid().toString().length())) + "..." : "";
    }

    // ==================================================================================
    // Display Info
    // ==================================================================================

    /**
     * Get the display information for an item.
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
    public static DisplayInfo displayInfo(Item item) {
        // Phase 5: check PresentationConfig cascade first
        PresentationConfig pc = resolvedPresentation(item);
        if (pc != null && (pc.glyph() != null || pc.primaryColor() != -1)) {
            DisplayInfo.Builder b = DisplayInfo.builder()
                    .name(findDisplayName(item))
                    .typeName(findTypeName(item));
            if (pc.glyph() != null) b.iconText(pc.glyph());
            if (pc.primaryColor() != -1) {
                b.color(Color.fromPacked(pc.primaryColor()));
            }
            if (pc.shape() != null) {
                b.shape(DisplayInfo.Shape.fromString(pc.shape()));
            }
            return b.build();
        }

        // Fall back to SurfaceTemplateComponent path
        // 1. Check for instance-level SurfaceTemplateComponent
        var stcOpt = item.frames().getLive(
                SurfaceTemplateComponent.HANDLE,
                SurfaceTemplateComponent.class);
        if (stcOpt.isPresent()) {
            return stcOpt.get().toDisplayInfo(findDisplayName(item));
        }

        // 2. Look up type's SurfaceTemplateComponent from library
        if (item.librarian != null) {
            var typeSurface = getTypeSurfaceTemplate(item);
            if (typeSurface != null) {
                return typeSurface.toDisplayInfo(findDisplayName(item));
            }
        }

        // 3. Fall back to annotation-based display info
        return DisplayInfo.builder()
                .name(findDisplayName(item))
                .typeName(findTypeName(item))
                .color(findTypeColor(item))
                .iconText(findIconText(item))
                .build();
    }

    // ==================================================================================
    // Presentation Config Cascade
    // ==================================================================================

    /**
     * Resolve merged PresentationConfig via three-level cascade.
     *
     * <p>Merges presentation data from:
     * <ol>
     *   <li><b>Type/Sememe level</b> — the type item's PRESENTATION config</li>
     *   <li><b>Instance level</b> — this item's manifest or frame PRESENTATION config</li>
     * </ol>
     *
     * <p>Instance-level overrides type-level. Palette tokens merge (instance wins),
     * style rules concatenate (instance rules are highest priority),
     * glyph/shape override (instance wins).
     *
     * @return the merged PresentationConfig, or null if no presentation data at any level
     */
    public static PresentationConfig resolvedPresentation(Item item) {
        PresentationConfig result = null;

        // Level 1 (base): type/sememe item's PRESENTATION config
        if (item.librarian != null) {
            Implements impl = item.getClass().getAnnotation(Implements.class);
            if (impl != null) {
                ItemID typeId = ItemID.fromString(impl.value());
                var typeItemOpt = item.librarian.get(typeId, Item.class);
                if (typeItemOpt.isPresent()) {
                    result = extractPresentation(typeItemOpt.get());
                }
            }
        }

        // Level 2 (override): this item's PRESENTATION config
        PresentationConfig instanceConfig = extractPresentation(item);
        if (instanceConfig != null) {
            result = result != null ? result.merge(instanceConfig) : instanceConfig;
        }

        return result;
    }

    /**
     * Extract PresentationConfig from an item's config maps or frames.
     */
    static PresentationConfig extractPresentation(Item item) {
        // Check manifest config first
        Manifest mf = item.current();
        if (mf != null) {
            Binding mb = mf.configBinding(ThematicRole.Presentation.SEED.iid());
            if (mb != null && mb.target() instanceof Literal lit) {
                return decodePresentationConfig(lit.payload());
            }
        }

        // Check PRESENTATION frame on the item
        FrameKey presKey = FrameKey.of(ThematicRole.Presentation.SEED.iid());
        var presFrame = item.frames().getFrame(presKey);
        if (presFrame.isPresent() && presFrame.get().body() != null) {
            BindingTarget topic = presFrame.get().body()
                    .binding(ThematicRole.Topic.SEED.iid());
            if (topic instanceof Literal lit) {
                return decodePresentationConfig(lit.payload());
            }
        }

        return null;
    }

    static PresentationConfig decodePresentationConfig(byte[] bytes) {
        if (bytes == null) return null;
        try {
            return Canonical.decodeBinary(
                    bytes, PresentationConfig.class,
                    Canonical.Scope.RECORD);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================================================================================
    // Type Surface Template
    // ==================================================================================

    /**
     * Get the SurfaceTemplateComponent from an item's type (if available in the library).
     */
    public static SurfaceTemplateComponent getTypeSurfaceTemplate(Item item) {
        if (item.librarian == null) return null;

        // Get this item's type ID
        Implements impl = item.getClass().getAnnotation(Implements.class);
        if (impl == null) return null;

        ItemID typeId = ItemID.fromString(impl.value());

        // Look up the type item
        var typeItemOpt = item.librarian.get(typeId, Item.class);
        if (typeItemOpt.isEmpty()) return null;

        Item typeItem = typeItemOpt.get();

        // Get the type's SurfaceTemplateComponent
        return typeItem.frames().getLive(
                SurfaceTemplateComponent.HANDLE,
                SurfaceTemplateComponent.class
        ).orElse(null);
    }

    // ==================================================================================
    // Name / Type Name / Color / Icon Text Resolution
    // ==================================================================================

    /**
     * Find a human-readable display name for an item.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Check for a name frame (RoutingVocabulary.Name)</li>
     *   <li>Check for a title frame (CoreVocabulary.Title)</li>
     *   <li>Check for a hash-key frame (CoreVocabulary.HashKey)</li>
     *   <li>Check for literal field-name keys ("name", "title", "label")</li>
     *   <li>Use the class simple name</li>
     * </ol>
     */
    public static String findDisplayName(Item item) {
        // Try semantic keys first
        for (FrameKey key : new FrameKey[]{
                FrameKey.of(ItemID.fromString(RoutingVocabulary.Name.KEY)),
                FrameKey.of(ItemID.fromString(CoreVocabulary.Title.KEY)),
                FrameKey.of(ItemID.fromString(CoreVocabulary.HashKey.KEY))}) {
            var opt = item.frames().getLive(key, Object.class);
            if (opt.isPresent()) {
                Object value = opt.get();
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }
        // Try literal field-name keys (for items with bare @Frame fields)
        for (String fieldName : new String[]{"name", "title", "label"}) {
            var opt = item.frames().getLive(FrameKey.literal(fieldName), Object.class);
            if (opt.isPresent()) {
                Object value = opt.get();
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }
        // Fallback to class name
        return item.getClass().getSimpleName();
    }

    /**
     * Find the type name from the @Implements or @Type annotation.
     */
    public static String findTypeName(Item item) {
        Implements impl = item.getClass().getAnnotation(Implements.class);
        if (impl != null) {
            String key = impl.value();
            // Extract last segment: "cg.sememe:librarian" -> "Librarian"
            int sep = key.lastIndexOf('/');
            if (sep < 0) sep = key.lastIndexOf(':');
            if (sep >= 0 && sep < key.length() - 1) {
                String shortName = key.substring(sep + 1);
                return shortName.substring(0, 1).toUpperCase() + shortName.substring(1);
            }
            return key;
        }
        return item.getClass().getSimpleName();
    }

    /**
     * Find the type color from SurfaceTemplateComponent or annotation.
     */
    public static Color findTypeColor(Item item) {
        // Try to get from type's SurfaceTemplateComponent
        var typeSurface = getTypeSurfaceTemplate(item);
        if (typeSurface != null) {
            return typeSurface.toColor();
        }

        // Fall back to annotation color
        Type typeAnnotation = item.getClass().getAnnotation(Type.class);
        if (typeAnnotation != null) {
            return Color.fromPacked(typeAnnotation.color());
        }

        return Color.rgb(120, 120, 140); // Default gray
    }

    /**
     * Find the icon text (glyph) from SurfaceTemplateComponent or annotation.
     */
    public static String findIconText(Item item) {
        // Try to get from type's SurfaceTemplateComponent
        var typeSurface = getTypeSurfaceTemplate(item);
        if (typeSurface != null && typeSurface.glyph() != null) {
            return typeSurface.glyph();
        }

        // Fall back to @Type annotation glyph
        Type typeAnnotation = item.getClass().getAnnotation(Type.class);
        if (typeAnnotation != null && !typeAnnotation.glyph().isEmpty()) {
            return typeAnnotation.glyph();
        }

        return "\uD83D\uDCE6";  // Default item glyph
    }

    // ==================================================================================
    // Internal Helpers
    // ==================================================================================

    /**
     * Find the implementing Java class for a type ID.
     *
     * <p>Delegates to store or librarian's unified findImplementation method.
     */
    private static Optional<Class<?>> findImplementation(Item item, ItemID typeId) {
        if (item.store != null) {
            return item.store.findImplementation(typeId);
        }
        if (item.librarian != null) {
            return item.librarian.library().findImplementation(typeId);
        }
        return Optional.empty();
    }
}
