package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.network.NetworkVocabulary;

import java.util.List;
import java.util.Objects;

/**
 * LoopbackEndpoint — an {@link Endpoint} that addresses the {@link
 * NetworkVocabulary.Loopback Loopback} sentinel transport.
 *
 * <p>Body shape: {@code Body[head=LoopbackEndpoint]} — no addressing bindings.
 * Used as the in-VM marker when wiring paired tunnels with no real network
 * destination; the producer and consumer share process memory.
 */
@Seed.Item(
        key = LoopbackEndpoint.KEY,
        head = Endpoint.KEY,
        bindings = {@Seed.Binding(role = NetworkVocabulary.Addresses.KEY, ref = NetworkVocabulary.Loopback.KEY)})
public final class LoopbackEndpoint extends Endpoint {

    public static final String KEY = "cg.archetype:loopback-endpoint";

    // ==================================================================================
    // Archetype-level lexical frames
    // ==================================================================================

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a network destination addressing the in-VM loopback sentinel — no real "
                    + "transport; used to wire paired tunnels within a single process";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "loopback endpoint";

    // ==================================================================================
    // Construction
    // ==================================================================================

    public LoopbackEndpoint() {
        super(ItemRef.iid(KEY), List.of());
    }

    /** Factory: in-VM loopback endpoint. */
    public static LoopbackEndpoint of() {
        return new LoopbackEndpoint();
    }

    /**
     * Typed view over an existing Body whose head is the LoopbackEndpoint
     * archetype.
     */
    public static LoopbackEndpoint from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof LoopbackEndpoint lb) return lb;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the LoopbackEndpoint archetype: " + body.headRef());
        }
        return new LoopbackEndpoint();
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    @Override
    public ItemRef transport() {
        return ItemRef.iid(NetworkVocabulary.Loopback.KEY);
    }

    @Override
    public String toString() {
        return "loopback:";
    }
}
