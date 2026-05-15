package dev.everydaythings.graph.runtime.stage;

import java.util.Set;

/**
 * The execution substrate of a Common Graph runtime — the stage on which items run.
 *
 * <p>ItemStage is the layer that <i>runs code</i>. Specifically:
 *
 * <ul>
 *   <li><b>Polyglot context.</b> Owns the GraalVM Polyglot environment and the
 *       per-language contexts (Java, GraalPython, embedded JS, future Rust via
 *       FFI, etc.). One process has one ItemStage; one ItemStage hosts many
 *       language contexts.</li>
 *   <li><b>Run primitive.</b> Given a handler reference and a {@code Frame},
 *       ItemStage invokes the handler in the appropriate language context and
 *       returns the result. The primitive is uniform across languages:
 *       Frame in, value-or-Frame out.</li>
 *   <li><b>Capability enforcement.</b> Trust matrix (held by Librarian) decides
 *       <i>which</i> handler should run. ItemStage applies the resulting
 *       capability constraints at run time — sandboxing, resource limits,
 *       refusal of privileged operations from non-privileged askers.</li>
 * </ul>
 *
 * <p>ItemStage does <b>not</b> own:
 *
 * <ul>
 *   <li>The handler registry — that's graph data (HANDLES frames), held in
 *       storage and queried via
 *       {@link dev.everydaythings.graph.runtime.librarian.Librarian Librarian}.</li>
 *   <li>Trust-matrix-driven selection of <i>which</i> implementation to run for
 *       a given predicate — that's policy, owned by Librarian.</li>
 *   <li>Storage, identity, signing — all Librarian's domain.</li>
 *   <li>User context, presence, device bindings — all Session's domain.</li>
 *   <li>Application orchestration (main, CLI options, daemon protocol). Those
 *       live on the entry classes ({@code Librarian.main}, {@code LibrarianDaemon},
 *       {@code RemoteSession.main}) — ItemStage is the substrate, not the entry.</li>
 * </ul>
 *
 * <h2>Substrate, not peer</h2>
 *
 * <p>Conceptually ItemStage is the <i>substrate</i> of the runtime; Librarian
 * and Session are the items that run on it. The bootstrap order is:
 *
 * <ol>
 *   <li>Entry-class {@code main()} (or {@code Daemon.start()}) constructs an
 *       ItemStage — its constructor probes Graal, degrades gracefully to
 *       Java-only if unavailable.</li>
 *   <li>The entry constructs a Librarian (or other top-level item) passing the
 *       ItemStage in. The librarian becomes the first item hosted on this
 *       stage.</li>
 *   <li>Subsequent frame evaluations: Librarian <i>selects</i> a handler via
 *       trust matrix, then asks ItemStage to <i>run</i> it.</li>
 * </ol>
 *
 * <p>The substrate exists before any item; items receive the substrate at
 * construction; the same capability path serves privileged Librarian operations
 * and sandboxed user-item code — the capability check just trivially passes for
 * Librarian and constrains other callers based on the trust web.
 *
 * <p>A non-Java Librarian (Rust, embedded, etc.) is a clean possibility under
 * this layout: any Librarian is just an item running on an ItemStage.
 *
 * <h2>Graceful Graal fallback</h2>
 *
 * <p>The constructor probes for GraalVM Polyglot exactly once. If unavailable,
 * the stage stays in Java-only mode — handlers written in Java still run; guest
 * languages are simply unavailable. No exceptions are thrown for missing
 * polyglot support. The {@link #polyglotAvailable()} accessor tells you which
 * mode you're in; callers that want to publish a Python or JS handler should
 * check it first.
 *
 * <h2>Phasing</h2>
 *
 * <p>Today this class carries only the polyglot-environment surface. The run
 * primitive and capability enforcement land in subsequent phases:
 *
 * <ol>
 *   <li><b>Now:</b> ItemStage exists. Constructor probes polyglot eagerly.
 *       Entry classes ({@code Librarian.main}, {@code LibrarianDaemon}) create
 *       it first; Librarian receives it.</li>
 *   <li><b>Next:</b> The {@code run(handler, frame)} primitive lands here.
 *       Librarian.dispatchToHandlers calls into it instead of invoking handlers
 *       directly.</li>
 *   <li><b>After:</b> GraalPython context comes up; first non-Java handler
 *       round-trips end-to-end. Proves the cross-language bridge.</li>
 * </ol>
 */
public class ItemStage {

    private final boolean polyglotAvailable;
    private final Set<String> polyglotLanguages;

    /**
     * Construct an ItemStage. Eagerly probes for GraalVM Polyglot; degrades to
     * Java-only if unavailable. Never throws regardless of host JVM.
     */
    public ItemStage() {
        this.polyglotAvailable = PolyglotEnvironment.isAvailable();
        this.polyglotLanguages = PolyglotEnvironment.availableLanguages();
    }

    private ItemStage(boolean polyglotAvailable, Set<String> polyglotLanguages) {
        this.polyglotAvailable = polyglotAvailable;
        this.polyglotLanguages = polyglotLanguages;
    }

    /**
     * Construct an ItemStage in explicit Java-only mode, skipping the Graal
     * probe. Useful in tests where the polyglot detection overhead is unwanted
     * and only Java handlers will run.
     */
    public static ItemStage javaOnly() {
        return new ItemStage(false, Set.of());
    }

    /**
     * Whether GraalVM Polyglot is available on this stage. {@code true} when
     * guest languages (Python, JS, etc.) can be loaded; {@code false} on plain
     * HotSpot or other non-GraalVM JVMs.
     */
    public boolean polyglotAvailable() {
        return polyglotAvailable;
    }

    /**
     * The set of polyglot guest languages this stage can host. Empty when
     * polyglot is unavailable. Each entry is a GraalVM language ID
     * ({@code "java"}, {@code "python"}, {@code "js"}, etc.).
     */
    public Set<String> polyglotLanguages() {
        return polyglotLanguages;
    }
}
