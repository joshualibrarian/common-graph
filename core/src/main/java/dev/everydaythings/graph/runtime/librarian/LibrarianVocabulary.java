package dev.everydaythings.graph.runtime.librarian;


import dev.everydaythings.graph.*;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.language.*;

public class LibrarianVocabulary {
    /**
     * The {@code LOOKUP} predicate — token-dictionary lookup.
     *
     * <p>A LOOKUP frame submitted to a Librarian consults the TokenDictionary and
     * returns response frames carrying postings. The frame's bindings:
     *
     * <ul>
     *   <li>{@code THEME → "<token>"} — the token text being looked up (required)</li>
     *   <li>{@code ATTRIBUTE[LIMIT] → <integer>} — optional; if present, prefix
     *       (range-scan) match with the given upper bound on results. If absent,
     *       exact (point) match.</li>
     * </ul>
     *
     * <p>LOOKUP frames are <b>ephemeral</b> — the predicate's manifest carries
     * {@code CONFIG[RETENTION] → @Ephemeral}, so submit doesn't persist body or
     * records. The handler fires; response frames flow back to the submitter; no
     * audit trail is kept. This is appropriate because LOOKUP is the per-keystroke
     * query a UI client issues during autocomplete or token disambiguation —
     * persisting every keystroke would be both privacy-toxic and storage-bloat.
     */
    @Seed.Item(key = Lookup.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Lookup {

        public static final String KEY = "cg.predicate:lookup";

        private Lookup() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate for token-dictionary lookup — submit a LOOKUP frame "
                        + "with a token in THEME to receive postings as ephemeral responses";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "look up";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "lookup";

        /**
         * Retention → Ephemeral: LOOKUP frames are not persisted.
         * Direct record binding (no CONFIG wrapper); the predicate's identity
         * doesn't include retention policy — that's per-attestation metadata
         * on the record.
         */
        @Seed.RecordBinding(role = SchemaVocabulary.Retention.KEY)
        static final ItemRef retention = ItemRef.iid(SchemaVocabulary.Ephemeral.KEY);

        /** The token text to look up. */
        @Seed.Property(schemaRole = ThematicRole.Theme.KEY)
        static final TypeRef expectsTheme = TypeRef.iid(Item.KEY);
    }

    /**
     * The {@code DELETE} predicate — a request to remove an item from local storage.
     *
     * <p>A DELETE frame is a <i>request</i>: it claims "the signer wants this item gone."
     * Each librarian receiving the frame decides independently whether to honor the
     * request, based on its own trust matrix. Phase 1 honors only DELETEs whose
     * records carry a signature verifiable against this librarian's own KEL — the
     * implicit "I'm asking my own librarian to delete this" case. Other-signed
     * DELETEs are stored as data but not acted upon.
     *
     * <p>When honored, the librarian's handler:
     * <ul>
     *   <li>Cascade-deletes the target item's manifest bodies and their records</li>
     *   <li>Evicts the item from the in-memory cache</li>
     *   <li>Does NOT cascade through endorsed frames — those may be referenced
     *       elsewhere; dangling references are an accepted cost.</li>
     * </ul>
     *
     * <p>The DELETE frame itself is retained as audit data regardless of whether
     * the action was taken — "the signer requested this; at time T; with this
     * authorization claim."
     *
     * <p>Bindings:
     * <ul>
     *   <li>{@code THEME → @<item-iid>} — required: the item to delete.</li>
     *   <li>{@code AGENT → @<signer>} — auto-populated from records.</li>
     * </ul>
     */
    @Seed.Item(key = Delete.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Delete {

        public static final String KEY = "cg.predicate:delete";

        private Delete() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate for requesting removal of an item from local storage — "
                        + "each librarian decides whether to honor based on its trust matrix";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "delete";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "deletion";

        /** The item to delete. */
        @Seed.Property(schemaRole = ThematicRole.Theme.KEY)
        static final TypeRef expectsTheme = TypeRef.iid(Item.KEY);
    }

    /**
     * The {@code CREATE} predicate — instantiate a fresh item.
     *
     * <p>A CREATE frame submitted to a Librarian causes a new item to be minted,
     * its initial manifest committed, and a post-construct hook fired.
     *
     * <p>Pure-data predicate seed.  The frame is handled on the Librarian by
     * {@link Librarian#createItem} (a {@code @Seed.Handler}), which owns the
     * store, indexes, and registry that minting requires.  Creating an item is
     * a librarian capability, not something the predicate does to itself.
     *
     * <p>Bindings on a CREATE frame:
     * <ul>
     *   <li>{@code THEME → @<archetype>} — required: the kind of item to create.</li>
     *   <li>{@code AGENT → @<signer>} — auto-populated from the CREATE frame's records;
     *       identifies who authorized the creation.</li>
     *   <li>{@code INSTRUMENT → @<implementation>} — optional: a specific
     *       implementation to use. Can be a Java-class name (text target) or an item
     *       reference. When absent, the librarian falls back to the archetype's
     *       own IMPLEMENTATION binding.</li>
     *   <li>Other bindings carry forward as initial bindings on the new item.</li>
     * </ul>
     */
    @Seed.Item(key = Create.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Create {

        public static final String KEY = "cg.predicate:create";

        private Create() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate for instantiating a new item — submit a CREATE frame with "
                        + "a THEME archetype and an authorizing record; the new item is minted, "
                        + "committed, and post-constructed";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "create";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "creation";
    }

    /**
     * The {@code NOT_FOUND} predicate — the miss signal for resolution operations.
     *
     * <p>Returned over a Parley connection when a {@link Lookup} (text → ref)
     * or a FETCH (ref → datum / item / content) cannot be satisfied locally.
     * The THEME binding carries the reference (or text) that was not found so
     * the receiver can correlate the response to its outstanding request.
     *
     * <p>NOT_FOUND is shared across resolution kinds — there is no separate
     * NOT_FOUND_TOKEN vs NOT_FOUND_DATUM.  The THEME's prefix tells the
     * receiver which kind of resolution missed.
     *
     * <p>Ephemeral: this is per-message scaffolding, not a graph claim.  Not
     * persisted; not audited.
     *
     * <p>Bindings:
     * <ul>
     *   <li>{@code THEME → <ref-or-text>} — required: the reference (or token)
     *       that could not be resolved.</li>
     * </ul>
     */
    @Seed.Item(key = NotFound.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class NotFound {

        public static final String KEY = "cg.predicate:not-found";

        private NotFound() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the miss signal for a resolution operation — returned when a LOOKUP "
                        + "or FETCH cannot be satisfied locally; THEME carries the unresolved "
                        + "reference or text";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "not found";

        /**
         * Retention → Ephemeral: NOT_FOUND frames are per-message scaffolding,
         * never persisted.
         */
        @Seed.RecordBinding(role = SchemaVocabulary.Retention.KEY)
        static final ItemRef retention = ItemRef.iid(SchemaVocabulary.Ephemeral.KEY);
    }

    /**
     * The {@code INPUT} predicate — text input delivered to a context item.
     *
     * <p>The bulk-text sibling of per-keystroke input events: where UI types
     * stream KEYPRESS frames into the librarian and the context item buffers
     * + parses on commit, batch sources (cg-eval, paste, scripted automation)
     * ship one INPUT frame carrying the entire string.  The context item's
     * input handler receives it as though typing had completed.
     *
     * <p>Bindings:
     * <ul>
     *   <li>{@code THEME → @<context-item-iid>} — required: which item receives
     *       the input.  Defaults to the session iid when the caller does not
     *       specify (the session is the implicit context per
     *       [[project_intrinsic_vs_ui_surface_2026_06_01]]).</li>
     *   <li>{@code VALUE → "<text>"} — required: the text to deliver.</li>
     * </ul>
     *
     * <p>The librarian's handler routes the INPUT frame to the context item.
     * The item's own input handler decides what to do with the text — typically
     * runs the consensus parse circle to generate a frame, then dispatches
     * that frame.  This means INPUT is the wire-level event; parse is what the
     * item does in response.  Per
     * [[project_intrinsic_vs_ui_surface_2026_06_01]] the parse surface is
     * intrinsic to every item.
     *
     * <p>For eval mode specifically: cg-eval submits an INPUT frame with the
     * full text and the chosen context (--context flag, defaults to session).
     * No clarification is possible mid-eval — if the input is ambiguous, the
     * item's handler returns an error frame.
     *
     * <p>Ephemeral: INPUT frames are per-message; only the parse-result frames
     * the item produces in response are candidates for persistence.
     */
    @Seed.Item(key = Input.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Input {

        public static final String KEY = "cg.predicate:input";

        private Input() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "text input delivered to a context item — the bulk-text sibling of "
                        + "per-keystroke KEYPRESS events; the item's input handler decides "
                        + "what to do with the text (typically: parse, dispatch the result)";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "input";

        /** Retention → Ephemeral: INPUT events are per-message scaffolding. */
        @Seed.RecordBinding(role = SchemaVocabulary.Retention.KEY)
        static final ItemRef retention = ItemRef.iid(SchemaVocabulary.Ephemeral.KEY);

        /** THEME identifies the context item that receives the input. */
        @Seed.Property(schemaRole = ThematicRole.Theme.KEY)
        static final TypeRef expectsTheme = TypeRef.iid(Item.KEY);
    }
}
