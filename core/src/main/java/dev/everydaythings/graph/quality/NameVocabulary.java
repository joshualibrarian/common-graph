package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.PartOfSpeech;

import static dev.everydaythings.graph.Seed.*;

/**
 * Name vocabulary — the {@link NamePart} meta-archetype and the role sememes
 * for the parts that go inside a {@code Name} body.
 *
 * <p>Cultures with different naming conventions use whichever parts apply:
 * Western names lean on {@link Given}/{@link Family}/{@link Middle}; Slavic
 * names add {@link Patronymic}; Spanish names add {@link Maternal}; Arabic
 * names chain multiple patronymics; East Asian names use the same Given +
 * Family bindings, just rendered family-first (rendering concern, not data).
 * Mononymous people have only {@link Given}.  Stage and pen names use
 * {@link Alias}.
 *
 * <p>The set here is intentionally not exhaustive.  Communities can add
 * culture-specific parts (Korean clan name, Hawaiian inoa po) by declaring
 * new sememes under {@link NamePart}.
 *
 * <p>The {@code KNOWN_AS} predicate is not declared separately.  The
 * {@code Name} archetype is itself grounded in CILI {@code i69761}
 * ("a language unit by which a person or thing is known") and serves as the
 * predicate when used in head-of-frame position: {@code Person → [Name] →
 * Name(given="Joshua")}.
 */
public final class NameVocabulary {

    private NameVocabulary() {}

    // ==================================================================================
    // Meta-archetype
    // ==================================================================================

    /**
     * NamePart — the archetype of name-part role sememes.  Each name-part
     * sememe ({@link Given}, {@link Family}, ...) declares this as its head.
     *
     * <p>Used as the binding role inside a {@code Name} body's bindings.  The
     * sememe itself isn't a quality of a person; it's the part of the name
     * that fills the slot.
     */
    @Seed.Item(key = NamePart.KEY)
    @Seed.Gloss(english = "the archetype of name-part role sememes — categories like "
                       + "given-name, family-name, nickname that fill binding slots inside a "
                       + "Name body")
    @Seed.Lexeme(english = {"name part", "name component"}, pos = PartOfSpeech.Noun.KEY)
    public static final class NamePart {
        public static final String KEY = "cg.archetype:name-part";
        private NamePart() {}
    }

    // ==================================================================================
    // Name-part sememes — fillers inside a Name body.
    // ==================================================================================

    /** Given name (first name in Western tradition; personal name). */
    @Seed.Item(key = Given.KEY, head = NamePart.KEY)
    @Seed.Gloss(english = "the part of a person's name that is given to them at birth, "
                       + "distinguishing them from family members")
    @Seed.Lexeme(english = {"given name", "first name", "forename", "Christian name"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class Given {
        public static final String KEY = "cg.name-part:given";
        private Given() {}
    }

    /** Family name (last name in Western tradition; surname). */
    @Seed.Item(key = Family.KEY, head = NamePart.KEY)
    @Seed.Gloss(english = "the part of a person's name that is shared with family, "
                       + "typically inherited from a parent")
    @Seed.Lexeme(english = {"family name", "surname", "last name"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class Family {
        public static final String KEY = "cg.name-part:family";
        private Family() {}
    }

    /** Middle name(s) — name parts positioned between given and family. */
    @Seed.Item(key = Middle.KEY, head = NamePart.KEY)
    @Seed.Gloss(english = "a name part positioned between the given and family names")
    @Seed.Lexeme(english = "middle name", pos = PartOfSpeech.Noun.KEY)
    public static final class Middle {
        public static final String KEY = "cg.name-part:middle";
        private Middle() {}
    }

    /** Nickname — informal name used by friends, family, or community. */
    @Seed.Item(key = Nickname.KEY, head = NamePart.KEY)
    @Seed.Gloss(english = "an informal or familiar name used in place of or in addition "
                       + "to a person's given name")
    @Seed.Lexeme(english = {"nickname", "pet name", "sobriquet"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class Nickname {
        public static final String KEY = "cg.name-part:nickname";
        private Nickname() {}
    }

    /** Alias — an assumed alternate name, often used to conceal identity. */
    @Seed.Item(key = Alias.KEY, head = NamePart.KEY)
    @Seed.Gloss(english = "an assumed alternate name, often used to conceal one's true "
                       + "identity or as a pseudonym")
    @Seed.Lexeme(english = {"alias", "pseudonym", "pen name"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class Alias {
        public static final String KEY = "cg.name-part:alias";
        private Alias() {}
    }

    /** Patronymic — a name derived from one's father (Slavic, Icelandic, Arabic). */
    @Seed.Item(key = Patronymic.KEY, head = NamePart.KEY)
    @Seed.Gloss(english = "a name derived from the name of one's father or paternal ancestor "
                       + "(as in Slavic, Icelandic, or Arabic naming traditions)")
    @Seed.Lexeme(english = "patronymic", pos = PartOfSpeech.Noun.KEY)
    public static final class Patronymic {
        public static final String KEY = "cg.name-part:patronymic";
        private Patronymic() {}
    }

    /**
     * Matronymic / maternal surname — name part from the mother's side
     * (common in Spanish-speaking cultures as the second surname).
     */
    @Seed.Item(key = Maternal.KEY, head = NamePart.KEY)
    @Seed.Gloss(english = "a name derived from one's mother or maternal ancestor, "
                       + "or the maternal surname in cultures that use two surnames")
    @Seed.Lexeme(english = {"matronymic", "maternal surname"}, pos = PartOfSpeech.Noun.KEY)
    public static final class Maternal {
        public static final String KEY = "cg.name-part:maternal";
        private Maternal() {}
    }

    /**
     * Honorific — a respectful or formal title prepended to a name (Mr., Dr.,
     * Sir, Honorable, etc., or culture-specific equivalents).
     */
    @Seed.Item(key = Honorific.KEY, head = NamePart.KEY)
    @Seed.Gloss(english = "a respectful or formal title prepended to a name "
                       + "(Mr., Dr., Sir, etc., or culture-specific equivalents)")
    @Seed.Lexeme(english = {"honorific", "title"}, pos = PartOfSpeech.Noun.KEY)
    public static final class Honorific {
        public static final String KEY = "cg.name-part:honorific";
        private Honorific() {}
    }

    /**
     * Suffix — a generational or credential suffix appended to a name (Jr., Sr.,
     * III, PhD, MD, etc.).
     */
    @Seed.Item(key = Suffix.KEY, head = NamePart.KEY)
    @Seed.Gloss(english = "a generational or credential suffix appended to a name "
                       + "(Jr., Sr., III, PhD, MD, ...)")
    @Seed.Lexeme(english = "name suffix", pos = PartOfSpeech.Noun.KEY)
    public static final class Suffix {
        public static final String KEY = "cg.name-part:suffix";
        private Suffix() {}
    }
}
