package dev.everydaythings.graph.value.address;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tilde address space: {@code ~host/path/to/item}.
 *
 * <p>Format: {@code ~host[/path/segments...]}
 * <ul>
 *   <li>host - the host identifier (required)</li>
 *   <li>path - optional path segments separated by /</li>
 * </ul>
 *
 * <p>Authority model: Host owners control their namespace.
 * Resolution contacts the host to resolve the path.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code ~alice} - Alice's root</li>
 *   <li>{@code ~example.com/docs} - docs folder on example.com</li>
 *   <li>{@code ~myhost/projects/common-graph/readme}</li>
 * </ul>
 */
@Type(value = Tilde.KEY, glyph = "〰️")
public class Tilde extends AddressSpace {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg.address:tilde";


    /** The address space item - use for predicates and type references. */
    public static final Tilde ITEM = new Tilde(KEY, "Tilde Address");

    // ==================================================================================
    // SYNTAX
    // ==================================================================================

    private static final Pattern PATTERN = Pattern.compile("^~([^/\\s]+)(/.*)?$");

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Type seed constructor - creates a minimal Tilde for use as type seed.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected Tilde(ItemID typeId) {
        super(typeId);
    }

    private Tilde(String key, String name) {
        super(key, name);
    }

    /**
     * Hydration constructor - reconstructs a Tilde from a stored manifest.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    private Tilde(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // ADDRESS SPACE IMPLEMENTATION
    // ==================================================================================

    @Override
    public Pattern syntaxPattern() {
        return PATTERN;
    }

    @Override
    public Optional<ParsedAddress> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Matcher m = PATTERN.matcher(raw.trim());
        if (!m.matches()) return Optional.empty();

        String host = m.group(1);
        String pathPart = m.group(2);

        List<String> path;
        if (pathPart == null || pathPart.equals("/") || pathPart.isBlank()) {
            path = List.of();
        } else {
            // Remove leading slash and split
            path = Arrays.stream(pathPart.substring(1).split("/"))
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        return Optional.of(new Parsed(host, path));
    }

    @Override
    public int score(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        if (raw.charAt(0) != '~') return -1;
        if (!PATTERN.matcher(raw.trim()).matches()) return -1;
        // Score based on path depth
        int slashes = (int) raw.chars().filter(ch -> ch == '/').count();
        return 5 + slashes * 2;
    }

    // ==================================================================================
    // PARSED ADDRESS
    // ==================================================================================

    /**
     * A parsed tilde address with host and path components.
     */
    public record Parsed(String host, List<String> path) implements ParsedAddress {

        public Parsed {
            Objects.requireNonNull(host, "host");
            path = path == null ? List.of() : List.copyOf(path);
        }

        @Override
        public AddressSpace space() {
            return ITEM;
        }

        @Override
        public String raw() {
            return path.isEmpty() ? "~" + host : "~" + host + "/" + String.join("/", path);
        }

        @Override
        public String canonical() {
            // Normalize: lowercase host
            String normHost = host.toLowerCase();
            return path.isEmpty() ? "~" + normHost : "~" + normHost + "/" + String.join("/", path);
        }

        /**
         * Check if this is a root address (no path).
         */
        public boolean isRoot() {
            return path.isEmpty();
        }

        /**
         * Get the last path segment, or empty if root.
         */
        public Optional<String> leaf() {
            return path.isEmpty() ? Optional.empty() : Optional.of(path.get(path.size() - 1));
        }

        /**
         * Get the parent path (all but last segment).
         */
        public Parsed parent() {
            if (path.isEmpty()) return this;
            return new Parsed(host, path.subList(0, path.size() - 1));
        }

        /**
         * Append a path segment.
         */
        public Parsed child(String segment) {
            var newPath = new ArrayList<>(path);
            newPath.add(segment);
            return new Parsed(host, newPath);
        }
    }
}
