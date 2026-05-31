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

import java.util.List;
import java.util.Objects;

/**
 * Endpoint — the abstract parent archetype for network destinations.
 *
 * <p>Endpoint is not directly instantiable.  Concrete endpoints are subarchetypes
 * with their own shape: {@link TcpEndpoint} carries host + port,
 * {@link UnixEndpoint} carries a filesystem path, {@link ReticulumEndpoint}
 * carries an identity hash, {@link LoopbackEndpoint} carries no addressing.
 * Each subarchetype's manifest declares the {@link
 * dev.everydaythings.graph.network.NetworkVocabulary.Transport Transport} it
 * addresses via a single
 * {@link dev.everydaythings.graph.network.NetworkVocabulary.Addresses Addresses}
 * binding.
 *
 * <p>The parallel to {@link Quantity} → Length/Mass/Time/etc. is exact: an
 * abstract value-archetype with no minted instances of its own, refined by
 * subarchetypes that each carry their own schema.  Unlike Quantity, Endpoint
 * subarchetypes don't share a slot template — TCP needs host + port, Unix
 * needs path, Reticulum needs identity — so the schema lives on each
 * subarchetype rather than on the parent.
 *
 * <p>{@link #from(Body)} is a head-dispatching polymorphic view: pass any
 * endpoint body in and you get back the appropriate subclass instance.
 */
@Seed.Item(key = Endpoint.KEY, head = Value.KEY)
public abstract class Endpoint extends Value {

    public static final String KEY = "cg.archetype:endpoint";

    // ==================================================================================
    // Archetype-level lexical frames
    // ==================================================================================

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the abstract archetype of network destinations — refined by per-transport "
                    + "subarchetypes (TcpEndpoint, UnixEndpoint, ReticulumEndpoint, ...) "
                    + "each declaring its own shape and the Transport it addresses";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "endpoint";

    // ==================================================================================
    // Construction — subclass entry point.
    // ==================================================================================

    /**
     * Subclass constructor — each concrete endpoint subarchetype calls this
     * with its own archetype IID as head and its protocol-specific bindings.
     */
    protected Endpoint(ItemRef head, List<Binding> bindings) {
        super(head, bindings);
    }

    // ==================================================================================
    // Per-subclass contract
    // ==================================================================================

    /**
     * The {@link
     * dev.everydaythings.graph.network.NetworkVocabulary.Transport Transport}
     * sememe this endpoint addresses — Tcp, Unix, Reticulum, Loopback, or one
     * a future bridge introduces.  Mirrors the {@code Addresses} binding on
     * the subarchetype's manifest; provided as a synchronous accessor so
     * runtime dispatch doesn't need a librarian lookup.
     */
    public abstract ItemRef transport();

    // ==================================================================================
    // Head-dispatching polymorphic view
    // ==================================================================================

    /**
     * Typed view over an existing Body whose head is one of the Endpoint
     * subarchetypes.  Dispatches to the appropriate subclass's
     * {@code from(Body)} based on the body's head IID.
     */
    public static Endpoint from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof Endpoint ep) return ep;
        ItemRef head = (body.headRef() instanceof ItemRef ir) ? ir : null;
        if (head == null) {
            throw new IllegalArgumentException(
                    "Endpoint body head must be an ItemRef, got " + body.headRef());
        }
        if (ItemRef.iid(TcpEndpoint.KEY).equals(head))       return TcpEndpoint.from(body);
        if (ItemRef.iid(UnixEndpoint.KEY).equals(head))      return UnixEndpoint.from(body);
        if (ItemRef.iid(ReticulumEndpoint.KEY).equals(head)) return ReticulumEndpoint.from(body);
        if (ItemRef.iid(LoopbackEndpoint.KEY).equals(head))  return LoopbackEndpoint.from(body);
        throw new IllegalArgumentException(
                "Body head is not a known Endpoint subarchetype: " + head);
    }
}
