package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.value.Value;

import static dev.everydaythings.graph.Seed.*;

/**
 * Animation qualities and the value sememes that target them.
 *
 * <p>Transition and keyframe animations share a common vocabulary: both have a
 * {@link Duration} (a Time quantity), a {@link Delay} (a Time quantity), and a
 * timing function selected by {@link Easing}.  Keyframe animations add
 * {@link IterationCount}, {@link FillMode}, {@link PlayState},
 * {@link AnimationDirection}, and a list of {@link Keyframe} value bodies.
 * Transitions additionally name the property they animate via
 * {@link TransitionProperty}, whose target is the IID of another quality
 * (Width, Opacity, BorderColor, ...).
 *
 * <p>Example transition on a scene node:
 * <pre>
 * Body[head = ContainerNode]
 *   Transition → Body[head = TransitionSpec,
 *                     TransitionProperty = @cg.quality:opacity,
 *                     Duration = Time{Value=300, @Millisecond=1},
 *                     Easing = @cg.easing:ease-in-out,
 *                     Delay = Time{Value=0, @Second=1}]
 * </pre>
 *
 * <p>Example keyframe animation:
 * <pre>
 * Body[head = ContainerNode]
 *   Animation → Body[head = AnimationSpec,
 *                    Duration = Time{Value=2, @Second=1},
 *                    IterationCount = 3,
 *                    AnimationDirection = @cg.direction:alternate,
 *                    FillMode = @cg.fill-mode:both,
 *                    PlayState = @cg.play-state:running,
 *                    Keyframe[0] = Body[head=Keyframe, Offset=0.0,  Opacity=0],
 *                    Keyframe[1] = Body[head=Keyframe, Offset=0.5,  Opacity=1],
 *                    Keyframe[2] = Body[head=Keyframe, Offset=1.0,  Opacity=0]]
 * </pre>
 */
public final class AnimationVocabulary {

    private AnimationVocabulary() {}

    // ==================================================================================
    // Quality binding-roles
    // ==================================================================================

    /**
     * The duration of an animation or transition.  Target is typically a
     * {@link dev.everydaythings.graph.value.Time Time} quantity.
     */
    @Seed.Item(key = Duration.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Duration {
        public static final String KEY = "cg.quality:duration";
        private Duration() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how long an animation or transition runs; typically a Time quantity";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "duration";
    }

    /** Delay before an animation or transition begins.  Target is typically a Time quantity. */
    @Seed.Item(key = Delay.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Delay {
        public static final String KEY = "cg.quality:delay";
        private Delay() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how long to wait before an animation or transition begins; typically a Time quantity";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "delay";
    }

    /**
     * The timing curve of an animation or transition.  Target is one of the
     * easing sememes ({@link Linear}, {@link Ease}, {@link EaseIn},
     * {@link EaseOut}, {@link EaseInOut}, {@link Bezier}, {@link Spring},
     * {@link Bounce}).
     */
    @Seed.Item(key = Easing.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Easing {
        public static final String KEY = "cg.quality:easing";
        private Easing() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the timing curve of an animation or transition";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "easing";
    }

    /** Number of times an animation repeats.  Target is typically an integer (or sentinel "infinite"). */
    @Seed.Item(key = IterationCount.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class IterationCount {
        public static final String KEY = "cg.quality:iteration-count";
        private IterationCount() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "number of times an animation repeats";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "iteration count";
    }

    /**
     * How an animation applies its first/last keyframe state outside its active
     * window.  Target is one of {@link FillNone}, {@link FillForwards},
     * {@link FillBackwards}, {@link FillBoth}.
     */
    @Seed.Item(key = FillMode.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class FillMode {
        public static final String KEY = "cg.quality:fill-mode";
        private FillMode() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how an animation applies its first or last keyframe state outside its active window";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "fill mode";
    }

    /**
     * Whether an animation is currently playing or paused.  Target is one of
     * {@link Running}, {@link Paused}.
     */
    @Seed.Item(key = PlayState.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class PlayState {
        public static final String KEY = "cg.quality:play-state";
        private PlayState() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "whether an animation is currently playing or paused";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "play state";
    }

    /**
     * The direction in which an animation plays through its keyframes.  Target
     * is one of {@link Normal}, {@link Reverse}, {@link Alternate},
     * {@link AlternateReverse}.  Named with the {@code Animation} prefix to
     * distinguish from {@link LayoutVocabulary.Direction} (Horizontal/Vertical).
     */
    @Seed.Item(key = AnimationDirection.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class AnimationDirection {
        public static final String KEY = "cg.quality:animation-direction";
        private AnimationDirection() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the direction in which an animation plays through its keyframes";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "animation direction";
    }

    /**
     * The property a transition animates.  Target is the IID of another
     * quality (Width, Opacity, BorderColor, ...).
     */
    @Seed.Item(key = TransitionProperty.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class TransitionProperty {
        public static final String KEY = "cg.quality:transition-property";
        private TransitionProperty() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the property a transition animates; target is a quality IID";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "transition property";
    }

    /**
     * The offset of a {@link Keyframe} within an animation timeline; a
     * dimensionless ratio in [0.0, 1.0] where 0 is the start and 1 is the end.
     */
    @Seed.Item(key = Offset.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Offset {
        public static final String KEY = "cg.quality:offset";
        private Offset() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "position within an animation timeline as a ratio in [0, 1]";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "offset";
    }

    // ==================================================================================
    // Value archetype
    // ==================================================================================

    /**
     * Keyframe — a Value whose body carries an {@link Offset} binding plus one
     * binding per property being animated at that offset.  Listed on an
     * animation spec as the snapshots between which the animation interpolates.
     *
     * <p>Body shape: {@code Body[head=Keyframe, Offset=<ratio>, <quality>=<value>, ...]}.
     */
    @Seed.Item(key = Keyframe.KEY, head = Value.KEY)
    public static final class Keyframe {
        public static final String KEY = "cg.value:keyframe";
        private Keyframe() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a single snapshot in a keyframe animation: an offset position in [0, 1] "
                        + "plus one binding per property being animated at that offset";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "keyframe";
    }

    // ==================================================================================
    // Easing values
    // ==================================================================================

    /** Linear — constant rate of change throughout the animation. */
    @Seed.Item(key = Linear.KEY)
    public static final class Linear {
        public static final String KEY = "cg.easing:linear";
        private Linear() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "constant rate of change throughout the animation";
    }

    /** Ease — slight ease-in/out; CSS's default timing function. */
    @Seed.Item(key = Ease.KEY)
    public static final class Ease {
        public static final String KEY = "cg.easing:ease";
        private Ease() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "slight ease-in then ease-out; the conventional default timing curve";
    }

    /** EaseIn — slow start, accelerating to full speed. */
    @Seed.Item(key = EaseIn.KEY)
    public static final class EaseIn {
        public static final String KEY = "cg.easing:ease-in";
        private EaseIn() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "slow start, accelerating to full speed";
    }

    /** EaseOut — full-speed start, decelerating to a slow finish. */
    @Seed.Item(key = EaseOut.KEY)
    public static final class EaseOut {
        public static final String KEY = "cg.easing:ease-out";
        private EaseOut() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "full-speed start, decelerating to a slow finish";
    }

    /** EaseInOut — slow start, full speed in the middle, slow finish. */
    @Seed.Item(key = EaseInOut.KEY)
    public static final class EaseInOut {
        public static final String KEY = "cg.easing:ease-in-out";
        private EaseInOut() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "slow start, full speed in the middle, slow finish";
    }

    /**
     * Bezier — cubic Bezier curve.  Without parameters this is the family
     * identifier; specific Bezier curves carry control-point parameters on a
     * body whose head is this sememe.
     */
    @Seed.Item(key = Bezier.KEY)
    public static final class Bezier {
        public static final String KEY = "cg.easing:bezier";
        private Bezier() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "cubic Bezier timing curve (family); specific curves carry control-point parameters";
    }

    /** Spring — physics-based spring response. */
    @Seed.Item(key = Spring.KEY)
    public static final class Spring {
        public static final String KEY = "cg.easing:spring";
        private Spring() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "physics-based spring response (mass, stiffness, damping)";
    }

    /** Bounce — decaying oscillation near the endpoint. */
    @Seed.Item(key = Bounce.KEY)
    public static final class Bounce {
        public static final String KEY = "cg.easing:bounce";
        private Bounce() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "decaying oscillation near the endpoint";
    }

    // ==================================================================================
    // FillMode values
    // ==================================================================================

    /** FillNone — no styling applied outside the animation's active window. */
    @Seed.Item(key = FillNone.KEY)
    public static final class FillNone {
        public static final String KEY = "cg.fill-mode:none";
        private FillNone() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "no styling applied outside the animation's active window";
    }

    /** FillForwards — the last keyframe persists after the animation ends. */
    @Seed.Item(key = FillForwards.KEY)
    public static final class FillForwards {
        public static final String KEY = "cg.fill-mode:forwards";
        private FillForwards() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the last keyframe persists after the animation ends";
    }

    /** FillBackwards — the first keyframe applies during the delay window. */
    @Seed.Item(key = FillBackwards.KEY)
    public static final class FillBackwards {
        public static final String KEY = "cg.fill-mode:backwards";
        private FillBackwards() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the first keyframe applies during the delay window";
    }

    /** FillBoth — first keyframe before, last keyframe after. */
    @Seed.Item(key = FillBoth.KEY)
    public static final class FillBoth {
        public static final String KEY = "cg.fill-mode:both";
        private FillBoth() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "first keyframe applies before; last keyframe persists after";
    }

    // ==================================================================================
    // PlayState values
    // ==================================================================================

    /** Running — the animation is actively playing. */
    @Seed.Item(key = Running.KEY)
    public static final class Running {
        public static final String KEY = "cg.play-state:running";
        private Running() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the animation is actively playing";
    }

    /** Paused — the animation is held at its current position. */
    @Seed.Item(key = Paused.KEY)
    public static final class Paused {
        public static final String KEY = "cg.play-state:paused";
        private Paused() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the animation is held at its current position";
    }

    // ==================================================================================
    // AnimationDirection values
    // ==================================================================================

    /** Normal — play each iteration forward. */
    @Seed.Item(key = Normal.KEY)
    public static final class Normal {
        public static final String KEY = "cg.direction:normal";
        private Normal() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "play each iteration in the forward direction";
    }

    /** Reverse — play each iteration backward. */
    @Seed.Item(key = Reverse.KEY)
    public static final class Reverse {
        public static final String KEY = "cg.direction:reverse";
        private Reverse() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "play each iteration in the reverse direction";
    }

    /** Alternate — odd iterations forward, even iterations backward. */
    @Seed.Item(key = Alternate.KEY)
    public static final class Alternate {
        public static final String KEY = "cg.direction:alternate";
        private Alternate() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "alternate: odd iterations forward, even iterations backward";
    }

    /** AlternateReverse — odd iterations backward, even iterations forward. */
    @Seed.Item(key = AlternateReverse.KEY)
    public static final class AlternateReverse {
        public static final String KEY = "cg.direction:alternate-reverse";
        private AlternateReverse() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "alternate-reverse: odd iterations backward, even iterations forward";
    }

    // ==================================================================================
    // Additional easings — spring variants and discrete-step.
    // ==================================================================================

    /** Overshoot — slight overshoot at the end before settling. */
    @Seed.Item(key = Overshoot.KEY)
    public static final class Overshoot {
        public static final String KEY = "cg.easing:overshoot";
        private Overshoot() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "slight overshoot at the end before settling";
    }

    /**
     * SpringGentle — slow, smooth spring with minimal overshoot.  Spring
     * easings compute their own duration from physical parameters; any
     * declared duration is ignored.
     */
    @Seed.Item(key = SpringGentle.KEY)
    public static final class SpringGentle {
        public static final String KEY = "cg.easing:spring-gentle";
        private SpringGentle() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "slow, smooth spring with minimal overshoot";
    }

    /** SpringSnappy — fast spring with a quick settle. */
    @Seed.Item(key = SpringSnappy.KEY)
    public static final class SpringSnappy {
        public static final String KEY = "cg.easing:spring-snappy";
        private SpringSnappy() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "fast spring with a quick settle";
    }

    /** SpringBouncy — pronounced overshoot and oscillation before settling. */
    @Seed.Item(key = SpringBouncy.KEY)
    public static final class SpringBouncy {
        public static final String KEY = "cg.easing:spring-bouncy";
        private SpringBouncy() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "pronounced overshoot and oscillation before settling";
    }

    /**
     * Steps — discrete-step easing.  The animation progresses in N equal
     * steps rather than continuously.  Specific step counts carry an
     * additional parameter binding.
     */
    @Seed.Item(key = Steps.KEY)
    public static final class Steps {
        public static final String KEY = "cg.easing:steps";
        private Steps() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "discrete-step easing — the animation progresses in N equal steps";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "steps";
    }
}
