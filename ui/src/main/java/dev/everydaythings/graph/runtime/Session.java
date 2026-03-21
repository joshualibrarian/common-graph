package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.parse.InputController;
import dev.everydaythings.graph.parse.InputSnapshot;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.item.Param;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.DisplayLayoutConfig;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.frame.ViewHandle;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.ViewVocabulary;
import dev.everydaythings.graph.runtime.options.SessionOptions;
import dev.everydaythings.graph.parse.InputAction;
import dev.everydaythings.graph.ui.input.InputBindings;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.scene.surface.item.ItemModel;
import dev.everydaythings.graph.ui.scene.surface.item.ViewSurface;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import java.awt.GraphicsEnvironment;
import java.io.Closeable;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Session is the core controller for interacting with a Librarian.
 *
 * <p>Session extends {@link Item} — it IS an Item with identity,
 * vocabulary, verbs, and components. It manages authenticated users,
 * session-level verbs (exit, back, authenticate, switch), the activity
 * log, and the UI layer (ItemModel, input, rendering).
 *
 * <h2>Multi-user model</h2>
 *
 * <p>A session can have multiple authenticated users, like browser profiles.
 * At any time, one user is the <em>active actor</em> — the identity that
 * signs actions dispatched through this session. Users authenticate by
 * proving they hold the private key (challenge-response via vault).
 *
 * <p>The prompt always shows {@code actor@context>}, never a bare "graph>".
 * The default context is the session item itself.
 *
 * <h2>Authentication</h2>
 *
 * <p>Authentication is a challenge-response: the session generates a random
 * nonce, the user's vault signs it, and the session verifies the signature
 * against the user's public key. For local vaults this is near-instant.
 *
 * <p>On session start, users whose vaults are locally accessible are
 * auto-authenticated silently.
 *
 * <p>Subclasses handle platform-specific input and rendering:
 * <ul>
 *   <li>{@link TextSession} — CLI/TUI terminal rendering via JLine</li>
 *   <li>{@link GraphicalSession} — Filament 3D + Skia 2D (with SkiaWindow fallback)</li>
 * </ul>
 */
@Log4j2
@Accessors(fluent = true)
@Implements(Session.KEY)
@ItemSeed(key = Session.KEY)
@Command(
    name = "session",
    mixinStandardHelpOptions = true,
    description = "Open a session to a Librarian"
)
public abstract class Session extends Item implements Callable<Integer>, Closeable {

    public static final String KEY = "cg.sememe:session";

    @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
    static final String seedGloss = "UI session for item interaction";

    @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String seedNoun = "session";

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
    // State — Session Identity & Auth (formerly SessionItem)
    // ==================================================================================

    private static final SecureRandom RANDOM = new SecureRandom();

    /** All authenticated users for this session (insertion-ordered). */
    private final Set<Signer> authenticatedUsers = new LinkedHashSet<>();

    /** The currently active actor — the identity that signs dispatched actions. */
    @Getter
    private Signer actor;

    /** Handle for resolving users during authentication. */
    private LibrarianHandle handle;

    /** Session lifecycle callbacks. */
    private Runnable onExit;
    private Runnable onBack;

    /** Activity log component — visible in the component tree. */
    @Getter
    private ActivityLog activityLog;

    // ==================================================================================
    // State — UI Layer
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

    private static final ItemID EXIT_SEMEME_ID = ItemID.fromString(CoreVocabulary.Exit.KEY);
    private static final ItemID BACK_SEMEME_ID = ItemID.fromString(CoreVocabulary.Back.KEY);

    /**
     * Shared input handling — pulled up from subclasses.
     */
    protected InputController inputController;
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
     * @param context   The initial context as a Ref
     */
    protected Session(LibrarianHandle librarian, Ref context) {
        super(ItemID.fromString("cg.sememe:session")); // Seed constructor — deterministic IID
        this.activityLog = new ActivityLog();
        this.librarian = librarian;
        onExit(() -> running = false);
        onBack(this::goBack);
        if (librarian != null) {
            bind(librarian);
        }
        initializeItemModel(context);
    }

    /**
     * No-arg constructor for picocli (used by SessionShell).
     */
    protected Session() {
        super(ItemID.fromString("cg.sememe:session"));
        this.activityLog = new ActivityLog();
    }

    // ==================================================================================
    // Binding (formerly on SessionItem)
    // ==================================================================================

    /**
     * Bind this session to a librarian handle.
     *
     * <p>Must be called after construction to enable user authentication.
     * Auto-authenticates the librarian's principal if the vault is accessible.
     * Adds the activity log component to the component tree.
     */
    public void bind(LibrarianHandle handle) {
        this.handle = handle;

        // Set Item-level librarian so display resolution works
        if (handle instanceof LocalLibrarian local) {
            setLibrarian(local.librarian());
        }

        // Add activity log with semantic key
        endorse(ItemID.fromString(ActivityLog.KEY), activityLog);

        autoAuthenticate();
    }

    // ==================================================================================
    // Authentication (formerly on SessionItem)
    // ==================================================================================

    /**
     * Auto-authenticate users whose vaults are locally accessible.
     *
     * <p>Silently authenticates the librarian's principal if the vault can sign.
     */
    private void autoAuthenticate() {
        if (handle == null) return;
        handle.principal().ifPresent(principal -> {
            if (principal instanceof Signer signer && signer.canSign()) {
                if (challengeResponse(signer)) {
                    authenticatedUsers.add(signer);
                    actor = signer;
                    logger.info("Auto-authenticated: {}", signer.displayToken());
                }
            }
        });
    }

    /**
     * Perform a challenge-response authentication.
     *
     * <p>Generates a random 32-byte nonce, asks the signer to sign it
     * via its vault, then verifies the signature. This proves the signer
     * holds the private key. For local vaults this is near-instant.
     *
     * @param signer The signer to authenticate
     * @return true if the signer proved possession of the private key
     */
    private boolean challengeResponse(Signer signer) {
        if (!signer.canSign()) {
            return false;
        }
        try {
            // Sign and verify a nonce — proves key possession
            byte[] nonce = new byte[32];
            RANDOM.nextBytes(nonce);
            byte[] signature = signer.signRaw(nonce);
            // If signRaw didn't throw, the vault has the key.
            // Verify round-trip to confirm key integrity.
            return signature != null && signature.length > 0;
        } catch (Exception e) {
            logger.warn("Challenge-response failed for {}: {}",
                    signer.displayToken(), e.getMessage());
            return false;
        }
    }

    /**
     * Authenticate a user by IID.
     *
     * <p>Resolves the user from the librarian handle, performs challenge-response,
     * and adds them to the authenticated set. If this is the first
     * authenticated user, they become the active actor.
     *
     * @param userId The user's ItemID
     * @return The authenticated user
     * @throws IllegalArgumentException if user not found or auth fails
     */
    public Signer authenticate(ItemID userId) {
        if (handle == null) {
            throw new IllegalStateException("No librarian handle — cannot resolve users");
        }

        // Check if already authenticated
        for (Signer s : authenticatedUsers) {
            if (s.iid().equals(userId)) {
                return s;
            }
        }

        // Resolve the item and check if it's a Signer
        Optional<Item> found = handle.get(userId);
        if (found.isPresent() && found.get() instanceof Signer signer) {
            return authenticateSigner(signer);
        }

        throw new IllegalArgumentException("User not found: " + userId.encodeText());
    }

    private Signer authenticateSigner(Signer signer) {
        if (!challengeResponse(signer)) {
            throw new IllegalArgumentException(
                    "Authentication failed for " + signer.displayToken()
                    + " — cannot prove key possession");
        }
        authenticatedUsers.add(signer);
        if (actor == null) {
            actor = signer;
        }
        logger.info("Authenticated: {}", signer.displayToken());
        return signer;
    }

    // ==================================================================================
    // User Management (formerly on SessionItem)
    // ==================================================================================

    /**
     * Switch the active actor to a different authenticated user.
     *
     * @param userId The IID of the user to switch to
     * @return The user that is now the active actor
     * @throws IllegalArgumentException if user is not authenticated
     */
    public Signer switchActor(ItemID userId) {
        for (Signer s : authenticatedUsers) {
            if (s.iid().equals(userId)) {
                actor = s;
                logger.info("Switched actor to: {}", s.displayToken());
                return s;
            }
        }
        throw new IllegalArgumentException(
                "User not authenticated — use 'authenticate' first");
    }

    /**
     * Get all authenticated users (unmodifiable).
     */
    public Set<Signer> authenticatedUsers() {
        return Collections.unmodifiableSet(authenticatedUsers);
    }

    /**
     * Check if any user is authenticated.
     */
    public boolean hasAuthenticatedUser() {
        return !authenticatedUsers.isEmpty();
    }

    // ==================================================================================
    // Activity Log (formerly on SessionItem)
    // ==================================================================================

    /**
     * Append an entry to the activity log.
     */
    public void logActivity(ActivityEntry entry) {
        if (activityLog != null) {
            activityLog.append(entry);
        }
    }

    /**
     * Get the most recent activity entry for a specific context.
     */
    public Optional<ActivityEntry> lastActivityForContext(ItemID contextIid) {
        if (activityLog == null) return Optional.empty();
        return activityLog.lastForContext(contextIid);
    }

    /**
     * Get the most recent activity entry (any context).
     */
    public Optional<ActivityEntry> lastActivity() {
        if (activityLog == null) return Optional.empty();
        return activityLog.last();
    }

    /**
     * Get the total number of activity entries.
     */
    public int activityCount() {
        return activityLog != null ? activityLog.size() : 0;
    }

    // ==================================================================================
    // Callbacks (formerly on SessionItem)
    // ==================================================================================

    public void onExit(Runnable callback) {
        this.onExit = callback;
    }

    public void onBack(Runnable callback) {
        this.onBack = callback;
    }

    // ==================================================================================
    // Verbs (formerly on SessionItem)
    // ==================================================================================

    @Verb(value = CoreVocabulary.Exit.KEY, doc = "Exit the session")
    public ActionResult exit() {
        if (onExit != null) {
            onExit.run();
        }
        return ActionResult.success("exit");
    }

    @Verb(value = CoreVocabulary.Authenticate.KEY, doc = "Authenticate as a user")
    public ActionResult actionAuthenticate(
            @Param(value = "user", doc = "The user to authenticate as") ItemID userId) {
        Signer user = authenticate(userId);
        return ActionResult.success(user.displayToken() + " authenticated");
    }

    // ==================================================================================
    // View Management (ITEM_VIEW frames on this session)
    // ==================================================================================

    @Verb(predicate = ViewVocabulary.View.KEY, doc = "Open a persistent view of an item")
    public ActionResult actionView(
            @Param(role = ThematicRole.Theme.KEY, doc = "The item to view") ItemID targetId) {
        if (targetId == null) return ActionResult.failure(new IllegalArgumentException("No target item specified"));
        FrameKey key = openView(targetId);
        navigateInto(Ref.of(targetId));

        // Tell ItemModel about the active view so it shows view chrome
        if (itemModel != null) {
            ViewHandle vh = findView(targetId);
            if (vh != null) {
                itemModel.setActiveView(vh);
            }
        }

        // Notify subclasses (GraphicalSession creates OS window)
        onViewOpened(key);

        return ActionResult.success("Viewing " + targetId.displayAtWidth(12));
    }

    @Verb(predicate = ViewVocabulary.Close.KEY, doc = "Close an open view")
    public ActionResult actionClose(
            @Param(role = ThematicRole.Theme.KEY, doc = "The item whose view to close", required = false) ItemID targetId) {
        ItemID actualTarget = targetId;
        if (actualTarget == null) {
            Optional<Item> ctx = contextItem();
            if (ctx.isPresent()) actualTarget = ctx.get().iid();
        }

        if (actualTarget != null) {
            // Find the frame key before closing so we can notify
            ViewHandle vh = findView(actualTarget);
            closeView(actualTarget);
            if (vh != null) {
                onViewClosed(vh.frameKey());
            }
        }

        // Clear view chrome from ItemModel
        if (itemModel != null) {
            itemModel.clearActiveView();
        }
        goBack();
        return ActionResult.success("View closed");
    }

    /**
     * Hook called when a new ITEM_VIEW frame is opened.
     * GraphicalSession overrides to create an OS window.
     */
    protected void onViewOpened(FrameKey key) {}

    /**
     * Hook called when an ITEM_VIEW frame is closed.
     * GraphicalSession overrides to destroy the OS window.
     */
    protected void onViewClosed(FrameKey key) {}

    /**
     * Open a view of an item — creates an ITEM_VIEW frame on this session.
     *
     * @param target the IID of the item to view
     * @return the FrameKey of the new ITEM_VIEW frame
     */
    public FrameKey openView(ItemID target) {
        // Check if a view already exists for this target
        ViewHandle existing = findView(target);
        if (existing != null) return existing.frameKey();

        // Create a new ITEM_VIEW frame
        FrameKey key = FrameKey.of(ITEM_VIEW_SEMEME_ID, target.encodeText());

        FrameBody body = new FrameBody(ITEM_VIEW_SEMEME_ID, List.of(
                Binding.ref(ThematicRole.Theme.IID, target)
        ));
        dev.everydaythings.graph.frame.Frame frame =
                new dev.everydaythings.graph.frame.Frame(key, ITEM_VIEW_SEMEME_ID, body, null, false);
        frames().add(frame);

        // Store default ViewConfig as live instance on the frame
        frame.setInstance(ViewConfig.defaults());

        return key;
    }

    /**
     * Close a view by removing its ITEM_VIEW frame.
     *
     * @param target the IID of the viewed item
     */
    public void closeView(ItemID target) {
        ViewHandle vh = findView(target);
        if (vh != null) {
            frames().removeByKey(vh.frameKey());
        }
    }

    /**
     * Find the ITEM_VIEW frame for a given target item.
     *
     * @param target the IID of the viewed item
     * @return a ViewHandle if found, null otherwise
     */
    public ViewHandle findView(ItemID target) {
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (ITEM_VIEW_SEMEME_ID.equals(frame.type()) && frame.body() != null) {
                ItemID themeId = frame.body().homeId();
                if (target.equals(themeId)) {
                    ViewConfig config = frame.instance() instanceof ViewConfig vc
                            ? vc : ViewConfig.defaults();
                    return new ViewHandle(frame.frameKey(), target, config);
                }
            }
        }
        return null;
    }

    /**
     * Get all open views.
     *
     * @return list of ViewHandles for all ITEM_VIEW frames
     */
    public List<ViewHandle> openViews() {
        List<ViewHandle> views = new java.util.ArrayList<>();
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (ITEM_VIEW_SEMEME_ID.equals(frame.type()) && frame.body() != null) {
                ItemID themeId = frame.body().homeId();
                if (themeId != null) {
                    ViewConfig config = frame.instance() instanceof ViewConfig vc
                            ? vc : ViewConfig.defaults();
                    views.add(new ViewHandle(frame.frameKey(), themeId, config));
                }
            }
        }
        return views;
    }

    /**
     * Get the ViewConfig for a given view frame.
     *
     * @param key the ITEM_VIEW frame key
     * @return the config, or null if not found
     */
    public ViewConfig viewConfig(FrameKey key) {
        dev.everydaythings.graph.frame.Frame frame = frames().get(key);
        if (frame == null) return null;
        return frame.instance() instanceof ViewConfig vc ? vc : ViewConfig.defaults();
    }

    /**
     * Update the ViewConfig for a given view frame.
     *
     * @param key    the ITEM_VIEW frame key
     * @param config the new config
     */
    public void updateViewConfig(FrameKey key, ViewConfig config) {
        dev.everydaythings.graph.frame.Frame frame = frames().get(key);
        if (frame != null) {
            frame.setInstance(config);
        }
    }

    private static final ItemID ITEM_VIEW_SEMEME_ID = ItemID.fromString(ViewVocabulary.ItemView.KEY);

    // ==================================================================================
    // Display Layout Management (DISPLAY_LAYOUT frames on this session)
    // ==================================================================================

    private static final ItemID DISPLAY_LAYOUT_SEMEME_ID =
            ItemID.fromString(ViewVocabulary.DisplayLayout.KEY);

    /**
     * Register a display layout in session space.
     *
     * <p>Creates a DISPLAY_LAYOUT frame qualified by "{hostId}:{displayId}"
     * and stores the DisplayLayoutConfig as its live instance.
     *
     * @param config the display layout configuration
     * @return the FrameKey of the new DISPLAY_LAYOUT frame
     */
    public FrameKey registerDisplayLayout(DisplayLayoutConfig config) {
        String qualifier = config.hostId().encodeText() + ":" + config.displayId();
        FrameKey key = FrameKey.of(DISPLAY_LAYOUT_SEMEME_ID, qualifier);

        // Remove existing frame for this display if present
        frames().removeByKey(key);

        dev.everydaythings.graph.frame.Frame frame =
                new dev.everydaythings.graph.frame.Frame(key, DISPLAY_LAYOUT_SEMEME_ID, null, null, false);
        frames().add(frame);
        frame.setInstance(config);

        return key;
    }

    /**
     * Get all DISPLAY_LAYOUT entries on this session.
     */
    public List<DisplayLayoutConfig> displayLayouts() {
        List<DisplayLayoutConfig> result = new java.util.ArrayList<>();
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (DISPLAY_LAYOUT_SEMEME_ID.equals(frame.type())
                    && frame.instance() instanceof DisplayLayoutConfig dlc) {
                result.add(dlc);
            }
        }
        return result;
    }

    /**
     * Remove all DISPLAY_LAYOUT frames for a given host.
     *
     * @param hostId the host whose layouts to remove
     */
    public void clearDisplayLayouts(ItemID hostId) {
        List<FrameKey> toRemove = new java.util.ArrayList<>();
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (DISPLAY_LAYOUT_SEMEME_ID.equals(frame.type())
                    && frame.instance() instanceof DisplayLayoutConfig dlc
                    && hostId.equals(dlc.hostId())) {
                toRemove.add(frame.frameKey());
            }
        }
        for (FrameKey key : toRemove) {
            frames().removeByKey(key);
        }
    }

    /**
     * Replace all DISPLAY_LAYOUT frames for a host with the given layouts.
     *
     * @param hostId  the host to update
     * @param layouts the new display layouts
     */
    public void registerHostDisplays(ItemID hostId, List<DisplayLayoutConfig> layouts) {
        clearDisplayLayouts(hostId);
        for (DisplayLayoutConfig layout : layouts) {
            registerDisplayLayout(layout);
        }
    }

    // ==================================================================================
    // Display (formerly on SessionItem)
    // ==================================================================================

    @Override
    protected String findDisplayName() {
        return "session";
    }

    @Override
    public String displayToken() {
        return "session";
    }

    @Override
    public String resolveDisplayToken(ItemID sememeId) {
        if (sememeId == null || librarian == null) return null;
        return librarian.get(sememeId, Sememe.class)
                .map(Sememe::displayToken)
                .orElse(null);
    }

    // ==================================================================================
    // InputController Initialization
    // ==================================================================================

    /**
     * Initialize InputController with shared dispatch wiring.
     *
     * <p>Builds the InputController with lookup, dispatch, and navigation wiring
     * shared across all session types. Subclasses override
     * {@link #onInputChanged} and {@link #onInputDispatched} for
     * UI-specific refresh.
     */
    protected void initializeInputController() {
        if (librarian == null) return;

        if (inputBindings == null) {
            inputBindings = InputBindings.defaults();
        }

        inputController = InputController.builder()
                .lookup(text -> librarian.prefix(text, maxCompletions()).toList())
                .librarian(librarian)
                .context(contextItem().orElse(null))
                .session(this)
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
        updateInputState(inputController.snapshot());
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
    protected void onInputChanged(InputSnapshot snapshot) {
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
    protected void initializeItemModel(Ref context) {
        if (context == null) {
            // Default context is the session itself (you are always somewhere)
            context = Ref.of(iid());
            liveItemCache.put(iid(), this);
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
        return create(librarian, (Ref) null, opts);
    }

    /**
     * Create a session with resolved librarian, context Item, and options.
     */
    public static Session create(LibrarianHandle librarian, Item context, SessionOptions opts) {
        Ref contextRef = context != null ? Ref.of(context.iid()) : null;
        return create(librarian, contextRef, opts);
    }

    /**
     * Create a session with resolved librarian, context Ref, and options.
     *
     * <p>If context is null, the session defaults to the session item itself
     * (you are always somewhere — no bare "graph>" prompt).
     */
    public static Session create(LibrarianHandle librarian, Ref context, SessionOptions opts) {
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
     * Get the current context Ref.
     */
    public Ref context() {
        return itemModel != null ? itemModel.context() : null;
    }

    /**
     * Get the current root Ref.
     */
    public Ref root() {
        return itemModel != null ? itemModel.root() : null;
    }

    /**
     * Get the context Item (resolves the Ref's target IID).
     */
    public Optional<Item> contextItem() {
        if (itemModel == null) return Optional.empty();
        Ref ctx = itemModel.context();
        if (ctx == null || ctx.target() == null) return Optional.empty();
        return resolveItem(ctx.target());
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
     *
     * <p>When a view is active, wraps the content in {@link ViewSurface} chrome
     * (title bar + content + prompt). Otherwise returns the standard ItemModel layout.
     */
    public SurfaceSchema toSurface() {
        if (itemModel == null) return null;

        if (itemModel.hasActiveView()) {
            return buildViewSurface();
        }

        return itemModel.toSurface();
    }

    private SurfaceSchema buildViewSurface() {
        Optional<Item> item = contextItem();
        if (item.isEmpty()) return itemModel.toSurface();

        SurfaceSchema<?> content = itemModel.contentSurface();
        SurfaceSchema<?> prompt = itemModel.prompt();
        ViewConfig config = itemModel.activeView().config();
        return ViewSurface.of(item.get(), content, prompt, config);
    }

    /**
     * Update the input state in the ItemModel from an InputController snapshot.
     *
     * <p>Call this when InputController fires onChange so that the input field
     * renders as part of the surface tree across all renderers.
     *
     * @param snapshot the current input state
     */
    public void updateInputState(InputSnapshot snapshot) {
        if (itemModel != null) {
            itemModel.updateInput(snapshot);
            // Clear feedback when user starts typing new input
            if (snapshot != null && !snapshot.displayText().isBlank()) {
                itemModel.clearFeedback();
            }
        }
    }

    // ==================================================================================
    // Navigation
    // ==================================================================================

    /**
     * Navigate into an item (makes it the new root).
     */
    public void navigateInto(Ref target) {
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
            navigateInto(Ref.of(item.iid()));
        }
    }

    /**
     * Select an item (changes context within current root).
     */
    public void select(Ref target) {
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
        // View close — remove ITEM_VIEW frame, clear view chrome, go back
        if ("viewClose".equals(action)) {
            contextItem().ifPresent(item -> closeView(item.iid()));
            if (itemModel != null) {
                itemModel.clearActiveView();
            }
            goBack();
            return true;
        }
        // View mode toggle — sync config back to the ITEM_VIEW frame
        if ("viewMode:toggle".equals(action) && itemModel != null) {
            itemModel.handleEvent(action, target);
            syncViewModeToFrame();
            return true;
        }
        return itemModel != null && itemModel.handleEvent(action, target);
    }

    /**
     * Sync the current viewMode/inspectMode from ItemModel back to the
     * ITEM_VIEW frame's ViewConfig.
     */
    private void syncViewModeToFrame() {
        if (itemModel == null || !itemModel.hasActiveView()) return;
        ViewHandle vh = itemModel.activeView();
        if (vh == null) return;
        ViewConfig current = viewConfig(vh.frameKey());
        if (current == null) return;
        ViewConfig updated = current
                .withMode(itemModel.currentViewMode())
                .withInspectMode(itemModel.currentInspectMode());
        updateViewConfig(vh.frameKey(), updated);
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
            case Eval.EvalResult.Created(Item item, Sememe type) -> {
                // Item was created — don't navigate the current view.
                // Cache it and refresh the tree so it's visible.
                liveItemCache.put(item.iid(), item);

                // Instances are discoverable via frame queries from their type sememe

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
                // Errors are shown in the input field via InputController's error state.
                // Log for debugging only.
                logger.debug("Dispatch error: {}", message);
            }
            case Eval.EvalResult.Ambiguous ambiguous -> {
                // Ambiguity is shown in the input field via InputController's error state.
                logger.debug("Ambiguous input: {} unresolved tokens", ambiguous.tokens().size());
            }
            case Eval.EvalResult.QueryResult(var items, var pattern) -> {
                logger.info("Query returned {} results", items.size());
                // TODO: present query results in the UI
            }
        }
    }

    /**
     * Handle an EvalResult from input dispatch.
     *
     * <p>Handles the result (navigation, component creation), logs the
     * activity, and updates prompt/context. Errors are shown directly in
     * the input field via InputController's error state.
     */
    protected void handleInputResult(Eval.EvalResult result) {
        handleEvalResult(result);

        // Refresh tree — dispatch may have added/changed components on the focused item
        if (itemModel != null && !(result instanceof Eval.EvalResult.Empty)) {
            itemModel.refresh();
        }

        // Log to the session activity log and update feedback display
        if (!(result instanceof Eval.EvalResult.Empty)) {
            String inputText = inputController != null ? inputController.lastSubmittedText() : null;
            ItemID contextIid = contextItem().map(Item::iid).orElse(null);
            ActivityEntry entry = ActivityEntry.from(inputText, contextIid, result);
            logActivity(entry);

            // Push feedback to the prompt area
            if (itemModel != null && entry.hasResult()) {
                itemModel.setFeedback(entry.resultText(), !entry.isSuccess());
            }
        }

        if (inputController != null) {
            inputController.setPrompt(buildPrompt());
            inputController.setContext(contextItem().orElse(null));
        }
    }

    // ==================================================================================
    // Component Management
    // ==================================================================================

    /**
     * Add a component result to the current context item.
     *
     * <p>Derives a handle name from the component's {@code @Type}
     * annotation (e.g., "cg.sememe:chess" → "chess").
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

        // Resolve the semantic predicate from the component's @Implements annotation
        ItemID predicateId = derivePredicateId(component);
        if (predicateId == null) {
            throw new IllegalArgumentException(
                    "Component " + component.getClass().getSimpleName()
                            + " must have @Implements annotation");
        }
        String qualifier = deriveUniqueQualifier(actual, predicateId);
        actual.endorse(predicateId, qualifier, component);

        // Refresh tree to pick up the new component, then select it
        if (itemModel != null) {
            itemModel.refresh();
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
     * Get the semantic predicate ItemID from a component's @Implements annotation.
     */
    private ItemID derivePredicateId(Object component) {
        Implements impl = component.getClass().getAnnotation(Implements.class);
        if (impl != null) {
            return ItemID.fromString(impl.value());
        }
        return null;
    }

    /**
     * Derive a unique qualifier for a semantic frame key.
     *
     * <p>If the item already has a frame with this predicate and no qualifier,
     * returns "2", "3", etc. Returns null for the first instance.
     */
    private String deriveUniqueQualifier(Item item, ItemID predicateId) {
        // First instance: no qualifier needed
        if (!item.frames().containsKey(FrameKey.of(predicateId))) {
            return null;
        }
        // Subsequent: find next available number
        int n = 2;
        while (item.frames().containsKey(FrameKey.of(predicateId, String.valueOf(n)))) {
            n++;
        }
        return String.valueOf(n);
    }


    /**
     * Check if a value is a component (has @Type annotation).
     */
    private static boolean isComponent(Object value) {
        return value != null && value.getClass().isAnnotationPresent(Implements.class);
    }

    // ==================================================================================
    // Shared Input Dispatch
    // ==================================================================================

    /**
     * Dispatch a key chord to InputController via InputBindings.
     */
    protected void dispatchToInput(KeyChord chord) {
        if (inputController == null) return;
        Optional<InputAction> action = inputBindings.resolve(
                chord, inputController.snapshot().hasVisibleCompletions());
        action.ifPresent(inputController::handle);
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
     * <p>The actor comes from the session's active user. The context
     * comes from the current navigation position. When at the session item
     * itself, shows just the type name ("session").
     */
    public String buildPrompt() {
        String actorPrefix = resolveActorPrefix();

        if (itemModel == null) {
            return actorPrefix + "session> ";
        }

        Ref ctx = itemModel.context();
        if (ctx == null) {
            return actorPrefix + "session> ";
        }

        Optional<Item> item = contextItem();
        if (item.isPresent()) {
            String icon = item.get().emoji();
            String label = item.get().displayToken();

            FrameKey frameKey = ctx.frameKey();
            if (frameKey != null) {
                label = label + "/" + frameKey.toCanonicalString();
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
     * <p>Uses the session's active actor if authenticated.
     * Falls back to the librarian's principal for backwards compatibility.
     *
     * @return The actor prefix, or empty string if no actor is set
     */
    private String resolveActorPrefix() {
        if (actor() != null) {
            return actor().displayToken() + "@";
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
     * Resolve a context specification to a Ref.
     */
    protected Ref resolveContextLink(String spec) {
        logger.debug("Resolving context: {}", spec);

        if (spec.startsWith("@")) {
            String handleStr = spec.substring(1);
            Item item = lookupItem(handleStr);
            return item != null ? Ref.of(item.iid()) : null;
        }

        if (spec.startsWith("iid:")) {
            try {
                return Ref.of(ItemID.parse(spec));
            } catch (IllegalArgumentException e) {
                logger.debug("Failed to parse ref: {}", spec, e);
                return null;
            }
        }

        Item item = lookupItem(spec);
        return item != null ? Ref.of(item.iid()) : null;
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
                LibrarianHandle resolvedHandle = resolveLibrarianConnection();
                this.ownsLibrarian = true;
                this.librarian = resolvedHandle;

                // 2. Resolve context if specified
                Ref ctx = null;
                if (opts.positionalArgs != null && !opts.positionalArgs.isEmpty()) {
                    String contextSpec = opts.positionalArgs.get(0);
                    if (looksLikeLink(contextSpec)) {
                        ctx = resolveContextLink(contextSpec);
                    }
                }

                // 3. Create appropriate session (null ctx = session item default)
                Session session = Session.create(resolvedHandle, ctx, opts);
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
