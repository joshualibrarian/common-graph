package dev.everydaythings.graph.network;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
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

    @ItemSeed(key = ReachableAt.KEY)
    public static class ReachableAt {
        public static final String KEY = "cg.core:reachable-at";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "be in or establish communication with";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i25412";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = AvailableAt.KEY)
    public static class AvailableAt {
        public static final String KEY = "cg.core:available-at";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "be located or situated somewhere; occupy a certain position";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i35108";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    // ==================================================================================
    // Peer relationship predicates
    // ==================================================================================

    @ItemSeed(key = PeersWith.KEY)
    public static class PeersWith {
        public static final String KEY = "cg.core:peers-with";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "be connected to as a network peer";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i34787";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    // ==================================================================================
    // Identification predicates
    // ==================================================================================

    @ItemSeed(key = Name.KEY)
    public static class Name {
        public static final String KEY = "cg.core:name";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a word or phrase that identifies something";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69761";

        @ItemFrame(predicate = CoreVocabulary.IndexWeight.KEY)
        static final String indexWeight = "1000";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Referent.KEY}))
        static final ItemID expectReferent = ThematicRole.Referent.IID;
    }

    // ==================================================================================
    // Acknowledgement predicates
    // ==================================================================================

    @ItemSeed(key = AcknowledgesDelivery.KEY)
    public static class AcknowledgesDelivery {
        public static final String KEY = "cg.trust:acknowledges-delivery";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "acknowledge receipt of a successful delivery";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i26081";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = AcknowledgesRelay.KEY)
    public static class AcknowledgesRelay {
        public static final String KEY = "cg.trust:acknowledges-relay";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "pass along; relay a message through an intermediary";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i25411";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = RequestId.KEY)
    public static class RequestId {
        public static final String KEY = "cg.trust:request-id";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "identifier of the request being acknowledged";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i74891";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    // ==================================================================================
    // Service predicates
    // ==================================================================================

    @ItemSeed(key = Serves.KEY)
    public static class Serves {
        public static final String KEY = "cg.core:serves";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "acts on behalf of; provides services to a principal";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Recipient.KEY}))
        static final ItemID expectRecipient = ThematicRole.Recipient.IID;
    }
}
