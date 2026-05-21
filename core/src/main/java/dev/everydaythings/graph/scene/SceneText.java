package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.BodyBinder;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.TypographyVocabulary;
import dev.everydaythings.graph.value.Length;
import dev.everydaythings.graph.value.Numeric;

import java.util.Objects;

/**
 * SceneText — the content primitive.  Displays literal or semantic
 * text plus the typography that styles it.
 *
 * <p>Two content modes, distinguished by which binding the node
 * carries:
 *
 * <ul>
 *   <li><b>Literal text</b> — {@link SceneVocabulary.Text Text} binding
 *       carries a String.  An optional {@link SceneVocabulary.Format
 *       Format} binding names a MIME-type sememe telling the renderer
 *       how to interpret the content ({@code text/markdown},
 *       {@code application/json}, ...).  Use for user content, code
 *       snippets, debug output — anything where the text IS the data.</li>
 *   <li><b>Semantic text</b> — {@link SceneVocabulary.Tokens Tokens}
 *       bindings (one per token, ordered by index) carry sememe
 *       references.  The language layer resolves each token to a
 *       display string in the user's current language at render time —
 *       so "checkmate" in English becomes "echec et mat" in French
 *       without changing the scene tree.</li>
 * </ul>
 *
 * <p>Typography belongs to SceneText only — Container and SceneBody
 * have no text to style.  If a future use case wants font-inheritance
 * through containers, typography can be promoted to SceneNode then.
 */
@Seed.Item(key = SceneText.KEY, head = SceneNode.KEY)
public class SceneText extends SceneNode {

    public static final String KEY = "cg.archetype:scene-text";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the content scene primitive — displays literal or semantic text plus the "
                    + "typography that styles it";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "scene text";

    // ==================================================================================
    // Content slots — literal text or semantic tokens.
    // Format is the optional MIME-type sememe (target is an Encoding instance).
    // Tokens is a list-valued slot read via a separate accessor when the
    // SceneResolver lands; not surfaced as an @Seed.Property field today.
    // ==================================================================================

    /** Literal text content. */
    @Seed.Property(role = SceneVocabulary.Text.KEY)   protected String text;

    /** MIME-type sememe naming how literal text should be interpreted. */
    @Seed.Property(role = SceneVocabulary.Format.KEY) protected ItemRef format;

    // ==================================================================================
    // Typography slots.
    //
    // FontFamily is a string (family name).  FontSize, LineHeight,
    // LetterSpacing are Lengths.  FontWeight is a Numeric or a weight
    // sememe (we use Numeric here; weight-sememe path is open if/when
    // needed).  FontStyle / TextAlign / TextDecoration / TextOverflow /
    // WhiteSpace target sememe instances — field type ItemRef.
    // ==================================================================================

    /** Font-family name (e.g., "sans-serif", "monospace", "Helvetica"). */
    @Seed.Property(role = TypographyVocabulary.FontFamily.KEY)     protected String fontFamily;

    /** Font size — typically a Length in px / em / pt. */
    @Seed.Property(role = TypographyVocabulary.FontSize.KEY)       protected Length fontSize;

    /** Font weight — Numeric (e.g., 400, 700) or a weight sememe. */
    @Seed.Property(role = TypographyVocabulary.FontWeight.KEY)     protected Numeric fontWeight;

    /** Font style sememe — normal, italic, oblique. */
    @Seed.Property(role = TypographyVocabulary.FontStyle.KEY)      protected ItemRef fontStyle;

    /** Text-decoration sememe — Underline / LineThrough / Overline. */
    @Seed.Property(role = TypographyVocabulary.TextDecoration.KEY) protected ItemRef textDecoration;

    /** Text-alignment sememe — Start / Center / End. */
    @Seed.Property(role = TypographyVocabulary.TextAlign.KEY)      protected ItemRef textAlign;

    /** Line height — Length or a unitless ratio Numeric. */
    @Seed.Property(role = TypographyVocabulary.LineHeight.KEY)     protected Length lineHeight;

    /** Letter spacing — Length. */
    @Seed.Property(role = TypographyVocabulary.LetterSpacing.KEY)  protected Length letterSpacing;

    /** Text-overflow sememe — Ellipsis / Clip. */
    @Seed.Property(role = TypographyVocabulary.TextOverflow.KEY)   protected ItemRef textOverflow;

    /** White-space sememe — NormalWhitespace / NoWrap / Pre / PreWrap. */
    @Seed.Property(role = TypographyVocabulary.WhiteSpace.KEY)     protected ItemRef whiteSpace;

    // ==================================================================================
    // Construction.
    // ==================================================================================

    public SceneText(Body body) {
        super(ItemRef.iid(KEY), body.bindings());
        BodyBinder.bind(this, body);
    }

    /** Typed view: dispatched from {@link SceneNode#from(Body)} on SceneText-headed bodies. */
    public static SceneText from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof SceneText st) return st;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the SceneText archetype: " + body.headRef());
        }
        return new SceneText(body);
    }
}
