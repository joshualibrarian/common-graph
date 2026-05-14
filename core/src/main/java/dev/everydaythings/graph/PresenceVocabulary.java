package dev.everydaythings.graph;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;
import static dev.everydaythings.graph.Seed.*;

/**
 * Presence vocabulary — predicates for real-time shared presence within an
 * item context (a chat room, a game, a shared document, a 3D space).
 *
 * <p>Presence is per-item and contextual. A user is present <i>in</i> a
 * specific item. There is no global "online status" — availability is the
 * set of items where a user has active PRESENT frames, visible only to the
 * participants of those items.
 *
 * <h2>Three temporal modes, one frame model</h2>
 * <ul>
 *   <li><b>Durable</b> ({@link Present}, {@link Leave}) — persisted, signed,
 *       endorsed. The anchor of participation.</li>
 *   <li><b>Ephemeral</b> ({@link AvatarState}, {@link Typing}, {@link Cursor},
 *       {@link Focus}) — in-memory only, latest-wins, expire when the
 *       signer's PRESENT frame is revoked. Carry
 *       {@code CONFIG:[RETENTION] → EPHEMERAL} (see
 *       {@link SchemaVocabulary.Ephemeral}).</li>
 *   <li><b>Streaming</b> (video, audio) — TOPIC bindings on a PRESENT frame
 *       referencing a content {@link dev.everydaythings.graph.item.id.ContentID}
 *       chain.</li>
 * </ul>
 *
 * <p>TODO: in the OLD code, {@link Present} and {@link Leave} carried behavior
 * (an {@code onFrameAssembled} hook) that updated item state when assembled.
 * That behavior moves to {@code @Handler} methods on the relevant items
 * (Session, Librarian, or per-item handlers) in the new model. This file
 * currently carries only the seed sememe data.
 */
public final class PresenceVocabulary {

    private PresenceVocabulary() {}

    // ==================================================================================
    // DURABLE PRESENCE
    // ==================================================================================

    /**
     * Asserts that a signer is currently present in an item context.
     *
     * <p>PRESENT is a normal durable signed frame — the anchor of participation.
     * TOPIC bindings on the frame can carry media-stream content references.
     * Revoking a PRESENT frame signals departure and cleans up ephemeral frames
     * tied to this presence.
     */
    @Seed.Item(key = Present.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Present {
        public static final String KEY = "cg.predicate:present";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Present() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "asserts that a signer is currently present in an item context";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "enter";
    }

    /**
     * Revokes presence — the signer is leaving an item context.
     *
     * <p>When processed, finds and removes the signer's PRESENT frame from the
     * target item, then clears all ephemeral presence frames (AVATAR_STATE,
     * TYPING, CURSOR, FOCUS) tied to that presence. Subscribers see the
     * departure.
     */
    @Seed.Item(key = Leave.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Leave {
        public static final String KEY = "cg.predicate:leave";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Leave() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "revoke presence — leave an item context";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "leave";
    }

    // ==================================================================================
    // EPHEMERAL PRESENCE STATE
    //
    // Frames using these predicates carry CONFIG:[RETENTION] → EPHEMERAL.
    // In-memory only, latest-wins per (item, selector, signer), expire when
    // the signer's PRESENT frame is revoked.
    // ==================================================================================

    /** Real-time avatar position/orientation in a shared space. */
    @Seed.Item(key = AvatarState.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class AvatarState {
        public static final String KEY = "cg.predicate:avatar-state";
        public static final ItemID IID = ItemID.fromString(KEY);
        private AvatarState() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "real-time avatar position and orientation in a shared space — "
                        + "ephemeral, latest-wins";
    }

    /** Typing indicator — the signer is composing input. */
    @Seed.Item(key = Typing.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Typing {
        public static final String KEY = "cg.predicate:typing";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Typing() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "typing indicator — the signer is composing input in this context";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "typing";
    }

    /** Cursor / selection position in shared content. */
    @Seed.Item(key = Cursor.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Cursor {
        public static final String KEY = "cg.predicate:cursor";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Cursor() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "cursor or selection position in shared content — ephemeral";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "cursor";
    }

    /** What the user is currently focused on within a shared context. */
    @Seed.Item(key = Focus.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Focus {
        public static final String KEY = "cg.predicate:focus";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Focus() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "what the user is currently focused on or interacting with — ephemeral";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "focus";
    }
}
