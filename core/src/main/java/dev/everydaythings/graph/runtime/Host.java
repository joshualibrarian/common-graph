package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.DisplayConfig;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ViewVocabulary;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.network.RoutingVocabulary;
import dev.everydaythings.graph.crypt.SigningPublicKey;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the physical/virtual host machine.
 *
 * <p>This is a self-describing type. A Host is a Signer (has its own keypair)
 * and can be identified by:
 * <ul>
 *   <li>name: the hostname</li>
 *   <li>reachable-at: IP addresses where it can be reached</li>
 * </ul>
 */
@Implements(Host.KEY)
@ItemSeed(key = Host.KEY)
public class Host extends Signer {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg.sememe:host";

    @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
    static final String seedGloss = "a network host device";

    @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                   features = {GrammaticalFeature.Lemma.KEY})
    static final String seedNoun = "host";

    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    @Frame(key = {RoutingVocabulary.ReachableAt.KEY}, endorsed = false)
    private List<InetAddress> ipAddresses;

    @Frame(key = {CoreVocabulary.Monitor.KEY}, path = ".monitor")
    private SystemMonitor systemMonitor;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Type seed constructor - creates a minimal Host for use as type seed.
     *
     * <p>Used by SeedStore to create the "cg.sememe:host" type item.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected Host(ItemID typeId) {
        super(typeId);
    }

    /**
     * Path-based constructor for materialized Host.
     *
     * <p>Creates or loads a Host at the given filesystem path.
     * On first boot, gathers network information and initializes.
     *
     * @param path          The filesystem path for this host
     * @param fallbackStore Fallback store for type lookups during construction
     */
    public Host(Path path, ItemStore fallbackStore) {
        super(path, fallbackStore);

        if (freshBoot) {
            initializeNetworkInfo();
        }
    }

    /**
     * Hydration constructor for loading Host type seeds from DB.
     *
     * <p>NOTE: This creates a non-functional Host (no storage, no networking).
     * It's only used to hydrate the type seed so it can provide displayInfo.
     *
     * @param librarian The librarian performing hydration (unused for type seeds)
     * @param manifest  The manifest to hydrate from
     */
    protected Host(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Type seeds don't need name/ipAddresses initialized
    }

    /**
     * Reference constructor for remote hosts.
     *
     * @param librarian The librarian (for context)
     * @param manifest  The manifest containing the Host's public state
     * @param publicKey The Host's public key
     */
    public Host(Librarian librarian, Manifest manifest, SigningPublicKey publicKey) {
        super(librarian, manifest, publicKey);
        // Remote hosts have their name/ipAddresses loaded from manifest
    }

    /**
     * In-memory constructor for ephemeral Host items.
     */
    public Host(Librarian librarian) {
        super(librarian, InMemoryMarker.INSTANCE);
        initializeNetworkInfo();
    }

    /**
     * Path-based constructor for persistent Host items.
     */
    public Host(Librarian librarian, Path path) {
        super(librarian, path);
        if (freshBoot) {
            initializeNetworkInfo();
        }
    }

    /**
     * Initialize network information on first boot.
     */
    private void initializeNetworkInfo() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            setName(localHost.getHostName());
            ipAddresses = List.of(InetAddress.getAllByName(name()));
        } catch (UnknownHostException e) {
            setName("localhost");
            ipAddresses = List.of();
        }
        systemMonitor = SystemMonitor.create();
    }

    // ==================================================================================
    // ACCESSORS
    // ==================================================================================

    public SystemMonitor systemMonitor() {
        return systemMonitor;
    }

    /**
     * Convenience alias for {@link #name()} — the Host's name IS the hostname.
     */
    public String hostname() {
        return name();
    }

    public List<InetAddress> ipAddresses() {
        return ipAddresses;
    }

    /**
     * Check if this Host item represents the actual local machine.
     */
    public boolean isLocal() {
        // TODO: compare against actual local hostname/IPs
        return true;
    }

    // ==================================================================================
    // DISPLAY MANAGEMENT
    // ==================================================================================

    private static final ItemID DISPLAY_SEMEME_ID = ItemID.fromString(ViewVocabulary.Display.KEY);

    /**
     * Projection of a DISPLAY frame on this Host.
     */
    public record DisplayInfo(FrameKey frameKey, String displayId, DisplayConfig config) {}

    /**
     * Register a physical display on this host.
     *
     * <p>Creates a DISPLAY frame with the given displayId as qualifier
     * and stores the DisplayConfig as its live instance.
     *
     * @param displayId unique identifier for the display (e.g., monitor name or index)
     * @param config    the display's physical properties
     * @return the FrameKey of the new DISPLAY frame
     */
    public FrameKey registerDisplay(String displayId, DisplayConfig config) {
        FrameKey key = FrameKey.mixed(DISPLAY_SEMEME_ID, displayId);

        // Remove existing frame for this display if present
        frames().removeByKey(key);

        dev.everydaythings.graph.frame.Frame frame =
                new dev.everydaythings.graph.frame.Frame(key, DISPLAY_SEMEME_ID, null, null, false);
        frames().add(frame);
        frame.setInstance(config);

        return key;
    }

    /**
     * Get all DISPLAY frames on this host.
     */
    public List<DisplayInfo> displays() {
        List<DisplayInfo> result = new ArrayList<>();
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (DISPLAY_SEMEME_ID.equals(frame.type()) && frame.instance() instanceof DisplayConfig dc) {
                List<FrameKey.FrameToken> quals = frame.frameKey().qualifiers();
                String qualifier = !quals.isEmpty() && quals.getFirst() instanceof FrameKey.Literal lit
                        ? lit.value() : null;
                result.add(new DisplayInfo(frame.frameKey(), qualifier, dc));
            }
        }
        return result;
    }

    /**
     * Remove all DISPLAY frames from this host.
     */
    public void clearDisplays() {
        List<FrameKey> toRemove = new ArrayList<>();
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (DISPLAY_SEMEME_ID.equals(frame.type())) {
                toRemove.add(frame.frameKey());
            }
        }
        for (FrameKey key : toRemove) {
            frames().removeByKey(key);
        }
    }
}
