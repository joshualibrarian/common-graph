package dev.everydaythings.graph.value;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;

import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;

/**
 * Salted — a generic {@link Value} wrapper that pairs an arbitrary inner
 * value with random salt bytes.
 *
 * <p>Used to defeat brute-force enumeration of low-entropy values when the
 * value will later be elided.  A bare boolean target has only two possible
 * structural hashes; an attacker who sees the elided hash can guess.  Wrap
 * the boolean in a Salted body and the hash now incorporates the salt bytes
 * too; without the salt, brute-force has to enumerate both possible values
 * and possible salts, which is infeasible.
 *
 * <p>Shape:
 * <pre>
 * Body[head=Salted, Value=&lt;inner&gt;, Salt=&lt;random-bytes&gt;]
 * </pre>
 *
 * <p>Domain-specific value archetypes (Color, Quantity, etc.) can declare
 * their own optional SALT binding directly in their schema; this generic
 * wrapper is for ad-hoc salting when no purpose-built archetype exists.
 *
 * <p>Salt is part of the structural identity from the moment the body is
 * composed.  Two Salted bodies with the same inner value but different salts
 * are distinct datums with distinct DatumIDs.  This is the privacy property;
 * the cost is loss of deduplication on salted values.
 */
@Seed.Item(key = Salted.KEY, head = Value.KEY)
public class Salted extends Value {

    public static final String KEY = "cg.value:salted";

    /** Default salt length in bytes — 16 (128 bits), more than enough to be unguessable. */
    public static final int DEFAULT_SALT_BYTES = 16;

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a generic salted wrapper — pairs an inner value with random salt bytes "
                    + "so the structural hash can't be brute-forced when the wrapper "
                    + "is later elided";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishAdjectiveLemma = "salted";

    /** Build a Salted body from an inner value and explicit salt bytes. */
    public Salted(Object inner, byte[] salt) {
        super(ItemRef.iid(KEY), buildBindings(inner, salt));
    }

    /** Build a Salted body from an inner value with freshly-generated random salt. */
    public Salted(Object inner) {
        this(inner, randomSalt(DEFAULT_SALT_BYTES));
    }

    /** Generate {@code n} cryptographically random bytes for use as salt. */
    public static byte[] randomSalt(int n) {
        byte[] bytes = new byte[n];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static List<Binding> buildBindings(Object inner, byte[] salt) {
        Objects.requireNonNull(inner, "inner");
        Objects.requireNonNull(salt, "salt");
        return List.of(
                Binding.literal(ItemRef.iid(ThematicRole.Value.KEY), inner),
                Binding.literal(ItemRef.iid(CoreVocabulary.Salt.KEY), salt.clone()));
    }
}
