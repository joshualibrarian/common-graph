package dev.everydaythings.graph.runtime;

import java.util.Set;

/**
 * Runtime-detection of GraalVM Polyglot capabilities.
 *
 * <p>The librarian needs to know whether it's running on a GraalVM-capable JVM
 * (which enables sandboxed Java via Espresso, plus polyglot guest languages —
 * Python, JavaScript, Ruby, etc.) or on a plain HotSpot JVM (which can only run
 * native trusted Java implementations, with no sandboxing of guest code).
 *
 * <p>Detection happens once at first call and is cached. Failure to initialize
 * Polyglot is treated as "not available" — the librarian degrades to native-Java
 * mode without the polyglot capabilities.
 *
 * <p>Usage:
 * <pre>{@code
 * if (PolyglotEnvironment.isAvailable()) {
 *     // load sandboxed implementations
 * } else {
 *     // limit to native trusted implementations only
 * }
 * }</pre>
 *
 * <p>The {@code org.graalvm.polyglot:polyglot} library is on the classpath
 * regardless; this class checks whether the runtime can actually instantiate
 * a Polyglot engine.
 */
public final class PolyglotEnvironment {

    private static volatile Boolean available;
    private static volatile Set<String> languages = Set.of();

    private PolyglotEnvironment() {}

    /**
     * Whether GraalVM Polyglot is available in this JVM.
     *
     * <p>{@code true} when {@code org.graalvm.polyglot.Engine.create()} succeeds.
     * {@code false} otherwise (plain HotSpot JVM, or some configuration error).
     *
     * <p>Result is cached after first invocation.
     */
    public static boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) return cached;
        synchronized (PolyglotEnvironment.class) {
            if (available != null) return available;
            available = detect();
            return available;
        }
    }

    /**
     * The set of polyglot guest languages installed alongside the engine.
     *
     * <p>Empty if Polyglot isn't available, or if no language implementations
     * are on the classpath. Each entry is a language ID like {@code "java"},
     * {@code "python"}, {@code "js"}, {@code "ruby"}, etc.
     *
     * <p>Note: presence of a language ID indicates the runtime CAN load it; the
     * librarian's policy still gates what user-supplied code may actually run.
     */
    public static Set<String> availableLanguages() {
        if (!isAvailable()) return Set.of();
        return languages;
    }

    private static boolean detect() {
        try {
            Class<?> engineCls = Class.forName("org.graalvm.polyglot.Engine");
            Object engine = engineCls.getMethod("create").invoke(null);
            // Read available language IDs from the engine
            try {
                Object langMap = engineCls.getMethod("getLanguages").invoke(engine);
                @SuppressWarnings("unchecked")
                java.util.Map<String, ?> map = (java.util.Map<String, ?>) langMap;
                languages = Set.copyOf(map.keySet());
            } catch (Exception ignored) {
                languages = Set.of();
            }
            // Close the engine — this was just a probe
            try {
                engineCls.getMethod("close").invoke(engine);
            } catch (Exception ignored) {
                // best-effort close; ignore if not available
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
