package dev.everydaythings.graph.runtime.host;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.Signer;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Represents the physical (or virtual) machine on which one or more Librarians run.
 *
 * <p>A Host is identity-bearing — extends {@link Signer} — but its signing capability
 * is <i>optional</i> per the auth taxonomy. A Host that needs to attest about itself
 * (hardware identity, machine-issued credentials, TLS-equivalent for inbound peer
 * connections) constructs with a vault; a Host that exists only as a referent
 * (the machine where some other principal's Librarian runs) can be identity-only.
 *
 * <p>Per the SERVES relationship: a Librarian runs ON a Host. Multiple Librarians
 * may coexist on one Host (system-Librarian + user-Librarian on same machine, USB
 * stick scenario, etc.). The Host's relationship to Librarians is independent of
 * the Librarian's served principal.
 *
 * <p>Phase 1 scope: minimal placeholder. Carries identity, hostname/IP discovery
 * is local-machine-only, no device management, no system monitor. The OLD
 * {@link HostOld} class has elaborate scaffolding (device registration for
 * displays/audio/peripherals, system monitor mounted at {@code .monitor}, etc.)
 * that will be ported back as concrete needs arise.
 *
 * <p>Future scope (sketch): Host becomes the "control panel" for the machine
 * when accessed by an authorized user — start/stop local services, manage
 * containers, expose system monitor, configure peripherals, etc. All exposed
 * via frames with appropriate predicates and a SERVES-based authorization
 * gate.
 */
@Seed.Item(key = Host.KEY)
@Seed.Embodies(key = Host.KEY)
public class Host extends Signer {

    /** Canonical key for the Host archetype. */
    public static final String KEY = "cg.archetype:host";

    /** The deterministic IID for the Host archetype-sememe itself. */
    public static final ItemRef ARCHETYPE_IID = ItemRef.fromString(KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the physical or virtual machine on which one or more Librarians run";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"host", "machine", "device"};

    @Override
    public ItemRef archetype() {
        return ARCHETYPE_IID;
    }

    /**
     * Identity-only constructor. Used when the local node knows about a remote
     * Host as a referent but doesn't hold its keys.
     */
    public Host(ItemRef iid) {
        super(iid);
    }

    /**
     * Hydration constructor — used by the librarian's IMPLEMENTATION-binding
     * dispatch to instantiate a Host loaded from storage. The hydrated Host
     * has no vault (the local node doesn't hold the remote Host's private
     * keys); subsequent operations that need signing capability will throw.
     */
    public Host(ItemRef iid, Librarian librarian) {
        super(iid);
        bindLibrarian(librarian);
    }

    /**
     * Local hostname as reported by the OS. Convenience accessor; the same data
     * could be carried as a binding on the Host's manifest (and probably will be,
     * once we have a NAME / HOSTNAME predicate landed).
     */
    public static String localHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            return "localhost";
        }
    }
}
