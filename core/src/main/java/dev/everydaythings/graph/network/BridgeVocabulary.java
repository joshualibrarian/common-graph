package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;

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
    public static class Bridge {
        public static final String KEY = "cg.sememe:bridge";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "a service that translates between CG and a foreign protocol")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "bridge");
    }

    // ==================================================================================
    // BRIDGES predicate — declares an active bridge on a Librarian
    // ==================================================================================

    /**
     * Predicate for a frame on a Librarian declaring an active bridge service.
     * Configuration (host, port, credentials, etc.) lives in the frame's bindings.
     */
    public static class Bridges {
        public static final String KEY = "cg.core:bridges";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "provides bridge service to a foreign protocol")
                .slot(ThematicRole.Instrument.KEY)
                .slot(ThematicRole.Goal.KEY);
    }

    // ==================================================================================
    // Protocol-specific bridge types (hyponyms of Bridge)
    // ==================================================================================

    /**
     * A bridge translating between CG and email (SMTP/IMAP).
     */
    public static class EmailBridge {
        public static final String KEY = "cg.sememe:bridge/email";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "a bridge translating between CG and email via SMTP/IMAP")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "email bridge");
    }

    /**
     * A bridge translating between CG and the ActivityPub federation protocol.
     */
    public static class ActivityPubBridge {
        public static final String KEY = "cg.sememe:bridge/activitypub";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "a bridge translating between CG and the ActivityPub federation protocol")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "activitypub bridge");
    }

    /**
     * A bridge providing HTTP/WebSocket access to CG (web gateway).
     */
    public static class HttpBridge {
        public static final String KEY = "cg.sememe:bridge/http";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "a bridge providing HTTP and WebSocket access to CG")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "web gateway");
    }

    // ==================================================================================
    // Foreign identity predicates
    // ==================================================================================

    /**
     * Asserts that a CG identity corresponds to a foreign identity.
     * Used when a phantom item is merged with a real principal.
     */
    public static class IdentifiesAs {
        public static final String KEY = "cg.bridge:identifies-as";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "asserts that this identity corresponds to another; merges a phantom with a real principal")
                .slot(ThematicRole.Theme.KEY)
                .slot(ThematicRole.Goal.KEY);
    }

    /**
     * Records that content was received via a specific bridge protocol.
     * Used on frames created from inbound foreign messages.
     */
    public static class ReceivedVia {
        public static final String KEY = "cg.bridge:received-via";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "records that content was received through a foreign protocol bridge")
                .slot(ThematicRole.Instrument.KEY)
                .slot(ThematicRole.Source.KEY);
    }
}
