package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.*;

/**
 * Layout qualities — sememes naming how container nodes arrange their
 * children.
 *
 * <p>The qualities here are binding-roles on container nodes; the targets
 * are typically other sememes in this vocabulary that name layout modes,
 * directions, alignments:
 *
 * <pre>
 * Body[head = ContainerNode]
 *   LayoutMode → @cg.layout:flex
 *   Axis       → @cg.layout:horizontal
 *   Alignment  → @cg.layout:center
 * </pre>
 *
 * <p>The mode sememes themselves (Flex, Grid, Stack) are plain archetype items;
 * they serve as identifiers used as targets of LayoutMode bindings.
 */
public final class LayoutVocabulary {

    private LayoutVocabulary() {}

    // ==================================================================================
    // Quality binding-roles
    // ==================================================================================

    /**
     * The layout mode of a container. Target is typically one of the mode
     * sememes ({@link Flex}, {@link Grid}, {@link Stack}).
     */
    @Seed.Item(key = LayoutMode.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class LayoutMode {
        public static final String KEY = "cg.quality:layout-mode";
        private LayoutMode() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how a container arranges its children";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "layout mode";
    }

    /** The primary axis of a container's flow. Target is typically Horizontal or Vertical. */
    @Seed.Item(key = Axis.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Axis {
        public static final String KEY = "cg.quality:axis";
        private Axis() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the primary axis of a container's flow";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "axis";
    }

    /**
     * How children are aligned along the cross-axis. Target is typically one of
     * the alignment sememes ({@link Start}, {@link Center}, {@link End},
     * {@link Stretch}).
     */
    @Seed.Item(key = Alignment.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Alignment {
        public static final String KEY = "cg.quality:alignment";
        private Alignment() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how children are aligned along the cross-axis";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "alignment";
    }

    /** Direction of flow along the axis (forward or reverse). */
    @Seed.Item(key = Direction.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Direction {
        public static final String KEY = "cg.quality:direction";
        private Direction() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "direction of flow along an axis";
    }

    // ==================================================================================
    // Layout-mode sememes (targets of LayoutMode bindings)
    // ==================================================================================

    /** Flex layout — children sized and positioned by flex rules. */
    @Seed.Item(key = Flex.KEY)
    public static final class Flex {
        public static final String KEY = "cg.layout:flex";
        private Flex() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "flex layout — children sized and positioned by flex rules";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "flex";
    }

    /** Grid layout — children placed in a 2D grid of rows and columns. */
    @Seed.Item(key = Grid.KEY)
    public static final class Grid {
        public static final String KEY = "cg.layout:grid";
        private Grid() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "grid layout — children placed in a 2D grid";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "grid";
    }

    /** Stack layout — children layered on top of each other (typically along Z). */
    @Seed.Item(key = Stack.KEY)
    public static final class Stack {
        public static final String KEY = "cg.layout:stack";
        private Stack() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "stack layout — children layered on top of each other";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "stack";
    }

    // ==================================================================================
    // Axis values
    // ==================================================================================

    /** The horizontal axis. */
    @Seed.Item(key = Horizontal.KEY)
    public static final class Horizontal {
        public static final String KEY = "cg.axis:horizontal";
        private Horizontal() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the horizontal axis";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "horizontal";
    }

    /** The vertical axis. */
    @Seed.Item(key = Vertical.KEY)
    public static final class Vertical {
        public static final String KEY = "cg.axis:vertical";
        private Vertical() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the vertical axis";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "vertical";
    }

    // ==================================================================================
    // Alignment values
    // ==================================================================================

    /** Align to the start of the cross-axis. */
    @Seed.Item(key = Start.KEY)
    public static final class Start {
        public static final String KEY = "cg.alignment:start";
        private Start() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "align to the start of the cross-axis";
    }

    /** Align centered on the cross-axis. */
    @Seed.Item(key = Center.KEY)
    public static final class Center {
        public static final String KEY = "cg.alignment:center";
        private Center() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "align centered on the cross-axis";
    }

    /** Align to the end of the cross-axis. */
    @Seed.Item(key = End.KEY)
    public static final class End {
        public static final String KEY = "cg.alignment:end";
        private End() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "align to the end of the cross-axis";
    }

    /** Stretch to fill the cross-axis. */
    @Seed.Item(key = Stretch.KEY)
    public static final class Stretch {
        public static final String KEY = "cg.alignment:stretch";
        private Stretch() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "stretch to fill the cross-axis";
    }
}
