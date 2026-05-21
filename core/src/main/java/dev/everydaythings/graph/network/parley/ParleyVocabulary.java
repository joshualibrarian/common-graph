package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
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
}
