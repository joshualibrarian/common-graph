package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "be in or establish communication with")
                .cili("i25412")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "be in or establish communication with";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25412";
    }

    @ItemSeed(key = AvailableAt.KEY, slots = {ThematicRole.Goal.KEY})
    public static class AvailableAt {
        public static final String KEY = "cg.core:available-at";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "be located or situated somewhere; occupy a certain position")
                .cili("i35108")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "be located or situated somewhere; occupy a certain position";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i35108";
    }

    // ==================================================================================
    // Peer relationship predicates
    // ==================================================================================

    @ItemSeed(key = PeersWith.KEY, slots = {ThematicRole.Goal.KEY})
    public static class PeersWith {
        public static final String KEY = "cg.core:peers-with";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "be connected to as a network peer")
                .cili("i34787")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "be connected to as a network peer";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i34787";
    }

    // ==================================================================================
    // Identification predicates
    // ==================================================================================

    @ItemSeed(key = Name.KEY, slots = {ThematicRole.Referent.KEY})
    public static class Name {
        public static final String KEY = "cg.core:name";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "a word or phrase that identifies something")
                .cili("i69761")
                .indexWeight(1000)
                .slot(ThematicRole.Referent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word or phrase that identifies something";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69761";

        @ItemSeed.Frame(key = {CoreVocabulary.IndexWeight.KEY})
        static final String indexWeight = "1000";
    }

    // ==================================================================================
    // Acknowledgement predicates
    // ==================================================================================

    @ItemSeed(key = AcknowledgesDelivery.KEY, slots = {ThematicRole.Theme.KEY})
    public static class AcknowledgesDelivery {
        public static final String KEY = "cg.trust:acknowledges-delivery";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "acknowledge receipt of a successful delivery")
                .cili("i26081")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "acknowledge receipt of a successful delivery";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i26081";
    }

    @ItemSeed(key = AcknowledgesRelay.KEY, slots = {ThematicRole.Theme.KEY})
    public static class AcknowledgesRelay {
        public static final String KEY = "cg.trust:acknowledges-relay";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "pass along; relay a message through an intermediary")
                .cili("i25411")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "pass along; relay a message through an intermediary";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25411";
    }

    @ItemSeed(key = RequestId.KEY, slots = {ThematicRole.Theme.KEY})
    public static class RequestId {
        public static final String KEY = "cg.trust:request-id";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "identifier of the request being acknowledged")
                .cili("i74891")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "identifier of the request being acknowledged";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i74891";
    }

    // ==================================================================================
    // Service predicates
    // ==================================================================================

    @ItemSeed(key = Serves.KEY, slots = {ThematicRole.Recipient.KEY})
    public static class Serves {
        public static final String KEY = "cg.core:serves";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "acts on behalf of; provides services to a principal")
                .slot(ThematicRole.Recipient.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "acts on behalf of; provides services to a principal";
    }
}
