package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.DisplayConfig;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.DeviceVocabulary;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.item.user.SignerOld;
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
public class Host extends SignerOld {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg.sememe:host";

    @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
    static final String seedGloss = "a network host device";

    @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
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
     * Path-based constructor (no librarian, e.g. standalone host).
     */
    public Host(Path path, ItemStore fallbackStore) {
        super(null, path, fallbackStore);
        if (freshBoot) initializeNetworkInfo();
    }

    /** Hydration constructor for loading Host from manifest. */
    protected Host(LibrarianOld librarian, ManifestOld manifest) {
        super(librarian, manifest);
    }

    /** Reference constructor for remote hosts. */
    public Host(LibrarianOld librarian, ManifestOld manifest, SigningPublicKey publicKey) {
        super(librarian, manifest, publicKey);
    }

    /** In-memory constructor for ephemeral Host items. */
    public Host(LibrarianOld librarian) {
        super(librarian, librarian.library().primaryStore().orElse(null));
        initializeNetworkInfo();
    }

    /** Path-based constructor with librarian. */
    public Host(LibrarianOld librarian, Path path) {
        super(librarian, path, librarian.library().primaryStore().orElse(null));
        if (freshBoot) initializeNetworkInfo();
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
    public record DeviceInfo(CompoundKey frameKey, ItemID deviceType, String deviceId, Object config) {

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
    public CompoundKey registerDevice(ItemID deviceType, String deviceId, Object config) {
        CompoundKey key = CompoundKey.of(DeviceVocabulary.Device.IID, deviceType, deviceId);

        // Remove existing frame for this device if present
        frames().removeByKey(key);

        FrameOld frame =
                new FrameOld(key, DeviceVocabulary.Device.IID, null, null, false);
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
    public CompoundKey registerDisplay(String displayId, DisplayConfig config) {
        return registerDevice(DeviceVocabulary.Display.IID, displayId, config);
    }

    /**
     * Disconnect a device — remove its frame from the endorsements table.
     *
     * <p>The frame body is preserved in the library for history. Re-registering
     * the same device will re-endorse it with the same identity.
     */
    public void disconnectDevice(CompoundKey key) {
        frames().removeByKey(key);
    }

    /**
     * Get all DEVICE frames on this host.
     */
    public List<DeviceInfo> devices() {
        List<DeviceInfo> result = new ArrayList<>();
        for (FrameOld frame : frames()) {
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
        for (FrameOld frame : frames()) {
            if (DeviceVocabulary.Device.IID.equals(frame.type())) {
                List<CompoundKey.FrameToken> quals = frame.frameKey().qualifiers();
                if (!quals.isEmpty()
                        && quals.getFirst() instanceof CompoundKey.Sememe sem
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
        List<CompoundKey> toRemove = new ArrayList<>();
        for (FrameOld frame : frames()) {
            if (DeviceVocabulary.Device.IID.equals(frame.type())) {
                toRemove.add(frame.frameKey());
            }
        }
        for (CompoundKey key : toRemove) {
            frames().removeByKey(key);
        }
    }

    private static void extractDeviceInfo(FrameOld frame, List<DeviceInfo> result) {
        List<CompoundKey.FrameToken> quals = frame.frameKey().qualifiers();
        ItemID deviceType = null;
        String deviceId = null;
        if (quals.size() >= 1 && quals.get(0) instanceof CompoundKey.Sememe sem) {
            deviceType = sem.id();
        }
        if (quals.size() >= 2 && quals.get(1) instanceof CompoundKey.Literal lit) {
            deviceId = lit.value();
        }
        result.add(new DeviceInfo(frame.frameKey(), deviceType, deviceId, frame.instance()));
    }
}
