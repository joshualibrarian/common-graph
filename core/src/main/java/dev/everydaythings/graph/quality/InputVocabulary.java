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
 * Input vocabulary — the CG-side <i>contract</i> for keyboard, pointer,
 * and focus input.  Event predicates, the {@link Key} archetype, key
 * categories, {@link Platform} sememes, and the {@link PointerButton}
 * archetype + instances live here.  Individual key sememes (the ~200
 * W3C UI Events codes) are seeded at bootstrap by
 * {@code :imports:keyboard} reading a TSV table — IIDs derive from
 * {@code cg.key:<W3C_code>} so they're stable across installations
 * regardless of which signer originally seeded them.
 *
 * <h2>Events</h2>
 *
 * <p>Six event predicates cover keyboard, pointer, focus, and wheel:
 *
 * <ul>
 *   <li>{@link KeyPress} — a key (or chord — multiple Key bindings)
 *       pressed.  No separate KeyChord type: chord IS multi-key KeyPress.</li>
 *   <li>{@link KeyRelease} — mirror of KeyPress.</li>
 *   <li>{@link PointerDown} / {@link PointerUp} / {@link PointerMove}
 *       / {@link PointerEnter} / {@link PointerLeave} — pointer events
 *       (mouse, touch, pen unified).</li>
 *   <li>{@link Wheel} — scroll-wheel rotation.</li>
 *   <li>{@link FocusEvent} / {@link BlurEvent} — focus gain / loss.</li>
 * </ul>
 *
 * <h2>Key archetype + categories</h2>
 *
 * <p>All keys are instances of {@link Key}.  Categories let queries find
 * groups of related keys: "all letter keys," "all modifiers," etc.
 *
 * <h2>Platform</h2>
 *
 * <p>{@link Platform} instances let conditional bindings select per-OS
 * behavior — e.g., a key chord that's {@code Meta+C} on macOS and
 * {@code Control+C} on Windows/Linux.
 *
 * <h2>PointerButton</h2>
 *
 * <p>Pointer buttons are abstracted from physical mouse buttons:
 * {@link Primary} (usually left), {@link Secondary} (usually right),
 * {@link Middle} (scroll wheel), {@link Back}, {@link Forward}.  This
 * lets users with left-handed mouse configurations swap Primary /
 * Secondary at the OS level without app-level changes.
 */
public final class InputVocabulary {

    private InputVocabulary() {}

    // ==================================================================================
    // Event predicates.
    // ==================================================================================

    /**
     * KeyPress — a keyboard event.  Bindings carry one or more
     * {@link Key} references; multiple keys = chord (implicit, no
     * separate KeyChord type).  Modifier keys (Shift, Control, Alt, Meta)
     * combine with non-modifiers via the same multi-Key shape.
     */
    @Seed.Item(key = KeyPress.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class KeyPress {
        public static final String KEY = "cg.predicate:key-press";
        private KeyPress() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a keyboard event — one or more Key bindings; multiple keys = chord";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "key press";
    }

    /** KeyRelease — mirror of {@link KeyPress}, fires when keys are released. */
    @Seed.Item(key = KeyRelease.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class KeyRelease {
        public static final String KEY = "cg.predicate:key-release";
        private KeyRelease() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "mirror of KeyPress — fires when keys are released";
    }

    /** PointerDown — pointer button pressed (mouse, touch, pen). */
    @Seed.Item(key = PointerDown.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class PointerDown {
        public static final String KEY = "cg.predicate:pointer-down";
        private PointerDown() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "pointer button pressed (mouse, touch, pen)";
    }

    /** PointerUp — pointer button released. */
    @Seed.Item(key = PointerUp.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class PointerUp {
        public static final String KEY = "cg.predicate:pointer-up";
        private PointerUp() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "pointer button released";
    }

    /** PointerMove — pointer position changed. */
    @Seed.Item(key = PointerMove.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class PointerMove {
        public static final String KEY = "cg.predicate:pointer-move";
        private PointerMove() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "pointer position changed";
    }

    /** PointerEnter — pointer entered the bounds of a node. */
    @Seed.Item(key = PointerEnter.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class PointerEnter {
        public static final String KEY = "cg.predicate:pointer-enter";
        private PointerEnter() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "pointer entered the bounds of a node";
    }

    /** PointerLeave — pointer left the bounds of a node. */
    @Seed.Item(key = PointerLeave.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class PointerLeave {
        public static final String KEY = "cg.predicate:pointer-leave";
        private PointerLeave() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "pointer left the bounds of a node";
    }

    /** Wheel — scroll-wheel rotation. */
    @Seed.Item(key = Wheel.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Wheel {
        public static final String KEY = "cg.predicate:wheel";
        private Wheel() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "scroll-wheel rotation";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "wheel";
    }

    /**
     * FocusEvent — a node gained keyboard focus.  Named with "Event"
     * suffix to avoid colliding with future focus-related qualities.
     */
    @Seed.Item(key = FocusEvent.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class FocusEvent {
        public static final String KEY = "cg.predicate:focus";
        private FocusEvent() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a node gained keyboard focus";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "focus";
    }

    /** BlurEvent — a node lost keyboard focus.  Named with "Event" suffix for symmetry. */
    @Seed.Item(key = BlurEvent.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class BlurEvent {
        public static final String KEY = "cg.predicate:blur";
        private BlurEvent() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a node lost keyboard focus";
    }

    // ==================================================================================
    // Key archetype + categories.
    // ==================================================================================

    /**
     * Key — the archetype of all keyboard keys.  Instances are seeded
     * from the W3C UI Events `code` table by
     * {@code :imports:keyboard}.  Each instance's IID derives from
     * {@code cg.key:<W3C_code>} (e.g., {@code cg.key:KeyA},
     * {@code cg.key:ControlLeft}).
     */
    @Seed.Item(key = Key.KEY, head = CoreVocabulary.Archetype.KEY)
    public static final class Key {
        public static final String KEY = "cg.archetype:key";
        private Key() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the archetype of all keyboard keys";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "key";
    }

    /** Letter — alphabetic keys (KeyA..KeyZ). */
    @Seed.Item(key = Letter.KEY, head = Key.KEY)
    public static final class Letter {
        public static final String KEY = "cg.key-category:letter";
        private Letter() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "alphabetic keys (A through Z)";
    }

    /** Digit — top-row digit keys (Digit0..Digit9). */
    @Seed.Item(key = Digit.KEY, head = Key.KEY)
    public static final class Digit {
        public static final String KEY = "cg.key-category:digit";
        private Digit() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "top-row digit keys (0 through 9)";
    }

    /** Modifier — keys that modify other keys (Shift, Control, Alt, Meta). */
    @Seed.Item(key = Modifier.KEY, head = Key.KEY)
    public static final class Modifier {
        public static final String KEY = "cg.key-category:modifier";
        private Modifier() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "keys that modify other keys (Shift, Control, Alt, Meta)";
    }

    /** Function — function keys (F1..F24). */
    @Seed.Item(key = Function.KEY, head = Key.KEY)
    public static final class Function {
        public static final String KEY = "cg.key-category:function";
        private Function() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "function keys (F1 through F24)";
    }

    /** Navigation — directional and paging keys (Arrow*, Home, End, PageUp, PageDown, Insert, Delete). */
    @Seed.Item(key = Navigation.KEY, head = Key.KEY)
    public static final class Navigation {
        public static final String KEY = "cg.key-category:navigation";
        private Navigation() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "directional and paging keys (arrows, Home, End, PageUp, PageDown, Insert, Delete)";
    }

    /** Whitespace — Space, Tab, Enter, Backspace. */
    @Seed.Item(key = Whitespace.KEY, head = Key.KEY)
    public static final class Whitespace {
        public static final String KEY = "cg.key-category:whitespace";
        private Whitespace() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "whitespace keys (Space, Tab, Enter, Backspace)";
    }

    /** SymbolKey — punctuation, brackets, math symbols. */
    @Seed.Item(key = SymbolKey.KEY, head = Key.KEY)
    public static final class SymbolKey {
        public static final String KEY = "cg.key-category:symbol";
        private SymbolKey() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "punctuation, brackets, math symbols";
    }

    /** Lock — CapsLock, NumLock, ScrollLock. */
    @Seed.Item(key = Lock.KEY, head = Key.KEY)
    public static final class Lock {
        public static final String KEY = "cg.key-category:lock";
        private Lock() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "lock keys (CapsLock, NumLock, ScrollLock)";
    }

    /** MediaKey — media-control keys (Play, Pause, Volume, etc.). */
    @Seed.Item(key = MediaKey.KEY, head = Key.KEY)
    public static final class MediaKey {
        public static final String KEY = "cg.key-category:media";
        private MediaKey() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "media-control keys (play, pause, volume, etc.)";
    }

    // ==================================================================================
    // Platform — the operating system / runtime environment.  Used for
    // conditional bindings ("on macOS use Meta+C, on Windows use
    // Control+C").
    // ==================================================================================

    /** The archetype of operating-system / runtime platforms. */
    @Seed.Item(key = Platform.KEY, head = CoreVocabulary.Archetype.KEY)
    public static final class Platform {
        public static final String KEY = "cg.archetype:platform";
        private Platform() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the operating system / runtime environment a session is running on";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "platform";
    }

    /** macOS / OS X — Apple desktop platform. */
    @Seed.Item(key = MacOS.KEY, head = Platform.KEY)
    public static final class MacOS {
        public static final String KEY = "cg.platform:macos";
        private MacOS() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Apple macOS desktop platform";
    }

    /** Windows — Microsoft Windows desktop platform. */
    @Seed.Item(key = Windows.KEY, head = Platform.KEY)
    public static final class Windows {
        public static final String KEY = "cg.platform:windows";
        private Windows() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Microsoft Windows desktop platform";
    }

    /** Linux — any Linux desktop platform (GNOME, KDE, etc.). */
    @Seed.Item(key = Linux.KEY, head = Platform.KEY)
    public static final class Linux {
        public static final String KEY = "cg.platform:linux";
        private Linux() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Linux desktop platform";
    }

    /** Android — Google Android mobile platform. */
    @Seed.Item(key = Android.KEY, head = Platform.KEY)
    public static final class Android {
        public static final String KEY = "cg.platform:android";
        private Android() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Google Android mobile platform";
    }

    /** iOS — Apple iOS mobile platform. */
    @Seed.Item(key = IOS.KEY, head = Platform.KEY)
    public static final class IOS {
        public static final String KEY = "cg.platform:ios";
        private IOS() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Apple iOS mobile platform";
    }

    /** Web — browser runtime (JavaScript, WebGL, DOM). */
    @Seed.Item(key = Web.KEY, head = Platform.KEY)
    public static final class Web {
        public static final String KEY = "cg.platform:web";
        private Web() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "browser runtime (JavaScript, WebGL, DOM)";
    }

    // ==================================================================================
    // PointerButton — abstracted pointer buttons (mouse, touch, pen).
    // Primary / Secondary names rather than Left / Right so left-handed
    // mouse configurations swap them at the OS level without app changes.
    // ==================================================================================

    /** The archetype of pointer buttons. */
    @Seed.Item(key = PointerButton.KEY, head = CoreVocabulary.Archetype.KEY)
    public static final class PointerButton {
        public static final String KEY = "cg.archetype:pointer-button";
        private PointerButton() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the archetype of pointer buttons";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "pointer button";
    }

    /** Primary — the main pointer button.  Usually left mouse button. */
    @Seed.Item(key = Primary.KEY, head = PointerButton.KEY)
    public static final class Primary {
        public static final String KEY = "cg.pointer-button:primary";
        private Primary() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the main pointer button — usually left mouse button";
    }

    /** Secondary — the secondary pointer button.  Usually right mouse button. */
    @Seed.Item(key = Secondary.KEY, head = PointerButton.KEY)
    public static final class Secondary {
        public static final String KEY = "cg.pointer-button:secondary";
        private Secondary() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the secondary pointer button — usually right mouse button";
    }

    /** Middle — the middle pointer button.  Usually the scroll wheel click. */
    @Seed.Item(key = Middle.KEY, head = PointerButton.KEY)
    public static final class Middle {
        public static final String KEY = "cg.pointer-button:middle";
        private Middle() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the middle pointer button — usually the scroll wheel click";
    }

    /** Back — the back-navigation pointer button. */
    @Seed.Item(key = Back.KEY, head = PointerButton.KEY)
    public static final class Back {
        public static final String KEY = "cg.pointer-button:back";
        private Back() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the back-navigation pointer button";
    }

    /** Forward — the forward-navigation pointer button. */
    @Seed.Item(key = Forward.KEY, head = PointerButton.KEY)
    public static final class Forward {
        public static final String KEY = "cg.pointer-button:forward";
        private Forward() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the forward-navigation pointer button";
    }
}
