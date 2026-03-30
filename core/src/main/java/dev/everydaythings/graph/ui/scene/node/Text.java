package dev.everydaythings.graph.ui.scene.node;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The content primitive — displays text in two modes.
 *
 * <p><b>Literal mode</b>: carries a text string + optional format sememe.
 * Displayed verbatim. The format (a MIME type sememe) tells the renderer
 * how to interpret the content — Markdown, Code, JSON, etc.
 *
 * <p><b>Semantic mode</b>: carries a list of {@link SemanticToken}s — sememe
 * references with grammatical features. The renderer resolves each token
 * through the language system at paint time. A single-token list is a label.
 * A multi-token list is a composed expression rendered as a sentence.
 *
 * <p>Only one mode is active per node. The renderer checks {@code tokens}
 * first — if non-empty, it's semantic. Otherwise, {@code text} is literal.
 */
@Getter
@Accessors(fluent = true)
@Canonical.Canonization
public class Text extends Node {

    // ==================================================================================
    // Literal Mode — verbatim text content
    // ==================================================================================

    /** Literal text content. */
    @Canon(order = 100)
    private String text;

    /** Format sememe (MIME type) for literal content — Markdown, Code, JSON, etc. */
    @Canon(order = 101)
    private ItemID format;

    // ==================================================================================
    // Semantic Mode — meaning tokens resolved at render time
    // ==================================================================================

    /**
     * Semantic tokens — sememe references resolved through the language system.
     *
     * <p>Single token = a label (button text, heading, status).
     * Multiple tokens = a composed expression rendered as a phrase or sentence.
     * The language system determines word order, morphology, and agreement
     * based on the active language.
     */
    @Canon(order = 110)
    private List<SemanticToken> tokens;

    // ==================================================================================
    // SemanticToken — a sememe + grammatical features
    // ==================================================================================

    /**
     * A single unit of meaning with grammatical context.
     *
     * <p>The sememe is the concept. The features specify the desired word form:
     * noun vs verb, lemma vs past participle, singular vs plural.
     * Default features (when empty): [NOUN, LEMMA] — the base dictionary form.
     */
    @Canonical.Canonization
    public record SemanticToken(
            @Canon(order = 0) ItemID sememe,
            @Canon(order = 1) List<ItemID> features
    ) implements Canonical {

        /** Token with default features (noun, lemma). */
        public static SemanticToken of(ItemID sememe) {
            return new SemanticToken(sememe, List.of());
        }

        /** Token with explicit features. */
        public static SemanticToken of(ItemID sememe, ItemID... features) {
            return new SemanticToken(sememe, List.of(features));
        }
    }

    // ==================================================================================
    // Constructors
    // ==================================================================================

    public Text() {}

    public Text(String text) {
        this.text = text;
    }

    // ==================================================================================
    // Factories — Literal Mode
    // ==================================================================================

    /** Literal text content. */
    public static Text of(String text) {
        return new Text(text);
    }

    /** Literal text with a format sememe (e.g., Markdown, Code). */
    public static Text of(String text, ItemID format) {
        Text t = new Text(text);
        t.format = format;
        return t;
    }

    /** Text bound to an expression. */
    public static Text bound(String expression) {
        Text t = new Text();
        t.bind = expression;
        return t;
    }

    // ==================================================================================
    // Factories — Semantic Mode
    // ==================================================================================

    /** Label from a single sememe — default features (noun, lemma). Double-click opens a view. */
    public static Text ofSememe(ItemID sememe) {
        Text t = new Text();
        t.tokens = List.of(SemanticToken.of(sememe));
        t.on(dev.everydaythings.graph.ui.scene.SceneEvent.of("doubleClick", "view", sememe.encodeText()));
        return t;
    }

    /** Label from a single sememe with explicit features. */
    public static Text ofSememe(ItemID sememe, ItemID... features) {
        Text t = new Text();
        t.tokens = List.of(new SemanticToken(sememe, List.of(features)));
        return t;
    }

    /** Composed semantic text from a token sequence. */
    public static Text ofTokens(List<SemanticToken> tokens) {
        Text t = new Text();
        t.tokens = List.copyOf(tokens);
        return t;
    }

    /** Composed semantic text from varargs tokens. */
    public static Text ofTokens(SemanticToken... tokens) {
        Text t = new Text();
        t.tokens = List.of(tokens);
        return t;
    }

    // ==================================================================================
    // Fluent Setters
    // ==================================================================================

    public Text text(String text) { this.text = text; return this; }
    public Text format(ItemID format) { this.format = format; return this; }
    public Text tokens(List<SemanticToken> tokens) { this.tokens = tokens; return this; }
}
