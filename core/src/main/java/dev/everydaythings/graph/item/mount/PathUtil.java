package dev.everydaythings.graph.item.mount;

/**
 * Path utilities for mount resolution.
 */
public final class PathUtil {

    /**
     * Canonicalize a path: ensure leading slash, no trailing slash (except root),
     * collapse multiple slashes, remove . and .. segments.
     */
    public static String canonicalize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }

        String path = rawPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // Collapse multiple slashes
        path = path.replaceAll("/+", "/");

        // Remove trailing slash (except for root)
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    /**
     * Get the depth of a path (number of segments).
     *
     * <p>"/" = 0, "/foo" = 1, "/foo/bar" = 2
     */
    public static int depth(String path) {
        if (path == null || path.equals("/")) {
            return 0;
        }
        String canonical = canonicalize(path);
        return (int) canonical.chars().filter(c -> c == '/').count();
    }

    private PathUtil() {}
}
