package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.item.ItemSeed;

/**
 * Core vocabulary seeds — the fundamental predicates and actions CG needs to function.
 *
 * <p>Contains action verbs (create, get, put, etc.), metadata predicates
 * (author, title, description), session verbs (exit, back, authenticate),
 * and infrastructure concepts (library, vault, key history).
 *
 * <p>Seeds here are plain {@link Sememe} instances. Part of speech is a
 * property on each seed, not a class identity.
 *
 * @see LexicalVocabulary for semantic/lexical relations (hypernym, antonym, etc.)
 * @see PrepositionVocabulary for thematic role carriers
 * @see dev.everydaythings.graph.network.RoutingVocabulary for network/routing concepts
 */
public final class CoreVocabulary {

    private CoreVocabulary() {}

    // ==================================================================================
    // TYPE SYSTEM
    // ==================================================================================

    @ItemSeed(key = ImplementedBy.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class ImplementedBy {
        public static final String KEY = "cg.type:implemented-by";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "is implemented by; has its design applied by")
                .cili("i33787")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is implemented by; has its design applied by";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33787";
    }

    /**
     * Declares a frame structure expected on instances of a type.
     *
     * <p>EXPECTS frames live on a Sememe (type definition) and describe what
     * frames its instances should carry. TOPIC identifies the expected predicate;
     * additional roles constrain expected bindings.
     *
     * <p>Serves double duty: forward (creation guidance — "chess needs players")
     * and backward (duck typing — "this item has players and moves, it's chess").
     */
    @ItemSeed(key = Expects.KEY, slots = {ThematicRole.Topic.KEY})
    public static class Expects {
        public static final String KEY = "cg.type:expects";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "declares a frame structure expected on instances of this type")
                .slot(ThematicRole.Topic.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "declares a frame structure expected on instances of this type";
    }

    // ==================================================================================
    // ACTION VERBS
    // ==================================================================================

    @ItemSeed(key = Create.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY, ThematicRole.Name.KEY, ThematicRole.Partner.KEY, ThematicRole.Source.KEY})
    public static class Create {
        public static final String KEY = "cg.verb:create";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "make or cause to be or to become").cili("i29849")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "create").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "new").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "make")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .slot(ThematicRole.Name.KEY).slot(ThematicRole.Partner.KEY)
                .slot(ThematicRole.Source.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "make or cause to be or to become";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i29849";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "create";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "new";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "make";
    }

    @ItemSeed(key = Get.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Get {
        public static final String KEY = "cg.verb:get";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "go or come after and bring or take back").cili("i28895")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "get").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "retrieve")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "fetch").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "lookup")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "go or come after and bring or take back";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i28895";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "get";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "retrieve";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "fetch";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word4 = "lookup";
    }

    @ItemSeed(key = Put.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Put {
        public static final String KEY = "cg.verb:put";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "find a place for and put away for storage").cili("i33146")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "put").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "store")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "add").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "insert")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "find a place for and put away for storage";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33146";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "put";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "store";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "add";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word4 = "insert";
    }

    @ItemSeed(key = Remove.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Remove {
        public static final String KEY = "cg.verb:remove";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "remove something concrete, as by lifting, pushing, or taking off").cili("i22577")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "remove").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "delete").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "drop")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "remove something concrete, as by lifting, pushing, or taking off";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i22577";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "remove";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "delete";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "drop";
    }

    @ItemSeed(key = ListVerb.KEY, slots = {ThematicRole.Theme.KEY})
    public static class ListVerb {
        public static final String KEY = "cg.verb:list";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "enumerate; list").cili("i26334")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "list").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "enumerate")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "all").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "tail").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "latest")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "enumerate; list";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i26334";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "list";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "enumerate";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "all";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word4 = "tail";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word5 = "latest";
    }

    @ItemSeed(key = Import.KEY, slots = {ThematicRole.Source.KEY})
    public static class Import {
        public static final String KEY = "cg.verb:import";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "transfer electronic data into a database or document").cili("i32905")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "import").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "ingest").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "load")
                .slot(ThematicRole.Source.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "transfer electronic data into a database or document";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32905";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "import";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "ingest";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "load";
    }

    @ItemSeed(key = Query.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Query {
        public static final String KEY = "cg.verb:query";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "pose a question").cili("i25610")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "query").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "search")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "pose a question";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25610";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "query";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "search";
    }

    @ItemSeed(key = Find.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Recipient.KEY, ThematicRole.Source.KEY})
    public static class Find {
        public static final String KEY = "cg.verb:find";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "find items related by a predicate").cili("i33164")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "find").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "lookup")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Recipient.KEY)
                .slot(ThematicRole.Source.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "find items related by a predicate";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33164";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "find";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "lookup";
    }

    @ItemSeed(key = Show.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Show {
        public static final String KEY = "cg.verb:show";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "make visible or apparent").cili("i32454")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "show").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "display")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "make visible or apparent";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32454";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "show";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "display";
    }

    @ItemSeed(key = Help.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Help {
        public static final String KEY = "cg.verb:help";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "give help or assistance; be of service").cili("i34433")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "help").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "assist").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "commands")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "give help or assistance; be of service";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i34433";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "help";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "assist";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "commands";
    }

    @ItemSeed(key = Edit.KEY, slots = {ThematicRole.Patient.KEY})
    public static class Edit {
        public static final String KEY = "cg.verb:edit";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "prepare for publication or presentation by correcting, revising, or adapting").cili("i22726")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "edit").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "modify").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "change")
                .slot(ThematicRole.Patient.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "prepare for publication or presentation by correcting, revising, or adapting";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i22726";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "edit";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "modify";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "change";
    }

    @ItemSeed(key = Count.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Count {
        public static final String KEY = "cg.verb:count";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "determine the number or amount of").cili("i26340")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "count").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "size")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "determine the number or amount of";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i26340";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "count";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "size";
    }

    @ItemSeed(key = Describe.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Describe {
        public static final String KEY = "cg.verb:describe";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "give an account or representation of in words").cili("i26422")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "describe").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "status").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "info")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "give an account or representation of in words";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i26422";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "describe";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "status";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "info";
    }

    @ItemSeed(key = Inspect.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Inspect {
        public static final String KEY = "cg.verb:inspect";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "look over carefully").cili("i32580")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "inspect").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "examine")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "look over carefully";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32580";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "inspect";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "examine";
    }

    @ItemSeed(key = Cd.KEY, slots = {ThematicRole.Goal.KEY})
    public static class Cd {
        public static final String KEY = "cg.verb:cd";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "change directory; navigate to")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "cd").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "go").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "enter")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "change directory; navigate to";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "cd";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "go";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "enter";
    }

    @ItemSeed(key = Exit.KEY)
    public static class Exit {
        public static final String KEY = "cg.session:exit";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "exit the session").cili("i31816")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "exit").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "quit").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "q");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "exit the session";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i31816";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "exit";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "quit";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "q";
    }

    @ItemSeed(key = Back.KEY)
    public static class Back {
        public static final String KEY = "cg.session:back";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "go back to previous item")
                .symbol("..").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "back").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "pop");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "go back to previous item";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "..";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "back";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "pop";
    }

    @ItemSeed(key = Serve.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Serve {
        public static final String KEY = "cg.verb:serve";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "work for or be a servant to").cili("i96785")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "serve").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "use")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "work for or be a servant to";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i96785";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "serve";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "use";
    }

    @ItemSeed(key = Invite.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Invite {
        public static final String KEY = "cg.verb:invite";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "request someone's participation").cili("i32987")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "invite")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "request someone's participation";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32987";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "invite";
    }

    @ItemSeed(key = Authenticate.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Authenticate {
        public static final String KEY = "cg.session:authenticate";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "prove identity by demonstrating possession of private key").cili("i25047")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "authenticate").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "auth").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "login")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "prove identity by demonstrating possession of private key";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25047";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "authenticate";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "auth";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word3 = "login";
    }

    @ItemSeed(key = Switch.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Switch {
        public static final String KEY = "cg.session:switch";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "change the active user for the current view").cili("i22420")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "switch").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "as")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "change the active user for the current view";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i22420";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "switch";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "as";
    }

    @ItemSeed(key = Rename.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Rename {
        public static final String KEY = "cg.verb:rename";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "assign a new name to").cili("i25424")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "rename").word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "name")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "assign a new name to";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25424";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "rename";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word2 = "name";
    }

    // ==================================================================================
    // METADATA PREDICATES
    // ==================================================================================

    @ItemSeed(key = Author.KEY, slots = {ThematicRole.Agent.KEY})
    public static class Author {
        public static final String KEY = "cg.core:author";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the creator or originator of a work")
                .cili("i90183")
                .slot(ThematicRole.Agent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the creator or originator of a work";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i90183";
    }

    @ItemSeed(key = CreatedAt.KEY, slots = {ThematicRole.Time.KEY})
    public static class CreatedAt {
        public static final String KEY = "cg.core:created-at";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the time at which something was created")
                .cili("i36666")
                .slot(ThematicRole.Time.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the time at which something was created";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i36666";
    }

    @ItemSeed(key = ModifiedAt.KEY, slots = {ThematicRole.Time.KEY})
    public static class ModifiedAt {
        public static final String KEY = "cg.core:modified-at";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the time at which something was last modified")
                .cili("i22389")
                .slot(ThematicRole.Time.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the time at which something was last modified";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i22389";
    }

    @ItemSeed(key = Title.KEY, slots = {ThematicRole.Referent.KEY})
    public static class Title {
        public static final String KEY = "cg.core:title";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the name or title of something")
                .cili("i69816")
                .indexWeight(1000)
                .slot(ThematicRole.Referent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the name or title of something";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69816";

        @ItemSeed.Frame(key = {CoreVocabulary.IndexWeight.KEY})
        static final int indexWeight = 1000;
    }

    @ItemSeed(key = Description.KEY, slots = {ThematicRole.Topic.KEY})
    public static class Description {
        public static final String KEY = "cg.core:description";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a textual description of something")
                .cili("i71841")
                .indexWeight(500)
                .slot(ThematicRole.Topic.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a textual description of something";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i71841";

        @ItemSeed.Frame(key = {CoreVocabulary.IndexWeight.KEY})
        static final int indexWeight = 500;
    }

    @ItemSeed(key = Slot.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Slot {
        public static final String KEY = "cg.core:slot";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a position in a frame that expects a particular role")
                .cili("i69534")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "slot")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a position in a frame that expects a particular role";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69534";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "slot";
    }

    @ItemSeed(key = LexemeSeed.KEY, slots = {ThematicRole.Referent.KEY})
    public static class LexemeSeed {
        public static final String KEY = "cg.core:lexeme";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a word-meaning mapping in a language's lexicon")
                .cili("i69622")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "lexeme")
                .slot(ThematicRole.Referent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word-meaning mapping in a language's lexicon";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69622";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "lexeme";
    }

    @ItemSeed(key = Frequency.KEY, slots = {ThematicRole.Topic.KEY})
    public static class Frequency {
        public static final String KEY = "cg.core:frequency";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "how often something occurs")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "frequency")
                .cili("i73785")
                .slot(ThematicRole.Topic.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "how often something occurs";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73785";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "frequency";
    }

    @ItemSeed(key = Provenance.KEY, slots = {ThematicRole.Source.KEY})
    public static class Provenance {
        public static final String KEY = "cg.core:provenance";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the origin or source of information")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "provenance")
                .cili("i77490")
                .slot(ThematicRole.Source.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the origin or source of information";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i77490";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "provenance";
    }

    @ItemSeed(key = Activity.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Topic.KEY})
    public static class Activity {
        public static final String KEY = "cg.core:activity";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a record of actions or events")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "activity")
                .slot(ThematicRole.Theme.KEY)
                .slot(ThematicRole.Topic.KEY)
                .cili("i30955");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a record of actions or events";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i30955";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "activity";
    }

    // ==================================================================================
    // INFRASTRUCTURE PREDICATES
    // ==================================================================================

    @ItemSeed(key = Library.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Library {
        public static final String KEY = "cg.core:library";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a collection of stored items; local persistent storage")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "library")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a collection of stored items; local persistent storage";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "library";
    }

    @ItemSeed(key = Vault.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Vault {
        public static final String KEY = "cg.core:vault";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a secure store for private keys and secrets")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "vault")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a secure store for private keys and secrets";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "vault";
    }

    @ItemSeed(key = KeyHistory.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Topic.KEY})
    public static class KeyHistory {
        public static final String KEY = "cg.core:key-history";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a public key history stream recording key lifecycle events")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "key history")
                .slot(ThematicRole.Theme.KEY)
                .slot(ThematicRole.Topic.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a public key history stream recording key lifecycle events";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "key history";
    }

    @ItemSeed(key = CertHistory.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Topic.KEY})
    public static class CertHistory {
        public static final String KEY = "cg.core:cert-history";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a certificate log tracking issued attestations")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "cert history")
                .slot(ThematicRole.Theme.KEY)
                .slot(ThematicRole.Topic.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a certificate log tracking issued attestations";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "cert history";
    }

    @ItemSeed(key = HashKey.KEY, slots = {ThematicRole.Referent.KEY})
    public static class HashKey {
        public static final String KEY = "cg.core:hash-key";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the preimage string that was hashed to produce a deterministic identifier")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "hash key")
                .slot(ThematicRole.Referent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the preimage string that was hashed to produce a deterministic identifier";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "hash key";
    }

    @ItemSeed(key = LanguageCode.KEY, slots = {ThematicRole.Referent.KEY})
    public static class LanguageCode {
        public static final String KEY = "cg.core:language-code";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "an ISO 639 code identifying a language")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "language code")
                .slot(ThematicRole.Referent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "an ISO 639 code identifying a language";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "language code";
    }

    @ItemSeed(key = Canonicalization.KEY, slots = {ThematicRole.Topic.KEY})
    public static class Canonicalization {
        public static final String KEY = "cg.core:canonicalization";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "rules for normalizing and encoding values of a type")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "canonicalization")
                .slot(ThematicRole.Topic.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "rules for normalizing and encoding values of a type";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "canonicalization";
    }

    @ItemSeed(key = Monitor.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Monitor {
        public static final String KEY = "cg.core:monitor";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "an observer of system health and resource usage")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "monitor")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "an observer of system health and resource usage";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "monitor";
    }

    // ==================================================================================
    // SEMEME METADATA PREDICATES
    // ==================================================================================

    @ItemSeed(key = Symbol.KEY, slots = {ThematicRole.Referent.KEY})
    public static class Symbol {
        public static final String KEY = "cg.core:symbol";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a language-neutral symbol representing a concept")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "symbol")
                .slot(ThematicRole.Referent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a language-neutral symbol representing a concept";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "symbol";
    }

    @ItemSeed(key = IndexWeight.KEY, slots = {ThematicRole.Extent.KEY})
    public static class IndexWeight {
        public static final String KEY = "cg.core:index-weight";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "how heavily to index relation targets using this predicate")
                .slot(ThematicRole.Extent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "how heavily to index relation targets using this predicate";
    }

    @ItemSeed(key = AssignedRole.KEY, slots = {ThematicRole.Referent.KEY})
    public static class AssignedRole {
        public static final String KEY = "cg.core:assigned-role";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the thematic role a function word assigns to its object")
                .slot(ThematicRole.Referent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the thematic role a function word assigns to its object";
    }

    @ItemSeed(key = Facet.KEY, slots = {ThematicRole.Topic.KEY})
    public static class Facet {
        public static final String KEY = "cg.core:facet";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a domain, range, or cardinality constraint on a predicate")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "facet")
                .slot(ThematicRole.Topic.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a domain, range, or cardinality constraint on a predicate";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "facet";
    }

    // ==================================================================================
    // BINDING QUALIFIERS
    //
    // Sememes used as compound key qualifiers on role bindings.
    // They modify HOW a role's value is accessed — not what the role IS.
    // E.g., (TOPIC, STREAM) means "the topic is a stream head,"
    //       (TOPIC, ENCRYPTED) means "the topic is encrypted."
    // ==================================================================================

    /** Qualifier: content is an append-only stream (compound key with Topic). */
    @ItemSeed(key = Stream.KEY)
    public static class Stream {
        public static final String KEY = "cg.core:stream";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "qualifier indicating append-only stream access mode")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "stream");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "qualifier indicating append-only stream access mode";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "stream";
    }

    /** Qualifier: content is stored externally at a filesystem path (compound key with Topic). */
    @ItemSeed(key = External.KEY)
    public static class External {
        public static final String KEY = "cg.core:external";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "qualifier indicating content stored externally at a filesystem path")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "external");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "qualifier indicating content stored externally at a filesystem path";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "external";
    }

    /** Qualifier: content is encrypted (compound key with Topic). */
    @ItemSeed(key = Encrypted.KEY)
    public static class Encrypted {
        public static final String KEY = "cg.core:encrypted";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "qualifier indicating content is encrypted")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "encrypted");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "qualifier indicating content is encrypted";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "encrypted";
    }

    // ==================================================================================
    // EXTERNAL ID PREDICATES
    // ==================================================================================

    @ItemSeed(key = CiliId.KEY, slots = {ThematicRole.Referent.KEY})
    public static class CiliId {
        public static final String KEY = "cg.core:cili-id";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a Collaborative Interlingual Index identifier anchoring a concept across languages")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "CILI")
                .slot(ThematicRole.Referent.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a Collaborative Interlingual Index identifier anchoring a concept across languages";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String word1 = "CILI";
    }

    // ==================================================================================
    // VALUE & OPERATOR PREDICATES
    // ==================================================================================

    @ItemSeed(key = Arity.KEY)
    public static class Arity {
        public static final String KEY = "cg.core:arity";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "number of operands or arguments");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "number of operands or arguments";
    }

    @ItemSeed(key = Precedence.KEY)
    public static class Precedence {
        public static final String KEY = "cg.core:precedence";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "parsing priority of an operator");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "parsing priority of an operator";
    }

    @ItemSeed(key = Associativity.KEY)
    public static class Associativity {
        public static final String KEY = "cg.core:associativity";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "left or right grouping direction of an operator");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "left or right grouping direction of an operator";
    }

    @ItemSeed(key = Fixity.KEY)
    public static class Fixity {
        public static final String KEY = "cg.core:fixity";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "position of an operator relative to its operands");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "position of an operator relative to its operands";
    }

    @ItemSeed(key = Category.KEY)
    public static class Category {
        public static final String KEY = "cg.core:category";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "classification or grouping");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "classification or grouping";
    }

    @ItemSeed(key = Bounds.KEY)
    public static class Bounds {
        public static final String KEY = "cg.core:bounds";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "value range constraints");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "value range constraints";
    }

    @ItemSeed(key = UnitRules.KEY)
    public static class UnitRules {
        public static final String KEY = "cg.core:unit-rules";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "rules governing units for a value type");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "rules governing units for a value type";
    }

    @ItemSeed(key = DimensionFormula.KEY)
    public static class DimensionFormula {
        public static final String KEY = "cg.core:dimension-formula";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "dimensional analysis formula mapping dimensions to exponents");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "dimensional analysis formula mapping dimensions to exponents";
    }

    @ItemSeed(key = ScaleNumerator.KEY)
    public static class ScaleNumerator {
        public static final String KEY = "cg.core:scale-numerator";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "numerator of a unit's scale factor relative to SI base");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "numerator of a unit's scale factor relative to SI base";
    }

    @ItemSeed(key = ScaleDenominator.KEY)
    public static class ScaleDenominator {
        public static final String KEY = "cg.core:scale-denominator";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "denominator of a unit's scale factor relative to SI base");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "denominator of a unit's scale factor relative to SI base";
    }

    @ItemSeed(key = Lexicon.KEY)
    public static class Lexicon {
        public static final String KEY = "cg.core:lexicon";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a language's word inventory");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a language's word inventory";
    }

    @ItemSeed(key = Lexeme.KEY)
    public static class Lexeme {
        public static final String KEY = "cg.core:lexeme";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a word form realizing a sememe in a specific language");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word form realizing a sememe in a specific language";
    }

    @ItemSeed(key = DialectOf.KEY)
    public static class DialectOf {
        public static final String KEY = "cg.core:dialect-of";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "regional or dialectal variant of a parent language");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "regional or dialectal variant of a parent language";
    }

    @ItemSeed(key = Names.KEY)
    public static class Names {
        public static final String KEY = "cg.core:names";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "language-tagged display names");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "language-tagged display names";
    }
}
