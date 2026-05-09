package dev.everydaythings.graph.text;

import dev.everydaythings.graph.item.id.ItemRef;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.List;

/**
 * Parameters for the text pipeline, used by both parsing and rendering.
 *
 * <p>The presenter / orchestrator declares these once; the values flow through the
 * full operation. Some fields apply to one direction more than the other — verbosity
 * and embedding mode are mostly render concerns; the language stack matters to both —
 * but a single shape covers both directions to avoid duplicating the configuration
 * surface.
 *
 * <p><b>Locale lives in the Language items themselves.</b> Sub-Languages (e.g.
 * English-US, English-GB, German-DE, German-CH) are Language items in the head
 * hierarchy under their parent Language; each declares its own number/date formats,
 * plural rules, lexeme variants, etc. The {@link #languageStack} carries refs to
 * sub-Languages where regional variant matters; the framework derives ICU
 * {@code ULocale} from the active Language when needed (e.g. for grapheme break
 * iteration, number formatting). There is no separate locale parameter.
 *
 * <p>Use {@link #defaults()} for a sensible baseline; modify via {@code withMode}
 * etc. or via {@link #builder()} for many fields at once.
 */
@Value
@With
@Builder(toBuilder = true)
public class ParseParams {

    /**
     * Priority-ordered list of Languages (often sub-Languages for regional variants)
     * active for this operation. Inner-first wins during rule lookup; e.g.
     * {@code [chess-notation, English-US]} inside a chess game with an American user
     * makes chess-notation the primary, English-US the fallback. Locale concerns
     * (number formats, plural rules, currency) live on the Language items themselves,
     * resolved via head-hierarchy walks.
     */
    List<ItemRef> languageStack;

    /** What kind of output the consumer expects (chip UI, flat text, voice, accessible). */
    Mode mode;

    /** How much detail to surface in rendering (terse / normal / verbose). */
    Verbosity verbosity;

    /** Tone / register for lexeme selection (formal / neutral / casual / technical). */
    Register register;

    /**
     * Recently-mentioned items in scope, ordered most-salient-first. Used by Languages
     * for pronoun selection, ellipsis, and definite-vs-indefinite article choice.
     */
    List<ItemRef> salientReferents;

    /** How a frame is being embedded — top-level clause, noun phrase, or adverbial. */
    EmbeddingMode embeddingMode;

    /** Sensible baseline: empty language stack, flat-text mode, normal everything. */
    public static ParseParams defaults() {
        return ParseParams.builder()
            .languageStack(List.of())
            .mode(Mode.FLAT)
            .verbosity(Verbosity.NORMAL)
            .register(Register.NEUTRAL)
            .salientReferents(List.of())
            .embeddingMode(EmbeddingMode.TOP_LEVEL)
            .build();
    }

    /**
     * Output mode hint. Affects rendering primarily — the parser cares mainly about
     * whether the input is plain text (typed) or structured chips (UI gestures).
     */
    public enum Mode {
        /** Chip-style UI: each frame part rendered as a clickable/editable chip with provenance. */
        CHIP,
        /** Flat text: spans concatenated to a single string. */
        FLAT,
        /** Voice / text-to-speech: text suitable for prosody-aware synthesis. */
        VOICE,
        /** Accessible / screen-reader: prose-favored, structural cues spelled out. */
        ACCESSIBLE
    }

    /** How much detail to surface. Mostly a render concern. */
    public enum Verbosity {
        TERSE,
        NORMAL,
        VERBOSE
    }

    /** Tone / register for lexeme selection. Influences which lexeme variant the Language picks. */
    public enum Register {
        FORMAL,
        NEUTRAL,
        CASUAL,
        TECHNICAL
    }

    /**
     * How a frame is being embedded in surrounding text. Flows down into recursive
     * render calls when a binding target is itself a frame.
     */
    public enum EmbeddingMode {
        /** Standalone clause / sentence. */
        TOP_LEVEL,
        /** Embedded as a noun phrase (e.g. "Alice's move to e4" inside a larger clause). */
        NOUN_PHRASE,
        /** Embedded as an adverbial / modifier. */
        ADVERBIAL
    }
}
