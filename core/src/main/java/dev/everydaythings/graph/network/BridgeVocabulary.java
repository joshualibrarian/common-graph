package dev.everydaythings.graph.network;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.CoreVocabulary;

/**
 * Seed vocabulary for bridge services and foreign protocol translation.
 *
 * <p>A bridge is a service on a Librarian that translates between CG and a foreign
 * protocol. Bridges are declared as frames on the Librarian with configuration
 * bindings — they are not separate Items.
 *
 * <p>Contains Sememe seeds for: the bridge concept, the BRIDGES predicate,
 * protocol-specific bridge types, and foreign identity resolution.
 *
 * <p>Seeds are discovered by {@link dev.everydaythings.graph.library.SeedVocabulary}
 * via classpath scanning of {@code @Seed} fields.
 */
public final class BridgeVocabulary {

    private BridgeVocabulary() {}

    // ==================================================================================
    // Bridge concept — root of the bridge type hierarchy
    // ==================================================================================

    /**
     * The general concept of a protocol bridge service.
     * Protocol-specific bridges (email, activitypub, http) are hyponyms.
     */
    @ItemSeed(key = Bridge.KEY)
    public static class Bridge {
        public static final String KEY = "cg.sememe:bridge";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a service that translates between CG and a foreign protocol";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"bridge"};
    }

    // ==================================================================================
    // BRIDGES predicate — declares an active bridge on a Librarian
    // ==================================================================================

    /**
     * Predicate for a frame on a Librarian declaring an active bridge service.
     * Configuration (host, port, credentials, etc.) lives in the frame's bindings.
     */
    @ItemSeed(key = Bridges.KEY)
    public static class Bridges {
        public static final String KEY = "cg.core:bridges";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "provides bridge service to a foreign protocol";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Instrument.KEY}))
        static final ItemID expectInstrument = ThematicRole.Instrument.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    // ==================================================================================
    // Protocol-specific bridge types (hyponyms of Bridge)
    // ==================================================================================

    /**
     * A bridge translating between CG and email (SMTP/IMAP).
     */
    @ItemSeed(key = EmailBridge.KEY)
    public static class EmailBridge {
        public static final String KEY = "cg.sememe:bridge/email";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a bridge translating between CG and email via SMTP/IMAP";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"email bridge"};
    }

    /**
     * A bridge translating between CG and the ActivityPub federation protocol.
     */
    @ItemSeed(key = ActivityPubBridge.KEY)
    public static class ActivityPubBridge {
        public static final String KEY = "cg.sememe:bridge/activitypub";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a bridge translating between CG and the ActivityPub federation protocol";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"activitypub bridge"};
    }

    /**
     * A bridge providing HTTP/WebSocket access to CG (web gateway).
     */
    @ItemSeed(key = HttpBridge.KEY)
    public static class HttpBridge {
        public static final String KEY = "cg.sememe:bridge/http";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a bridge providing HTTP and WebSocket access to CG";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"web gateway"};
    }

    // ==================================================================================
    // Foreign identity predicates
    // ==================================================================================

    /**
     * Asserts that a CG identity corresponds to a foreign identity.
     * Used when a phantom item is merged with a real principal.
     */
    @ItemSeed(key = IdentifiesAs.KEY)
    public static class IdentifiesAs {
        public static final String KEY = "cg.bridge:identifies-as";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "asserts that this identity corresponds to another; merges a phantom with a real principal";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    /**
     * Records that content was received via a specific bridge protocol.
     * Used on frames created from inbound foreign messages.
     */
    @ItemSeed(key = ReceivedVia.KEY)
    public static class ReceivedVia {
        public static final String KEY = "cg.bridge:received-via";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "records that content was received through a foreign protocol bridge";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Instrument.KEY}))
        static final ItemID expectInstrument = ThematicRole.Instrument.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Source.KEY}))
        static final ItemID expectSource = ThematicRole.Source.IID;
    }
}
