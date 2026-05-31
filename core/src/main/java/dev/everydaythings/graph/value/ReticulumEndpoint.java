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

import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ReticulumEndpoint — an {@link Endpoint} that addresses the {@link
 * NetworkVocabulary.Reticulum Reticulum} transport.
 *
 * <p>Body shape: {@code Body[head=ReticulumEndpoint, @Identity=<bytes>]}.  The
 * Identity target is the destination-hash bytes Reticulum uses to address a
 * destination on its mesh.
 */
@Seed.Item(
        key = ReticulumEndpoint.KEY,
        head = Endpoint.KEY,
        bindings = {@Seed.Binding(role = NetworkVocabulary.Addresses.KEY, ref = NetworkVocabulary.Reticulum.KEY)})
public final class ReticulumEndpoint extends Endpoint {

    public static final String KEY = "cg.archetype:reticulum-endpoint";

    // ==================================================================================
    // Archetype-level lexical frames
    // ==================================================================================

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a network destination addressing the Reticulum transport — mesh-routed bytes "
                    + "with built-in authenticated encryption, addressed by destination "
                    + "identity hash";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "Reticulum endpoint";

    // ==================================================================================
    // Construction
    // ==================================================================================

    public ReticulumEndpoint(byte[] identity) {
        super(ItemRef.iid(KEY), assemble(identity));
    }

    private static List<Binding> assemble(byte[] identity) {
        Objects.requireNonNull(identity, "identity");
        return List.of(
                Binding.literal(ItemRef.iid(NetworkVocabulary.Identity.KEY), identity.clone()));
    }

    /** Factory: Reticulum endpoint at the given destination identity hash. */
    public static ReticulumEndpoint of(byte[] identity) {
        return new ReticulumEndpoint(identity);
    }

    /**
     * Typed view over an existing Body whose head is the ReticulumEndpoint
     * archetype.
     */
    public static ReticulumEndpoint from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof ReticulumEndpoint rx) return rx;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the ReticulumEndpoint archetype: " + body.headRef());
        }
        byte[] identity = readTarget(body, NetworkVocabulary.Identity.KEY, byte[].class)
                .orElseThrow(() -> new IllegalArgumentException("ReticulumEndpoint missing @Identity binding"));
        return new ReticulumEndpoint(identity);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    @Override
    public ItemRef transport() {
        return ItemRef.iid(NetworkVocabulary.Reticulum.KEY);
    }

    public byte[] identity() {
        return readTarget(this, NetworkVocabulary.Identity.KEY, byte[].class)
                .map(byte[]::clone)
                .orElseThrow(() -> new IllegalStateException("ReticulumEndpoint missing @Identity binding"));
    }

    @Override
    public String toString() {
        return "reticulum:" + HexFormat.of().formatHex(identity());
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static <T> Optional<T> readTarget(Body body, String roleKey, Class<T> type) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : body.bindings()) {
            if (role.equals(b.role()) && type.isInstance(b.target())) {
                return Optional.of(type.cast(b.target()));
            }
        }
        return Optional.empty();
    }
}
