package dev.everydaythings.graph.cryptography.algorithm;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Algorithm — the abstract base class for cryptographic algorithms.
 *
 * <p>An algorithm is both a graph sememe (its identity, its metadata) and a
 * runtime callable (its operations).  Concrete sub-archetypes (one per
 * cryptographic shape) live in their own files:
 *
 * <ul>
 *   <li>{@link Signing} — {@code verify(message, signature, publicKey)},
 *       {@code decodePublicKey(rawBytes)}.</li>
 *   <li>{@link Hash} — {@code digest(bytes)}.</li>
 *   <li>{@link KeyAgreement} — {@code agree(vault, peerPublicKey)}.</li>
 *   <li>{@link Aead} — {@code encrypt} / {@code decrypt} with a derived symmetric key.</li>
 *   <li>{@link Ciphersuite} — structured reference to a bundle.</li>
 * </ul>
 *
 * <p>Each sub-archetype is an abstract class whose concrete leaves (Ed25519,
 * Sha256, X25519, …) sit nested inside the same file.  The leaves carry only
 * static {@code @Seed.Property} fields declaring their facts; the sub-archetype
 * holds the operations that read those facts via instance fields populated by
 * {@link dev.everydaythings.graph.item.BodyBinder BodyBinder} at hydration.
 *
 * <p>Polyglot algorithms (implementations written in a non-Java language, deployed
 * as code items) participate via the same shape: their manifest carries the
 * same metadata bindings, the {@code @Embodies}/Stage dispatch resolves them to a
 * polyglot runtime, and the verify/decode/etc. methods route to the polyglot
 * implementation via {@code receive(Frame)}.
 */
@Seed.Item(key = Algorithm.KEY)
public abstract class Algorithm extends Item {

    /** Canonical key for the Algorithm archetype. */
    public static final String KEY = "cg.archetype:algorithm";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the archetype of cryptographic algorithm sememes — each instance carries "
                    + "its wire identifiers, JCA names, and byte-size facts as bindings; "
                    + "runtime forms expose verify, decode, digest, encrypt, agree";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "algorithm";

    protected Algorithm(ItemRef iid) {
        super(iid);
    }

    protected Algorithm(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }
}
