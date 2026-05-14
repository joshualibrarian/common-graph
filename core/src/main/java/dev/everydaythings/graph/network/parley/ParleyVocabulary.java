package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;
import static dev.everydaythings.graph.Seed.*;

/**
 * Parley vocabulary — predicates exchanged on the wire after the codec
 * handshake.
 *
 * <p>Right now this is just {@link Hello}. Future additions if/when needed:
 * explicit HEARTBEAT or ACK predicates (if we want them visible at the
 * application layer rather than buried in the tunnel/transport), GOODBYE, etc.
 * They get added here, not into the protocol structure — Parley itself stays
 * thin.
 */
public final class ParleyVocabulary {

    private ParleyVocabulary() {}

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
        public static final ItemID IID = ItemID.fromString(KEY);
        private Hello() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "opening frame of a Parley conversation — carries the sender's "
                        + "current identity and any gossip the counterparty might want";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Interjection.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishInterjection = "hello";
    }
}
