package dev.everydaythings.graph.item.component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Implement on types that expose subpaths/selectors (Jar, Json, CborMap, etc). */
public interface Selectable {

    /** Optional hint (MIME-ish), useful for UI and downstream selection. */
    default Optional<String> mediaType() { return Optional.empty(); }

    /**
     * Return a view/slice of this content at the given path + selectors.
     * Empty = not found / not applicable.
     */
    Optional<Selectable> select(Query q);

    /** Optional: enumerate child names for a path (for browsing/UIs). */
    default List<String> children(Path path) { return List.of(); }

    /** Optional: extract outbound references (e.g., embedded addresses) under a path. */
    default List<String> references(Path path) { return List.of(); } // or Address

    /* ----------------- tiny DTOs you can extend later ----------------- */

    /** Unix-like path split; never null; "" means root. */
    final class Path {
        private final List<String> segments;

        public Path(List<String> segments) {
            this.segments = segments == null ? List.of() : List.copyOf(segments);
        }

        public List<String> segments() {
            return segments;
        }

        public static Path of(String raw) {
            if (raw == null || raw.isBlank() || "/".equals(raw)) return new Path(List.of());
            return new Path(Arrays.stream(raw.split("/"))
                    .filter(s -> !s.isEmpty()).toList());
        }
    }

    /** One Query = path + zero or more selectors (order matters). */
    final class Query {
        private final Path path;
        private final List<Selector> selectors;
        private final Options options;

        public Query(Path path, List<Selector> selectors, Options options) {
            this.path = path == null ? new Path(List.of()) : path;
            this.selectors = selectors == null ? List.of() : List.copyOf(selectors);
            this.options = options == null ? Options.DEFAULT : options;
        }

        public Path path() { return path; }
        public List<Selector> selectors() { return selectors; }
        public Options options() { return options; }

        public static Query of(String path, List<Selector> selectors) {
            return new Query(Path.of(path), selectors, Options.DEFAULT);
        }
    }

    /** Minimal selector algebra; add more kinds when you need them. */
    interface Selector {
        /** Select element at index i (for arrays). */
        final class Index implements Selector {
            private final int i;
            public Index(int i) { this.i = i; }
            public int i() { return i; }
        }

        /** Require object[key] == value, or find first array element satisfying that predicate. */
        final class Equals implements Selector {
            private final String key;
            private final String value;
            public Equals(String key, String value) { this.key = key; this.value = value; }
            public String key() { return key; }
            public String value() { return value; }
        }

        /** Require object has key, or find first array element that has key. */
        final class Exists implements Selector {
            private final String key;
            public Exists(String key) { this.key = key; }
            public String key() { return key; }
        }
    }

    /** Per-field knobs from @Item.ContentField (dialect, basePath, etc.). */
    final class Options {
        public static final Options DEFAULT = new Options("", "");

        private final String dialect;
        private final String basePath;

        public Options(String dialect, String basePath) {
            this.dialect = dialect == null ? "" : dialect;
            this.basePath = basePath == null ? "" : basePath;
        }

        public String dialect() { return dialect; }
        public String basePath() { return basePath; }
    }
}
