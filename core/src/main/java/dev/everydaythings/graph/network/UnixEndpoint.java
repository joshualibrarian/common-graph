package dev.everydaythings.graph.network;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UnixEndpoint — an {@link Endpoint} that addresses the {@link
 * NetworkVocabulary.Unix Unix} domain-socket transport.
 *
 * <p>Body shape: {@code Body[head=UnixEndpoint, @Path=<string>]}.  The Path
 * target is a filesystem path; both abstract and pathname namespaces are
 * representable as strings.
 */
@Seed.Item(
        key = UnixEndpoint.KEY,
        head = Endpoint.KEY,
        bindings = {@Seed.Binding(role = NetworkVocabulary.Addresses.KEY, ref = NetworkVocabulary.Unix.KEY)})
public final class UnixEndpoint extends Endpoint {

    public static final String KEY = "cg.archetype:unix-endpoint";

    // ==================================================================================
    // Archetype-level lexical frames
    // ==================================================================================

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a network destination addressing the Unix-domain-socket transport — local IPC "
                    + "addressed by filesystem path";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "Unix endpoint";

    // ==================================================================================
    // Construction
    // ==================================================================================

    public UnixEndpoint(String path) {
        super(ItemRef.iid(KEY), assemble(path));
    }

    private static List<Binding> assemble(String path) {
        Objects.requireNonNull(path, "path");
        return List.of(
                Binding.literal(ItemRef.iid(NetworkVocabulary.Path.KEY), path));
    }

    /** Factory: Unix-domain-socket endpoint at the given filesystem path. */
    public static UnixEndpoint of(String path) {
        return new UnixEndpoint(path);
    }

    /**
     * Typed view over an existing Body whose head is the UnixEndpoint
     * archetype.
     */
    public static UnixEndpoint from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof UnixEndpoint unix) return unix;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the UnixEndpoint archetype: " + body.headRef());
        }
        String path = readTarget(body, NetworkVocabulary.Path.KEY, String.class)
                .orElseThrow(() -> new IllegalArgumentException("UnixEndpoint missing @Path binding"));
        return new UnixEndpoint(path);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    @Override
    public ItemRef transport() {
        return ItemRef.iid(NetworkVocabulary.Unix.KEY);
    }

    public String path() {
        return readTarget(this, NetworkVocabulary.Path.KEY, String.class)
                .orElseThrow(() -> new IllegalStateException("UnixEndpoint missing @Path binding"));
    }

    @Override
    public String toString() {
        return "unix:" + path();
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
