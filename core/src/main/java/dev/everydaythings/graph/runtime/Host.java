package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.crypt.SigningPublicKey;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
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
@Type(value = Host.KEY, glyph = "🖥️", color = 0x5080B0)
public class Host extends Signer {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/host";


    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    @Frame(key = {"cg.core:reachable-at"}, endorsed = false)
    private List<InetAddress> ipAddresses;

    @Frame(handle = "monitor", path = ".monitor")
    private SystemMonitor systemMonitor;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Type seed constructor - creates a minimal Host for use as type seed.
     *
     * <p>Used by SeedStore to create the "cg:type/host" type item.
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
}
