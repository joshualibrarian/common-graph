package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.RelationID;
import dev.everydaythings.graph.item.id.VersionID;

import java.util.Objects;
import java.util.Optional;

/**
 * Universal address for anything in Common Graph.
 *
 * <p>Link consolidates two addressing modes:
 *
 * <h2>Formal Syntax</h2>
 * <pre>
 * iid:&lt;iid&gt;[@&lt;versionSpec&gt;][#&lt;relationSpec&gt; | \&lt;contentSpec&gt;[\&lt;selector&gt;] | \\&lt;selector&gt;]
 * </pre>
 * <ul>
 *   <li>{@code iid:abc123} - Item itself</li>
 *   <li>{@code iid:abc123@v1} - Specific version</li>
 *   <li>{@code iid:abc123#predicate} - Relation by handle</li>
 *   <li>{@code iid:abc123\readme} - Content component by handle</li>
 *   <li>{@code iid:abc123\readme\line:42} - Content with selector</li>
 * </ul>
 *
 * <h2>Path Syntax</h2>
 * <pre>
 * iid:&lt;iid&gt;/path/to/thing
 * </pre>
 * <p>Paths are resolved through the mount system, which maps simple paths to
 * formal component addresses. This provides a navigable tree view.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Formal syntax
 * Link.parse("iid:abc123@latest\\readme")
 * Link.ofItem(myId).version("latest").content("readme")
 *
 * // Path syntax
 * Link.of(myId, "/documents/readme")
 * link.child("subdir").parent()
 * }</pre>
 *
 * @see dev.everydaythings.graph.item.component.FrameTable
 */
public final class Link {

    private final ItemID item;
    private final VersionSpec version;      // nullable
    private final Component component;      // nullable
    private final String selector;          // nullable; only if component is Content
    private final String path;              // nullable; alternative to component addressing

    // ==================== Constructors ====================

    private Link(ItemID item, VersionSpec version, Component component, String selector, String path) {
        this.item = Objects.requireNonNull(item, "item");
        this.version = version;
        this.component = component;
        this.selector = selector;
        this.path = path;

        // Validation
        if (selector != null) {
            if (!(component instanceof Component.Content)) {
                throw new IllegalArgumentException("selector requires content component");
            }
            if (selector.isEmpty()) throw new IllegalArgumentException("selector cannot be empty");
            if (containsWhitespace(selector)) throw new IllegalArgumentException("selector cannot contain whitespace");
            if (selector.indexOf('\\') >= 0) throw new IllegalArgumentException("selector cannot contain backslash");
        }

        if (path != null && component != null) {
            throw new IllegalArgumentException("cannot have both path and component addressing");
        }
    }

    // ==================== Factory Methods ====================

    /**
     * Link to an Item itself (no path or component).
     */
    public static Link of(ItemID item) {
        return new Link(item, null, null, null, null);
    }

    /**
     * Link with path-based addressing (resolved through mounts).
     */
    public static Link of(ItemID item, String path) {
        return new Link(item, null, null, null, path != null && !path.isEmpty() ? path : null);
    }

    /**
     * Link to an Item (alias for {@link #of(ItemID)}).
     */
    public static Link ofItem(ItemID item) {
        return of(item);
    }

    // ==================== Accessors ====================

    public ItemID item() { return item; }
    public Optional<VersionSpec> version() { return Optional.ofNullable(version); }
    public Optional<Component> component() { return Optional.ofNullable(component); }
    public Optional<String> selector() { return Optional.ofNullable(selector); }
    public Optional<String> path() { return Optional.ofNullable(path); }

    // ==================== Path Navigation ====================

    /**
     * Is this a link to the Item root (no path or component)?
     */
    public boolean isRoot() {
        return (path == null || path.isEmpty()) && component == null && selector == null;
    }

    /**
     * Get the parent link (one level up in the path).
     */
    public Link parent() {
        if (path == null || path.isEmpty()) return this;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) return of(item);
        return of(item, path.substring(0, lastSlash));
    }

    /**
     * Create a child link by appending a path segment.
     */
    public Link child(String segment) {
        String childPath = (path == null || path.isEmpty())
            ? "/" + segment
            : path + "/" + segment;
        return of(item, childPath);
    }

    // ==================== Builder-style Methods ====================

    /**
     * Add version specification.
     */
    public Link version(String versionHandle) {
        return new Link(item, new VersionSpec.Handle(versionHandle), component, selector, path);
    }

    /**
     * Add version specification by ID.
     */
    public Link version(VersionID vid) {
        return new Link(item, new VersionSpec.Id(vid), component, selector, path);
    }

    /**
     * Add content component by handle.
     */
    public Link content(String handle) {
        return new Link(item, version, new Component.Content(new ContentSpec.Handle(handle)), null, null);
    }

    /**
     * Add content component by ID.
     */
    public Link content(ContentID cid) {
        return new Link(item, version, new Component.Content(new ContentSpec.Id(cid)), null, null);
    }

    /**
     * Add relation by handle.
     */
    public Link relation(String handle) {
        return new Link(item, version, new Component.Relation(new RelationSpec.Handle(handle)), null, null);
    }

    /**
     * Add relation by ID.
     */
    public Link relation(RelationID rid) {
        return new Link(item, version, new Component.Relation(new RelationSpec.Id(rid)), null, null);
    }

    /**
     * Add selector (requires content component).
     */
    public Link selector(String selector) {
        if (!(component instanceof Component.Content)) {
            throw new IllegalStateException("selector requires content component");
        }
        return new Link(item, version, component, selector, null);
    }

    // ==================== Encoding ====================

    /**
     * Encode to text representation.
     *
     * <p>Path-based links encode as: {@code iid:xxx/path/to/thing}
     * <p>Component-based links encode as: {@code iid:xxx@ver#rel} or {@code iid:xxx\content\selector}
     */
    public String encodeText() {
        StringBuilder sb = new StringBuilder();
        sb.append(item.encodeText());

        if (version != null) sb.append('@').append(version.encodeToken());

        if (path != null) {
            sb.append(path);
        } else if (component != null) {
            if (component instanceof Component.Relation rel) {
                sb.append('#').append(rel.spec().encodeToken());
            } else if (component instanceof Component.Content con) {
                sb.append('\\').append(con.spec().encodeTokenOrEmpty());
                if (selector != null) sb.append('\\').append(selector);
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() { return encodeText(); }

    // ==================== Parsing ====================

    /**
     * Parse a link from text.
     *
     * <p>Supports both formal syntax ({@code iid:xxx@ver#rel\content\selector})
     * and path syntax ({@code iid:xxx/path/to/thing}).
     */
    public static Link parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) throw new IllegalArgumentException("blank link");
        if (!text.startsWith(ItemID.IID_PREFIX)) throw new IllegalArgumentException("Link must start with iid:");

        // Check for path syntax first (contains / after iid: prefix)
        int pathSlash = text.indexOf('/', ItemID.IID_PREFIX.length());
        int atSign = text.indexOf('@');
        int hash = text.indexOf('#');
        int backslash = text.indexOf('\\');

        // Path syntax: slash comes before any formal syntax markers
        if (pathSlash >= 0 && (atSign < 0 || pathSlash < atSign)
                          && (hash < 0 || pathSlash < hash)
                          && (backslash < 0 || pathSlash < backslash)) {
            String iidPart = text.substring(0, pathSlash);
            String pathPart = text.substring(pathSlash);
            return of(ItemID.parse(iidPart), pathPart);
        }

        // Formal syntax parsing
        return parseFormalSyntax(text);
    }

    private static Link parseFormalSyntax(String text) {
        if (containsWhitespace(text)) throw new IllegalArgumentException("whitespace not allowed in link: " + text);

        // split off backslash tail first (content/selector)
        int firstSlash = text.indexOf('\\');
        String head = (firstSlash >= 0) ? text.substring(0, firstSlash) : text;
        String tail = (firstSlash >= 0) ? text.substring(firstSlash) : null;

        // optional #relation in head (mutually exclusive with tail)
        String relTok = null;
        int hash = head.indexOf('#');
        if (hash >= 0) {
            relTok = head.substring(hash + 1);
            head = head.substring(0, hash);
            if (relTok.isEmpty()) throw new IllegalArgumentException("empty #relation token: " + text);
        }

        // optional @version in remaining head
        String verTok = null;
        int at = head.indexOf('@');
        String iidPart = (at >= 0) ? head.substring(0, at) : head;
        if (at >= 0) {
            verTok = head.substring(at + 1);
            if (verTok.isEmpty()) throw new IllegalArgumentException("empty @version token: " + text);
        }

        ItemID item = ItemID.parse(iidPart);
        VersionSpec version = (verTok != null) ? VersionSpec.bestGuess(verTok) : null;

        if (relTok != null && tail != null) {
            throw new IllegalArgumentException("cannot have both #relation and \\content in same link: " + text);
        }

        if (relTok != null) {
            return new Link(item, version, new Component.Relation(RelationSpec.bestGuess(relTok)), null, null);
        }

        Component component = null;
        String selector = null;

        if (tail != null) {
            String rest = tail.substring(1);
            String[] parts = rest.split("\\\\", -1);

            if (parts.length == 0) throw new IllegalArgumentException("invalid content tail: " + text);

            String contentTok = parts[0];
            if (contentTok.isEmpty()) {
                if (parts.length != 2 || parts[1].isEmpty()) {
                    throw new IllegalArgumentException("empty content requires selector (\\\\selector): " + text);
                }
                component = new Component.Content(ContentSpec.defaultContent());
                selector = parts[1];
            } else {
                component = new Component.Content(ContentSpec.bestGuess(contentTok));
                if (parts.length == 2) {
                    if (parts[1].isEmpty()) throw new IllegalArgumentException("empty selector: " + text);
                    selector = parts[1];
                } else if (parts.length > 2) {
                    throw new IllegalArgumentException("selector cannot contain additional backslash segments: " + text);
                }
            }
        }

        return new Link(item, version, component, selector, null);
    }

    // ==================== Equality ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Link link)) return false;
        return Objects.equals(item, link.item)
            && Objects.equals(version, link.version)
            && Objects.equals(component, link.component)
            && Objects.equals(selector, link.selector)
            && Objects.equals(path, link.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, version, component, selector, path);
    }

    // ==================== Sealed Types ====================

    public sealed interface Component {
        record Relation(RelationSpec spec) implements Component {
            public Relation { Objects.requireNonNull(spec, "spec"); }
        }
        record Content(ContentSpec spec) implements Component {
            public Content { Objects.requireNonNull(spec, "spec"); }
        }
    }

    public sealed interface VersionSpec {
        String encodeToken();

        record Id(VersionID id) implements VersionSpec {
            public Id { Objects.requireNonNull(id, "id"); }
            @Override public String encodeToken() { return id.encodeText().substring(VersionID.PREFIX.length()); }
        }

        record Handle(String handle) implements VersionSpec {
            public Handle {
                Objects.requireNonNull(handle, "handle");
                if (handle.isEmpty()) throw new IllegalArgumentException("empty version handle");
                if (containsWhitespace(handle)) throw new IllegalArgumentException("whitespace in version handle");
            }
            @Override public String encodeToken() { return handle; }
        }

        static VersionSpec bestGuess(String token) {
            Objects.requireNonNull(token, "token");
            if (containsWhitespace(token)) throw new IllegalArgumentException("whitespace in version token");
            try {
                VersionID id = VersionID.bestGuess(token);
                return new Id(id);
            } catch (Exception ignore) {
                return new Handle(token);
            }
        }
    }

    public sealed interface RelationSpec {
        String encodeToken();

        record Id(RelationID id) implements RelationSpec {
            public Id { Objects.requireNonNull(id, "id"); }
            @Override public String encodeToken() { return id.encodeText().substring(RelationID.PREFIX.length()); }
        }

        record Handle(String handle) implements RelationSpec {
            public Handle {
                Objects.requireNonNull(handle, "handle");
                if (handle.isEmpty()) throw new IllegalArgumentException("empty relation handle");
                if (containsWhitespace(handle)) throw new IllegalArgumentException("whitespace in relation handle");
            }
            @Override public String encodeToken() { return handle; }
        }

        static RelationSpec bestGuess(String token) {
            Objects.requireNonNull(token, "token");
            if (containsWhitespace(token)) throw new IllegalArgumentException("whitespace in relation token");
            try {
                RelationID id = RelationID.bestGuess(token);
                return new Id(id);
            } catch (Exception ignore) {
                return new Handle(token);
            }
        }
    }

    public sealed interface ContentSpec {
        boolean isDefault();
        String encodeTokenOrEmpty();

        record Default() implements ContentSpec {
            @Override public boolean isDefault() { return true; }
            @Override public String encodeTokenOrEmpty() { return ""; }
        }

        record Id(ContentID id) implements ContentSpec {
            public Id { Objects.requireNonNull(id, "id"); }
            @Override public boolean isDefault() { return false; }
            @Override public String encodeTokenOrEmpty() {
                return id.encodeText().substring(ContentID.CONTENT_PREFIX.length());
            }
        }

        record Handle(String handle) implements ContentSpec {
            public Handle {
                Objects.requireNonNull(handle, "handle");
                if (handle.isEmpty()) throw new IllegalArgumentException("empty content handle");
                if (containsWhitespace(handle)) throw new IllegalArgumentException("whitespace in content handle");
            }
            @Override public boolean isDefault() { return false; }
            @Override public String encodeTokenOrEmpty() { return handle; }
        }

        static ContentSpec defaultContent() { return new Default(); }

        static ContentSpec bestGuess(String token) {
            Objects.requireNonNull(token, "token");
            if (containsWhitespace(token)) throw new IllegalArgumentException("whitespace in content token");
            try {
                ContentID id = ContentID.bestGuess(token);
                return new Id(id);
            } catch (Exception ignore) {
                return new Handle(token);
            }
        }
    }

    // ==================== Utilities ====================

    private static boolean containsWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return true;
        }
        return false;
    }
}
