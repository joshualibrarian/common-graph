package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.language.AdjectiveSememe;
import dev.everydaythings.graph.language.NounSememe;
import dev.everydaythings.graph.language.VerbSememe;

/**
 * Seed vocabulary for routing and networking predicates.
 *
 * <p>Contains all Sememe seeds for peer-to-peer networking: reachability,
 * peering, acknowledgement, and identification. These are discovered by
 * {@link dev.everydaythings.graph.library.SeedVocabulary} via classpath
 * scanning of {@code @Seed} fields.
 *
 * <p>Core system verbs (create, get, list, edit, etc.) remain in
 * {@link dev.everydaythings.graph.language.VerbSememe}.
 */
public final class RoutingVocabulary {

    private RoutingVocabulary() {}

    // ==================================================================================
    // Reachability and location predicates
    // ==================================================================================

    public static class ReachableAt {
        public static final String KEY = "cg.core:reachable-at";
        @Seed public static final AdjectiveSememe SEED = new AdjectiveSememe(KEY)
                .gloss("en", "be in or establish communication with")
                .cili("i25412");
    }

    public static class AvailableAt {
        public static final String KEY = "cg.core:available-at";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss("en", "be located or situated somewhere; occupy a certain position")
                .cili("i35108");
    }

    // ==================================================================================
    // Peer relationship predicates
    // ==================================================================================

    public static class PeersWith {
        public static final String KEY = "cg.core:peers-with";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss("en", "be connected to as a network peer")
                .cili("i34787");
    }

    // ==================================================================================
    // Identification predicates
    // ==================================================================================

    public static class Name {
        public static final String KEY = "cg.core:name";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss("en", "a word or phrase that identifies something")
                .cili("i69761")
                .indexWeight(1000);
    }

    // ==================================================================================
    // Acknowledgement predicates
    // ==================================================================================

    public static class AcknowledgesDelivery {
        public static final String KEY = "cg.trust:acknowledges-delivery";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss("en", "acknowledge receipt of a successful delivery")
                .cili("i26081");
    }

    public static class AcknowledgesRelay {
        public static final String KEY = "cg.trust:acknowledges-relay";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss("en", "pass along; relay a message through an intermediary")
                .cili("i25411");
    }

    public static class RequestId {
        public static final String KEY = "cg.trust:request-id";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss("en", "identifier of the request being acknowledged")
                .cili("i74891");
    }
}
