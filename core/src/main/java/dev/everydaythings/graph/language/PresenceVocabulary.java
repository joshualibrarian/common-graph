package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.frame.eval.FrameAssemblyContext;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.LibrarianOld;

/**
 * Presence vocabulary — predicates for real-time shared presence in items.
 *
 * <p>Presence is per-item and contextual. A user is present <i>in</i> a specific
 * item (a chat room, a game, a shared document, a 3D space). There is no global
 * "online status" — availability is the set of items where a user has active
 * PRESENT frames, visible only to other participants of those items.
 *
 * <p>The PRESENT frame is a normal durable signed frame — the anchor for a user's
 * participation. It can carry TOPIC stream bindings for video/audio feeds.
 *
 * <p>Ephemeral predicates (AVATAR_STATE, TYPING, CURSOR, FOCUS) declare
 * {@code CONFIG:[DURABILITY] → EPHEMERAL} — they are in-memory only, latest-wins,
 * and expire when the signer's PRESENT frame is revoked.
 *
 * <p>Three temporal modes, one frame model:
 * <ul>
 *   <li><b>Durable</b> (PRESENT, MOVE, MESSAGE) — persisted, signed, endorsed</li>
 *   <li><b>Ephemeral</b> (AVATAR_STATE, TYPING, CURSOR, FOCUS) — in-memory, latest-wins</li>
 *   <li><b>Streaming</b> (video, audio) — TOPIC bindings on PRESENT → Chain</li>
 * </ul>
 *
 * @see CoreVocabulary.Durability
 * @see CoreVocabulary.Ephemeral
 */
public final class PresenceVocabulary {

    private PresenceVocabulary() {}

    // ==================================================================================
    // DURABLE PRESENCE
    // ==================================================================================

    /**
     * Asserts that a signer is currently present in an item context.
     *
     * <p>PRESENT is a normal durable signed frame — the anchor for participation.
     * TOPIC stream bindings on the PRESENT frame carry media feeds (video, audio).
     * Revoking a PRESENT frame signals departure and triggers cleanup of ephemeral
     * frames tied to this presence (AVATAR_STATE, TYPING, CURSOR, FOCUS).
     */
    @Implements(Present.KEY)
    @ItemSeed(key = Present.KEY)
    public static class Present extends Sememe {
        public static final String KEY = "cg.presence:present";
        public static final ItemID IID = ItemID.fromString(KEY);

        public Present() { super(KEY); }
        protected Present(ItemID iid) { super(iid); }
        protected Present(LibrarianOld lib, ManifestOld m) { super(lib, m); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "asserts that a signer is currently present in an item context";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"enter", "join"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Agent.KEY}))
        static final ItemID expectAgent = ThematicRole.Agent.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Location.KEY}))
        static final ItemID expectLocation = ThematicRole.Location.IID;

        @Override
        public void onFrameAssembled(FrameAssemblyContext ctx) {
            // Store the PRESENT frame — durable, signed
            ctx.handled("present");
        }
    }

    /**
     * Revokes presence — the signer is leaving an item context.
     *
     * <p>When assembled, finds and removes the signer's PRESENT frame from the
     * target item, then clears all ephemeral frames (AVATAR_STATE, TYPING, etc.)
     * tied to that presence. Subscribers see the departure.
     */
    @Implements(Leave.KEY)
    @ItemSeed(key = Leave.KEY)
    public static class Leave extends Sememe {
        public static final String KEY = "cg.presence:leave";
        public static final ItemID IID = ItemID.fromString(KEY);

        public Leave() { super(KEY); }
        protected Leave(ItemID iid) { super(iid); }
        protected Leave(LibrarianOld lib, ManifestOld m) { super(lib, m); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "revoke presence — leave an item context";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"leave", "depart"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Location.KEY}))
        static final ItemID expectLocation = ThematicRole.Location.IID;

        @Override
        public void onFrameAssembled(FrameAssemblyContext ctx) {
            // Find the target item (LOCATION, filled from context)
            ItemOld target = ctx.item(ThematicRole.Location.IID);
            if (target == null || target.frames() == null) return;

            LibrarianOld lib = ctx.scope().librarian();
            if (lib == null) return;

            // Use the principal's identity (matches what announcePresence uses)
            ItemID signerId = lib.principal().map(ItemOld::iid).orElse(lib.iid());

            // Find and remove PRESENT frames from this signer on this item
            ItemID presentPredicate = ItemID.fromString(Present.KEY);
            java.util.List<CompoundKey> toRemove = new java.util.ArrayList<>();
            for (FrameOld frame : target.frames()) {
                if (frame.body() == null) continue;
                if (!presentPredicate.equals(frame.body().predicate())) continue;
                // Check if the AGENT binding matches our signer
                var agentTarget = frame.body().binding(ItemID.fromString(ThematicRole.Agent.KEY));
                if (agentTarget instanceof dev.everydaythings.graph.frame.BindingTarget.IidTarget iidTarget
                        && signerId.equals(iidTarget.iid())) {
                    toRemove.add(frame.frameKey());
                }
            }

            for (var key : toRemove) {
                target.frames().remove(key);
            }

            // Clear all ephemeral frames for this signer on this item
            lib.clearEphemeralFrames(target.iid(), signerId);

            ctx.handledCommand(toRemove.isEmpty() ? "Not present" : "Left " + target.displayToken());
        }
    }

    // ==================================================================================
    // EPHEMERAL PRESENCE STATE
    //
    // All predicates below declare CONFIG:[DURABILITY] → EPHEMERAL.
    // Frames are in-memory only, latest-wins per (item, selector, signer),
    // and expire when the signer's PRESENT frame is revoked.
    // ==================================================================================

    /**
     * Real-time avatar state — position, orientation in a shared space.
     * Replaced on every update (LATEST retention). Not persisted.
     */
    @ItemSeed(key = AvatarState.KEY)
    public static class AvatarState {
        public static final String KEY = "cg.presence:avatar-state";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "real-time avatar position and orientation in a shared space";

        // CONFIG:[DURABILITY] → EPHEMERAL (predicate-level default)
        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Config.KEY, qualifiers = {CoreVocabulary.Durability.KEY}))
        static final ItemID durability = CoreVocabulary.Ephemeral.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Agent.KEY}))
        static final ItemID expectAgent = ThematicRole.Agent.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Location.KEY}))
        static final ItemID expectLocation = ThematicRole.Location.IID;
    }

    /**
     * Typing indicator — signals that a user is composing input in an item context.
     * Replaced on every update (LATEST retention). Not persisted.
     */
    @ItemSeed(key = Typing.KEY)
    public static class Typing {
        public static final String KEY = "cg.presence:typing";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "typing indicator — the signer is composing input in this context";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"typing"};

        // CONFIG:[DURABILITY] → EPHEMERAL
        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Config.KEY, qualifiers = {CoreVocabulary.Durability.KEY}))
        static final ItemID durability = CoreVocabulary.Ephemeral.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Agent.KEY}))
        static final ItemID expectAgent = ThematicRole.Agent.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Location.KEY}))
        static final ItemID expectLocation = ThematicRole.Location.IID;
    }

    /**
     * Cursor position — pointer/selection state in shared content.
     * Replaced on every update (LATEST retention). Not persisted.
     */
    @ItemSeed(key = Cursor.KEY)
    public static class Cursor {
        public static final String KEY = "cg.presence:cursor";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "cursor or selection position in shared content";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"cursor"};

        // CONFIG:[DURABILITY] → EPHEMERAL
        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Config.KEY, qualifiers = {CoreVocabulary.Durability.KEY}))
        static final ItemID durability = CoreVocabulary.Ephemeral.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Agent.KEY}))
        static final ItemID expectAgent = ThematicRole.Agent.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Location.KEY}))
        static final ItemID expectLocation = ThematicRole.Location.IID;
    }

    /**
     * Focus — what the user is currently looking at or interacting with in a space.
     * Replaced on every update (LATEST retention). Not persisted.
     */
    @ItemSeed(key = Focus.KEY)
    public static class Focus {
        public static final String KEY = "cg.presence:focus";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "what the user is currently focused on or interacting with";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"focus"};

        // CONFIG:[DURABILITY] → EPHEMERAL
        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Config.KEY, qualifiers = {CoreVocabulary.Durability.KEY}))
        static final ItemID durability = CoreVocabulary.Ephemeral.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Agent.KEY}))
        static final ItemID expectAgent = ThematicRole.Agent.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Location.KEY}))
        static final ItemID expectLocation = ThematicRole.Location.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }
}
