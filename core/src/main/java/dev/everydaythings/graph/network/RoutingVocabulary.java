package dev.everydaythings.graph.network;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.SememeGloss;
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

    @ItemSeed(key = ReachableAt.KEY, slots = {ThematicRole.Goal.KEY})
    public static class ReachableAt {
        public static final String KEY = "cg.core:reachable-at";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "be in or establish communication with";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25412";
    }

    @ItemSeed(key = AvailableAt.KEY, slots = {ThematicRole.Goal.KEY})
    public static class AvailableAt {
        public static final String KEY = "cg.core:available-at";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "be located or situated somewhere; occupy a certain position";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i35108";
    }

    // ==================================================================================
    // Peer relationship predicates
    // ==================================================================================

    @ItemSeed(key = PeersWith.KEY, slots = {ThematicRole.Goal.KEY})
    public static class PeersWith {
        public static final String KEY = "cg.core:peers-with";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "be connected to as a network peer";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i34787";
    }

    // ==================================================================================
    // Identification predicates
    // ==================================================================================

    @ItemSeed(key = Name.KEY, slots = {ThematicRole.Referent.KEY})
    public static class Name {
        public static final String KEY = "cg.core:name";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word or phrase that identifies something";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69761";

        @ItemFrame(key = {CoreVocabulary.IndexWeight.KEY})
        static final String indexWeight = "1000";
    }

    // ==================================================================================
    // Acknowledgement predicates
    // ==================================================================================

    @ItemSeed(key = AcknowledgesDelivery.KEY, slots = {ThematicRole.Theme.KEY})
    public static class AcknowledgesDelivery {
        public static final String KEY = "cg.trust:acknowledges-delivery";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "acknowledge receipt of a successful delivery";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i26081";
    }

    @ItemSeed(key = AcknowledgesRelay.KEY, slots = {ThematicRole.Theme.KEY})
    public static class AcknowledgesRelay {
        public static final String KEY = "cg.trust:acknowledges-relay";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "pass along; relay a message through an intermediary";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25411";
    }

    @ItemSeed(key = RequestId.KEY, slots = {ThematicRole.Theme.KEY})
    public static class RequestId {
        public static final String KEY = "cg.trust:request-id";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "identifier of the request being acknowledged";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i74891";
    }

    // ==================================================================================
    // Service predicates
    // ==================================================================================

    @ItemSeed(key = Serves.KEY, slots = {ThematicRole.Recipient.KEY})
    public static class Serves {
        public static final String KEY = "cg.core:serves";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "acts on behalf of; provides services to a principal";
    }
}
