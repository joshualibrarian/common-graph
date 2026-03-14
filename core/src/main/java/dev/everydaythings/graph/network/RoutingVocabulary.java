package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Seed vocabulary for routing and networking predicates.
 *
 * <p>Contains all Sememe seeds for peer-to-peer networking: reachability,
 * peering, acknowledgement, and identification. These are discovered by
 * {@link dev.everydaythings.graph.library.SeedVocabulary} via classpath
 * scanning of {@code @Seed} fields.
 *
 * <p>Core system verbs (create, get, list, edit, etc.) remain in
 * {@link dev.everydaythings.graph.language.CoreVocabulary}.
 */
public final class RoutingVocabulary {

    private RoutingVocabulary() {}

    // ==================================================================================
    // Reachability and location predicates
    // ==================================================================================

    public static class ReachableAt {
        public static final String KEY = "cg.core:reachable-at";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.ADJECTIVE)
                .gloss("en", "be in or establish communication with")
                .cili("i25412")
                .slot(ThematicRole.Goal.KEY);
    }

    public static class AvailableAt {
        public static final String KEY = "cg.core:available-at";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss("en", "be located or situated somewhere; occupy a certain position")
                .cili("i35108")
                .slot(ThematicRole.Goal.KEY);
    }

    // ==================================================================================
    // Peer relationship predicates
    // ==================================================================================

    public static class PeersWith {
        public static final String KEY = "cg.core:peers-with";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "be connected to as a network peer")
                .cili("i34787")
                .slot(ThematicRole.Goal.KEY);
    }

    // ==================================================================================
    // Identification predicates
    // ==================================================================================

    public static class Name {
        public static final String KEY = "cg.core:name";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss("en", "a word or phrase that identifies something")
                .cili("i69761")
                .indexWeight(1000)
                .slot(ThematicRole.Referent.KEY);
    }

    // ==================================================================================
    // Acknowledgement predicates
    // ==================================================================================

    public static class AcknowledgesDelivery {
        public static final String KEY = "cg.trust:acknowledges-delivery";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "acknowledge receipt of a successful delivery")
                .cili("i26081")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class AcknowledgesRelay {
        public static final String KEY = "cg.trust:acknowledges-relay";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "pass along; relay a message through an intermediary")
                .cili("i25411")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class RequestId {
        public static final String KEY = "cg.trust:request-id";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss("en", "identifier of the request being acknowledged")
                .cili("i74891")
                .slot(ThematicRole.Theme.KEY);
    }

    // ==================================================================================
    // Service predicates
    // ==================================================================================

    public static class Serves {
        public static final String KEY = "cg.core:serves";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "acts on behalf of; provides services to a principal")
                .slot(ThematicRole.Recipient.KEY);
    }
}
