package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.ItemFrame;
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
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.DeviceVocabulary;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
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

    @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
    static final String seedGloss = "a network host device";

    @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String seedNoun = "host";

    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    @ItemFrame(predicate = RoutingVocabulary.ReachableAt.KEY, endorsement = @ItemFrame.Endorsed(false))
    private List<InetAddress> ipAddresses;

    @ItemFrame(predicate = CoreVocabulary.Monitor.KEY, endorsement = @ItemFrame.Endorsed(mounts = {".monitor"}))
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
    // DEVICE MANAGEMENT
    // ==================================================================================

    /**
     * Projection of a DEVICE frame on this Host.
     *
     * @param frameKey   the device's FrameKey (DEVICE, type, deviceId)
     * @param deviceType the device type qualifier (Display, Audio, etc.)
     * @param deviceId   the string identifier for the device
     * @param config     the device's configuration (e.g., DisplayConfig)
     */
    public record DeviceInfo(FrameKey frameKey, ItemID deviceType, String deviceId, Object config) {

        /** Build a compound Ref targeting this device on the given host. */
        public Ref refOn(ItemID hostIid) {
            return Ref.of(hostIid, frameKey);
        }
    }

    /**
     * Register a device on this host.
     *
     * <p>Creates a DEVICE frame with the device type as a qualifier and the
     * deviceId as a literal qualifier. Stores the config as its live instance.
     *
     * <p>FrameKey structure: {@code (DEVICE, <deviceType>, <deviceId>)}
     *
     * @param deviceType the device type qualifier (e.g., DeviceVocabulary.Display.IID)
     * @param deviceId   unique identifier for the device
     * @param config     the device's configuration object
     * @return the FrameKey of the new DEVICE frame
     */
    public FrameKey registerDevice(ItemID deviceType, String deviceId, Object config) {
        FrameKey key = FrameKey.of(DeviceVocabulary.Device.IID, deviceType, deviceId);

        // Remove existing frame for this device if present
        frames().removeByKey(key);

        dev.everydaythings.graph.frame.Frame frame =
                new dev.everydaythings.graph.frame.Frame(key, DeviceVocabulary.Device.IID, null, null, false);
        frames().add(frame);
        frame.setInstance(config);

        return key;
    }

    /**
     * Register a physical display on this host.
     *
     * @param displayId unique identifier for the display (e.g., "Built-in Retina Display-0")
     * @param config    the display's physical properties
     * @return the FrameKey of the new DEVICE frame
     */
    public FrameKey registerDisplay(String displayId, DisplayConfig config) {
        return registerDevice(DeviceVocabulary.Display.IID, displayId, config);
    }

    /**
     * Disconnect a device — remove its frame from the endorsements table.
     *
     * <p>The frame body is preserved in the library for history. Re-registering
     * the same device will re-endorse it with the same identity.
     */
    public void disconnectDevice(FrameKey key) {
        frames().removeByKey(key);
    }

    /**
     * Get all DEVICE frames on this host.
     */
    public List<DeviceInfo> devices() {
        List<DeviceInfo> result = new ArrayList<>();
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (DeviceVocabulary.Device.IID.equals(frame.type())) {
                extractDeviceInfo(frame, result);
            }
        }
        return result;
    }

    /**
     * Get all DEVICE frames of a specific type on this host.
     *
     * @param deviceType the device type to filter by (e.g., DeviceVocabulary.Display.IID)
     */
    public List<DeviceInfo> devices(ItemID deviceType) {
        List<DeviceInfo> result = new ArrayList<>();
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (DeviceVocabulary.Device.IID.equals(frame.type())) {
                List<FrameKey.FrameToken> quals = frame.frameKey().qualifiers();
                if (!quals.isEmpty()
                        && quals.getFirst() instanceof FrameKey.Sememe sem
                        && deviceType.equals(sem.id())) {
                    extractDeviceInfo(frame, result);
                }
            }
        }
        return result;
    }

    /**
     * Get all display devices on this host.
     */
    public List<DeviceInfo> displays() {
        return devices(DeviceVocabulary.Display.IID);
    }

    /**
     * Update the set of connected displays from OS enumeration.
     *
     * <p>Compares incoming display list against current DEVICE frames.
     * New displays are registered. Missing displays are disconnected.
     *
     * @param updates the currently connected displays
     */
    public void updateDisplays(List<DisplayUpdate> updates) {
        java.util.Set<String> currentIds = new java.util.HashSet<>();

        for (DisplayUpdate update : updates) {
            currentIds.add(update.displayId());
            registerDisplay(update.displayId(), update.config());
        }

        // Disconnect displays that are no longer present
        for (DeviceInfo existing : displays()) {
            if (!currentIds.contains(existing.deviceId())) {
                disconnectDevice(existing.frameKey());
            }
        }
    }

    /**
     * Update payload for {@link #updateDisplays(List)}.
     */
    public record DisplayUpdate(String displayId, DisplayConfig config) {}

    /**
     * Remove all DEVICE frames from this host.
     */
    public void clearDevices() {
        List<FrameKey> toRemove = new ArrayList<>();
        for (dev.everydaythings.graph.frame.Frame frame : frames()) {
            if (DeviceVocabulary.Device.IID.equals(frame.type())) {
                toRemove.add(frame.frameKey());
            }
        }
        for (FrameKey key : toRemove) {
            frames().removeByKey(key);
        }
    }

    private static void extractDeviceInfo(dev.everydaythings.graph.frame.Frame frame, List<DeviceInfo> result) {
        List<FrameKey.FrameToken> quals = frame.frameKey().qualifiers();
        ItemID deviceType = null;
        String deviceId = null;
        if (quals.size() >= 1 && quals.get(0) instanceof FrameKey.Sememe sem) {
            deviceType = sem.id();
        }
        if (quals.size() >= 2 && quals.get(1) instanceof FrameKey.Literal lit) {
            deviceId = lit.value();
        }
        result.add(new DeviceInfo(frame.frameKey(), deviceType, deviceId, frame.instance()));
    }
}
