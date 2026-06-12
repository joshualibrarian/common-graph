package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.network.NetworkVocabulary;

import static dev.everydaythings.graph.Seed.Binding;
import static dev.everydaythings.graph.Seed.Frame;

/**
 * Parley vocabulary — the {@link Parley} sememe (Parley as an instance of the
 * {@link NetworkVocabulary.Protocol Protocol} archetype) plus the predicates
 * exchanged on the wire after the codec handshake.
 *
 * <p>Right now the wire-vocabulary is just {@link Hello}.  Future additions
 * if/when needed: explicit HEARTBEAT or ACK predicates (if we want them visible
 * at the application layer rather than buried in the tunnel/transport),
 * GOODBYE, etc.  They get added here, not into the protocol structure —
 * Parley itself stays thin.
 */
public final class ParleyVocabulary {

    private ParleyVocabulary() {}

    /**
     * Parley — Common Graph's native application-level protocol.  An instance
     * of {@link NetworkVocabulary.Protocol}; future bridge protocols (HTTP,
     * ActivityPub, SMTP, SIP) join this list as siblings under the same
     * archetype.
     */
    @Seed.Item(key = Parley.KEY, head = NetworkVocabulary.Protocol.KEY)
    public static final class Parley {
        public static final String KEY = "cg.protocol:parley";
        private Parley() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "Common Graph's native application-level protocol — codec point-and-grunt "
                        + "followed by a stream of self-describing values (Bodies, Records, "
                        + "references, text lookups, encrypted envelopes)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "parley";
    }

    /**
     * The HELLO predicate — opening frame of a Parley conversation, sent by
     * each side after the codec handshake succeeds.
     *
     * <p>Canonical shape: {@code HELLO { AGENT → <my-current-item> }} plus any
     * supplementary bindings the sender thinks the counterparty might want —
     * recent identity/key updates, presence info, "hot gossip." The shape can
     * evolve freely; only the predicate IID is stable. Receivers are liberal
     * in what they parse.
     *
     * <p>HELLO is a <i>social</i> greeting, not a protocol message. There's no
     * required field set, no schema enforcement beyond AGENT being present.
     */
    @Seed.Item(key = Hello.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Hello {
        public static final String KEY = "cg.predicate:hello";
        private Hello() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "opening frame of a Parley conversation — carries the sender's "
                        + "current identity and any gossip the counterparty might want";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Interjection.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishInterjection = "hello";
    }

    /**
     * The GOODBYE predicate — closing frame of a Parley conversation, sent by
     * a party that intends to tear down the connection cleanly.  Symmetric
     * counterpart to {@link Hello}.
     *
     * <p>Optional bindings: AGENT (echoed self-identification; the peer already
     * knows you from HELLO), and any free-form gossip explaining the close
     * (e.g., ATTRIBUTE[COMMENT] → "shutting down").  None required — bare
     * GOODBYE is meaningful on its own.
     *
     * <p>Unlike a dropped TCP / Noise connection, GOODBYE signals intentional
     * close so the peer can distinguish "they meant to leave" from "the
     * underlying tunnel failed."
     */
    @Seed.Item(key = Goodbye.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Goodbye {
        public static final String KEY = "cg.predicate:goodbye";
        private Goodbye() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "closing frame of a Parley conversation — signals intentional "
                        + "tear-down so the peer can distinguish a clean close from a "
                        + "dropped tunnel";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Interjection.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishInterjection = "goodbye";
    }

    /**
     * The SEQUENCE sememe — conversation metadata carried as a binding role
     * on a record.  Every signed record exchanged on a Parley conversation
     * carries {@code SEQUENCE → <integer>}, declaring its position in the
     * sender's outgoing counter to this specific peer.  Per-pair scoped:
     * each side maintains its own counter for messages sent to each peer.
     *
     * <p>Used to multiplex multiple in-flight exchanges on a single
     * connection.  Responses carry {@link ResponseTo} naming the request's
     * sequence so the sender can route incoming responses to the awaiting
     * caller.  Replay detection and gap detection fall out of monitoring
     * incoming sequences for monotonicity.
     *
     * <p>Records on pure-push bodies (scene graphs, keystrokes) do NOT exist —
     * pushes have no records and thus no SEQUENCE.  SEQUENCE is only on
     * records produced for exchanges that participate in request/response.
     */
    @Seed.Item(key = Sequence.KEY)
    public static final class Sequence {
        public static final String KEY = "cg.sememe:sequence";
        private Sequence() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "conversation metadata on records — the sender's outgoing counter "
                        + "to this specific peer at the time the record was signed";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "sequence";
    }

    /**
     * The RESPONSE_TO sememe — conversation metadata carried as a binding
     * role on a record.  Names the {@link Sequence} of the request this
     * record's body is responding to.  References the OTHER side's sequence
     * space (the requester's outgoing counter), not the responder's.
     *
     * <p>Used by the dispatcher receiving the response to route to the
     * awaiting caller, who registered its request with a known sequence and
     * is holding a future keyed by that number.
     */
    @Seed.Item(key = ResponseTo.KEY)
    public static final class ResponseTo {
        public static final String KEY = "cg.sememe:response-to";
        private ResponseTo() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "conversation metadata on records — the request sequence (from "
                        + "the peer's outgoing counter) this body is responding to";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "response-to";
    }
}
