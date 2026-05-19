package dev.everydaythings.graph.actor;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Actor vocabulary — sememes for the social-entity layer of the world model.
 *
 * <p>The archetypes ({@link Actor}, {@link Person}, {@link Group},
 * {@link Service}) live in their own files in this package and carry their
 * own seed declarations.  This file owns the predicates and roles that
 * connect actors to the rest of the graph, starting with the
 * Signer-to-Actor bridge.
 *
 * <h2>REPRESENTS</h2>
 *
 * <p>A {@code REPRESENTS} frame asserts that one entity stands for another.
 * The canonical use is connecting a
 * {@link dev.everydaythings.graph.identity.Signer Signer} to an
 * {@link Actor}: the Signer (with its keys) is the cryptographic name for the
 * Actor (the social entity in the world).  Many-to-many: one Actor may be
 * represented by multiple Signers (rotation, multi-device, multi-sig), and
 * one Signer may represent multiple Actors (a Service that stands for both
 * its operating Group and its individual operator).
 *
 * <p>REPRESENTS frames can be self-attested (the Signer says "I represent
 * this Actor") or third-party (someone else attests the link).
 *
 * <p>For Signer-to-Signer relationships (delegation, on-behalf-of, alias
 * between two cryptographic identities), use the {@code SERVES} predicate
 * instead.  REPRESENTS specifically bridges a Signer to a non-Signer
 * (an Actor in the world); SERVES bridges a Signer to another Signer.
 *
 * <p>Shape:
 * <pre>
 *   predicate = REPRESENTS
 *   Agent     → the attester's Signer
 *   Theme     → the Signer doing the representing
 *   Goal      → the entity being represented (Actor or another Signer)
 *   Time      → when the assertion was made
 * </pre>
 */
public final class ActorVocabulary {

    private ActorVocabulary() {}

    // ==================================================================================
    // Predicates
    // ==================================================================================

    /**
     * REPRESENTS — a Signer stands for a non-Signer Actor.  The canonical
     * Signer-to-Actor bridge.  For Signer-to-Signer relationships, use the
     * {@code SERVES} predicate instead.
     */
    @Seed.Item(key = Represents.KEY)
    public static final class Represents {
        public static final String KEY = "cg.predicate:represents";
        private Represents() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "one entity stands for another; the canonical bridge from a signer "
                        + "(cryptographic identity with keys) to an actor (social entity "
                        + "in the world), and the alias relation between signers that "
                        + "share an operator";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "represent";
    }
}
