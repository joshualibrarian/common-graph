package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.ArrayList;
import java.util.List;

/**
 * FullName — a compound {@link Name} whose body carries indexed bindings
 * to other Name instances, encoding a culturally-ordered full name.
 *
 * <p>Each binding's role identifies the kind of name-part ({@link
 * GivenName}, {@link FamilyName}, {@link MiddleName}, {@link Honorific},
 * {@link Suffix}, {@link Patronymic}, {@link Maternal}, ...).  Each
 * binding's target is the typed Name instance.  Each binding's {@code
 * index} encodes the rendering order — cultures pick their own order:
 *
 * <ul>
 *   <li>Western "Joshua A. Chambers Jr.":
 *       <pre>FullName {
 *         [0]: Honorific  → Honorific("Mr.")        (optional, often absent)
 *         [1]: GivenName  → GivenName("Joshua")
 *         [2]: MiddleName → MiddleName("A.")
 *         [3]: FamilyName → FamilyName("Chambers")
 *         [4]: Suffix     → Suffix("Jr.")
 *       }</pre></li>
 *   <li>East Asian "Wang Wei":
 *       <pre>FullName {
 *         [0]: FamilyName → FamilyName("Wang")
 *         [1]: GivenName  → GivenName("Wei")
 *       }</pre></li>
 *   <li>Spanish "María González Pérez":
 *       <pre>FullName {
 *         [0]: GivenName → GivenName("María")
 *         [1]: FamilyName → FamilyName("González")   (paternal)
 *         [2]: Maternal   → Maternal("Pérez")
 *       }</pre></li>
 * </ul>
 *
 * <p>Render order is the binding index.  A rendering layer walks bindings
 * in order, joining their canonical text with locale-appropriate
 * separators (spaces in most cases).
 *
 * <p>Each part body is content-addressed independently — the same
 * GivenName("Joshua") body is reused across all FullNames that include
 * "Joshua" as a given name, regardless of the FullNames' own identities.
 * Dedup at the part level, composition at the FullName level.
 *
 * <p>Structural validation: at least one part binding; every target is a
 * {@link Name}.
 */
@Seed.Item(key = FullName.KEY, head = Name.KEY)
public final class FullName extends Name {

    public static final String KEY = "cg.archetype:full-name";

    private FullName(List<Binding> bindings) {
        super(ItemRef.iid(KEY), bindings);
    }

    /** Open a fluent builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Render the FullName by walking its part bindings in index order and
     * joining their canonical text with a single space.  Suitable for
     * Western and East Asian cultures whose rendering is just "parts joined
     * by spaces"; locales with different separators or stylized rendering
     * should walk {@link #bindings()} directly.
     */
    @Override
    public String encodeText() {
        // Bindings are stored canonical-sorted (by structural hash), not by
        // their authored render order.  Re-sort by index to recover order;
        // bindings without an index sort first.
        List<Binding> ordered = new ArrayList<>(bindings());
        ordered.sort((a, b) -> {
            Long ai = a.index();
            Long bi = b.index();
            if (ai == null && bi == null) return 0;
            if (ai == null) return -1;
            if (bi == null) return 1;
            return Long.compare(ai, bi);
        });
        StringBuilder sb = new StringBuilder();
        for (Binding b : ordered) {
            if (b.target() instanceof Name name) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(name.encodeText());
            }
        }
        return sb.toString();
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a compound name whose body carries indexed bindings to other Name "
                    + "instances (GivenName, FamilyName, Honorific, ...), encoding a "
                    + "culturally-ordered full name; render order is the binding index";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"full name", "proper name"};

    // ==================================================================================
    // Builder — indexed insertion preserves culturally-ordered rendering
    // ==================================================================================

    /**
     * Fluent builder for FullName.  Parts are added in render order;
     * each {@code with*} call assigns the next index and appends.  Skip
     * methods you don't need; nothing is required to be filled.
     */
    public static final class Builder {
        private final List<Binding> bindings = new ArrayList<>();
        private long nextIndex = 0;

        public Builder with(Name part) {
            if (part == null) return this;
            bindings.add(Binding.indexed(ItemRef.iid(typeKeyOf(part)), part, nextIndex++));
            return this;
        }

        /** Convenience: append a GivenName from raw text. */
        public Builder given(String text)      { return with(GivenName.of(text)); }
        /** Convenience: append a FamilyName from raw text. */
        public Builder family(String text)     { return with(FamilyName.of(text)); }
        /** Convenience: append a MiddleName from raw text. */
        public Builder middle(String text)     { return with(MiddleName.of(text)); }
        /** Convenience: append a Nickname from raw text. */
        public Builder nickname(String text)   { return with(Nickname.of(text)); }
        /** Convenience: append an Alias from raw text. */
        public Builder alias(String text)      { return with(Alias.of(text)); }
        /** Convenience: append a Pseudonym from raw text. */
        public Builder pseudonym(String text)  { return with(Pseudonym.of(text)); }
        /** Convenience: append an Honorific from raw text. */
        public Builder honorific(String text)  { return with(Honorific.of(text)); }
        /** Convenience: append a Suffix from raw text. */
        public Builder suffix(String text)     { return with(Suffix.of(text)); }
        /** Convenience: append a Patronymic from raw text. */
        public Builder patronymic(String text) { return with(Patronymic.of(text)); }
        /** Convenience: append a Maternal name from raw text. */
        public Builder maternal(String text)   { return with(Maternal.of(text)); }

        public FullName build() {
            if (bindings.isEmpty()) {
                throw new IllegalStateException("FullName requires at least one part");
            }
            return new FullName(new ArrayList<>(bindings));
        }

        /** Map a concrete Name subtype to its archetype KEY. */
        private static String typeKeyOf(Name part) {
            // Each concrete Name subtype declares its KEY as a constant; we
            // pick the matching one.  A reflective lookup would be cleaner
            // but adds runtime cost for negligible benefit.
            if (part instanceof GivenName) return GivenName.KEY;
            if (part instanceof FamilyName) return FamilyName.KEY;
            if (part instanceof MiddleName) return MiddleName.KEY;
            if (part instanceof Nickname) return Nickname.KEY;
            if (part instanceof Alias) return Alias.KEY;
            if (part instanceof Pseudonym) return Pseudonym.KEY;
            if (part instanceof Honorific) return Honorific.KEY;
            if (part instanceof Suffix) return Suffix.KEY;
            if (part instanceof Patronymic) return Patronymic.KEY;
            if (part instanceof Maternal) return Maternal.KEY;
            if (part instanceof Handle) return Handle.KEY;
            throw new IllegalArgumentException(
                    "Unknown Name subtype for FullName: " + part.getClass().getName());
        }
    }
}
