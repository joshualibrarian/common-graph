package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
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

        // Resolve component by name (scans by sememe short name)
        Object component = item.component(handle);
        if (component != null) {
            return Optional.of(resolvePayloadDisplayToken(component));
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

        // Resolve component by name (scans by sememe short name)
        Object component = item.component(handle);
        if (component != null) {
            return Optional.of(resolvePayloadEmoji(component));
        }
        return Optional.empty();
    }

    /**
     * Resolve emoji from frame metadata with semantic type fallback.
     */
    public static String resolveFrameEmoji(Item item, Frame frame) {
        if (frame == null) return "\uD83D\uDCE6";
        // TODO: resolve glyph from type item's scene metadata
        return frame.emoji();
    }

    // ==================================================================================
    // Payload Resolution (static — no item context needed)
    // ==================================================================================

    /**
     * Resolve an emoji/glyph for an arbitrary live payload object.
     */
    static String resolvePayloadEmoji(Object payload) {
        if (payload == null) return "\uD83D\uDCE6";
        if (payload instanceof Value value) {
            return value.emoji();
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

    public static Optional<String> resolvePathIconResource(Item item, String path) {
        return Optional.empty();
    }

    public static Optional<Color> resolvePathTypeColor(Item item, String path) {
        return Optional.empty();
    }

    // ==================================================================================
    // Icon, Color Category, Subtitle
    // ==================================================================================

    public static ItemID icon(Item item) {
        Implements impl = item.getClass().getAnnotation(Implements.class);
        if (impl != null) {
            return ItemID.fromString(impl.value());
        }
        return ItemID.fromString(Item.KEY);
    }

    public static String colorCategory(Item item) {
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
     */
    public static DisplayInfo displayInfo(Item item) {
        return DisplayInfo.builder()
                .name(findDisplayName(item))
                .typeName(findTypeName(item))
                .color(findTypeColor(item))
                .iconText(findIconText(item))
                .build();
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
     *   <li>Use the class simple name</li>
     * </ol>
     */
    public static String findDisplayName(Item item) {
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
        return item.getClass().getSimpleName();
    }

    /**
     * Find the type name from the @Implements annotation.
     */
    public static String findTypeName(Item item) {
        Implements impl = item.getClass().getAnnotation(Implements.class);
        if (impl != null) {
            String key = impl.value();
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
     * Find the type color.
     */
    public static Color findTypeColor(Item item) {
        return Color.rgb(120, 120, 140); // Default gray
    }

    /**
     * Find the icon text (glyph).
     */
    public static String findIconText(Item item) {
        // TODO: resolve glyph from type item's scene metadata
        return "\uD83D\uDCE6";
    }
}
