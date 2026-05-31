package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;

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

    // ==================================================================================
    // Container layout — properties controlling how children flow and where
    // they sit within their parent.
    // ==================================================================================

    /**
     * Gap — the spacing between children in a flow layout.  Target is a
     * Length-Quantity.
     */
    @Seed.Item(key = Gap.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Gap {
        public static final String KEY = "cg.quality:gap";
        private Gap() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the spacing between children in a flow layout";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "gap";
    }

    /**
     * Justify — how children are distributed along the main axis.  Target
     * is one of the justify-mode sememes
     * ({@link JustifyStart} / {@link JustifyCenter} / {@link JustifyEnd}
     * / {@link SpaceBetween} / {@link SpaceAround}).
     */
    @Seed.Item(key = Justify.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Justify {
        public static final String KEY = "cg.quality:justify";
        private Justify() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how children are distributed along the main axis";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "justify";
    }

    /** Distribute children flush with the start of the main axis. */
    @Seed.Item(key = JustifyStart.KEY)
    public static final class JustifyStart {
        public static final String KEY = "cg.justify:start";
        private JustifyStart() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "distribute children flush with the start of the main axis";
    }

    /** Distribute children centered on the main axis. */
    @Seed.Item(key = JustifyCenter.KEY)
    public static final class JustifyCenter {
        public static final String KEY = "cg.justify:center";
        private JustifyCenter() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "distribute children centered on the main axis";
    }

    /** Distribute children flush with the end of the main axis. */
    @Seed.Item(key = JustifyEnd.KEY)
    public static final class JustifyEnd {
        public static final String KEY = "cg.justify:end";
        private JustifyEnd() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "distribute children flush with the end of the main axis";
    }

    /** Distribute children with equal space between them, no space at the ends. */
    @Seed.Item(key = SpaceBetween.KEY)
    public static final class SpaceBetween {
        public static final String KEY = "cg.justify:space-between";
        private SpaceBetween() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "distribute children with equal space between them, no space at the ends";
    }

    /** Distribute children with equal space around each, half-gaps at the ends. */
    @Seed.Item(key = SpaceAround.KEY)
    public static final class SpaceAround {
        public static final String KEY = "cg.justify:space-around";
        private SpaceAround() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "distribute children with equal space around each, half-gaps at the ends";
    }

    /**
     * Wrap — whether children flow to the next line when the current line
     * is full.  Boolean.
     */
    @Seed.Item(key = Wrap.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Wrap {
        public static final String KEY = "cg.quality:wrap";
        private Wrap() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "whether children flow to the next line when the current line is full";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "wrap";
    }

    /** Columns — number of columns in a grid layout.  Integer target. */
    @Seed.Item(key = Columns.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Columns {
        public static final String KEY = "cg.quality:columns";
        private Columns() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "number of columns in a grid layout";
    }

    /** Rows — number of rows in a grid layout.  Integer target. */
    @Seed.Item(key = Rows.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Rows {
        public static final String KEY = "cg.quality:rows";
        private Rows() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "number of rows in a grid layout";
    }

    /** AspectRatio — enforced width:height ratio.  Numeric target. */
    @Seed.Item(key = AspectRatio.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class AspectRatio {
        public static final String KEY = "cg.quality:aspect-ratio";
        private AspectRatio() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the enforced width:height ratio of an element";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "aspect ratio";
    }

    /**
     * Repeat — a data-driven binding-expression that iterates a collection
     * into templated children.  Target is a binding-expression (resolved to
     * a collection at resolver time, expanded into concrete children).
     */
    @Seed.Item(key = Repeat.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Repeat {
        public static final String KEY = "cg.quality:repeat";
        private Repeat() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a data-driven binding-expression that iterates a collection into templated "
                        + "children";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "repeat";
    }

    // ==================================================================================
    // Anchor positioning — take a child out of flow, position by edge offsets.
    // Compound with the Side qualifier: Anchor[Top], Anchor[Right], etc.
    // ==================================================================================

    /**
     * Anchor — position a child relative to a side of its parent (or to a
     * sibling edge).  Compound with a {@link Side} qualifier identifying
     * which edge is being anchored: {@code Anchor[Top]}, {@code Anchor[Right]},
     * {@code Anchor[Pinkie]}, etc.  Target is a Length-Quantity offset or
     * a reference to a sibling edge.
     */
    @Seed.Item(key = Anchor.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Anchor {
        public static final String KEY = "cg.quality:anchor";
        private Anchor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "position a child relative to a side of its parent (or to a sibling edge); "
                        + "qualified by Side to identify which edge is being anchored";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "anchor";
    }

    // ==================================================================================
    // Overflow — how content that exceeds an element's bounds is handled.
    // ==================================================================================

    /**
     * Overflow — how content exceeding an element's bounds is handled.
     * Target is one of {@link VisibleOverflow} / {@link HiddenOverflow}
     * / {@link Scroll} / {@link Auto}.
     */
    @Seed.Item(key = Overflow.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Overflow {
        public static final String KEY = "cg.quality:overflow";
        private Overflow() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "how content that exceeds an element's bounds is handled";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "overflow";
    }

    /** Content exceeding the bounds is rendered anyway, spilling outside. */
    @Seed.Item(key = VisibleOverflow.KEY)
    public static final class VisibleOverflow {
        public static final String KEY = "cg.overflow:visible";
        private VisibleOverflow() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "content exceeding the bounds is rendered anyway, spilling outside";
    }

    /** Content exceeding the bounds is clipped — not rendered, no scrollbar. */
    @Seed.Item(key = HiddenOverflow.KEY)
    public static final class HiddenOverflow {
        public static final String KEY = "cg.overflow:hidden";
        private HiddenOverflow() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "content exceeding the bounds is clipped — not rendered, no scrollbar";
    }

    /** Content exceeding the bounds is clipped, scrollbars always shown. */
    @Seed.Item(key = Scroll.KEY)
    public static final class Scroll {
        public static final String KEY = "cg.overflow:scroll";
        private Scroll() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "content exceeding the bounds is clipped; scrollbars are always shown";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "scroll";
    }

    /** Content exceeding the bounds is clipped; scrollbars appear only when needed. */
    @Seed.Item(key = Auto.KEY)
    public static final class Auto {
        public static final String KEY = "cg.overflow:auto";
        private Auto() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "content exceeding the bounds is clipped; scrollbars appear only when needed";
    }

    // ==================================================================================
    // Side — qualifier identifying one of the edges of a box.
    //
    // Six instances total: the four fixed-orientation edges (Top / Right /
    // Bottom / Left) and two handedness-relative edges (Pinkie / Thumb).
    // Pinkie / Thumb resolve to a physical edge based on the enclosing
    // container's Handedness binding — Pinkie maps to the right edge in a
    // right-handed layout, the left edge in a left-handed one (and Thumb
    // mirrors).
    //
    // Used as a qualifier on per-side qualities (e.g., BorderWidth, Padding,
    // Margin, Anchor) to address one edge at a time.
    // ==================================================================================

    /**
     * The Side qualifier — one of the edges of a box.  Six instances:
     * Top / Right / Bottom / Left are fixed-orientation; Pinkie / Thumb are
     * handedness-relative and resolve against the enclosing container's
     * {@link Handedness} binding.
     */
    @Seed.Item(key = Side.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Side {
        public static final String KEY = "cg.quality:side";
        private Side() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "one of the edges of a box — Top / Right / Bottom / Left for fixed-orientation "
                        + "layouts, Pinkie / Thumb for handedness-relative layouts";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "side";
    }

    /** The top edge. */
    @Seed.Item(key = Top.KEY)
    public static final class Top {
        public static final String KEY = "cg.side:top";
        private Top() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the top edge of a box";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "top";
    }

    /** The right edge. */
    @Seed.Item(key = Right.KEY)
    public static final class Right {
        public static final String KEY = "cg.side:right";
        private Right() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the right edge of a box";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "right";
    }

    /** The bottom edge. */
    @Seed.Item(key = Bottom.KEY)
    public static final class Bottom {
        public static final String KEY = "cg.side:bottom";
        private Bottom() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the bottom edge of a box";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "bottom";
    }

    /** The left edge. */
    @Seed.Item(key = Left.KEY)
    public static final class Left {
        public static final String KEY = "cg.side:left";
        private Left() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the left edge of a box";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "left";
    }

    /**
     * The pinkie-side edge of a handed layout — the edge closest to the
     * pinkie finger of the operating hand.  Resolves to the right edge in a
     * right-handed layout, the left edge in a left-handed layout.
     */
    @Seed.Item(key = Pinkie.KEY)
    public static final class Pinkie {
        public static final String KEY = "cg.side:pinkie";
        private Pinkie() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the pinkie-side edge of a handed layout — resolves to the right edge in a "
                        + "right-handed layout, the left edge in a left-handed layout";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "pinkie";
    }

    /**
     * The thumb-side edge of a handed layout — the edge closest to the
     * thumb of the operating hand.  Resolves to the left edge in a
     * right-handed layout, the right edge in a left-handed layout.
     */
    @Seed.Item(key = Thumb.KEY)
    public static final class Thumb {
        public static final String KEY = "cg.side:thumb";
        private Thumb() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the thumb-side edge of a handed layout — resolves to the left edge in a "
                        + "right-handed layout, the right edge in a left-handed layout";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "thumb";
    }

    // ==================================================================================
    // Handedness — the ergonomic orientation of a container, for handed
    // layouts using Pinkie / Thumb sides.
    // ==================================================================================

    /**
     * Whether a container is oriented for the right or left hand.  Determines
     * how {@link Pinkie} and {@link Thumb} sides resolve to physical edges.
     * A container without a Handedness binding is non-handed; using
     * Pinkie/Thumb in such a container is an error (or falls back to a
     * configured default).
     */
    @Seed.Item(key = Handedness.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Handedness {
        public static final String KEY = "cg.quality:handedness";
        private Handedness() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the ergonomic orientation of a container — determines how Pinkie and Thumb "
                        + "sides resolve to physical edges";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "handedness";
    }

    /** Right-handed orientation — Pinkie resolves to the right edge, Thumb to the left. */
    @Seed.Item(key = RightHanded.KEY)
    public static final class RightHanded {
        public static final String KEY = "cg.handedness:right";
        private RightHanded() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "right-handed orientation — Pinkie resolves to the right edge, Thumb to the left";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "right-handed";
    }

    /** Left-handed orientation — Pinkie resolves to the left edge, Thumb to the right. */
    @Seed.Item(key = LeftHanded.KEY)
    public static final class LeftHanded {
        public static final String KEY = "cg.handedness:left";
        private LeftHanded() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "left-handed orientation — Pinkie resolves to the left edge, Thumb to the right";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "left-handed";
    }
}
