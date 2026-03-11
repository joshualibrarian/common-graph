package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.AdjectiveSememe;
import dev.everydaythings.graph.language.NounSememe;
import dev.everydaythings.graph.language.VerbSememe;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.trust.SigningPublicKey;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
    // SEMEMES USED BY HOST (defined where they're used)
    // ==================================================================================

    /** Predicate: network address where something can be reached */
    @Seed
    public static final AdjectiveSememe REACHABLE_AT = new AdjectiveSememe(
            "cg.core:reachable-at",
            Map.of("en", "be in or establish communication with"),
            Map.of("cili", "i25412")
    );

    /** Predicate: location where something is available */
    @Seed
    public static final NounSememe AVAILABLE_AT = new NounSememe(
            "cg.core:available-at",
            Map.of("en", "be located or situated somewhere; occupy a certain position"),
            Map.of("cili", "i35108")
    );

    /** Predicate: bidirectional peer adjacency between librarians */
    @Seed
    public static final VerbSememe PEERS_WITH = new VerbSememe(
            "cg.core:peers-with",
            Map.of("en", "be connected to as a network peer"),
            Map.of("cili", "i34787")
    );

    /** Predicate: hostname or name */
    @Seed
    @SuppressWarnings("unchecked")
    public static final NounSememe NAME = ((NounSememe) new NounSememe(
            "cg.core:name",
            Map.of("en", "a word or phrase that identifies something"),
            Map.of("cili", "i69761")
    ).indexWeight(1000));

    /** Predicate: acknowledges a successful delivery from a peer */
    @Seed
    public static final VerbSememe ACKNOWLEDGES_DELIVERY = new VerbSememe(
            "cg.trust:acknowledges-delivery",
            Map.of("en", "acknowledge receipt of a successful delivery"),
            Map.of("cili", "i26081")
    );

    /** Predicate: acknowledges successful relay forwarding by a peer */
    @Seed
    public static final VerbSememe ACKNOWLEDGES_RELAY = new VerbSememe(
            "cg.trust:acknowledges-relay",
            Map.of("en", "pass along; relay a message through an intermediary"),
            Map.of("cili", "i25411")
    );

    /** Qualifier: request identifier for an acknowledgement */
    @Seed
    public static final NounSememe REQUEST_ID = new NounSememe(
            "cg.trust:request-id",
            Map.of("en", "identifier of the request being acknowledged"),
            Map.of("cili", "i74891")
    );

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
