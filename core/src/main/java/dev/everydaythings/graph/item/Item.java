package dev.everydaythings.graph.item;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.dispatch.VerbEntry;
import dev.everydaythings.graph.dispatch.VerbInvoker;
import dev.everydaythings.graph.dispatch.VerbSpec;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameAware;
import dev.everydaythings.graph.frame.FrameContext;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.item.Param;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.FrameRecord;
import lombok.extern.log4j.Log4j2;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.frame.EndorsementsTable;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.item.mount.Mount;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.workingtree.WorkingTreeStore;
import dev.everydaythings.graph.policy.PolicySet;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.network.RoutingVocabulary;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.ValueType;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.ItemSurface;
import dev.everydaythings.graph.ui.scene.SceneMode;
import dev.everydaythings.graph.ui.scene.View;
import lombok.Getter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * The fundamental unit of Common Graph — versioned containers with identity,
 * frames, and vocabulary. Think of them as "little git repos."
 *
 * <p>This is a self-describing type. The class IS the definition.
 *
 * <p>State:
 * <ul>
 *   <li>base: current version (ContentID) or null if fresh/uncommitted</li>
 *   <li>endorsedFrames: all frames (endorsed content, semantic assertions)</li>
 *   <li>vocabulary: declared verbs and dispatching</li>
 * </ul>
 *
 * <p>Dirty tracking: items can be created/modified without committing.
 * Call commit() when ready to persist a new version.
 */
@Log4j2
@Scene.Rule(match = ".heading", color = "#89B4FA", fontSize = "1.33")
@Scene.Rule(match = ".muted", color = "#6C7086", fontSize = "0.87")
@Scene.Rule(match = ".small", fontSize = "0.87")
@Scene.Rule(match = ".selected", background = "#313244")
@Scene.Rule(match = ":selected", background = "reverse")
@Scene.Rule(match = ":hover", opacity = "bright")
@Implements(Item.KEY)
@ItemSeed(key = Item.KEY)
@Scene(as = ItemSurface.class)
public class Item {

    // === TYPE DEFINITION ===
    public static final String KEY = "cg.sememe:item";

    @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
    static final String seedGloss = "the fundamental unit of Common Graph";

    @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                   features = {GrammaticalFeature.Lemma.KEY})
    static final String seedNoun = "item";

    // === WELL-KNOWN FRAME KEYS ===
    // Lazy-initialized via holder class to break circular clinit.
    private static class BuiltinKeys {
        static final FrameKey POLICY = FrameKey.of(ItemID.fromString(PolicySet.KEY));
    }

    // ==================================================================================
    // Instance Fields
    // ==================================================================================

    /** Runtime context - null for siloed items and seed items. Set by Librarian after bootstrap. */
    protected Librarian librarian;

    /** Get the librarian context, or null for siloed/seed items. */
    public Librarian itemLibrarian() {
        return librarian;
    }

    /**
     * Associate this item with its librarian.
     *
     * <p>Called during bootstrap to fix up seed items that were created
     * before the librarian existed. After this, the item can resolve
     * other items, display tokens, etc.
     */
    public void setLibrarian(Librarian librarian) {
        this.librarian = librarian;
    }

    @Getter
    protected final ItemID iid;

    /** Store for materialized items (null if not materialized). Typically a WorkingTreeStore. */
    @Getter
    protected transient ItemStore store;

    /** True if this is a fresh creation (not loaded from disk). */
    @Getter
    protected transient boolean freshBoot;

    /** Current version (base) - null if fresh/uncommitted. */
    @Getter
    protected ContentID base;

    /** Currently loaded manifest (if any). */
    @Getter
    protected Manifest current;

    /**
     * Unified state: endorsed frames, mounts, and policy.
     *
     * <p>ItemState holds all the versioned state of an Item, providing a
     * unified abstraction that can be shared with Manifest for serialization.
     */
    protected final ItemState state = new ItemState();

    /** Version history (for items with history loaded). */
    @Getter
    protected List<ContentID> versions = new ArrayList<>();

    /**
     * Intrinsic runtime vocabulary derived from item + component verbs.
     *
     * <p>This is not stored as a component entry; it's rebuilt from schema/content.
     */
    private final transient Vocabulary runtimeVocabulary = new Vocabulary();

    /** True when in "edit mode" - editing a new version. */
    private boolean editing;

    /** Tracks whether item has uncommitted changes. */
    @Getter
    private boolean dirty;

    // ==================================================================================
    // Schema Access (cached per class via ItemScanner)
    // ==================================================================================

    /**
     * Get the cached schema for this item's class.
     *
     * <p>The schema contains all annotation-derived metadata (frame fields,
     * verbs) and is computed once per class.
     */
    @SuppressWarnings("unchecked")
    protected ItemSchema schema() {
        return ItemScanner.schemaFor(getClass());
    }

    // ==================================================================================
    // State Accessors (delegate to ItemState)
    // ==================================================================================

    /** Frame table — all endorsed frames on this item. */
    public EndorsementsTable frames() {
        return state.frames();
    }

    /**
     * Bare frames — frames whose type is FrameBody.TYPE_ID (semantic assertions
     * without a component wrapper).
     *
     * @return stream of FrameBody objects from bare frames
     */
    public java.util.stream.Stream<FrameBody> bareFrameBodies() {
        return frames().bareFrames()
                .map(frame -> frames().getLive(frame.frameKey(), FrameBody.class))
                .flatMap(java.util.Optional::stream);
    }


    /** Per-item policies — stored in EndorsementsTable under well-known handle. */
    public PolicySet policy() {
        return frames().getLive(BuiltinKeys.POLICY, PolicySet.class).orElse(null);
    }

    /** Intrinsic vocabulary: semantic actions available on this item. */
    public Vocabulary vocabulary() {
        return runtimeVocabulary;
    }

    // ==================================================================================
    // Display & Navigation
    // ==================================================================================

    public Ref ref() {
        return Ref.of(iid);
    }

    public String displayToken() {
        return getClass().getSimpleName();
    }

    /**
     * Resolve a sememe ItemID to its English display name.
     *
     * <p>Looks up the sememe in the library and returns its display token
     * (e.g., "item-view", "display-layout", "activity"). Returns null if
     * the sememe cannot be resolved (no librarian, or ID not found).
     *
     * @param sememeId the ItemID of the sememe to resolve
     * @return the display token, or null if unresolvable
     */
    public String resolveDisplayToken(ItemID sememeId) {
        if (sememeId == null || librarian == null) return null;
        return librarian.get(sememeId, Sememe.class)
                .map(Sememe::displayToken)
                .orElse(null);
    }

    /**
     * Resolve a path within this item to get display token.
     *
     * <p>Paths like "/componentHandle" are resolved through the content table.
     *
     * @param path the path to resolve (e.g., "/readme")
     * @return display token for the component, or empty if not found
     */
    public Optional<String> resolvePathDisplayToken(String path) {
        return DisplayResolver.resolvePathDisplayToken(this, path);
    }

    public Optional<String> resolvePathEmoji(String path) {
        return DisplayResolver.resolvePathEmoji(this, path);
    }

    public Optional<String> resolvePathIconResource(String path) {
        return DisplayResolver.resolvePathIconResource(this, path);
    }

    public Optional<dev.everydaythings.graph.value.Color> resolvePathTypeColor(String path) {
        return DisplayResolver.resolvePathTypeColor(this, path);
    }

    public boolean isExpandable() {
        return true; // Items always have structure to explore
    }

    // ==================================================================================
    // Tree Navigation (Link-based)
    // ==================================================================================

    /**
     * Check if this item has children in the given mode.
     *
     * @param mode PRESENTATION (mount tree) or INSPECT (raw tables)
     * @return true if there are children to show
     */
    public boolean isExpandable(TreeLink.ChildMode mode) {
        return switch (mode) {
            case PRESENTATION -> !frames().childrenAt("/").isEmpty();
            case INSPECT -> !frames().isEmpty();
        };
    }

    /**
     * Get children as Refs for tree navigation.
     *
     * <p>In PRESENTATION mode, returns the mount table roots as navigable refs.
     * <p>In INSPECT mode, returns refs to content components and tables.
     *
     * @param mode which view of the item's structure
     * @return list of child Refs
     */
    public List<Ref> children(TreeLink.ChildMode mode) {
        return switch (mode) {
            case PRESENTATION -> childrenPresentation();
            case INSPECT -> childrenInspect();
        };
    }

    /**
     * Presentation mode: mount tree children at root, including virtual directories.
     */
    private List<Ref> childrenPresentation() {
        return childrenAtPath("/");
    }

    /**
     * Get children at a specific path in presentation mode.
     *
     * <p>Returns Refs for both real mounted components and virtual
     * directories implied by deeper mounts.
     *
     * @param path the parent path to list children of
     * @return list of child Refs
     */
    public List<Ref> childrenAtPath(String path) {
        List<Ref> children = new ArrayList<>();
        for (var child : frames().childrenAt(path)) {
            if (child.frame() != null) {
                children.add(Ref.of(iid(), child.frame().frameKey()));
            } else {
                children.add(Ref.of(iid()));
            }
        }
        return children;
    }

    /**
     * Inspect mode: raw content + tables.
     */
    private List<Ref> childrenInspect() {
        List<Ref> children = new ArrayList<>();

        // All non-built-in component frames (content components, relations).
        // Policy now lives under component config metadata and should not appear
        // as a standalone inspect tree component.
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (BuiltinKeys.POLICY.equals(frame.frameKey())) {
                continue;
            }
            Ref ref = frame.ref();
            if (ref != null) {
                children.add(ref);
            }
        }

        return children;
    }

    public ItemID icon() {
        return DisplayResolver.icon(this);
    }

    public String colorCategory() {
        return DisplayResolver.colorCategory(this);
    }

    public String displaySubtitle() {
        return DisplayResolver.displaySubtitle(this);
    }

    public DisplayInfo displayInfo() {
        return DisplayResolver.displayInfo(this);
    }

    public dev.everydaythings.graph.frame.PresentationConfig resolvedPresentation() {
        return DisplayResolver.resolvedPresentation(this);
    }

    protected dev.everydaythings.graph.frame.SurfaceTemplateComponent getTypeSurfaceTemplate() {
        return DisplayResolver.getTypeSurfaceTemplate(this);
    }

    protected String findDisplayName() {
        return DisplayResolver.findDisplayName(this);
    }

    protected String findTypeName() {
        return DisplayResolver.findTypeName(this);
    }

    protected dev.everydaythings.graph.value.Color findTypeColor() {
        return DisplayResolver.findTypeColor(this);
    }

    protected String findIconText() {
        return DisplayResolver.findIconText(this);
    }

    public String emoji() {
        var pc = resolvedPresentation();
        if (pc != null && pc.glyph() != null) return pc.glyph();
        return findIconText();
    }

    public ItemID targetId() {
        return iid;
    }

    public String displayDetail() {
        return iid != null ? iid.encodeText() : null;
    }

    // ==================================================================================
    // CBOR View Rendering
    // ==================================================================================

    /**
     * Render this item as a compact handle view (CBOR-serializable).
     *
     * <p>Uses ItemSurface at COMPACT mode for consistent rendering.
     */
    public View renderHandle() {
        return View.of(ItemSurface.from(this, SceneMode.COMPACT));
    }

    /**
     * Render this item as an expanded detail view (CBOR-serializable).
     *
     * <p>Uses ItemSurface at FULL mode to show all content.
     */
    public View renderDetail() {
        return View.of(ItemSurface.from(this, SceneMode.FULL));
    }

    // ==================================================================================
    // Token Extraction (for indexing) — delegated to TokenExtractor
    // ==================================================================================

    /**
     * A token entry for indexing this item.
     *
     * @param token the token string (will be normalized by the index)
     * @param weight relevance weight (1.0 = primary name, 0.9 = alias, etc.)
     */
    public record TokenEntry(String token, float weight) {}

    /**
     * Extract tokens for indexing this item.
     *
     * <p>Delegates to {@link TokenExtractor#extractTokens(Item)}.
     * Subclasses may override for type-specific token extraction.
     *
     * @return stream of tokens for this item
     */
    public Stream<TokenEntry> extractTokens() {
        return TokenExtractor.extractTokens(this);
    }

    // ==================================================================================
    // Property Implementation
    // ==================================================================================

    /**
     * Item's top-level properties: components, actions, relations, policy.
     */
    private static final List<String> TOP_LEVEL_PROPERTIES = List.of(
            "components"
    );

    public Object property(String name) {
        return switch (name) {
            case "components" -> frames();
            case "vocabulary" -> vocabulary();
            default -> {
                // Resolve via component lookup (scans by sememe short name)
                Object comp = component(name);
                yield comp;
            }
        };
    }

    public Stream<String> properties() {
        return TOP_LEVEL_PROPERTIES.stream();
    }

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Create a seed item with a deterministic IID.
     *
     * <p>Seed items are created without a librarian. They exist in the SeedStore,
     * which is an in-memory store populated at startup. This constructor is used
     * for statically-defined vocabulary items (Sememes, types, units, etc.).
     *
     * @param iid The item's identity (deterministic, derived from canonical key)
     */
    protected Item(ItemID iid) {
        this.librarian = null;
        this.iid = Objects.requireNonNull(iid, "iid");
        this.dirty = false;  // Seed items are immutable
        state.setOwner(this);
        ensurePolicy();
        populateVocabulary();
    }

    /**
     * Create a fresh new item.
     */
    protected Item(Librarian librarian) {
        this.librarian = librarian;
        this.iid = ItemID.random();
        this.dirty = true;
        state.setOwner(this);
        ensurePolicy();
        populateVocabulary();
    }

    /**
     * Create a fresh item with a specific IID.
     */
    protected Item(Librarian librarian, ItemID iid) {
        this.librarian = librarian;
        this.iid = Objects.requireNonNull(iid, "iid");
        this.dirty = true;
        state.setOwner(this);
        ensurePolicy();
        populateVocabulary();
    }

    /**
     * Hydrate an existing item from a manifest.
     *
     * <p>This constructor populates the EndorsementsTable from the manifest,
     * then calls hydrate() to decode all components and bind fields.
     *
     * @param librarian The librarian (provides store access for content fetching)
     * @param manifest  The manifest describing this item's state
     */
    protected Item(Librarian librarian, Manifest manifest) {
        this.librarian = librarian;
        this.store = librarian.library().primaryStore().orElse(null);
        this.iid = manifest.iid();
        this.current = manifest;
        this.base = manifest.vid();
        this.dirty = false;

        // Set owner on state tables before populating them
        state.setOwner(this);

        // Populate component table from manifest
        for (dev.everydaythings.graph.frame.Frame frame : manifest.components()) {
            frames().add(frame);
        }

        // Hydrate: decode all, bind fields, invoke callbacks
        hydrate();
        ensurePolicy();
        populateVocabulary();
    }

    /**
     * Path-based constructor for materialized items.
     *
     * <p>If path exists (.item/ structure): loads existing item (IID from disk, components loaded)
     * <p>If path doesn't exist: creates new item (random IID, components initialized)
     *
     * <p>This is the preferred constructor for filesystem-backed items like Librarian.
     * It handles both "create at path" and "load from path" automatically.
     *
     * @param path    The filesystem path for this item
     * @param fallbackStore Store to fall back on for at least type lookups
     */
    protected Item(Path path, ItemStore fallbackStore) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(fallbackStore, "fallbackStore");

        this.librarian = null;  // Path-based items don't have a librarian

        // Set owner on state tables before populating them
        state.setOwner(this);

        if (WorkingTreeStore.exists(path)) {
            // LOAD existing
            WorkingTreeStore wts = WorkingTreeStore.open(path, fallbackStore);
            this.store = wts;
            this.iid = wts.iid();
            this.freshBoot = false;
            this.dirty = false;

            // Load component entries from store into table
            for (dev.everydaythings.graph.frame.Frame frame : wts.loadHeadComponents()) {
                frames().add(frame);
            }

            // Hydrate: decode all, bind fields, invoke callbacks
            hydrate();
        } else {
            // CREATE new
            this.iid = ItemID.random();
            this.store = WorkingTreeStore.materialize(path, iid, fallbackStore);
            this.freshBoot = true;
            this.dirty = true;

            // Initialize fresh components (creates defaults, populates EndorsementsTable)
            initializeFreshComponents();

            // Hydrate: bind fields and invoke callbacks (components already in table)
            hydrate();
        }

        // Call hook after all components are initialized
        onFullyInitialized();
    }

    /**
     * In-memory constructor for ephemeral items.
     *
     * <p>Creates a fresh item with in-memory storage. The item is fully functional
     * but data is lost when the JVM exits. Used for testing, demos, and temporary sessions.
     *
     * <p>This constructor:
     * <ul>
     *   <li>Creates a random IID</li>
     *   <li>Uses the provided store for type lookups and storage</li>
     *   <li>Initializes fresh components</li>
     *   <li>Calls hydrate() and onFullyInitialized()</li>
     * </ul>
     *
     * @param store In-memory store for type lookups and content storage
     * @param inMemoryMarker Marker parameter to distinguish from path-based constructor
     */
    protected Item(ItemStore store, InMemoryMarker inMemoryMarker) {
        Objects.requireNonNull(store, "store");

        this.librarian = null;  // In-memory items don't have a librarian reference
        this.store = store;
        this.iid = ItemID.random();
        this.freshBoot = true;
        this.dirty = true;

        // Set owner on state tables before populating them
        state.setOwner(this);

        // Initialize fresh components (creates defaults, populates EndorsementsTable)
        initializeFreshComponents();

        // Hydrate: bind fields and invoke callbacks (components already in table)
        hydrate();

        // Call hook after all components are initialized
        onFullyInitialized();
    }

    /**
     * Marker class to distinguish in-memory constructor from path-based constructor.
     * This avoids signature collision with Item(Path, ItemStore).
     */
    protected static final class InMemoryMarker {
        public static final InMemoryMarker INSTANCE = new InMemoryMarker();
        private InMemoryMarker() {}
    }

    /**
     * Path-based constructor with librarian reference.
     *
     * <p>Combines librarian reference (for principal tracking, library access) with
     * full path-based initialization (vault creation, key generation, component init).
     * Used by User and other items that need both a librarian and a home directory.
     *
     * <p>If path exists (.item/ structure): loads existing item.
     * <p>If path doesn't exist: creates new item with full component initialization.
     *
     * @param librarian The librarian (provides store access and library)
     * @param path      The filesystem path for this item's home directory
     */
    protected Item(Librarian librarian, Path path) {
        Objects.requireNonNull(librarian, "librarian");
        Objects.requireNonNull(path, "path");

        this.librarian = librarian;
        ItemStore fallbackStore = librarian.library().primaryStore().orElse(null);

        // Set owner on state tables before populating them
        state.setOwner(this);

        if (WorkingTreeStore.exists(path)) {
            // LOAD existing
            WorkingTreeStore wts = WorkingTreeStore.open(path, fallbackStore);
            this.store = wts;
            this.iid = wts.iid();
            this.freshBoot = false;
            this.dirty = false;

            // Load component entries from store into table
            for (dev.everydaythings.graph.frame.Frame frame : wts.loadHeadComponents()) {
                frames().add(frame);
            }

            // Hydrate: decode all, bind fields, invoke callbacks
            hydrate();
        } else {
            // CREATE new
            this.iid = ItemID.random();
            this.store = WorkingTreeStore.materialize(path, iid, fallbackStore);
            this.freshBoot = true;
            this.dirty = true;

            // Initialize fresh components (creates defaults, populates EndorsementsTable)
            initializeFreshComponents();

            // Hydrate: bind fields and invoke callbacks (components already in table)
            hydrate();
        }

        // Call hook after all components are initialized
        onFullyInitialized();
    }

    /**
     * In-memory constructor with librarian reference.
     *
     * <p>Combines librarian reference with full in-memory initialization
     * (vault creation, key generation, component init). Used for creating
     * items that need both a librarian and full component initialization
     * but don't need a filesystem path (testing, ephemeral users).
     *
     * @param librarian The librarian (provides store access and library)
     * @param marker    Marker to distinguish from other constructors
     */
    protected Item(Librarian librarian, InMemoryMarker marker) {
        Objects.requireNonNull(librarian, "librarian");

        this.librarian = librarian;
        this.store = librarian.library().primaryStore().orElse(null);
        this.iid = ItemID.random();
        this.freshBoot = true;
        this.dirty = true;

        // Set owner on state tables before populating them
        state.setOwner(this);

        // Initialize fresh components (creates defaults, populates EndorsementsTable)
        initializeFreshComponents();

        // Hydrate: bind fields and invoke callbacks (components already in table)
        hydrate();

        // Call hook after all components are initialized
        onFullyInitialized();
    }

    /**
     * Called after all components are initialized but before the constructor completes.
     *
     * <p>Override in subclasses for post-initialization logic that needs all components
     * ready. This is called at the end of the path-based constructor, after
     * {@link #hydrate()} has decoded components and invoked initComponent() callbacks.
     *
     * <p>Typical uses:
     * <ul>
     *   <li>First-boot initialization (generate keys, commit initial version)</li>
     *   <li>Reload verification (check integrity, refresh state)</li>
     * </ul>
     *
     * <p>Note: Subclass constructor body has NOT yet run when this is called.
     * Only access fields set via superclass constructors or component initialization.
     *
     * <p><b>Important:</b> Subclasses MUST call {@code super.onFullyInitialized()} first
     * to ensure the vocabulary is populated.
     */
    protected void onFullyInitialized() {
        ensurePolicy();
        populateVocabulary();
        populateUnendorsedFrames();
        // Sync pre-initialized field values to EndorsementsTable (handles subclass field initializers)
        syncFieldValuesToTable();
    }

    /**
     * Sync pre-initialized field values to the EndorsementsTable.
     *
     * <p>This handles the case where a subclass has field initializers like:
     * {@code ExpressionComponent typesExpr = ExpressionComponent.subjects(...)}
     *
     * <p>Since superclass constructor runs before subclass field initializers,
     * the EndorsementsTable may have a default instance while the field has the
     * actual desired value. This method syncs them.
     */
    private void syncFieldValuesToTable() {
        if (!freshBoot) return; // Only needed for fresh creation

        for (FrameFieldSpec spec : schema().endorsedFrameFields()) {
            Object fieldValue = spec.getValue(this);
            if (fieldValue == null) continue;

            // Check if EndorsementsTable has a different instance
            var tableValue = frames().getLive(spec.frameKey(), Object.class);
            if (tableValue.isPresent() && tableValue.get() != fieldValue) {
                // Field has a different value - sync it to the table
                frames().setLive(spec.frameKey(), fieldValue);
            }
        }
    }

    /**
     * Populate unendorsed frames in the endorsements table from the cached schema.
     *
     * <p>Called automatically from {@link #onFullyInitialized()}.
     */
    protected void populateUnendorsedFrames() {
        schema().populateUnendorsedFrames(frames(), this);
    }

    /**
     * Populate the vocabulary from the cached schema.
     *
     * <p>Uses {@link ItemSchema#populateVocabulary(Vocabulary, Item)} to add
     * all verbs discovered during class scanning:
     * <ul>
     *   <li>{@code @Verb} methods on this class hierarchy</li>
     *   <li>{@code @Verb} methods on all components (future)</li>
     * </ul>
     *
     * <p>Called automatically from {@link #onFullyInitialized()} for path-based items.
     * Other item types should call this explicitly if they need verb dispatch.
     */
    protected void populateVocabulary() {
        // Clear any existing verbs (in case called multiple times)
        vocabulary().clear();

        // Code layer: @Verb annotations from class hierarchy
        schema().populateVocabulary(vocabulary(), this);

        // User/data layer: EntryVocabulary contributions from frames
        // TODO: vocabulary contributions will move to CONFIG binding on FrameBody (separate track)
    }

    /**
     * Get a component instance by alias, HID text, or handleKey.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Alias index (human-facing names)</li>
     *   <li>Raw "hid:..." reference</li>
     *   <li>Backward compat: hash as handleKey</li>
     * </ol>
     *
     * @param ref The component reference (alias, hid:..., or handleKey)
     * @return The component, or null if not found
     */
    public Object component(String ref) {
        // Scan by sememe short name or canonical string
        for (var entry : frames().entrySet()) {
            FrameKey k = entry.getKey();
            // Match against the full canonical string
            if (ref.equals(k.toCanonicalString())) {
                return component(k);
            }
            // Match against the sememe short name (e.g., "chess" matches cg.sememe:chess)
            ItemID head = k.headSememe();
            if (head != null) {
                String headText = head.encodeText();
                int colon = headText.lastIndexOf(':');
                if (colon >= 0 && ref.equals(headText.substring(colon + 1))) {
                    return component(k);
                }
            }
        }
        return null;
    }

    /**
     * Get a component instance by frame key.
     *
     * @param key The frame key
     * @return The component, or null if not found
     */
    public Object component(FrameKey key) {
        Optional<Object> live = frames().getLive(key);
        if (live.isPresent()) return live.get();

        // Lazy decode from frame when live cache is cold.
        Optional<dev.everydaythings.graph.frame.Frame> frameOpt = frames().getFrame(key);
        if (frameOpt.isEmpty()) return null;

        try {
            Object decoded = decodeComponent(frameOpt.get());
            if (decoded != null) {
                frames().setLive(key, decoded);
                return decoded;
            }
        } catch (Exception e) {
            logger.debug("Lazy component decode failed for {}: {}", key, e.getMessage());
        }

        // Final fallback: read the bound schema field directly.
        // This keeps simple @Frame values visible even if no live decode path exists.
        FrameFieldSpec spec = schema().getFrameField(key);
        if (spec != null) {
            Object fieldValue = spec.getValue(this);
            if (fieldValue != null) {
                frames().setLive(key, fieldValue);
                return fieldValue;
            }
        }
        return null;
    }

    /**
     * Find the Frame that owns a given live component instance.
     *
     * @param componentInstance live component instance
     * @return matching frame, if present
     */
    public Optional<dev.everydaythings.graph.frame.Frame> componentFrame(Object componentInstance) {
        if (componentInstance == null) return Optional.empty();
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (frame.instance() == componentInstance) {
                return Optional.of(frame);
            }
        }
        return Optional.empty();
    }

    /**
     * Get a component instance by alias/handleKey, cast to a specific type.
     *
     * @param ref The component reference (alias, hid:..., or handleKey)
     * @param type The expected component type
     * @return The component, or empty if not found or wrong type
     */
    public <T> Optional<T> component(String ref, Class<T> type) {
        Object value = component(ref);
        if (value == null) return Optional.empty();
        if (type.isInstance(value)) return Optional.of(type.cast(value));
        return Optional.empty();
    }

    /**
     * Dynamically add a component to this item.
     *
     * <p>This enables the core pattern: any Item can host any component.
     * The component's verbs are scanned and added to this item's vocabulary,
     * so they "bubble up" and become dispatchable through this item.
     *
     * <p>The component can be any object with a {@code @Type}
     * annotation — it does not need to implement Component.
     *
     * @param handle    The component handle (e.g., "chess")
     * @param component The component instance
     */
    /**
     * Dynamically add a component with a semantic predicate key.
     *
     * <p>This is the preferred way to attach components to items.
     * The component's verbs are scanned and added to this item's vocabulary.
     *
     * @param predicateId the semantic predicate (Sememe IID) for the frame key
     * @param component   the component instance
     */
    public void endorse(ItemID predicateId, Object component) {
        endorse(predicateId, null, component);
    }

    /**
     * Endorse a frame on this item — adds it to the endorsements table.
     *
     * <p>An endorsed frame is part of this item's identity. When the item
     * commits, endorsed frames affect the manifest hash. The component's
     * verbs are scanned and added to this item's vocabulary.
     *
     * @param predicateId the semantic predicate (Sememe IID) for the frame key
     * @param qualifier   optional qualifier for multiple instances (null for first/only)
     * @param component   the component instance
     */
    public void endorse(ItemID predicateId, String qualifier, Object component) {
        Objects.requireNonNull(predicateId, "predicateId");
        Objects.requireNonNull(component, "component");

        FrameKey key = qualifier != null
                ? FrameKey.of(predicateId, qualifier)
                : FrameKey.of(predicateId);
        ItemID typeId = Item.idOf(component.getClass());
        String handle = resolveDisplayToken(predicateId);
        if (handle == null) {
            String text = predicateId.encodeText();
            int colon = text.lastIndexOf(':');
            handle = colon >= 0 ? text.substring(colon + 1) : text;
        }
        if (qualifier != null) {
            handle = handle + "-" + qualifier;
        }

        // 1. Add frame
        dev.everydaythings.graph.frame.Frame frame = dev.everydaythings.graph.frame.Frame.snapshot(key, typeId, null, true);

        frames().add(frame);

        // 2. Register live instance
        frames().setLive(key, component);

        // 3. Call lifecycle hooks
        if (component instanceof FrameAware fa) {
            fa.onFramePlaced(new FrameContext(this, key, frame));
        }

        // 5. Scan component class for verbs and register them
        List<VerbSpec> verbs = ItemScanner.scanComponentVerbs(component.getClass(), handle);
        for (VerbSpec spec : verbs) {
            vocabulary().add(VerbEntry.componentVerb(spec, handle, component));
        }

        // 6. Register handle as local vocabulary posting
        vocabulary().addLocalPosting(Posting.builder()
                .token(handle)
                .scope(iid())
                .target(iid())
                .weight(1.0f)
                .build());
    }


    // ==================================================================================
    // Verb Dispatch
    // ==================================================================================

    /**
     * Dispatch a command to this item via vocabulary lookup.
     *
     * <p>Resolves the command token to a verb using the item's {@link Vocabulary}:
     * <ul>
     *   <li>With librarian: token → {@link dev.everydaythings.graph.library.dictionary.TokenDictionary}
     *       → Sememe ID → Vocabulary → VerbEntry</li>
     *   <li>Without librarian (seed items): tries direct Sememe ID lookup</li>
     * </ul>
     *
     * <p>This enables language-agnostic dispatch: the same verb can be
     * invoked via "create", "crear", "新建", etc.
     *
     * @param caller  The identity of who is invoking this verb
     * @param command The command token (resolved via TokenDictionary)
     * @param args    The arguments as strings
     * @return The invocation result
     */
    public ActionResult dispatch(ItemID caller, String command, List<String> args) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(args, "args");

        // Build context — resolve caller to Signer if available
        dev.everydaythings.graph.item.user.Signer callerSigner = null;
        if (librarian != null && caller != null) {
            callerSigner = librarian.get(caller, dev.everydaythings.graph.item.user.Signer.class).orElse(null);
        }
        ActionContext ctx = ActionContext.of(caller, callerSigner, this, librarian);

        // Vocabulary dispatch (language-agnostic via TokenDictionary)
        Optional<VerbEntry> verbOpt;
        if (librarian != null) {
            verbOpt = vocabulary().lookupToken(command, librarian);
        } else {
            // Without librarian (seed items), try direct sememe ID lookup
            verbOpt = vocabulary().lookup(ItemID.fromString(command));
        }

        if (verbOpt.isEmpty()) {
            return ActionResult.failure(
                    new IllegalArgumentException("Unknown command: " + command));
        }

        VerbInvoker invoker = new VerbInvoker();
        return invoker.invokeWithStrings(verbOpt.get(), ctx, args);
    }

    /**
     * Dispatch a command to this item (anonymous caller).
     *
     * <p>Convenience method for local dispatch where caller identity isn't needed.
     *
     * @param command The action name
     * @param args    The arguments
     * @return The action result
     */
    public ActionResult dispatch(String command, List<String> args) {
        return dispatch(null, command, args);
    }

    // ==================================================================================
    // Relations
    // ==================================================================================

    /**
     * Get all relations where this item is the subject.
     *
     * <p>Returns a stream of relations that can be filtered by predicate:
     * <pre>{@code
     * // All relations from this item
     * item.relations().forEach(r -> ...);
     *
     * // Filter by predicate
     * item.relations()
     *     .filter(r -> r.predicate().equals(Sememe.WROTE.iid()))
     *     .forEach(r -> ...);
     * }</pre>
     *
     * <p>For more complex queries, use {@code librarian.library().find().from(this)}.
     *
     * @return Stream of frame bodies where this item is a participant
     */
    public Stream<FrameBody> relations() {
        if (librarian == null) {
            return Stream.empty();
        }
        return librarian.library().byItem(this.iid());
    }

    /**
     * Get relations involving this item with a specific predicate.
     *
     * <p>Convenience method for common pattern:
     * <pre>{@code
     * // Get all "authored by" frame bodies involving this item
     * item.relations(Sememe.AUTHORED_BY).forEach(r -> ...);
     * }</pre>
     *
     * @param predicate The predicate to filter by
     * @return Stream of frame bodies involving this item via the predicate
     */
    public Stream<FrameBody> relations(ItemID predicate) {
        if (librarian == null) {
            return Stream.empty();
        }
        return librarian.library().byItemPredicate(this.iid(), predicate);
    }

    /**
     * Create a relation with this item as THEME and another as TARGET.
     *
     * <p>This is the most common relation pattern: this item is what the
     * relation is about, and the target is what it points to.
     * <pre>{@code
     * // This animal IS-A mammal
     * animal.relate(LexicalVocabulary.Hypernym.IID, mammal.iid());
     *
     * // With a literal target
     * item.relate(predicateId, Literal.ofText("some value"));
     * }</pre>
     *
     * @param predicate The predicate (relationship type)
     * @param target The target (value bound to TARGET role)
     * @return The created frame body
     */
    public FrameBody relate(ItemID predicate, BindingTarget target) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(target, "target");

        FrameBody body = FrameBody.of(predicate, iid,
                Map.of(ThematicRole.Goal.IID, target));

        // Sign if we have a signer
        if (this instanceof dev.everydaythings.graph.item.user.Signer signer) {
            FrameRecord.create(body, signer);
        }

        // Store if we have a librarian
        if (librarian != null) {
            librarian.storeFrame(body);
        }

        return body;
    }

    /**
     * Create a relation with this item as THEME and another item as TARGET.
     *
     * <p>Convenience overload for item-to-item relations:
     * <pre>{@code
     * animal.relate(LexicalVocabulary.Hypernym.IID, mammal);
     * }</pre>
     *
     * @param predicate The predicate (relationship type)
     * @param target The target item
     * @return The created frame body
     */
    public FrameBody relate(ItemID predicate, Item target) {
        return relate(predicate, BindingTarget.iid(target.iid()));
    }

    /**
     * Create a relation with this item as THEME and another item (by ID) as TARGET.
     *
     * @param predicate The predicate (relationship type)
     * @param targetId The target item ID
     * @return The created frame body
     */
    public FrameBody relate(ItemID predicate, ItemID targetId) {
        return relate(predicate, BindingTarget.iid(targetId));
    }

    // ==================================================================================
    // Frame Cache — endorsed + unendorsed frames
    // ==================================================================================

    /**
     * Get ALL frames on this item — endorsed (from manifest) plus unendorsed
     * (from index: likes, annotations, trust attestations).
     *
     * <p>Endorsed frames come from the EndorsementsTable. Unendorsed frames
     * are loaded from the FRAME_BY_ITEM index, filtered to those where this
     * item is the theme. Each unendorsed frame carries its attestation
     * records (from RECORD_BY_BODY).
     *
     * @return list of all frames (endorsed first, then unendorsed)
     */
    public List<dev.everydaythings.graph.frame.Frame> allFrames() {
        List<dev.everydaythings.graph.frame.Frame> result = new ArrayList<>(frames().values());
        result.addAll(unendorsedFrames());
        return Collections.unmodifiableList(result);
    }

    /**
     * Get unendorsed frames on this item — frames stored independently
     * (not in the manifest) where this item is the theme.
     *
     * <p>These are frames like likes, annotations, trust attestations,
     * and moderation signals. Each is loaded from the FRAME_BY_ITEM index
     * with its attestation records from RECORD_BY_BODY.
     *
     * @return list of unendorsed frames with records populated
     */
    public List<dev.everydaythings.graph.frame.Frame> unendorsedFrames() {
        if (librarian == null) return List.of();

        // Collect endorsed body hashes to exclude
        Set<ContentID> endorsedHashes = new HashSet<>();
        for (dev.everydaythings.graph.frame.Frame f : frames()) {
            if (f.bodyHash() != null) endorsedHashes.add(f.bodyHash());
        }

        var library = librarian.library();
        List<dev.everydaythings.graph.frame.Frame> result = new ArrayList<>();

        library.framesByItem(iid).forEach(ref -> {
            // Skip frames already endorsed in the manifest
            if (endorsedHashes.contains(ref.bodyHash())) return;

            // Load the body
            library.loadFrameBody(ref.bodyHash()).ifPresent(body -> {
                // Only include frames where this item is the theme
                if (!iid.equals(body.homeId())) return;

                dev.everydaythings.graph.frame.Frame frame =
                        dev.everydaythings.graph.frame.Frame.fromBody(body);
                frame.setOwner(this);

                // Load attestation records
                List<FrameRecord> records = library.loadRecords(body.hash());
                if (!records.isEmpty()) {
                    frame.setRecords(records);
                }

                result.add(frame);
            });
        });

        return Collections.unmodifiableList(result);
    }

    /**
     * Get unendorsed frames on this item with a specific predicate.
     *
     * @param predicate the predicate to filter by
     * @return list of unendorsed frames matching the predicate
     */
    public List<dev.everydaythings.graph.frame.Frame> unendorsedFrames(ItemID predicate) {
        if (librarian == null) return List.of();

        Set<ContentID> endorsedHashes = new HashSet<>();
        for (dev.everydaythings.graph.frame.Frame f : frames()) {
            if (f.bodyHash() != null) endorsedHashes.add(f.bodyHash());
        }

        var library = librarian.library();
        List<dev.everydaythings.graph.frame.Frame> result = new ArrayList<>();

        library.framesByItemPredicate(iid, predicate).forEach(ref -> {
            if (endorsedHashes.contains(ref.bodyHash())) return;

            library.loadFrameBody(ref.bodyHash()).ifPresent(body -> {
                if (!iid.equals(body.homeId())) return;

                dev.everydaythings.graph.frame.Frame frame =
                        dev.everydaythings.graph.frame.Frame.fromBody(body);
                frame.setOwner(this);

                List<FrameRecord> records = library.loadRecords(body.hash());
                if (!records.isEmpty()) {
                    frame.setRecords(records);
                }

                result.add(frame);
            });
        });

        return Collections.unmodifiableList(result);
    }

    // ==================================================================================
    // Config Cascade — frame → item → predicate resolution
    // ==================================================================================

    /**
     * Resolve a config binding for a frame by walking the cascade:
     * <ol>
     *   <li><b>Frame binding</b> — compound binding {@code (CONFIG, qualifier)} on the frame body</li>
     *   <li><b>Item frame</b> — frame with key {@code (qualifier)} on this item</li>
     *   <li><b>Predicate frame</b> — frame with key {@code (qualifier)} on the predicate sememe item</li>
     * </ol>
     *
     * <p>Most frames carry NO config bindings — they inherit from the predicate type.
     * Overrides are only needed when a specific frame or item wants to customize.
     *
     * @param frame     the frame to resolve config for
     * @param qualifier the config qualifier (e.g., Presentation or Vocabulary role IID)
     * @return the raw payload bytes from the first matching binding/frame, or null
     */
    public byte[] resolveConfig(dev.everydaythings.graph.frame.Frame frame, ItemID qualifier) {
        // Step 1: check config map, then compound binding on this frame's body
        if (frame != null && frame.body() != null) {
            // New path: direct qualifier key in config map
            Binding configBinding = frame.body().configBinding(qualifier);
            if (configBinding != null && configBinding.target() instanceof Literal lit) {
                return lit.payload();
            }
            // Backward compat: compound (CONFIG, qualifier) in body bindings
            Binding binding = frame.body().getCompoundBinding(
                    ThematicRole.Config.IID, qualifier);
            if (binding != null && binding.target() instanceof Literal lit) {
                return lit.payload();
            }
        }

        // Step 2: check manifest config map, then (qualifier) frame on this item
        Manifest mf = current();
        if (mf != null) {
            Binding mb = mf.configBinding(qualifier);
            if (mb != null && mb.target() instanceof Literal lit) {
                return lit.payload();
            }
        }
        // Fall back to (qualifier) frame on this item (backward compat)
        FrameKey itemKey = FrameKey.of(qualifier);
        Optional<dev.everydaythings.graph.frame.Frame> itemFrame = frames().getFrame(itemKey);
        if (itemFrame.isPresent()) {
            dev.everydaythings.graph.frame.Frame f = itemFrame.get();
            if (f.body() != null) {
                // The frame's content IS the config
                BindingTarget topic = f.body().binding(ThematicRole.Topic.IID);
                if (topic instanceof Literal lit) return lit.payload();
                // Or the whole body may encode the config
                return f.body().encodeBinary(dev.everydaythings.graph.Canonical.Scope.RECORD);
            }
        }

        // Step 3: check for (qualifier) frame on the predicate's sememe item
        if (frame != null && frame.type() != null && librarian != null) {
            Optional<Item> predicateItem = librarian.get(frame.type(), Item.class);
            if (predicateItem.isPresent()) {
                Optional<dev.everydaythings.graph.frame.Frame> predFrame =
                        predicateItem.get().frames().getFrame(itemKey);
                if (predFrame.isPresent() && predFrame.get().body() != null) {
                    BindingTarget topic = predFrame.get().body()
                            .binding(ThematicRole.Topic.IID);
                    if (topic instanceof Literal lit) return lit.payload();
                    return predFrame.get().body()
                            .encodeBinary(dev.everydaythings.graph.Canonical.Scope.RECORD);
                }
            }
        }

        return null;
    }

    /**
     * Resolve presentation config for a frame via cascade: frame → item → predicate.
     *
     * @param frame the frame to resolve presentation for
     * @return raw presentation payload bytes, or null if none found at any level
     */
    public byte[] resolvePresentation(dev.everydaythings.graph.frame.Frame frame) {
        return resolveConfig(frame, ThematicRole.Presentation.IID);
    }

    /**
     * Resolve vocabulary config for a frame via cascade: frame → item → predicate.
     *
     * @param frame the frame to resolve vocabulary for
     * @return raw vocabulary payload bytes, or null if none found at any level
     */
    public byte[] resolveVocabulary(dev.everydaythings.graph.frame.Frame frame) {
        return resolveConfig(frame, ThematicRole.Vocabulary.IID);
    }

    /**
     * Resolve general config for a frame via cascade: frame → item → predicate.
     *
     * <p>For general config, step 1 looks for a simple {@code (CONFIG)} binding
     * (not a compound key), then walks to item and predicate.
     *
     * @param frame the frame to resolve config for
     * @return raw config payload bytes, or null if none found at any level
     */
    public byte[] resolveGeneralConfig(dev.everydaythings.graph.frame.Frame frame) {
        // Step 1: (CONFIG) simple binding on this frame's body
        if (frame != null && frame.body() != null) {
            byte[] payload = frame.body().configPayload();
            if (payload != null) return payload;
        }

        // Steps 2-3: (CONFIG) frame on this item, then on predicate
        return resolveConfig(frame, ThematicRole.Config.IID);
    }

    // ==================================================================================
    // Path-Based Component Management
    // ==================================================================================

    /**
     * Ensure the item-level policy binding exists.
     *
     * <p>Policy is item-level configuration — it will move to the manifest's
     * config map when dual-bindings are implemented. For now, stored as a
     * semantic frame in the endorsements table.
     *
     * <p>Called in every constructor after {@code state.setOwner(this)}.
     */
    private void ensurePolicy() {
        if (!frames().hasLive(BuiltinKeys.POLICY)) {
            ItemID typeId = Item.idOf(PolicySet.class);
            dev.everydaythings.graph.frame.Frame frame =
                    dev.everydaythings.graph.frame.Frame.snapshot(BuiltinKeys.POLICY, typeId, null, true);
            frames().add(frame);
            frames().setLive(BuiltinKeys.POLICY, new PolicySet());
        }
    }

    /**
     * Initialize fresh components for a newly created item.
     *
     * <p>Uses the cached {@link ItemSchema} to iterate over all @Item.ComponentField
     * annotations and create each component. For local resource components, opens at
     * the mount path. For other components, creates a default instance.
     *
     * <p>This method populates the EndorsementsTable with both frames (metadata) and
     * live instances. Field binding and initComponent() callbacks are handled
     * by hydrate() which is called afterward.
     */
    private void initializeFreshComponents() {
        ItemSchema itemSchema = schema();

        // Create instances for all Component-typed @ComponentField fields
        // (Non-Component fields like SigningPublicKey are handled during commit)
        for (FrameFieldSpec spec : itemSchema.endorsedFrameFields()) {
            // Skip fields that don't have @Implements annotation
            if (!spec.fieldType().isAnnotationPresent(Implements.class)) {
                continue;
            }

            // Use the field's declared type directly (not lookup by type ID)
            // This avoids issues with @Inherited annotations where subclasses
            // share the same type ID as their abstract parent
            Class<?> type = spec.fieldType();

            Object instance;
            dev.everydaythings.graph.frame.Frame frame;

            String alias = spec.canonicalKeyString();

            if (spec.localOnly()) {
                if (store != null && store.root() != null) {
                    // Local resource with filesystem: open at mount path
                    Path componentPath = store.root().resolve(spec.path());
                    instance = CreationScanner.openPathBased(type, componentPath);
                } else {
                    // Local resource but in-memory mode: create default in-memory instance
                    instance = CreationScanner.createDefault(type)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Cannot create default in-memory instance of local resource: " + type.getName()));
                }
                frame = dev.everydaythings.graph.frame.Frame.localResource(spec.frameKey(), spec.type(), spec.identity());
                // alias removed — display names resolved via TokenDictionary
            } else {
                // Regular component: use pre-initialized field value if present, else create default
                Object existingValue = spec.getValue(this);
                if (existingValue != null && type.isInstance(existingValue)) {
                    // Field was pre-initialized (e.g., ExpressionComponent with pattern)
                    instance = existingValue;
                } else {
                    // Try createDefault first, fall back to instantiateComponent
                    Optional<?> defaultOpt = CreationScanner.createDefault(type);
                    instance = defaultOpt.isPresent()
                            ? defaultOpt.get()
                            : CreationScanner.instantiate(type);
                }
                // Create appropriate frame type based on component kind
                if (spec.stream()) {
                    // Stream component: starts with empty heads, content added via append
                    frame = dev.everydaythings.graph.frame.Frame.stream(spec.frameKey(), spec.type(), List.of(), spec.identity());
                    // alias removed — display names resolved via TokenDictionary
                } else {
                    // Snapshot component: CID computed during commit, use placeholder for now
                    frame = dev.everydaythings.graph.frame.Frame.snapshot(spec.frameKey(), spec.type(), null, spec.identity());
                    // alias removed — display names resolved via TokenDictionary
                }
            }

            // Add mount for path-based components
            if (spec.hasMountPath()) {
                String mountPath = "/" + spec.path();  // Convert filesystem path to presentation path
                frames().addMount(spec.frameKey(), new Mount.PathMount(mountPath));
            }

            // Add to table: both frame and live instance
            frames().add(frame);
            frames().setLive(spec.frameKey(), instance);

        }

        // Save component metadata to store and materialize mount directories
        if (store != null) {
            store.runInWriteTransaction(tx -> {
                store.saveHeadComponents(frames(), tx);
            });
            if (store instanceof WorkingTreeStore wts) {
                wts.materializeMountPaths(frames());
            }
        }
    }

    // ==================================================================================
    // Edit / Commit Lifecycle
    // ==================================================================================

    /**
     * Begin editing a new version. Call commit() when done.
     */
    public void edit() {
        if (editing) {
            throw new IllegalStateException("Already in edit mode");
        }
        editing = true;
        dirty = true;
    }

    /**
     * Commit the current state as a new version.
     *
     * @param signer The signer to sign the manifest (required)
     * @return The new ContentID
     */
    public ContentID commit(Signer signer) {
        return commit(signer, null);
    }

    /**
     * Commit with encryption: sign and version this item, encrypting frames per the context.
     *
     * @param signer            The signer to sign the manifest
     * @param encryptionContext  Encryption policy (null for no encryption)
     * @return The new ContentID
     */
    public ContentID commit(Signer signer, dev.everydaythings.graph.crypt.EncryptionContext encryptionContext) {
        Objects.requireNonNull(signer, "signer required for commit");
        logger.debug("Committing item {} (type={})", iid, getClass().getSimpleName());

        // Start edit mode if not already
        if (!editing) {
            edit();
        }

        // Populate state tables from annotated fields
        scanAndBindFields(encryptionContext);

        // Build endorsements from EndorsementsTable for manifest serialization
        state.buildEndorsements();

        // Build manifest with the item's state
        Manifest manifest = Manifest.builder()
                .iid(iid)
                .implementation(Manifest.javaImplementation(this.getClass()))
                .parents(base != null ? List.of(base) : List.of())
                .state(state)
                .build();
        manifest.sign(signer);

        // Store manifest and content
        if (librarian != null) {
            storeManifest(manifest);
        }

        // Materialize mount directories for working tree items
        if (store instanceof WorkingTreeStore wts) {
            wts.materializeMountPaths(frames());
        }

        // Update state
        this.current = manifest;
        this.base = manifest.vid();
        this.versions.add(manifest.vid());
        this.dirty = false;
        this.editing = false;

        logger.debug("Committed item {} -> vid={}", iid, manifest.vid());
        return manifest.vid();
    }

    /**
     * Persist the current item state without creating a new version.
     *
     * <p>This saves the working state of the item - component content, metadata,
     * and mounts - without creating a signed version. Use this for:
     * <ul>
     *   <li>Auto-save / periodic persistence</li>
     *   <li>Saving work-in-progress before committing</li>
     *   <li>Items that don't need version history</li>
     * </ul>
     *
     * <p>Unlike {@link #commit(Signer)}, this method:
     * <ul>
     *   <li>Does NOT require a signer</li>
     *   <li>Does NOT create a new ContentID</li>
     *   <li>Does NOT update the manifest</li>
     *   <li>DOES save all component content to the store</li>
     *   <li>DOES update component and mount metadata</li>
     * </ul>
     *
     * <p>Requires a store - items without a backing store cannot persist.
     *
     * @throws IllegalStateException if no store is available
     */
    public void persist() {
        if (store == null) {
            throw new IllegalStateException("Cannot persist: no store available");
        }

        logger.debug("Persisting item {} (type={})", iid, getClass().getSimpleName());

        store.runInWriteTransaction(tx -> {
            // Persist each component
            for (dev.everydaythings.graph.frame.Frame frame : frames()) {
                persistComponent(frame, store, tx);
            }

            // Save component metadata
            store.saveHeadComponents(frames(), tx);
        });

        // Materialize mount directories for working tree items
        if (store instanceof WorkingTreeStore wts) {
            wts.materializeMountPaths(frames());
        }

        // Clear dirty flag
        this.dirty = false;

        logger.debug("Persisted item {}", iid);
    }

    /**
     * Persist a single component's content.
     */
    private void persistComponent(dev.everydaythings.graph.frame.Frame frame, ItemStore targetStore,
                                  dev.everydaythings.graph.library.WriteTransaction tx) {
        FrameBody body = frame.body();
        if (body == null) return;

        // External/local resources are managed by the component itself - nothing to persist
        if (body.isExternal()) return;

        // References point to another item - no content bytes to persist
        if (body.isReference()) return;

        // Get live instance
        Optional<?> liveOpt = frames().getLive(frame.frameKey(), Object.class);
        if (liveOpt.isEmpty()) return;

        Object live = liveOpt.get();
        byte[] bytes;

        if (live instanceof Canonical canonical) {
            bytes = canonical.encodeBinary(Canonical.Scope.RECORD);
        } else if (ItemSchema.isSimpleSerializableType(live)) {
            bytes = ItemSchema.encodeSimpleValue(live);
        } else {
            return; // Unknown type
        }

        // Store content
        targetStore.persistContent(bytes, tx);
    }

    /**
     * Generate a deterministic manifest for seed items.
     *
     * <p>Seed items need manifests that are identical across all machines.
     * This is achieved by:
     * <ul>
     *   <li>Fixed timestamp of 0 (epoch)</li>
     *   <li>No signature (seed items are code-defined, not user-signed)</li>
     *   <li>Deterministic IID (derived from canonical key)</li>
     *   <li>Deterministic content hashes (same fields → same hash)</li>
     * </ul>
     *
     * <p>Note: This does NOT update the item's state (current, base, versions).
     * It just generates the manifest for storage during bootstrap.
     *
     * @return A deterministic manifest for this seed item
     */
    public Manifest generateSeedManifest() {
        // Start edit mode if not already
        if (!editing) {
            edit();
        }

        // Populate state tables from annotated fields (no encryption for save)
        scanAndBindFields(null);

        // Build endorsements from EndorsementsTable for manifest serialization
        state.buildEndorsements();

        // Build without signature (seed items are code-defined, deterministic)
        Manifest manifest = Manifest.builder()
                .iid(iid)
                .implementation(Manifest.javaImplementation(this.getClass()))
                .parents(base != null ? List.of(base) : List.of())
                .state(state)
                .build();

        // Reset edit mode (don't persist state changes)
        this.editing = false;

        return manifest;
    }

    /**
     * Abort current edit, discarding uncommitted changes.
     */
    public void abortEdit() {
        editing = false;
        // Note: in-memory field changes are not reverted
    }

    /**
     * Encode a component field value by handle.
     *
     * <p>Used during seed import to get the encoded bytes for storage.
     * This re-encodes the field value (deterministic, same hash as manifest).
     *
     * @param handle The component handle to encode
     * @return Encoded bytes, or null if handle not found
     */
    public byte[] encodeComponentValue(FrameKey key) {
        // Find the field spec with this key from cached schema
        for (FrameFieldSpec spec : schema().endorsedFrameFields()) {
            if (!spec.frameKey().equals(key)) continue;

            Object value = spec.getValue(this);
            if (value == null) return null;

            if (value instanceof Canonical canonical) {
                return canonical.encodeBinary(Canonical.Scope.RECORD);
            } else if (ItemSchema.isSimpleSerializableType(value)) {
                return ItemSchema.encodeSimpleValue(value);
            }
            return null;
        }
        return null;
    }

    /**
     * Mark item as dirty (has uncommitted changes).
     */
    protected void markDirty() {
        this.dirty = true;
    }

    // ==================================================================================
    // Field Binding (Annotation Processing)
    // ==================================================================================

    /**
     * Populate state tables from annotated fields for commit.
     *
     * <p>Since Manifest now embeds ItemState directly, we just populate the tables.
     * Delegates to {@link ItemSchema} for the actual field processing.
     */
    private void scanAndBindFields(dev.everydaythings.graph.crypt.EncryptionContext encryptionContext) {
        // Payload storage function - stores bytes via librarian and returns CID
        java.util.function.Function<byte[], ContentID> storePayload = (librarian != null)
                ? bytes -> { librarian.storePayload(bytes); return ContentID.of(bytes); }
                : null;

        // Payload storage as Consumer for component fields (legacy signature)
        java.util.function.Consumer<byte[]> storePayloadConsumer = (librarian != null) ? librarian::storePayload : null;

        // Frame body storage function - stores canonical frame bodies via librarian
        java.util.function.Consumer<FrameBody> storeFrame = (librarian != null) ? librarian::storeFrame : null;

        // Key resolver for per-frame EncryptionPolicy (ItemID → EncryptionPublicKeys)
        java.util.function.Function<ItemID, java.util.List<dev.everydaythings.graph.crypt.EncryptionPublicKey>> keyResolver =
                (librarian != null) ? librarian::resolveEncryptionKeys : iid -> java.util.List.of();

        // Bind component fields (encode and add to content table, with optional encryption + frame body)
        schema().bindComponentFieldsForCommit(this, frames(), storePayloadConsumer, encryptionContext, storeFrame, keyResolver);

        // Bind unendorsed frame fields (create frame bodies, store and index)
        schema().bindUnendorsedFramesForCommit(this, frames(), storePayload, storeFrame);

    }

    // ==================================================================================
    // Storage Operations
    // ==================================================================================

    private void storeManifest(Manifest manifest) {
        byte[] body = manifest.encodeBinary(Canonical.Scope.RECORD);
        librarian.storeManifest(body);
    }

    // ==================================================================================
    // Hydration (Loading from Manifest)
    // ==================================================================================

    /**
     * Unified hydration: decode components from store, populate EndorsementsTable, bind fields.
     *
     * <p>The EndorsementsTable is the source of truth for what an item contains.
     * Fields (@ComponentField) are optional developer ergonomics that bind to
     * entries in the table.
     *
     * <p>Flow:
     * <ol>
     *   <li>For each Frame in the table, decode the content from the store</li>
     *   <li>Store the live instance in EndorsementsTable</li>
     *   <li>Bind matching @ComponentField fields</li>
     *   <li>Invoke initComponent() callbacks on all Component instances</li>
     * </ol>
     *
     * <p>Components are the ONLY non-Canonical things:
     * <ul>
     *   <li>Component types → Component.decode() or Component.openPathBased()</li>
     *   <li>Everything else → Canonical.decodeBinary()</li>
     * </ul>
     */
    protected void hydrate() {
        // Phase 0: Resolve endorsements into EndorsementsTable entries (new manifest format)
        resolveEndorsements();

        // Phase 1: Decode components that don't already have live instances
        // (Fresh items may already have live instances from initializeFreshComponents())
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (frames().hasLive(frame.frameKey())) {
                continue;  // Already decoded/created
            }
            try {
                Object instance = decodeComponent(frame);
                if (instance != null) {
                    frames().setLive(frame.frameKey(), instance);
                }
            } catch (Exception e) {
                logger.warn("Failed to decode component {} (type {}): {}",
                        frame.frameKey(), frame.type(), e.getMessage());
            }
        }

        // Phase 2: Bind @ComponentField fields from EndorsementsTable
        bindFieldsFromTable();

        // Phase 3: Frame hydration — notify frame instances of their context
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            Object instance = frames().getLive(frame.frameKey()).orElse(null);
            if (instance == null) continue;
            if (instance instanceof FrameAware fa) {
                fa.onFramePlaced(new FrameContext(this, frame.frameKey(), frame));
            }
        }
    }

    /**
     * Resolve endorsements from the manifest into EndorsementsTable entries.
     *
     * <p>When a manifest was decoded from the new endorsement format, the
     * EndorsementsTable is empty and the endorsements list contains the frame
     * metadata. This method fetches each FrameBody by its body hash,
     * reconstructs a Frame from it, and populates the EndorsementsTable.
     *
     * <p>For old-format manifests (EndorsementsTable already populated), this
     * method is a no-op.
     */
    private void resolveEndorsements() {
        if (state == null) return;
        java.util.List<dev.everydaythings.graph.frame.FrameEndorsement> endorsements = state.endorsements();
        if (endorsements.isEmpty()) return;
        if (!frames().isEmpty()) return; // Already have entries (old format or fresh item)

        for (dev.everydaythings.graph.frame.FrameEndorsement endorsement : endorsements) {
            try {
                dev.everydaythings.graph.frame.Frame frame = resolveEndorsement(endorsement);
                if (frame != null) {
                    frames().add(frame, endorsement.mounts());
                }
            } catch (Exception e) {
                logger.warn("Failed to resolve endorsement {}: {}",
                        endorsement.key(), e.getMessage());
            }
        }
    }

    /**
     * Resolve a single endorsement to a Frame by fetching its FrameBody.
     */
    private dev.everydaythings.graph.frame.Frame resolveEndorsement(dev.everydaythings.graph.frame.FrameEndorsement endorsement) {
        if (librarian == null) {
            // No librarian — can't fetch bodies. Create a minimal frame.
            return new dev.everydaythings.graph.frame.Frame(endorsement.key(),
                    ItemID.fromString("cg.sememe:unknown"), null,
                    endorsement.bodyHash(), true);
        }

        // Fetch the FrameBody from the object store
        java.util.Optional<byte[]> bodyBytes = fetchContent(endorsement.bodyHash());
        if (bodyBytes.isEmpty()) {
            logger.warn("FrameBody not found for endorsement {}: bodyHash={}",
                    endorsement.key(), endorsement.bodyHash());
            return null;
        }

        FrameBody body = FrameBody.fromCborTree(
                com.upokecenter.cbor.CBORObject.DecodeFromBytes(bodyBytes.get()));
        if (body == null) {
            logger.warn("Failed to decode FrameBody for endorsement {}", endorsement.key());
            return null;
        }

        return dev.everydaythings.graph.frame.Frame.fromFrameBody(body, endorsement);
    }

    /**
     * Decode a component from its Frame.
     *
     * @param frame The frame metadata
     * @return The decoded instance, or null if content unavailable
     */
    @SuppressWarnings("unchecked")
    private Object decodeComponent(dev.everydaythings.graph.frame.Frame frame) {
        FrameBody body = frame.body();
        if (body == null) return null;

        // Reference → resolve the target item via librarian
        if (body.isReference()) {
            return resolveReference(frame);
        }

        // External/local resource → open at mount path (requires filesystem)
        if (body.isExternal()) {
            return openLocalResource(frame);
        }

        // Snapshot → fetch content by CID (decrypt if encrypted)
        if (body.hasContent()) {
            Optional<byte[]> bytesOpt;
            if (body.isEncrypted()) {
                bytesOpt = fetchAndDecrypt(frame);
            } else {
                bytesOpt = fetchContent(body.contentCid());
            }
            if (bytesOpt.isEmpty()) {
                return null;
            }
            return decodeContent(frame, bytesOpt.get());
        }

        // Stream → create a fresh instance via factory
        if (body.isStream()) {
            return createStreamComponent(frame);
        }

        return null;
    }

    /**
     * Create a fresh instance of a stream component.
     *
     * @param frame The stream component frame
     * @return A fresh component instance, or null if the type can't be created
     */
    @SuppressWarnings("unchecked")
    private Object createStreamComponent(dev.everydaythings.graph.frame.Frame frame) {
        Optional<Class<?>> impl = findImplementation(frame.type());
        if (impl.isEmpty()) {
            logger.debug("createStreamComponent() - no implementation for type {}", frame.type());
            return null;
        }
        Class<?> cls = impl.get();
        Optional<?> instance = CreationScanner.createDefault(cls);
        if (instance.isPresent()) {
            return instance.get();
        }
        // Fall back to instantiate via factory/constructor
        return CreationScanner.instantiate(cls);
    }

    /**
     * Resolve a reference frame to the target item.
     *
     * @param frame The reference frame
     * @return The resolved Item, or null if unavailable
     */
    private Object resolveReference(dev.everydaythings.graph.frame.Frame frame) {
        if (librarian == null) {
            return null;
        }
        ItemID target = frame.body().referenceTargetId();
        Optional<Item> resolved = librarian.get(target, Item.class);
        if (resolved.isEmpty()) {
            logger.debug("Reference target not found: {} (handle={})",
                    target, frame.displayToken());
        }
        return resolved.orElse(null);
    }

    /**
     * Open a local resource component at its mount path.
     *
     * @param frame The frame (must be local resource)
     * @return The opened component, or null if no filesystem access
     */
    @SuppressWarnings("unchecked")
    private Object openLocalResource(dev.everydaythings.graph.frame.Frame frame) {
        // Need filesystem access to open local resources
        Path root = (store != null) ? store.root() : null;
        if (root == null) {
            return null;
        }

        // Find mount path for this handle
        Path mountPath = resolveMountPath(frame.frameKey());
        if (mountPath == null) {
            return null;
        }

        // Find implementation class
        Optional<Class<?>> implOpt = findImplementation(frame.type());
        if (implOpt.isEmpty()) {
            return null;
        }

        return CreationScanner.openPathBased(implOpt.get(), mountPath);
    }

    /**
     * Fetch content bytes by CID from the store or librarian.
     */
    private Optional<byte[]> fetchContent(ContentID cid) {
        // Try store first (for path-based items)
        if (store != null) {
            Optional<byte[]> bytes = store.content(cid);
            if (bytes.isPresent()) {
                return bytes;
            }
        }

        // Fall back to librarian (for manifest-based items)
        if (librarian != null) {
            return librarian.content(cid);
        }

        return Optional.empty();
    }

    /**
     * Fetch encrypted content, decrypt via the librarian's vault, and return plaintext bytes.
     *
     * <p>Fetches the Tag 10 envelope by {@code encryptedCid}, decrypts using the
     * librarian's encryption key, and verifies the plaintext matches {@code snapshotCid}.
     * If the librarian has no encryption key or decryption fails, falls back to trying
     * the plaintext CID directly (in case the content was stored cleartext locally).
     */
    private Optional<byte[]> fetchAndDecrypt(dev.everydaythings.graph.frame.Frame frame) {
        FrameBody body = frame.body();
        ContentID encCid = body.encryptedCid();
        ContentID plainCid = body.contentCid();

        // Fetch the encrypted envelope bytes
        Optional<byte[]> envelopeBytes = fetchContent(encCid);
        if (envelopeBytes.isEmpty()) {
            // Fallback: try plaintext CID (content might have been decrypted locally)
            return fetchContent(plainCid);
        }

        // Try to decrypt if the librarian has an encryption key
        if (librarian != null && librarian.encryptionPublicKey() != null) {
            try {
                com.upokecenter.cbor.CBORObject cbor = com.upokecenter.cbor.CBORObject.DecodeFromBytes(envelopeBytes.get());
                dev.everydaythings.graph.crypt.EncryptedEnvelope envelope =
                        dev.everydaythings.graph.crypt.EncryptedEnvelope.fromCborTree(cbor);
                byte[] myKeyId = librarian.encryptionPublicKey().keyId();
                byte[] plaintext = librarian.decryptEnvelope(envelope, myKeyId);
                return Optional.of(plaintext);
            } catch (Exception e) {
                logger.debug("Failed to decrypt frame {} (encryptedCid={}): {}",
                        frame.frameKey(), encCid, e.getMessage());
                // Fallback: try plaintext CID
                return fetchContent(plainCid);
            }
        }

        // No vault available — try plaintext CID as fallback
        return fetchContent(plainCid);
    }

    /**
     * Decode content bytes into an instance based on type.
     *
     * <p>Priority: FrameBody, then primitive types, then Canonical types via universal decoder.
     */
    @SuppressWarnings("unchecked")
    private Object decodeContent(dev.everydaythings.graph.frame.Frame frame, byte[] bytes) {
        ItemID typeId = frame.type();
        // FrameBody entries → decode directly (FrameBody is Canonical, not a Component)
        if (FrameBody.TYPE_ID.equals(typeId)) {
            return Canonical.decodeBinary(bytes, FrameBody.class, Canonical.Scope.RECORD);
        }

        // Primary path: typeId -> IMPLEMENTED_BY -> Java class
        Optional<Class<?>> impl = findImplementation(typeId);
        if (impl.isPresent()) {
            Class<?> cls = impl.get();
            CBORObject node = CBORObject.DecodeFromBytes(bytes);
            return Canonical.decodeIntoType(cls, cls, node, Canonical.Scope.RECORD);
        }

        // Fallback for intrinsic schema-backed fields:
        // decode using the declared field type.
        FrameFieldSpec frameSpec = schema().getFrameField(frame.frameKey());
        if (frameSpec != null) {
            CBORObject node = CBORObject.DecodeFromBytes(bytes);
            return Canonical.decodeIntoType(frameSpec.fieldType(), frameSpec.fieldType(), node, Canonical.Scope.RECORD);
        }

        return null;
    }

    /**
     * Find the implementing Java class for a type ID.
     *
     * <p>Delegates to store or librarian's unified findImplementation method.
     */
    private Optional<Class<?>> findImplementation(ItemID typeId) {
        if (store != null) {
            return store.findImplementation(typeId);
        }
        if (librarian != null) {
            return librarian.library().findImplementation(typeId);
        }
        return Optional.empty();
    }

    /**
     * Find Canonical implementation class for a type ID.
     */
    @SuppressWarnings("unchecked")
    private Optional<Class<? extends Canonical>> findCanonicalImplementation(ItemID typeId) {
        return findImplementation(typeId)
                .filter(Canonical.class::isAssignableFrom)
                .map(c -> (Class<? extends Canonical>) c);
    }

    /**
     * Resolve the mount path for a component handle.
     *
     * <p>Looks up the path from cached schema or mount table.
     * Priority: schema path > mount table > null
     */
    private Path resolveMountPath(FrameKey key) {
        Path root = (store != null) ? store.root() : null;
        if (root == null) {
            return null;
        }

        // Check cached schema for path
        for (FrameFieldSpec spec : schema().endorsedFrameFields()) {
            if (spec.frameKey().equals(key) && spec.hasMountPath()) {
                return root.resolve(spec.path());
            }
        }

        // Check content table for runtime mounts
        return frames().pathForKey(key)
                .map(mountPath -> {
                    // Convert presentation path to filesystem path
                    // e.g., "/documents" -> ".documents" (leading dot for hidden)
                    String fsPath = mountPath.equals("/") ? "" : mountPath.substring(1);
                    return root.resolve(fsPath);
                })
                .orElse(null);
    }

    /**
     * Bind @ComponentField fields from the EndorsementsTable's live instances.
     *
     * <p>Uses {@link ItemSchema#bindFieldsFromTable(Item, EndorsementsTable)} to inject
     * live instances into their corresponding fields.
     *
     * <p>For simple types (String, int, etc.) that weren't decoded during hydrate(),
     * fetch and decode inline using the field's declared type.
     */
    private void bindFieldsFromTable() {
        // Use schema for efficient field binding
        schema().bindFieldsFromTable(this, frames());

        // Handle simple types that weren't decoded - decode and cache
        for (FrameFieldSpec spec : schema().endorsedFrameFields()) {
            FrameKey key = spec.frameKey();

            // Skip if already has a live instance in the table
            if (frames().hasLive(key)) continue;

            // Try to decode from stored content
            Optional<dev.everydaythings.graph.frame.Frame> frameOpt = frames().getFrame(key);
            if (frameOpt.isPresent() && frameOpt.get().body() != null && frameOpt.get().body().hasContent()) {
                Optional<byte[]> bytesOpt = fetchContent(frameOpt.get().body().contentCid());
                if (bytesOpt.isPresent()) {
                    Object value = ItemSchema.decodeSimpleValue(spec.field(), bytesOpt.get());
                    if (value != null) {
                        spec.setValue(this, value);
                        frames().setLive(key, value);
                    }
                }
            }
        }
    }

    // ==================================================================================
    // Actions
    // ==================================================================================

    /**
     * Create a new instance of this item's type.
     *
     * <p>This action treats every item as a potential "template" for creating
     * new instances. When invoked on a type item (seed), it creates a new
     * instance of that type. When invoked on a regular item, it creates
     * another item of the same type.
     *
     * <p>The new item is created with a random IID, marked as dirty, and
     * returned without being saved. The caller is responsible for adding
     * components, setting relations, and saving the item.
     *
     * <p>Subclasses can override to provide custom initialization logic.
     *
     * @param ctx The action context (provides librarian reference)
     * @return A new instance of this type
     * @throws IllegalStateException if no librarian is available
     * @throws IllegalArgumentException if the type is abstract or has no suitable constructor
     */
    public Item actionNew(
            ActionContext ctx,
            @Param(
                    value = "name", required = false, role = "NAME") String name) {
        Librarian lib = ctx.librarian();
        if (lib == null) {
            throw new IllegalStateException("Cannot create item without librarian");
        }

        @SuppressWarnings("unchecked")
        Class<? extends Item> itemClass = (Class<? extends Item>) this.getClass();

        // Check if instantiable (not abstract, not interface)
        if (Modifier.isAbstract(itemClass.getModifiers())) {
            throw new IllegalArgumentException(
                    "Cannot instantiate abstract type: " + itemClass.getSimpleName());
        }

        // Find and invoke constructor(Librarian)
        Item newItem;
        try {
            Constructor<? extends Item> ctor = itemClass.getDeclaredConstructor(Librarian.class);
            ctor.setAccessible(true);
            newItem = ctor.newInstance(lib);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Type " + itemClass.getSimpleName() + " has no Librarian constructor");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create " + itemClass.getSimpleName(), e);
        }

        // Apply title if a name was provided
        if (name != null && !name.isBlank()) {
            newItem.relate(
                    dev.everydaythings.graph.language.CoreVocabulary.Title.IID,
                    Literal.ofText(name));
        }

        return newItem;
    }

    /**
     * Show available verbs and their documentation.
     *
     * <p>Returns the vocabulary itself — it's a Component with a Surface,
     * so the rendering pipeline handles display.
     */
    @Verb(value = dev.everydaythings.graph.language.CoreVocabulary.Help.KEY, doc = "Show available verbs and their documentation")
    public Object actionHelp(ActionContext ctx) {
        return vocabulary();
    }

    /**
     * Navigate to a path within this item's mount tree.
     *
     * <p>Resolves the target path and returns a {@link Ref} that the session
     * can use for navigation. Supports:
     * <ul>
     *   <li>{@code ".."} — navigate to parent path (or back to root)</li>
     *   <li>{@code "/path"} — absolute path within this item's mounts</li>
     *   <li>{@code "path"} — relative path (treated as absolute)</li>
     * </ul>
     *
     * <p>The path must resolve to either a real mounted component or a virtual
     * directory implied by deeper mounts. Returns a failure if the path doesn't exist.
     *
     * @param target The path to navigate to
     * @return A Ref for the session to navigate to
     */
    @Verb(value = dev.everydaythings.graph.language.CoreVocabulary.Cd.KEY, doc = "Navigate to path within item")
    public Ref actionCd(
            @Param(value = "target", doc = "Path or '..' to go back") String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("cd requires a target path");
        }

        // ".." — navigate to parent
        if ("..".equals(target.trim())) {
            return Ref.of(iid());  // Back to item root
        }

        // Canonicalize path
        String path = target.startsWith("/") ? target : "/" + target;
        String canonical = dev.everydaythings.graph.item.mount.PathUtil.canonicalize(path);

        // Check if path exists as a real component
        return frames().atPath(canonical)
                .map(entry -> Ref.of(iid(), entry.frameKey()))
                .orElseGet(() -> {
                    // Check if path exists as a virtual directory (has children under it)
                    if (frames().hasChildren(canonical)) {
                        return Ref.of(iid());
                    }
                    throw new IllegalArgumentException("No such path: " + target);
                });
    }

    // ==================================================================================
    // Convenience Methods
    // ==================================================================================

    /**
     * Create a frame body with this item as THEME and the given predicate and bindings.
     *
     * <p>For the common case of a single TARGET binding, use
     * {@link #relate(ItemID, BindingTarget)} instead.
     *
     * @param predicate the frame type
     * @param bindings  additional role bindings beyond theme
     * @return the created frame body
     */
    public FrameBody relate(ItemID predicate, Map<ItemID, BindingTarget> bindings) {
        return FrameBody.of(predicate, iid, bindings);
    }

    /**
     * Resolve the item type from @Implements or @Type annotation.
     */
    protected String resolveItemType() {
        Implements impl = getClass().getAnnotation(Implements.class);
        if (impl != null) return impl.value();
        return getClass().getName();
    }

    // ==================================================================================
    // Static Factory Methods
    // ==================================================================================

    /**
     * Create a new basic Item.
     *
     * <p>This is the factory method for creating plain Items. For typed items,
     * use the {@code new} action on the appropriate type item.
     *
     * @param librarian The librarian for this item
     * @return A new Item
     */
    public static Item create(Librarian librarian) {
        return new Item(librarian);
    }

    // ==================================================================================
    // Static Type Utilities
    // ==================================================================================

    /**
     * Get the type key from any @Implements or @Type-annotated class.
     */
    public static String keyOf(Class<?> type) {
        Implements impl = type.getAnnotation(Implements.class);
        if (impl != null) return impl.value();
        throw new IllegalArgumentException(
                "Class " + type.getName() + " is missing @Implements annotation");
    }

    /**
     * Get the type ID from any @Type-annotated class (Item or component).
     */
    public static ItemID idOf(Class<?> type) {
        return ItemID.fromString(keyOf(type));
    }

    /**
     * Marks a static Item field as a seed instance for the SeedStore.
     *
     * <p>Seed items are bootstrap vocabulary: types, predicates, dimensions, units, etc.
     * They have deterministic IIDs derived from their canonical key.
     *
     * <p>Usage:
     * <pre>{@code
     * @Item.Seed
     * public static final Dimension LENGTH = new Dimension("cg.dim:length", "L", "length");
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Seed {}

    // ==================================================================================
    // Frame Annotation
    // ==================================================================================

    /**
     * Declares a frame field on this Item.
     *
     * <p>By default, {@code @Frame} fields are <b>endorsed</b> — they are
     * part of the item's manifest and contribute to its version identity.
     * Endorsement means the item's owner asserts this frame as part of their
     * item's definition. Set {@code endorsed=false} for frames that are
     * independently signed assertions (stored as FrameRecords, indexed for
     * cross-item queries, but not in the manifest).
     *
     * <p>The {@code key} attribute is <b>required</b> and provides semantic
     * FrameKey tokens as ItemID canonical strings. All frame keys must be
     * semantic — no literal string keys.
     *
     * <p>Usage:
     * <pre>{@code
     * // Endorsed frame (in manifest, contributes to VID)
     * @Item.Frame(key = {"cg.core:vault"}, path = ".vault", localOnly = true)
     * private Vault vault;
     *
     * // Endorsed frame with semantic key
     * @Item.Frame(key = {"cg.core:title"})
     * private String title;
     *
     * // Unendorsed frame (independently signed, not in manifest)
     * @Item.Frame(key = {"cg.core:author"}, endorsed = false)
     * private ItemID author;
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Frame {
        /** Semantic FrameKey tokens (ItemID canonical strings). Required. */
        String[] key() default {};

        /** Mount path relative to item root. Required for localOnly. */
        String path() default EMPTY;

        /** Store as snapshot content. Default true. */
        boolean snapshot() default true;

        /** Store as stream content. Default false. */
        boolean stream() default false;

        /** Local-only (no sync). Default false. */
        boolean localOnly() default false;

        /** Contributes to version identity (VID). Default true. */
        boolean identity() default true;

        /** Endorsed = in manifest. False = unendorsed (relation-style). Default true. */
        boolean endorsed() default true;
    }
}
