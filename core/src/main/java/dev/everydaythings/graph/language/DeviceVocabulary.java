package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Seed vocabulary for device predicates.
 *
 * <p>A DEVICE frame on a Host represents a physical device attached to that host.
 * The device type (Display, Audio, Usb, etc.) appears as a qualifier on the
 * FrameKey, distinguishing different device classes.
 *
 * <p>Endorsement lifecycle:
 * <ul>
 *   <li>Endorsed = device currently connected</li>
 *   <li>Unendorsed = device disconnected (frame body preserved for history)</li>
 *   <li>Re-endorsed = device reconnected (same identity restored)</li>
 * </ul>
 */
public final class DeviceVocabulary {

    private DeviceVocabulary() {}

    // ==================================================================================
    // BASE PREDICATE
    // ==================================================================================

    /**
     * Base predicate for device frames on a Host.
     *
     * <p>FrameKey structure: {@code (DEVICE, <type>, <device-id>)}
     * where type is a qualifier sememe (Display, Audio, etc.) and
     * device-id is a string literal identifying the specific device.
     */
    @ItemSeed(key = Device.KEY)
    public static class Device {
        public static final String KEY = "cg.device:device";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a physical device attached to a host";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"device"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.Name.KEY}))
        static final ItemID expectName = ThematicRole.Name.IID;
    }

    // ==================================================================================
    // DEVICE TYPE QUALIFIERS
    // ==================================================================================

    /**
     * Display device — a monitor, screen, or projector.
     */
    @ItemSeed(key = Display.KEY)
    public static class Display {
        public static final String KEY = "cg.device:display";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a display device such as a monitor or screen";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"display", "monitor", "screen"};
    }

    /**
     * Audio output device — speakers, headphones.
     */
    @ItemSeed(key = Audio.KEY)
    public static class Audio {
        public static final String KEY = "cg.device:audio";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "an audio output device such as speakers or headphones";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"audio"};
    }

    /**
     * Audio input device — microphones.
     */
    @ItemSeed(key = AudioInput.KEY)
    public static class AudioInput {
        public static final String KEY = "cg.device:audio-input";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "an audio input device such as a microphone";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"microphone", "mic"};
    }

    /**
     * USB peripheral device.
     */
    @ItemSeed(key = Usb.KEY)
    public static class Usb {
        public static final String KEY = "cg.device:usb";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a USB peripheral device";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"usb"};
    }

    /**
     * Input device — keyboard, mouse, trackpad.
     */
    @ItemSeed(key = Input.KEY)
    public static class Input {
        public static final String KEY = "cg.device:input";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "an input device such as a keyboard, mouse, or trackpad";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"input"};
    }
}
