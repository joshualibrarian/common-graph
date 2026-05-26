package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.SchemaVocabulary;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.language.*;

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
 *       referencing a content {@link ContentRef}
 *       chain.</li>
 * </ul>
 *
 * <p>The behavioural side (handling presence-state transitions) lives as
 * {@code @Seed.Handler} methods on the relevant items (Session, Librarian,
 * or per-item handlers); this file carries only the seed sememe data.
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
    @Item(key = Present.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Present {
        public static final String KEY = "cg.predicate:present";
        private Present() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "asserts that a signer is currently present in an item context";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
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
    @Item(key = Leave.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Leave {
        public static final String KEY = "cg.predicate:leave";
        private Leave() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "revoke presence — leave an item context";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
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
    @Item(key = AvatarState.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class AvatarState {
        public static final String KEY = "cg.predicate:avatar-state";
        private AvatarState() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "real-time avatar position and orientation in a shared space — "
                        + "ephemeral, latest-wins";
    }

    /** Typing indicator — the signer is composing input. */
    @Item(key = Typing.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Typing {
        public static final String KEY = "cg.predicate:typing";
        private Typing() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "typing indicator — the signer is composing input in this context";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "typing";
    }

    /** Cursor / selection position in shared content. */
    @Item(key = Cursor.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Cursor {
        public static final String KEY = "cg.predicate:cursor";
        private Cursor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "cursor or selection position in shared content — ephemeral";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "cursor";
    }

    /** What the user is currently focused on within a shared context. */
    @Item(key = Focus.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Focus {
        public static final String KEY = "cg.predicate:focus";
        private Focus() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "what the user is currently focused on or interacting with — ephemeral";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "focus";
    }
}
