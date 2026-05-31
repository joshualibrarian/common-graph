package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.network.NetworkVocabulary;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * LoopbackEndpoint — an {@link Endpoint} that addresses the {@link
 * NetworkVocabulary.Loopback Loopback} sentinel transport.
 *
 * <p>Body shape: {@code Body[head=LoopbackEndpoint]} for an unnamed
 * endpoint, or {@code Body[head=LoopbackEndpoint, @Name=<string>]} when
 * multiple loopback endpoints need to coexist in the same JVM (the
 * name keys the loopback transport's listener registry).
 *
 * <p>Used as the in-VM marker when wiring paired tunnels with no real
 * network destination; the producer and consumer share process memory.
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
                    + "transport; used to wire paired tunnels within a single process, "
                    + "optionally named so multiple endpoints can coexist";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "loopback endpoint";

    // ==================================================================================
    // Construction
    // ==================================================================================

    /** Unnamed loopback endpoint. */
    public LoopbackEndpoint() {
        super(ItemRef.iid(KEY), List.of());
    }

    /** Named loopback endpoint — the name keys the loopback registry. */
    public LoopbackEndpoint(String name) {
        super(ItemRef.iid(KEY), List.of(
                Binding.literal(ItemRef.iid(NetworkVocabulary.Name.KEY),
                        Objects.requireNonNull(name, "name"))));
    }

    /** Factory: unnamed in-VM loopback endpoint. */
    public static LoopbackEndpoint of() {
        return new LoopbackEndpoint();
    }

    /** Factory: named in-VM loopback endpoint. */
    public static LoopbackEndpoint of(String name) {
        return new LoopbackEndpoint(name);
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
        return readName(body)
                .map(LoopbackEndpoint::new)
                .orElseGet(LoopbackEndpoint::new);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    @Override
    public ItemRef transport() {
        return ItemRef.iid(NetworkVocabulary.Loopback.KEY);
    }

    /** Optional friendly name — empty for unnamed endpoints. */
    public Optional<String> name() {
        return readName(this);
    }

    @Override
    public String toString() {
        return name().map(n -> "loopback:" + n).orElse("loopback:");
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static Optional<String> readName(Body body) {
        ItemRef role = ItemRef.iid(NetworkVocabulary.Name.KEY);
        for (Binding b : body.bindings()) {
            if (role.equals(b.role()) && b.target() instanceof String s) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
