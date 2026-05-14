package dev.everydaythings.graph;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.*;

/**
 * Structural-syntax sememes — the symbols that shape expression structure
 * (parentheses, separators, pipes, access dots).
 *
 * <p>These sememes appear in input text and the parser recognizes them to
 * structure expressions. They aren't predicates — they don't appear as the
 * head of a frame. They're tokens with structural meaning.
 *
 * <p>Polysemy across languages is normal:
 * <ul>
 *   <li>{@code (} — grouping in math, function-call open, parenthetical in
 *       English prose</li>
 *   <li>{@code .} — property access, decimal point, English sentence end</li>
 *   <li>{@code ,} — argument separator, list separator, English clause
 *       boundary</li>
 * </ul>
 *
 * <p>Different languages may register additional lexemes for the same
 * symbols, each scoped to its language; the token lattice + context scoring
 * disambiguate.
 *
 * <p>TODO: the OLD version carried parsing behavior (a {@code contribute()}
 * method returning a parse contribution) directly on each subclass. The new
 * text pipeline (task #40 — FrameMap / Participant / RoundContext) handles
 * structural parsing through a different mechanism; this file is currently
 * just the seed data.
 */
public final class StructuralVocabulary {

    private StructuralVocabulary() {}

    // ==================================================================================
    // GROUPING
    // ==================================================================================

    @Seed.Item(key = OpenGroup.KEY)
    public static final class OpenGroup {
        public static final String KEY = "cg.syntax:open-group";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private OpenGroup() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "opens a group or parenthesized expression";
    }

    @Seed.Item(key = CloseGroup.KEY)
    public static final class CloseGroup {
        public static final String KEY = "cg.syntax:close-group";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private CloseGroup() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "closes a group or parenthesized expression";
    }

    // ==================================================================================
    // SEPARATORS
    // ==================================================================================

    @Seed.Item(key = Separator.KEY)
    public static final class Separator {
        public static final String KEY = "cg.syntax:separator";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Separator() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "separates arguments, list elements, or clause boundaries";
    }

    @Seed.Item(key = Sequence.KEY)
    public static final class Sequence {
        public static final String KEY = "cg.syntax:sequence";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Sequence() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "separates sequential expressions or statements";
    }

    // ==================================================================================
    // CHAINING
    // ==================================================================================

    @Seed.Item(key = Pipe.KEY)
    public static final class Pipe {
        public static final String KEY = "cg.syntax:pipe";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Pipe() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "explicit chain break; pipe output to next expression";
    }

    // ==================================================================================
    // ACCESS
    // ==================================================================================

    @Seed.Item(key = Access.KEY)
    public static final class Access {
        public static final String KEY = "cg.syntax:access";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Access() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "property access; navigating into an item";
    }
}
