package dev.everydaythings.graph;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;
import static dev.everydaythings.graph.Seed.*;

/**
 * Device vocabulary — sememes for physical devices attached to a host or bound
 * to a session.
 *
 * <p>A DEVICE frame represents one physical device. The kind of device
 * (Display, Audio, Input, Usb, …) appears as a qualifier sememe on the
 * frame's compound key, distinguishing classes of device.
 *
 * <p>Endorsement lifecycle:
 * <ul>
 *   <li><b>Endorsed</b> — device currently connected</li>
 *   <li><b>Unendorsed</b> — device disconnected (body preserved for history)</li>
 *   <li><b>Re-endorsed</b> — device reconnected (same identity restored)</li>
 * </ul>
 *
 * <p>Sessions bind to devices via DEVICE frames on the Session item; hosts
 * track their attached devices the same way.
 */
public final class DeviceVocabulary {

    private DeviceVocabulary() {}

    /**
     * Base predicate for device frames on a Host or Session.
     *
     * <p>Compound key structure: {@code (DEVICE, <kind>, <device-id>)} where
     * {@code kind} is one of the qualifier sememes below (Display, Audio,
     * Input, Usb, AudioInput) and {@code device-id} is a literal identifying
     * the specific device.
     */
    @Seed.Item(key = Device.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Device {
        public static final String KEY = "cg.predicate:device";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Device() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a physical device attached to a host or session";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "device";
    }

    /** Display device — a monitor, screen, or projector. */
    @Seed.Item(key = Display.KEY)
    public static final class Display {
        public static final String KEY = "cg.device:display";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Display() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a display device such as a monitor, screen, or projector";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "display";
    }

    /** Audio output device — speakers or headphones. */
    @Seed.Item(key = Audio.KEY)
    public static final class Audio {
        public static final String KEY = "cg.device:audio";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Audio() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an audio output device such as speakers or headphones";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "audio";
    }

    /** Audio input device — microphones. */
    @Seed.Item(key = AudioInput.KEY)
    public static final class AudioInput {
        public static final String KEY = "cg.device:audio-input";
        public static final ItemID IID = ItemID.fromString(KEY);
        private AudioInput() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an audio input device such as a microphone";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "microphone";
    }

    /** Generic USB peripheral. */
    @Seed.Item(key = Usb.KEY)
    public static final class Usb {
        public static final String KEY = "cg.device:usb";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Usb() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a USB peripheral device";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "usb";
    }

    /** Input device — keyboard, mouse, trackpad. */
    @Seed.Item(key = Input.KEY)
    public static final class Input {
        public static final String KEY = "cg.device:input";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Input() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an input device such as a keyboard, mouse, or trackpad";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "input";
    }
}
