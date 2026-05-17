package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.*;

/**
 * Media vocabulary — sememes naming the body-content properties of a
 * scene's Body node.  Where {@link ShapeVocabulary} handles geometric
 * primitives, this file handles raster / vector / streaming / model
 * content: still images, 3D models, glyphs, video, audio, plus the
 * stream-control properties (loop, volume).  Subset names from
 * {@link AnimationVocabulary} (Muted, Paused) compose into the
 * stream-control set without being redeclared.
 *
 * <p>Body nodes carry as many of these as the renderer can use; the
 * renderer selects the highest-fidelity representation it supports:
 * {@link Model} (3D) → {@link Image} (2D) → {@link Glyph} (text) → {@link Alt}.
 */
public final class MediaVocabulary {

    private MediaVocabulary() {}

    // ==================================================================================
    // Body-content qualities — the representations a body can carry.
    // ==================================================================================

    /**
     * Image — a 2D image reference (SVG, PNG, JPEG, WebP, GIF).  Target
     * is typically a resource ref or a binding expression that resolves
     * to one.
     */
    @Seed.Item(key = Image.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Image {
        public static final String KEY = "cg.quality:image";
        private Image() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a 2D image reference (SVG, PNG, JPEG, WebP, GIF)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "image";
    }

    /**
     * Model — a 3D mesh reference (GLB, glTF).  Used by spatial
     * renderers; 2D renderers fall back to the body's Image or Shape.
     */
    @Seed.Item(key = Model.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Model {
        public static final String KEY = "cg.quality:model";
        private Model() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a 3D mesh reference (GLB, glTF)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "model";
    }

    /**
     * Glyph — a single Unicode character used as a text-fallback
     * representation of a body.  Used by text renderers when richer
     * representations aren't available.
     */
    @Seed.Item(key = Glyph.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Glyph {
        public static final String KEY = "cg.quality:glyph";
        private Glyph() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a single Unicode character used as a text-fallback representation of a body";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "glyph";
    }

    /**
     * Alt — an accessibility / text-fallback description of a body.
     * Renderers that can't display a body's visual representation (text
     * painters, screen readers) use this instead.
     */
    @Seed.Item(key = Alt.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Alt {
        public static final String KEY = "cg.quality:alt";
        private Alt() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an accessibility / text-fallback description of a body";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "alt";
    }

    // ==================================================================================
    // Streaming media — video, audio, and their control properties.
    // (Muted and Paused are already defined in AnimationVocabulary and
    // compose into the stream-control set; volume / loop / etc. are new.)
    // ==================================================================================

    /**
     * Video — a video stream reference.  May be a file ref, a live
     * stream ref, or a capture-device scheme like {@code camera:default}.
     * Independent of {@link Audio}; either can appear without the other.
     */
    @Seed.Item(key = Video.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Video {
        public static final String KEY = "cg.quality:video";
        private Video() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a video stream reference — file, live stream, or capture device";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "video";
    }

    /**
     * Audio — an audio stream reference.  Independent of {@link Video};
     * may exist with or without it (voice chat has audio without video; a
     * silent security camera has video without audio).
     */
    @Seed.Item(key = Audio.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Audio {
        public static final String KEY = "cg.quality:audio";
        private Audio() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an audio stream reference";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "audio";
    }

    /**
     * Loop — whether a prerecorded stream restarts on end.  Boolean
     * target.  Meaningless for live streams.
     */
    @Seed.Item(key = Loop.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Loop {
        public static final String KEY = "cg.quality:loop";
        private Loop() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "whether a prerecorded stream restarts on end";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "loop";
    }

    /**
     * Volume — audio level from 0 (silent) to 1 (full).  Numeric or
     * Quantity target; animatable via keyframes.
     */
    @Seed.Item(key = Volume.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Volume {
        public static final String KEY = "cg.quality:volume";
        private Volume() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "audio level from 0 (silent) to 1 (full)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "volume";
    }
}
