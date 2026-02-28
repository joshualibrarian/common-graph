package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.expression.EvalInput;
import dev.everydaythings.graph.expression.EvalInputSnapshot;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Link;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.runtime.options.SessionOptions;
import dev.everydaythings.graph.ui.input.InputAction;
import dev.everydaythings.graph.ui.input.InputBindings;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.scene.surface.item.ItemModel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import java.awt.GraphicsEnvironment;
import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Session is the core controller for interacting with a Librarian.
 *
 * <p>Session manages:
 * <ul>
 *   <li>{@link ItemModel} - UI state (tree, selection, navigation)</li>
 *   <li>Command dispatch - routing input to context Item's actions</li>
 *   <li>Built-in commands - help, back, exit, mode switching</li>
 * </ul>
 *
 * <p>Subclasses handle platform-specific input and rendering:
 * <ul>
 *   <li>{@link TextSession} — CLI/TUI terminal rendering via JLine</li>
 *   <li>{@link GraphicalSession} — Filament 3D + Skia 2D (with SkiaWindow fallback)</li>
 * </ul>
 *
 * <p>Session can run as:
 * <ul>
 *   <li>{@code session} - standalone command</li>
 *   <li>{@code graph session} - as a subcommand of graph</li>
 * </ul>
 */
@Log4j2
@Accessors(fluent = true)
@Command(
    name = "session",
    mixinStandardHelpOptions = true,
    description = "Open a session to a Librarian"
)
public abstract class Session implements Callable<Integer>, Closeable {

    // ==================================================================================
    // UI Mode
    // ==================================================================================

    /**
     * UI presentation mode.
     */
    public enum UIMode {
        AUTO,   // Auto-detect based on environment
        CLI,    // Simple command-line REPL
        TUI,    // Terminal UI (JLine-based)
        SKIA,   // Graphical session, starting in FLAT mode
        SPACE   // Graphical session, starting in SPATIAL mode
    }

    /**
     * Render mode for graphical sessions.
     * FLAT = orthographic 2D (panel fills window).
     * SPATIAL = perspective 3D (panel in white room).
     */
    public enum RenderMode {
        FLAT,
        SPATIAL
    }

    // ==================================================================================
    // State
    // ==================================================================================

    @Getter
    protected LibrarianHandle librarian;

    /**
     * The ItemModel manages UI state: root, context, tree, navigation.
     */
    @Getter
    protected ItemModel itemModel;

    /**
     * Running flag for the main loop.
     */
    protected boolean running = false;

    /**
     * Session-level item cache.
     *
     * <p>Preserves live item instances with dynamic modifications
     * (added components, vocabulary updates) across navigation.
     * Without this, Librarian.get() creates fresh instances from
     * stored manifests, losing runtime modifications.
     */
    private final Map<ItemID, Item> liveItemCache = new HashMap<>();

    private static final ItemID EXIT_SEMEME_ID = ItemID.fromString(Sememe.EXIT);
    private static final ItemID BACK_SEMEME_ID = ItemID.fromString(Sememe.BACK);

    /**
     * Session-level item for inner-to-outer dispatch.
     * Holds session verbs (exit, back) and participates as the outermost scope.
     */
    @Getter
    protected SessionItem sessionItem;

    /**
     * Shared input handling — pulled up from subclasses.
     */
    protected EvalInput evalInput;
    protected InputBindings inputBindings;

    /**
     * Callback for mode switch requests (CLI ↔ TUI ↔ GUI).
     */
    protected Consumer<String> onModeSwitch;

    protected boolean ownsLibrarian = false;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Subclass constructor - receives resolved state.
     *
     * @param librarian The librarian connection
     * @param context   The initial context as a Link
     */
    protected Session(LibrarianHandle librarian, Link context) {
        this.librarian = librarian;
        initializeSessionItem();
        initializeItemModel(context);
    }

    /**
     * No-arg constructor for picocli (used by SessionShell).
     */
    protected Session() {
    }

    /**
     * Initialize the session item with exit/back callbacks.
     *
     * <p>Binds to the librarian handle (if available) so the session item
     * can resolve and authenticate users. Auto-authenticates the
     * librarian's principal if the vault is locally accessible.
     */
    private void initializeSessionItem() {
        sessionItem = new SessionItem();
        sessionItem.onExit(() -> running = false);
        sessionItem.onBack(this::goBack);
        if (librarian != null) {
            sessionItem.bind(librarian);
        }
    }

    /**
     * Initialize EvalInput with shared dispatch wiring.
     *
     * <p>Builds the EvalInput with lookup, dispatch, and navigation wiring
     * shared across all session types. Subclasses override
     * {@link #onInputChanged} and {@link #onInputDispatched} for
     * UI-specific refresh.
     */
    protected void initializeEvalInput() {
        if (librarian == null) return;

        if (inputBindings == null) {
            inputBindings = InputBindings.defaults();
        }

        evalInput = EvalInput.builder()
                .lookup(text -> librarian.prefix(text, maxCompletions()).toList())
                .librarian(librarian)
                .context(contextItem().orElse(null))
                .session(sessionItem)
                .prompt(buildPrompt())
                .hint("")
                .onChange(snapshot -> {
                    updateInputState(snapshot);
                    onInputChanged(snapshot);
                })
                .onNavigate(this::navigateInto)
                .onResult(result -> {
                    handleInputResult(result);
                    onInputDispatched(result);
                })
                .build();
        updateInputState(evalInput.snapshot());
    }

    /**
     * Maximum number of completion results to show.
     * Subclasses may override for platform-specific limits.
     */
    protected int maxCompletions() {
        return 10;
    }

    /**
     * Called after input state changes (typing, cursor movement, completions).
     * Subclasses override for UI-specific refresh (repaint, re-render).
     */
    protected void onInputChanged(EvalInputSnapshot snapshot) {
        // Default: no-op (updateInputState already called)
    }

    /**
     * Called after input dispatch completes (verb executed).
     * Subclasses override for UI-specific refresh after dispatch.
     */
    protected void onInputDispatched(Eval.EvalResult result) {
        // Default: no-op (handleInputResult already called)
    }

    /**
     * Initialize the ItemModel for this session.
     *
     * <p>If no explicit context is provided, defaults to the session item
     * itself — you are always somewhere.
     */
    protected void initializeItemModel(Link context) {
        if (context == null) {
            // Default context is the session item itself
            context = Link.of(sessionItem.iid());
            liveItemCache.put(sessionItem.iid(), sessionItem);
        }

        // Cache the principal so resolveItem() can find it
        cachePrincipal();

        itemModel = new ItemModel(context, this::resolveItem);
    }

    /**
     * Cache the principal in the session's liveItemCache so resolveItem() can find it.
     */
    private void cachePrincipal() {
        if (librarian != null) {
            librarian.principal().ifPresent(p -> liveItemCache.put(p.iid(), p));
        }
    }

    // ==================================================================================
    // Entry Point
    // ==================================================================================

    /**
     * Standalone entry point for Session command.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new SessionShell()).execute(args);
        System.exit(exitCode);
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create a session with resolved librarian and options.
     */
    public static Session create(LibrarianHandle librarian, SessionOptions opts) {
        return create(librarian, (Link) null, opts);
    }

    /**
     * Create a session with resolved librarian, context Item, and options.
     */
    public static Session create(LibrarianHandle librarian, Item context, SessionOptions opts) {
        Link contextLink = context != null ? Link.of(context.iid()) : null;
        return create(librarian, contextLink, opts);
    }

    /**
     * Create a session with resolved librarian, context Link, and options.
     *
     * <p>If context is null, the session defaults to the session item itself
     * (you are always somewhere — no bare "graph>" prompt).
     */
    public static Session create(LibrarianHandle librarian, Link context, SessionOptions opts) {
        // Determine UI mode
        UIMode mode = determineMode(opts);

        // Create appropriate session — null context is handled by initializeItemModel
        Session session = switch (mode) {
            case SKIA -> new GraphicalSession(librarian, context, RenderMode.FLAT);
            case SPACE -> new GraphicalSession(librarian, context, RenderMode.SPATIAL);
            case CLI, TUI -> new TextSession(librarian, context, mode, opts);
            default -> new TextSession(librarian, context, UIMode.CLI, opts);
        };

        return session;
    }

    /**
     * Create a text-mode fallback session (TUI if supported, else CLI).
     * Used when a graphical session fails to initialize.
     */
    public static Session createTextFallback(LibrarianHandle librarian, SessionOptions opts) {
        UIMode mode = TextSession.isTuiSupported() ? UIMode.TUI : UIMode.CLI;
        logger.info("Falling back to {} mode", mode);
        return new TextSession(librarian, null, mode, opts);
    }

    /**
     * Determine the UI mode based on options and environment.
     */
    protected static UIMode determineMode(SessionOptions opts) {
        if (opts != null && opts.uiMode != null && !opts.uiMode.isEmpty()) {
            String requested = opts.uiMode.toLowerCase();
            return switch (requested) {
                case "tui" -> {
                    if (!TextSession.isTuiSupported()) {
                        throw new IllegalArgumentException(
                                "TUI mode requested but not supported (no terminal or dumb terminal)");
                    }
                    yield UIMode.TUI;
                }
                case "cli" -> UIMode.CLI;
                case "2d", "skia" -> {
                    if (GraphicsEnvironment.isHeadless()) {
                        throw new IllegalArgumentException(
                                "Skia mode requested but running headless (no display)");
                    }
                    yield UIMode.SKIA;
                }
                case "3d", "space" -> {
                    if (GraphicsEnvironment.isHeadless()) {
                        throw new IllegalArgumentException(
                                "Space mode requested but running headless (no display)");
                    }
                    yield UIMode.SPACE;
                }
                case "auto" -> resolveAutoMode();
                default -> throw new IllegalArgumentException(
                        "Unknown UI mode: " + requested + " (use: 2d, 3d, skia, space, tui, cli, or auto)");
            };
        }
        return resolveAutoMode();
    }

    /**
     * Auto-detect the best UI mode for the current environment.
     *
     * <p>Priority: SPACE (3D) → TUI → CLI → GUI → CLI.
     * 3D is the default when a display is available and the native
     * rendering libraries (GLFW + Filament) can load.
     */
    protected static UIMode resolveAutoMode() {
        boolean hasDisplay = !GraphicsEnvironment.isHeadless();

        // 3D is the primary mode when a display is available
        if (hasDisplay && isSpaceSupported()) {
            return UIMode.SPACE;
        }

        if (TextSession.isTuiSupported()) {
            return UIMode.TUI;
        }

        boolean hasTTY = System.console() != null;
        String term = System.getenv("TERM");
        boolean hasTermEnv = term != null && !term.isEmpty() && !"dumb".equals(term);

        if (hasTTY || hasTermEnv) {
            return UIMode.CLI;
        }

        return UIMode.CLI;
    }

    /**
     * Check if the 3D rendering pipeline (GLFW + Filament) is available.
     *
     * <p>Probes by attempting to load the native libraries. If the shared
     * objects (.so) are not present, this returns false and we fall back
     * to a text or 2D mode.
     */
    private static boolean isSpaceSupported() {
        try {
            // Trigger class loading which loads native libraries
            Class.forName("org.lwjgl.glfw.GLFW");
            Class.forName("dev.everydaythings.filament.Filament");
            return true;
        } catch (Throwable t) {
            logger.debug("3D mode not available: {}", t.getMessage());
            return false;
        }
    }

    // ==================================================================================
    // Abstract Methods - Platform-specific
    // ==================================================================================

    /**
     * Run the session.
     * @return Exit code (0 = success)
     */
    public abstract int run();

    /**
     * Render the current state.
     * Called when ItemModel changes.
     */
    protected abstract void render();

    /**
     * Output a message to the user.
     */
    protected abstract void output(String message);

    // ==================================================================================
    // Callable Implementation
    // ==================================================================================

    @Override
    public Integer call() {
        try {
            return run();
        } finally {
            close();
        }
    }

    // ==================================================================================
    // ItemModel Accessors
    // ==================================================================================

    /**
     * Get the current context Link.
     */
    public Link context() {
        return itemModel != null ? itemModel.context() : null;
    }

    /**
     * Get the current root Link.
     */
    public Link root() {
        return itemModel != null ? itemModel.root() : null;
    }

    /**
     * Get the context Item (resolves the Link's IID).
     */
    public Optional<Item> contextItem() {
        if (itemModel == null) return Optional.empty();
        Link ctx = itemModel.context();
        if (ctx == null || ctx.item() == null) return Optional.empty();
        return resolveItem(ctx.item());
    }

    /**
     * Resolve an item by IID, checking the session cache first.
     *
     * <p>The cache preserves live instances with dynamic modifications
     * (e.g., added components) that aren't yet persisted to the store.
     */
    public Optional<Item> resolveItem(ItemID iid) {
        Item cached = liveItemCache.get(iid);
        if (cached != null) return Optional.of(cached);
        Optional<Item> resolved = librarian.get(iid);
        resolved.ifPresent(item -> liveItemCache.put(iid, item));
        return resolved;
    }

    /**
     * Generate the current surface for rendering.
     */
    public SurfaceSchema toSurface() {
        return itemModel != null ? itemModel.toSurface() : null;
    }

    /**
     * Update the input state in the ItemModel from an EvalInput snapshot.
     *
     * <p>Call this when EvalInput fires onChange so that the input field
     * renders as part of the surface tree across all renderers.
     *
     * @param snapshot the current input state
     */
    public void updateInputState(dev.everydaythings.graph.expression.EvalInputSnapshot snapshot) {
        if (itemModel != null) {
            itemModel.updateInput(snapshot);
        }
    }

    // ==================================================================================
    // Navigation
    // ==================================================================================

    /**
     * Navigate into an item (makes it the new root).
     */
    public void navigateInto(Link target) {
        if (itemModel != null) {
            itemModel.navigateInto(target);
            contextItem().ifPresent(this::onContextComponentsChanged);
        }
    }

    /**
     * Navigate into an Item.
     */
    public void navigateInto(Item item) {
        if (item != null) {
            liveItemCache.put(item.iid(), item);
            navigateInto(Link.of(item.iid()));
        }
    }

    /**
     * Select an item (changes context within current root).
     */
    public void select(Link target) {
        if (itemModel != null) {
            itemModel.select(target);
        }
    }

    /**
     * Go back in navigation history.
     */
    public boolean goBack() {
        return itemModel != null && itemModel.goBack();
    }

    /**
     * Check if we can go back.
     */
    public boolean canGoBack() {
        return itemModel != null && itemModel.canGoBack();
    }

    // ==================================================================================
    // Key Handling
    // ==================================================================================

    /**
     * Handle a key chord.
     * Routes to ItemModel for navigation keys.
     *
     * @return true if consumed
     */
    public boolean handleKey(KeyChord chord) {
        return itemModel != null && itemModel.handleKey(chord);
    }

    /**
     * Handle a surface event (from mouse clicks, etc.).
     */
    public boolean handleEvent(String action, String target) {
        return itemModel != null && itemModel.handleEvent(action, target);
    }

    // ==================================================================================
    // Command Dispatch
    // ==================================================================================

    /**
     * Handle an EvalResult from Eval's unified evaluation path.
     */
    protected void handleEvalResult(Eval.EvalResult result) {
        switch (result) {
            case Eval.EvalResult.Empty() -> {}
            case Eval.EvalResult.ItemResult(Item item) -> {
                if (isSessionVerb(item)) {
                    executeSessionVerb(item);
                    return;
                }
                navigateInto(item);
            }
            case Eval.EvalResult.Created(Item item) -> {
                // Item was created — don't navigate the current view.
                // Cache it and refresh the tree so it's visible.
                liveItemCache.put(item.iid(), item);
                if (itemModel != null) {
                    itemModel.refresh();
                }
                logger.info("Created: {} ({})", item.displayToken(), item.iid().encodeText());
            }
            case Eval.EvalResult.Value(Object value) -> {
                if (isComponent(value)) {
                    addComponentToContext(value);
                } else {
                    displayValue(value);
                }
            }
            case Eval.EvalResult.ValueWithTarget(Object value, Item targetItem) -> {
                if (isComponent(value)) {
                    addComponentToItem(value, targetItem);
                }
            }
            case Eval.EvalResult.Error(String message) -> {
                // Errors are shown in the input field via EvalInput's error state.
                // Log for debugging only.
                logger.debug("Dispatch error: {}", message);
            }
        }
    }

    /**
     * Handle an EvalResult from input dispatch.
     *
     * <p>Handles the result (navigation, component creation) and updates
     * prompt/context. Errors are shown directly in the input field via
     * EvalInput's error state — no separate transcript needed.
     */
    protected void handleInputResult(Eval.EvalResult result) {
        handleEvalResult(result);
        if (evalInput != null) {
            evalInput.setPrompt(buildPrompt());
            evalInput.setContext(contextItem().orElse(null));
        }
    }

    // ==================================================================================
    // Component Management
    // ==================================================================================

    /**
     * Add a component result to the current context item.
     *
     * <p>Derives a handle name from the component's {@code @Type}
     * annotation (e.g., "cg:type/chess" → "chess").
     */
    protected void addComponentToContext(Object component) {
        Item ctx = contextItem().orElse(null);
        if (ctx == null) {
            logger.warn("No context item to add component to");
            return;
        }
        addComponentToItem(component, ctx);
    }

    /**
     * Add a component to a specific target item.
     *
     * <p>Used by {@link #addComponentToContext} (implicit target) and by
     * the {@code ValueWithTarget} result path (explicit "on" target).
     */
    protected void addComponentToItem(Object component, Item target) {
        // Use cached instance if available to preserve prior modifications
        Item actual = liveItemCache.getOrDefault(target.iid(), target);
        liveItemCache.put(actual.iid(), actual);

        String handle = deriveUniqueHandle(actual, deriveHandle(component));
        actual.addComponent(handle, component);

        // Refresh tree to pick up the new component, then select it
        if (itemModel != null) {
            itemModel.refresh();
            // Build link with HandleID-encoded path to match ComponentEntry.link() format
            Link componentLink = Link.of(actual.iid(),
                    "/" + dev.everydaythings.graph.item.id.HandleID.of(handle).encodeText());
            itemModel.select(componentLink);
        }

        // Notify subclasses (e.g., GraphicalSession rebuilds tick registry)
        onContextComponentsChanged(actual);
    }

    /**
     * Hook for subclasses to react when the context item's components change.
     *
     * <p>Called after navigation and after adding components. Subclasses
     * (e.g., {@link GraphicalSession}) override this to rebuild the
     * tick registry for live widget updates.
     *
     * @param item the item whose components changed
     */
    protected void onContextComponentsChanged(Item item) {
        // Default: no-op
    }

    /**
     * Derive a handle name from a component's type annotation.
     *
     * <p>Extracts the short name from the canonical key
     * (e.g., "cg:type/chess" → "chess").
     */
    private String deriveHandle(Object component) {
        Type ann = component.getClass().getAnnotation(Type.class);
        if (ann != null) {
            String key = ann.value();
            int lastSlash = key.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < key.length() - 1) {
                return key.substring(lastSlash + 1);
            }
            int lastColon = key.lastIndexOf(':');
            if (lastColon >= 0 && lastColon < key.length() - 1) {
                return key.substring(lastColon + 1);
            }
        }
        return component.getClass().getSimpleName().toLowerCase();
    }

    /**
     * Ensure runtime-added component handles are unique within an item.
     *
     * <p>Repeated "create chess" should produce chess, chess-2, chess-3...,
     * not overwrite the existing "chess" entry.
     */
    private String deriveUniqueHandle(Item item, String baseHandle) {
        String normalized = (baseHandle == null || baseHandle.isBlank())
                ? "component"
                : baseHandle;

        String candidate = normalized;
        int n = 2;
        while (item.content().get(dev.everydaythings.graph.item.id.HandleID.of(candidate)).isPresent()) {
            candidate = normalized + "-" + n++;
        }
        return candidate;
    }

    /**
     * Check if a value is a component (has @Type annotation).
     */
    private static boolean isComponent(Object value) {
        return value != null && value.getClass().isAnnotationPresent(Type.class);
    }

    // ==================================================================================
    // Shared Input Dispatch
    // ==================================================================================

    /**
     * Dispatch a key chord to EvalInput via InputBindings.
     *
     * <p>Maps the key chord to the appropriate EvalInput method call.
     * Pulled up from subclasses (identical in TextSession and GraphicalSession).
     */
    protected void dispatchToEvalInput(KeyChord chord) {
        if (evalInput == null) return;

        boolean hasCompletions = evalInput.snapshot().hasVisibleCompletions();
        Optional<InputAction> action = inputBindings.resolve(chord, hasCompletions);

        if (action.isEmpty()) return;

        switch (action.get()) {
            case InputAction.Char c -> evalInput.type(c.c());
            case InputAction.Backspace b -> evalInput.backspace();
            case InputAction.Delete d -> evalInput.delete();
            case InputAction.DeleteWord d -> evalInput.deleteWord();
            case InputAction.CursorLeft l -> evalInput.cursorLeft();
            case InputAction.CursorRight r -> evalInput.cursorRight();
            case InputAction.CursorHome h -> evalInput.cursorHome();
            case InputAction.CursorEnd e -> evalInput.cursorEnd();
            case InputAction.CompletionUp u -> evalInput.completionUp();
            case InputAction.CompletionDown d -> evalInput.completionDown();
            case InputAction.Tab t -> evalInput.tab();
            case InputAction.Accept a -> evalInput.accept();
            case InputAction.Cancel c -> evalInput.cancel();
            case InputAction.TokenBoundary b -> evalInput.tokenBoundary();
            case InputAction.HistoryPrev p -> evalInput.historyPrev();
            case InputAction.HistoryNext n -> evalInput.historyNext();
        }
    }

    // ==================================================================================
    // Session Verbs
    // ==================================================================================

    /**
     * Check if an item is a session-level verb sememe (exit, back).
     */
    protected boolean isSessionVerb(Item item) {
        ItemID iid = item.iid();
        return iid.equals(EXIT_SEMEME_ID) || iid.equals(BACK_SEMEME_ID);
    }

    /**
     * Execute a session-level verb (exit, back).
     */
    protected void executeSessionVerb(Item item) {
        ItemID iid = item.iid();
        if (iid.equals(EXIT_SEMEME_ID)) {
            running = false;
        } else if (iid.equals(BACK_SEMEME_ID)) {
            goBack();
        }
    }

    /**
     * Request a mode switch.
     */
    protected void requestModeSwitch(String mode) {
        running = false;
        if (onModeSwitch != null) {
            onModeSwitch.accept(mode);
        }
    }

    // ==================================================================================
    // Utilities
    // ==================================================================================

    /**
     * Build a prompt showing {@code actor@context>}.
     *
     * <p>The actor comes from the session item's active user. The context
     * comes from the current navigation position. When at the session item
     * itself, shows just the type name ("session").
     */
    public String buildPrompt() {
        String actorPrefix = resolveActorPrefix();

        if (itemModel == null) {
            return actorPrefix + "session> ";
        }

        Link ctx = itemModel.context();
        if (ctx == null) {
            return actorPrefix + "session> ";
        }

        Optional<Item> item = contextItem();
        if (item.isPresent()) {
            String icon = item.get().emoji();
            String label = item.get().displayToken();

            Optional<String> path = ctx.path();
            if (path.isPresent() && !path.get().isEmpty()) {
                label = label + path.get();
            }

            String fullLabel = actorPrefix + label;
            if (fullLabel.length() < 40) {
                return (icon != null ? icon + " " : "") + fullLabel + "> ";
            }
        }

        return actorPrefix + "session> ";
    }

    /**
     * Resolve the actor prefix for the prompt (e.g., "alice@").
     *
     * <p>Uses the session item's active actor if authenticated.
     * Falls back to the librarian's principal for backwards compatibility.
     *
     * @return The actor prefix, or empty string if no actor is set
     */
    private String resolveActorPrefix() {
        if (sessionItem != null && sessionItem.actor() != null) {
            return sessionItem.actor().displayToken() + "@";
        }
        if (librarian != null) {
            return librarian.principal()
                    .map(p -> p.displayToken() + "@")
                    .orElse("");
        }
        return "";
    }

    /**
     * Display a value returned from verb dispatch.
     *
     * <p>If the value has a {@link Surface} annotation, compiles it to a View
     * and outputs the formatted result. Otherwise outputs toString().
     */
    protected void displayValue(Object value) {
        if (value == null) return;

        // Try to compile as a Surface-annotated object
        View view = SceneCompiler.compile(value);
        if (view != null && view.root() != null) {
            output(formatValue(view));
            return;
        }

        // Plain value — just show toString
        String text = formatValue(value);
        if (!text.isEmpty()) {
            output(text);
        }
    }

    /**
     * Format a value for display.
     */
    protected String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof View view && view.root() != null) {
            // Subclasses should override for proper rendering
            return "[View]";
        }
        if (value instanceof Item item) {
            return item.emoji() + " " + item.displayToken();
        }
        return value.toString();
    }

    /**
     * Check if a string looks like a link (item reference).
     */
    protected boolean looksLikeLink(String s) {
        return s.startsWith("@") ||
               s.startsWith("iid:") ||
               s.startsWith("~/") ||
               s.startsWith("./");
    }

    /**
     * Lookup an item by query string.
     */
    protected Item lookupItem(String query) {
        List<Posting> postings = librarian.lookup(query).limit(10).toList();

        for (Posting p : postings) {
            if (p.token().equalsIgnoreCase(query)) {
                return librarian.get(p.target()).orElse(null);
            }
        }

        if (!postings.isEmpty()) {
            logger.debug("No exact match for '{}', closest: {}", query, postings.get(0).token());
        }
        return null;
    }

    /**
     * Resolve a context specification to a Link.
     */
    protected Link resolveContextLink(String spec) {
        logger.debug("Resolving context: {}", spec);

        if (spec.startsWith("@")) {
            String handle = spec.substring(1);
            Item item = lookupItem(handle);
            return item != null ? Link.of(item.iid()) : null;
        }

        if (spec.startsWith("iid:")) {
            try {
                return Link.parse(spec);
            } catch (IllegalArgumentException e) {
                logger.debug("Failed to parse link: {}", spec, e);
                return null;
            }
        }

        Item item = lookupItem(spec);
        return item != null ? Link.of(item.iid()) : null;
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public void close() {
        running = false;
        if (ownsLibrarian && librarian != null) {
            librarian.close();
            librarian = null;
        }
    }

    // ==================================================================================
    // SessionShell - Concrete Session for CLI Parsing
    // ==================================================================================

    /**
     * SessionShell is a concrete Session used for picocli parsing.
     */
    @Command(
        name = "session",
        mixinStandardHelpOptions = true,
        description = "Open a session to a Librarian"
    )
    public static class SessionShell extends Session {

        @Mixin
        private SessionOptions opts = new SessionOptions();

        @Override
        public Integer call() {
            try {
                // 1. Resolve librarian connection
                LibrarianHandle handle = resolveLibrarianConnection();
                this.ownsLibrarian = true;
                this.librarian = handle;

                // 2. Resolve context if specified
                Link ctx = null;
                if (opts.positionalArgs != null && !opts.positionalArgs.isEmpty()) {
                    String contextSpec = opts.positionalArgs.get(0);
                    if (looksLikeLink(contextSpec)) {
                        ctx = resolveContextLink(contextSpec);
                    }
                }

                // 3. Create appropriate session (null ctx = session item default)
                Session session = Session.create(handle, ctx, opts);
                session.ownsLibrarian = true;

                // 4. Run it
                return session.run();
            } catch (Exception e) {
                logger.error("Session failed", e);
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        private LibrarianHandle resolveLibrarianConnection() {
            String target = opts.connectionTarget;

            if (target == null || target.isBlank() || "local".equalsIgnoreCase(target)) {
                logger.info("Creating local in-memory librarian");
                return LibrarianHandle.inMemory();
            }

            if (target.contains(":") && !target.startsWith("/")) {
                logger.info("Connecting to remote librarian at {}", target);
                return LibrarianHandle.remote(target);
            } else {
                logger.info("Connecting to librarian via Unix socket {}", target);
                return LibrarianHandle.remote(target);
            }
        }

        @Override
        public int run() {
            throw new IllegalStateException("SessionShell.run() should not be called directly");
        }

        @Override
        protected void render() {
            // SessionShell doesn't render
        }

        @Override
        protected void output(String message) {
            System.out.println(message);
        }
    }
}
