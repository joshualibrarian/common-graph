package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.PartOfSpeech;

import static dev.everydaythings.graph.Seed.*;

/**
 * Telephony vocabulary — sememes naming telephony devices (cellphone, fax
 * machine, ...).  Small for now; will grow as more telephony concepts land
 * (voicemail, SMS, ringtone, ...).
 *
 * <p>Each sememe carries its meaning regardless of where it appears in a
 * frame.  As qualifier on a {@code REACHABLE_AT} binding, {@link Mobile}
 * narrows a phone number to a cell line; as a target it identifies the
 * device kind.  Same sememe, different frame slots.
 */
public final class TelephonyVocabulary {

    private TelephonyVocabulary() {}

    /**
     * Mobile / cellular phone — a hand-held mobile radiotelephone.
     *
     * <p>Grounded in OEWN synset oewn-02995984-n (CILI {@code i51696}):
     * "a hand-held mobile radiotelephone" (cellular telephone, cellular
     * phone, cellphone, cell, mobile phone).
     */
    @Seed.Item(key = Mobile.KEY)
    @Seed.Cili("i51696")
    @Seed.Gloss(english = "a hand-held mobile radiotelephone")
    @Seed.Lexeme(english = {"mobile phone", "cellphone", "cellular phone", "cell"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class Mobile {
        public static final String KEY = "cg.sememe:cellphone";
        private Mobile() {}
    }

    /**
     * Fax / facsimile machine — a duplicator that transmits its copy by wire
     * or radio.
     *
     * <p>Grounded in OEWN synset oewn-03321050-n (CILI {@code i53562}):
     * "duplicator that transmits the copy by wire or radio" (facsimile,
     * facsimile machine, fax).
     */
    @Seed.Item(key = Fax.KEY)
    @Seed.Cili("i53562")
    @Seed.Gloss(english = "duplicator that transmits the copy by wire or radio")
    @Seed.Lexeme(english = {"fax", "facsimile", "facsimile machine"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class Fax {
        public static final String KEY = "cg.sememe:fax";
        private Fax() {}
    }
}
