package dev.everydaythings.graph.network;

import dev.everydaythings.graph.frame.ItemFrame;
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
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a service that translates between CG and a foreign protocol";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "bridge";
    }

    // ==================================================================================
    // BRIDGES predicate — declares an active bridge on a Librarian
    // ==================================================================================

    /**
     * Predicate for a frame on a Librarian declaring an active bridge service.
     * Configuration (host, port, credentials, etc.) lives in the frame's bindings.
     */
    @ItemSeed(key = Bridges.KEY, slots = {ThematicRole.Instrument.KEY, ThematicRole.Goal.KEY})
    public static class Bridges {
        public static final String KEY = "cg.core:bridges";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "provides bridge service to a foreign protocol";
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
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a bridge translating between CG and email via SMTP/IMAP";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "email bridge";
    }

    /**
     * A bridge translating between CG and the ActivityPub federation protocol.
     */
    @ItemSeed(key = ActivityPubBridge.KEY)
    public static class ActivityPubBridge {
        public static final String KEY = "cg.sememe:bridge/activitypub";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a bridge translating between CG and the ActivityPub federation protocol";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "activitypub bridge";
    }

    /**
     * A bridge providing HTTP/WebSocket access to CG (web gateway).
     */
    @ItemSeed(key = HttpBridge.KEY)
    public static class HttpBridge {
        public static final String KEY = "cg.sememe:bridge/http";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a bridge providing HTTP and WebSocket access to CG";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "web gateway";
    }

    // ==================================================================================
    // Foreign identity predicates
    // ==================================================================================

    /**
     * Asserts that a CG identity corresponds to a foreign identity.
     * Used when a phantom item is merged with a real principal.
     */
    @ItemSeed(key = IdentifiesAs.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class IdentifiesAs {
        public static final String KEY = "cg.bridge:identifies-as";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "asserts that this identity corresponds to another; merges a phantom with a real principal";
    }

    /**
     * Records that content was received via a specific bridge protocol.
     * Used on frames created from inbound foreign messages.
     */
    @ItemSeed(key = ReceivedVia.KEY, slots = {ThematicRole.Instrument.KEY, ThematicRole.Source.KEY})
    public static class ReceivedVia {
        public static final String KEY = "cg.bridge:received-via";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "records that content was received through a foreign protocol bridge";
    }
}
