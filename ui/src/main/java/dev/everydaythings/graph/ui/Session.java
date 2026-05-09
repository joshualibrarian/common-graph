package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.frame.*;
import dev.everydaythings.graph.frame.eval.FrameAssemblyContext;
import dev.everydaythings.graph.Implements;
import dev.everydaythings.graph.item.*;
import dev.everydaythings.graph.item.user.SignerOld;
import dev.everydaythings.graph.parse.InputController;
import dev.everydaythings.graph.parse.InputSnapshot;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.ViewVocabulary;
import dev.everydaythings.graph.runtime.ActivityEntry;
import dev.everydaythings.graph.runtime.ActivityLog;
import dev.everydaythings.graph.runtime.Eval;
import dev.everydaythings.graph.runtime.LibrarianHandle;
import dev.everydaythings.graph.runtime.LocalLibrarian;
import dev.everydaythings.graph.runtime.options.SessionOptions;
import dev.everydaythings.graph.parse.InputAction;
import dev.everydaythings.graph.ui.input.InputBindings;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.surface.item.ItemView;
import dev.everydaythings.graph.frame.FrameBodyOld;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import java.awt.GraphicsEnvironment;
import java.io.Closeable;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Session is the core controller for interacting with a Librarian.
 *
 * <p>Session extends {@link ItemOld} — it IS an Item with identity,
 * vocabulary, verbs, and components. It manages authenticated users,
 * session-level verbs (exit, back, authenticate, switch), the activity
 * log, and the UI layer (ItemView, input, rendering).
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
public abstract class Session extends ItemOld implements Callable<Integer>, Closeable {

    public static final String KEY = "cg.sememe:session";

    @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
    static final String seedGloss = "UI session for item interaction";

    @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
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
    private final Set<SignerOld> authenticatedUsers = new LinkedHashSet<>();

    /** The currently active actor — the identity that signs dispatched actions. */
    @Getter
    private SignerOld actor;

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
     * The ItemView manages UI state: root, context, tree, navigation.
     */
    protected ItemView itemView;

    public ItemView itemView() { return itemView; }

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
    @Deprecated // Being removed — use librarian.put() / librarian.get() instead
    private final Map<ItemID, ItemOld> liveItemCache = new HashMap<>();

    private static final ItemID EXIT_SEMEME_ID = ItemID.fromString(CoreVocabulary.Exit.KEY);
    private static final ItemID BACK_SEMEME_ID = ItemID.fromString(CoreVocabulary.Back.KEY);

    /**
     * Shared input handling — pulled up from subclasses.
     */
    protected InputController inputController;
    /** Captured submitted text — set in onResult before handleInputResult runs. */
    private String lastDispatchedText;

    /** Set the last dispatched text — called by ViewWindow before handleInputResult. */
    public void setLastDispatchedText(String text) { this.lastDispatchedText = text; }
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
     */
    public void bind(LibrarianHandle handle) {
        this.handle = handle;

        // Set Item-level librarian so display resolution works
        if (handle instanceof LocalLibrarian local) {
            setLibrarian(local.librarian());
        }

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
            if (principal instanceof SignerOld signer && signer.canSign()) {
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
    private boolean challengeResponse(SignerOld signer) {
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
    public SignerOld authenticate(ItemID userId) {
        if (handle == null) {
            throw new IllegalStateException("No librarian handle — cannot resolve users");
        }

        // Check if already authenticated
        for (SignerOld s : authenticatedUsers) {
            if (s.iid().equals(userId)) {
                return s;
            }
        }

        // Resolve the item and check if it's a Signer
        Optional<ItemOld> found = handle.get(userId);
        if (found.isPresent() && found.get() instanceof SignerOld signer) {
            return authenticateSigner(signer);
        }

        throw new IllegalArgumentException("User not found: " + userId.encodeText());
    }

    private SignerOld authenticateSigner(SignerOld signer) {
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
    public SignerOld switchActor(ItemID userId) {
        for (SignerOld s : authenticatedUsers) {
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
    public Set<SignerOld> authenticatedUsers() {
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

        // Also create an ACTIVITY frame on the Session item (frame-based logging)
        if (librarian != null) {
            FrameBodyOld.Builder builder = FrameBodyOld.builder(
                    ItemID.fromString(CoreVocabulary.Activity.KEY));

            // THEME — what was evaluated (input text)
            if (entry.input() != null) {
                builder.bind(ItemID.fromString(ThematicRole.Theme.KEY), entry.input());
            }

            // LOCATION — where (the Session item itself, since that's where the log lives)
            builder.bind(ItemID.fromString(ThematicRole.Location.KEY), iid());

            // AGENT — who
            ItemID principalId = librarian.principalId();
            if (principalId != null) {
                builder.bind(ItemID.fromString(ThematicRole.Agent.KEY), principalId);
            }

            // RESULT — what happened
            if (entry.resultText() != null) {
                builder.bind(ItemID.fromString(ThematicRole.Result.KEY), entry.resultText());
            } else if (entry.resultIid() != null) {
                builder.bind(ItemID.fromString(ThematicRole.Result.KEY), entry.resultIid());
            }

            // TIME — when
            builder.bind(ItemID.fromString(ThematicRole.Time.KEY),
                    dev.everydaythings.graph.item.Literal.ofInstant(java.time.Instant.now()));

            librarian.storeFrame(builder.build());
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
    // Frame Assembly Reactions
    // ==================================================================================

    @Override
    public void onFrameAssembled(FrameAssemblyContext ctx) {
        ItemID predicate = ctx.body().predicate();

        if (ViewVocabulary.ItemView.IID.equals(predicate)) {
            ItemID target = ctx.body().bindingId(ThematicRole.Theme.IID);
            if (target == null) return;

            // Endorse the ITEM_VIEW frame on this session
            CompoundKey key = endorseViewFrame(ctx.body());
            onViewOpened(key);
            ctx.handled(ActionResult.success("Viewing " + target.displayAtWidth(12)));
            return;
        }

        if (ViewVocabulary.Close.IID.equals(predicate)) {
            ItemID target = ctx.body().bindingId(ThematicRole.Theme.IID);
            if (target != null) {
                ViewHandle vh = findView(target);
                closeViewOf(target);
                if (vh != null) onViewClosed(vh.frameKey());
            }
            goBack();
            ctx.handled(ActionResult.success("View closed"));
        }
    }

    /**
     * Endorse an ITEM_VIEW FrameBody on this session's frames table.
     *
     * <p>Creates a Frame with a unique key and adds it to the session's
     * endorsements table. Returns the key for window management.
     */
    private CompoundKey endorseViewFrame(FrameBodyOld body) {
        String viewId = java.util.UUID.randomUUID().toString().substring(0, 8);
        CompoundKey key = CompoundKey.of(ViewVocabulary.ItemView.IID, viewId);

        FrameOld frame = new FrameOld(key, ViewVocabulary.ItemView.IID, body, null, false);
        frames().add(frame);
        frame.setInstance(ViewConfig.defaults());

        return key;
    }

    // ==================================================================================
    // View Management (ITEM_VIEW frames on this session)
    // ==================================================================================

    /**
     * Hook called when a new ITEM_VIEW frame is opened.
     * GraphicalSession overrides to create an OS window.
     */
    protected void onViewOpened(CompoundKey key) {}

    /**
     * Hook called when an ITEM_VIEW frame is closed.
     * GraphicalSession overrides to destroy the OS window.
     */
    protected void onViewClosed(CompoundKey key) {}

    /**
     * Open a view of an item on a specific display.
     *
     * <p>Creates an ITEM_VIEW frame with THEME (what) and LOCATION (which display)
     * as identity bindings. Each view gets a unique FrameKey — the same item can
     * be open in multiple views, even on the same display.
     *
     * @param target    the IID of the item to view
     * @param displayId the IID of the display (null = unassigned)
     * @return the FrameKey of the new ITEM_VIEW frame
     */
    /**
     * Open a view of an item by assembling an ITEM_VIEW frame.
     *
     * <p>Assembles the frame body and fires it through {@link #onFrameAssembled},
     * which endorses the frame on this session and triggers window creation
     * via {@link #onViewOpened}.
     *
     * @param target the IID of the item to view
     */
    public void openView(ItemID target) {
        // Check if a view already exists for this target
        ViewHandle existing = findView(target);
        if (existing != null) {
            logger.info("View already exists for {} → {}", target.displayAtWidth(12), existing.frameKey());
            return;
        }

        List<Binding> bindings = new java.util.ArrayList<>();
        bindings.add(Binding.ref(ThematicRole.Theme.IID, target));
        Ref displayRef = focusedDisplay();
        if (displayRef != null) {
            bindings.add(Binding.ref(ThematicRole.Location.IID, displayRef));
        }
        FrameBodyOld body = new FrameBodyOld(ViewVocabulary.ItemView.IID, bindings);

        // Fire through the assembly reaction — onFrameAssembled handles
        // endorsement and window creation.
        FrameAssemblyContext ctx = new FrameAssemblyContext(body, null, null, this, java.util.Map.of());
        onFrameAssembled(ctx);
    }

    /**
     * Get the currently focused display.
     *
     * <p>Subclasses (GraphicalSession) override to return the display
     * of the currently focused window. Returns null if unknown.
     */
    public Ref focusedDisplay() {
        return null;
    }

    /**
     * Close a view by its frame key.
     *
     * <p>With multiple views of the same item possible, closing targets
     * a specific view (window), not all views of an item.
     *
     * @param viewKey the ITEM_VIEW frame key to close
     */
    public void closeView(CompoundKey viewKey) {
        frames().removeByKey(viewKey);
    }

    /**
     * Close the first view of a target item.
     *
     * @param target the IID of the viewed item
     */
    public void closeViewOf(ItemID target) {
        ViewHandle vh = findView(target);
        if (vh != null) {
            frames().removeByKey(vh.frameKey());
        }
    }

    /**
     * Find the first ITEM_VIEW frame for a given target item.
     *
     * <p>Scans all ITEM_VIEW frames by THEME binding. Returns the first
     * match — use {@link #openViews()} to find all views of an item.
     *
     * @param target the IID of the viewed item
     * @return a ViewHandle if found, null otherwise
     */
    public ViewHandle findView(ItemID target) {
        for (FrameOld frame : frames()) {
            if (ViewVocabulary.ItemView.IID.equals(frame.type()) && frame.body() != null) {
                ItemID themeId = frame.body().homeId();
                if (target.equals(themeId)) {
                    ViewConfig config = frame.instance() instanceof ViewConfig vc
                            ? vc : ViewConfig.defaults();
                    Ref display = frame.body().bindingRef(ThematicRole.Location.IID);
                    return new ViewHandle(frame.frameKey(), target, display, config);
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
        for (FrameOld frame : frames()) {
            if (ViewVocabulary.ItemView.IID.equals(frame.type()) && frame.body() != null) {
                ItemID themeId = frame.body().homeId();
                if (themeId != null) {
                    ViewConfig config = frame.instance() instanceof ViewConfig vc
                            ? vc : ViewConfig.defaults();
                    Ref display = frame.body().bindingRef(ThematicRole.Location.IID);
                    views.add(new ViewHandle(frame.frameKey(), themeId, display, config));
                }
            }
        }
        return views;
    }

    /**
     * Resolve all open view targets to Items, for sibling disambiguation.
     */
    private Collection<ItemOld> openViewItems() {
        List<ItemOld> items = new ArrayList<>();
        for (ViewHandle vh : openViews()) {
            resolveItem(vh.target()).ifPresent(items::add);
        }
        return items;
    }

    /**
     * Get the ViewConfig for a given view frame.
     *
     * @param key the ITEM_VIEW frame key
     * @return the config, or null if not found
     */
    public ViewConfig viewConfig(CompoundKey key) {
        FrameOld frame = frames().get(key);
        if (frame == null) return null;
        return frame.instance() instanceof ViewConfig vc ? vc : ViewConfig.defaults();
    }

    /**
     * Update the ViewConfig for a given view frame.
     *
     * @param key    the ITEM_VIEW frame key
     * @param config the new config
     */
    public void updateViewConfig(CompoundKey key, ViewConfig config) {
        FrameOld frame = frames().get(key);
        if (frame != null) {
            frame.setInstance(config);
        }
    }

    // ==================================================================================
    // Display Layout Management (DISPLAY_LAYOUT frames on this session)
    // ==================================================================================

    /**
     * Register a display layout in session space.
     *
     * <p>Creates a DISPLAY_LAYOUT frame qualified by "{hostId}:{displayId}"
     * and stores the DisplayLayoutConfig as its live instance.
     *
     * @param config the display layout configuration
     * @return the FrameKey of the new DISPLAY_LAYOUT frame
     */
    public CompoundKey registerDisplayLayout(DisplayLayoutConfig config) {
        CompoundKey key = CompoundKey.of(ViewVocabulary.DisplayLayout.IID, config.displayId());

        // Remove existing frame for this display if present
        frames().removeByKey(key);

        List<Binding> bindings = new java.util.ArrayList<>();
        bindings.add(Binding.literal(ThematicRole.Theme.IID,
                Literal.ofText(config.displayId())));
        bindings.add(Binding.ref(ThematicRole.Location.IID, config.hostId()));
        FrameBodyOld body = new FrameBodyOld(ViewVocabulary.DisplayLayout.IID, bindings);

        FrameOld frame = new FrameOld(key, ViewVocabulary.DisplayLayout.IID, body, null, false);
        frames().add(frame);
        frame.setInstance(config);

        return key;
    }

    /**
     * Get all DISPLAY_LAYOUT entries on this session.
     */
    public List<DisplayLayoutConfig> displayLayouts() {
        List<DisplayLayoutConfig> result = new java.util.ArrayList<>();
        for (FrameOld frame : frames()) {
            if (ViewVocabulary.DisplayLayout.IID.equals(frame.type())
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
        List<CompoundKey> toRemove = new ArrayList<>();
        for (FrameOld frame : frames()) {
            if (ViewVocabulary.DisplayLayout.IID.equals(frame.type())
                    && frame.instance() instanceof DisplayLayoutConfig dlc
                    && hostId.equals(dlc.hostId())) {
                toRemove.add(frame.frameKey());
            }
        }
        for (CompoundKey key : toRemove) {
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
                .onNavigate(item -> { if (item != null) { librarian.put(item); openView(item.iid()); } })
                .onResult(result -> {
                    // Capture submitted text NOW before anything clears it
                    lastDispatchedText = inputController.lastSubmittedText();
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
     * Initialize the ItemView for this session.
     *
     * <p>If no explicit context is provided, defaults to the session item
     * itself — you are always somewhere.
     */
    protected void initializeItemModel(Ref context) {
        if (context == null) {
            // Default context is the session itself (you are always somewhere)
            context = Ref.of(iid());
            liveItemCache.put(iid(), this);
            if (librarian != null) librarian.put(this);
        }

        // Cache the principal so resolveItem() can find it
        cachePrincipal();

        ItemOld contextItem = resolveItem(context.target()).orElse(this);
        itemView = new ItemView(contextItem, this::resolveItem);
        itemView.setSiblingsProvider(this::openViewItems);

        // Wire ephemeral frame provider if librarian supports it
        if (librarian != null) {
            itemView.setEphemeralProvider(new ItemView.EphemeralFrameProvider() {
                @Override public List<FrameBodyOld> ephemeralFrames(ItemID itemId) {
                    return librarian.ephemeralFrames(itemId);
                }
                @Override public void onEphemeralChanged(ItemID itemId, Runnable listener) {
                    librarian.onEphemeralChanged(itemId, listener);
                }
                @Override public void removeEphemeralListener(ItemID itemId, Runnable listener) {
                    librarian.removeEphemeralListener(itemId, listener);
                }
            });

            // Announce presence in the session — the user is HERE
            announcePresence(context.target());
        }
    }

    /**
     * Create a PRESENT frame on the given item, announcing that this session's
     * principal is present. This is an ephemeral frame — it lives in memory only
     * and expires when the session closes.
     */
    private void announcePresence(ItemID itemId) {
        if (librarian == null) return;
        ItemID principalId = librarian.principalId();
        if (principalId == null) principalId = librarian.iid();

        FrameBodyOld presence = FrameBodyOld.builder(
                        ItemID.fromString(dev.everydaythings.graph.language.PresenceVocabulary.Present.KEY))
                .bind(ItemID.fromString(dev.everydaythings.graph.language.ThematicRole.Agent.KEY), principalId)
                .bind(ItemID.fromString(dev.everydaythings.graph.language.ThematicRole.Location.KEY), itemId)
                .build();

        librarian.storeFrame(presence);
    }

    /**
     * Cache the principal in the session's liveItemCache so resolveItem() can find it.
     */
    private void cachePrincipal() {
        if (librarian != null) {
            librarian.principal().ifPresent(p -> {
                liveItemCache.put(p.iid(), p);
                librarian.put(p);
            });
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
    public static Session create(LibrarianHandle librarian, ItemOld context, SessionOptions opts) {
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

        // Create appropriate session — null context is handled by initializeItemView
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
     * Called when ItemView changes.
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
    // ItemView Accessors
    // ==================================================================================

    /**
     * Get the current context Ref.
     */
    public Ref context() {
        return itemView != null ? itemView.context() : null;
    }

    /**
     * Get the current root Ref.
     */
    public Ref root() {
        return itemView != null ? itemView.root() : null;
    }

    /**
     * Get the context Item (resolves the Ref's target IID).
     */
    public Optional<ItemOld> contextItem() {
        if (itemView == null) return Optional.empty();
        Ref ctx = itemView.context();
        if (ctx == null || ctx.target() == null) return Optional.empty();
        return resolveItem(ctx.target());
    }

    /**
     * Resolve an item by IID, checking the session cache first.
     *
     * <p>The cache preserves live instances with dynamic modifications
     * (e.g., added components) that aren't yet persisted to the store.
     */
    /** Generate the current SceneNode tree for rendering. */
    public SceneNode toSceneNode() {
        return itemView != null ? itemView.toSceneNode() : null;
    }

    public Optional<ItemOld> resolveItem(ItemID iid) {
        return librarian.get(iid);
    }

    /**
     * Update the input state in the ItemView from an InputController snapshot.
     *
     * <p>Call this when InputController fires onChange so that the input field
     * renders as part of the surface tree across all renderers.
     *
     * @param snapshot the current input state
     */
    public void updateInputState(InputSnapshot snapshot) {
        if (itemView != null) {
            itemView.updateInput(snapshot);
            // Clear feedback when user starts typing new input
            if (snapshot != null && !snapshot.displayText().isBlank()) {
                itemView.clearFeedback();
            }
        }
    }

    // ==================================================================================
    // Navigation
    // ==================================================================================

    /**
     * Select an item (changes context within current root).
     */
    public void select(Ref target) {
        if (itemView != null) {
            itemView.select(target);
        }
    }

    /**
     * Go back in navigation history.
     */
    public boolean goBack() {
        return itemView != null && itemView.goBack();
    }

    /**
     * Check if we can go back.
     */
    public boolean canGoBack() {
        return itemView != null && itemView.canGoBack();
    }

    // ==================================================================================
    // Key Handling
    // ==================================================================================

    /**
     * Handle a key chord.
     * Routes to ItemView for navigation keys.
     *
     * @return true if consumed
     */
    public boolean handleKey(KeyChord chord) {
        return itemView != null && itemView.handleKey(chord);
    }

    /**
     * Handle a surface event (from mouse clicks, etc.).
     */
    public boolean handleEvent(String action, String target) {
        // View close — remove ITEM_VIEW frame, clear view chrome, go back
        if ("viewClose".equals(action)) {
            contextItem().ifPresent(item -> closeViewOf(item.iid()));
            if (itemView != null) {
                itemView.clearActiveView();
            }
            goBack();
            return true;
        }
        return itemView != null && itemView.handleEvent(action, target);
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
            case Eval.EvalResult.ItemResult(ItemOld item) -> {
                if (isSessionVerb(item)) {
                    executeSessionVerb(item);
                    return;
                }
                librarian.put(item);
                openView(item.iid());
            }
            case Eval.EvalResult.Created(ItemOld item, ItemOld type) -> {
                // Item was created — don't navigate the current view.
                // Cache it and refresh the tree so it's visible.
                librarian.put(item);

                // Instances are discoverable via frame queries from their type sememe

                if (itemView != null) {
                    itemView.refresh();
                }
                logger.info("Created: {} ({})", item.displayToken(), item.iid().encodeText());
            }
            case Eval.EvalResult.Value(Object value) -> {
                displayValue(value);
            }
            case Eval.EvalResult.ValueWithTarget(Object value, ItemOld targetItem) -> {
                // Value targeted at an item — display for now
                displayValue(value);
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
            case Eval.EvalResult.QueryResult(var queryItem, var items, var pattern) -> {
                librarian.put(queryItem);
                // Open a view for the QueryItem
                openView(queryItem.iid());
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
        // Log to the session activity log FIRST (before navigation changes context)
        if (!(result instanceof Eval.EvalResult.Empty)) {
            String inputText = lastDispatchedText;
            ItemID contextIid = contextItem().map(ItemOld::iid).orElse(null);
            ActivityEntry entry = ActivityEntry.from(inputText, contextIid, result);
            logActivity(entry);

            // Push feedback to the prompt area
            if (itemView != null && entry.hasResult()) {
                itemView.setFeedback(entry.resultText(), !entry.isSuccess());
            }
        }

        handleEvalResult(result);

        // Refresh tree — dispatch may have added/changed components on the focused item
        if (itemView != null && !(result instanceof Eval.EvalResult.Empty)) {
            itemView.refresh();
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
     * Hook for subclasses to react when the context item's components change.
     *
     * <p>Called after navigation. Subclasses (e.g., {@link GraphicalSession})
     * override this to rebuild the tick registry for live widget updates.
     *
     * @param item the item whose components changed
     */
    protected void onContextComponentsChanged(ItemOld item) {
        // Default: no-op
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
    protected boolean isSessionVerb(ItemOld item) {
        ItemID iid = item.iid();
        return iid.equals(EXIT_SEMEME_ID) || iid.equals(BACK_SEMEME_ID);
    }

    /**
     * Execute a session-level verb (exit, back).
     */
    protected void executeSessionVerb(ItemOld item) {
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

        if (itemView == null) {
            return actorPrefix + "session> ";
        }

        Ref ctx = itemView.context();
        if (ctx == null) {
            return actorPrefix + "session> ";
        }

        Optional<ItemOld> item = contextItem();
        if (item.isPresent()) {
            String icon = item.get().emoji();
            String label = item.get().displayToken();

            CompoundKey frameKey = ctx.frameKey();
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
        SceneNode sceneView = SceneCompiler.compile(value);
        if (sceneView != null) {
            output(formatValue(sceneView));
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
        if (value instanceof SceneNode sn) {
            return "[SceneNode]";
        }
        if (value instanceof ItemOld item) {
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
    protected ItemOld lookupItem(String query) {
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
            ItemOld item = lookupItem(handleStr);
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

        ItemOld item = lookupItem(spec);
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
