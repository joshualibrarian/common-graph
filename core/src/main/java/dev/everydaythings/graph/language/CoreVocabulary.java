package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;

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
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is implemented by; has its design applied by";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
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
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "declares a frame structure expected on instances of this type";
    }

    // ==================================================================================
    // ACTION VERBS
    // ==================================================================================

    @ItemSeed(key = Create.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY, ThematicRole.Name.KEY, ThematicRole.Partner.KEY, ThematicRole.Source.KEY})
    public static class Create {
        public static final String KEY = "cg.verb:create";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "make or cause to be or to become";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i29849";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "create";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "new";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "make";
    }

    @ItemSeed(key = Get.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Get {
        public static final String KEY = "cg.verb:get";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "go or come after and bring or take back";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i28895";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "get";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "retrieve";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "fetch";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word4 = "lookup";
    }

    @ItemSeed(key = Put.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Put {
        public static final String KEY = "cg.verb:put";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "find a place for and put away for storage";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33146";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "put";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "store";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "add";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word4 = "insert";
    }

    @ItemSeed(key = Remove.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Remove {
        public static final String KEY = "cg.verb:remove";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "remove something concrete, as by lifting, pushing, or taking off";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i22577";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "remove";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "delete";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "drop";
    }

    @ItemSeed(key = ListVerb.KEY, slots = {ThematicRole.Theme.KEY})
    public static class ListVerb {
        public static final String KEY = "cg.verb:list";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "enumerate; list";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i26334";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "list";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "enumerate";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "all";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word4 = "tail";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word5 = "latest";
    }

    @ItemSeed(key = Import.KEY, slots = {ThematicRole.Source.KEY})
    public static class Import {
        public static final String KEY = "cg.verb:import";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "transfer electronic data into a database or document";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32905";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "import";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "ingest";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "load";
    }

    @ItemSeed(key = Query.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Query {
        public static final String KEY = "cg.verb:query";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "pose a question";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25610";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "query";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "search";
    }

    @ItemSeed(key = Find.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Recipient.KEY, ThematicRole.Source.KEY})
    public static class Find {
        public static final String KEY = "cg.verb:find";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "find items related by a predicate";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33164";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "find";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "lookup";
    }

    @ItemSeed(key = Show.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Show {
        public static final String KEY = "cg.verb:show";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "make visible or apparent";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32454";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "show";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "display";
    }

    @ItemSeed(key = Help.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Help {
        public static final String KEY = "cg.verb:help";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "give help or assistance; be of service";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i34433";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "help";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "assist";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "commands";
    }

    @ItemSeed(key = Edit.KEY, slots = {ThematicRole.Patient.KEY})
    public static class Edit {
        public static final String KEY = "cg.verb:edit";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "prepare for publication or presentation by correcting, revising, or adapting";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i22726";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "edit";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "modify";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "change";
    }

    @ItemSeed(key = Count.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Count {
        public static final String KEY = "cg.verb:count";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "determine the number or amount of";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i26340";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "count";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "size";
    }

    @ItemSeed(key = Describe.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Describe {
        public static final String KEY = "cg.verb:describe";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "give an account or representation of in words";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i26422";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "describe";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "status";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "info";
    }

    @ItemSeed(key = Inspect.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Inspect {
        public static final String KEY = "cg.verb:inspect";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "look over carefully";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32580";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "inspect";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "examine";
    }

    @ItemSeed(key = Cd.KEY, slots = {ThematicRole.Goal.KEY})
    public static class Cd {
        public static final String KEY = "cg.verb:cd";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "change directory; navigate to";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "cd";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "go";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "enter";
    }

    @ItemSeed(key = Exit.KEY)
    public static class Exit {
        public static final String KEY = "cg.session:exit";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "exit the session";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i31816";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "exit";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "quit";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "q";
    }

    @ItemSeed(key = Back.KEY)
    public static class Back {
        public static final String KEY = "cg.session:back";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "go back to previous item";

        @ItemFrame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "..";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "back";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "pop";
    }

    @ItemSeed(key = Serve.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Serve {
        public static final String KEY = "cg.verb:serve";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "work for or be a servant to";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i96785";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "serve";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "use";
    }

    @ItemSeed(key = Invite.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Invite {
        public static final String KEY = "cg.verb:invite";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "request someone's participation";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32987";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "invite";
    }

    @ItemSeed(key = Authenticate.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Authenticate {
        public static final String KEY = "cg.session:authenticate";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "prove identity by demonstrating possession of private key";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25047";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "authenticate";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "auth";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word3 = "login";
    }

    @ItemSeed(key = Switch.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Switch {
        public static final String KEY = "cg.session:switch";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "change the active user for the current view";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i22420";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "switch";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "as";
    }

    @ItemSeed(key = Rename.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Rename {
        public static final String KEY = "cg.verb:rename";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "assign a new name to";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25424";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "rename";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word2 = "name";
    }

    // ==================================================================================
    // METADATA PREDICATES
    // ==================================================================================

    @ItemSeed(key = Author.KEY, slots = {ThematicRole.Agent.KEY})
    public static class Author {
        public static final String KEY = "cg.core:author";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the creator or originator of a work";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i90183";
    }

    @ItemSeed(key = CreatedAt.KEY, slots = {ThematicRole.Time.KEY})
    public static class CreatedAt {
        public static final String KEY = "cg.core:created-at";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the time at which something was created";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i36666";
    }

    @ItemSeed(key = ModifiedAt.KEY, slots = {ThematicRole.Time.KEY})
    public static class ModifiedAt {
        public static final String KEY = "cg.core:modified-at";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the time at which something was last modified";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i22389";
    }

    @ItemSeed(key = Title.KEY, slots = {ThematicRole.Referent.KEY})
    public static class Title {
        public static final String KEY = "cg.core:title";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the name or title of something";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69816";

        @ItemFrame(key = {CoreVocabulary.IndexWeight.KEY})
        static final int indexWeight = 1000;
    }

    @ItemSeed(key = Description.KEY, slots = {ThematicRole.Topic.KEY})
    public static class Description {
        public static final String KEY = "cg.core:description";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a textual description of something";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i71841";

        @ItemFrame(key = {CoreVocabulary.IndexWeight.KEY})
        static final int indexWeight = 500;
    }

    @ItemSeed(key = Slot.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Slot {
        public static final String KEY = "cg.core:slot";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a position in a frame that expects a particular role";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69534";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "slot";
    }

    @ItemSeed(key = LexemeSeed.KEY, slots = {ThematicRole.Referent.KEY})
    public static class LexemeSeed {
        public static final String KEY = "cg.core:lexeme";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word-meaning mapping in a language's lexicon";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69622";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "lexeme";
    }

    @ItemSeed(key = Frequency.KEY, slots = {ThematicRole.Topic.KEY})
    public static class Frequency {
        public static final String KEY = "cg.core:frequency";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "how often something occurs";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73785";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "frequency";
    }

    @ItemSeed(key = Provenance.KEY, slots = {ThematicRole.Source.KEY})
    public static class Provenance {
        public static final String KEY = "cg.core:provenance";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the origin or source of information";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i77490";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "provenance";
    }

    @ItemSeed(key = Activity.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Topic.KEY})
    public static class Activity {
        public static final String KEY = "cg.core:activity";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a record of actions or events";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i30955";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "activity";
    }

    // ==================================================================================
    // INFRASTRUCTURE PREDICATES
    // ==================================================================================

    @ItemSeed(key = Library.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Library {
        public static final String KEY = "cg.core:library";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a collection of stored items; local persistent storage";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "library";
    }

    @ItemSeed(key = Vault.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Vault {
        public static final String KEY = "cg.core:vault";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a secure store for private keys and secrets";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "vault";
    }

    @ItemSeed(key = KeyHistory.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Topic.KEY})
    public static class KeyHistory {
        public static final String KEY = "cg.core:key-history";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a public key history stream recording key lifecycle events";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "key history";
    }

    @ItemSeed(key = CertHistory.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Topic.KEY})
    public static class CertHistory {
        public static final String KEY = "cg.core:cert-history";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a certificate log tracking issued attestations";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "cert history";
    }

    @ItemSeed(key = HashKey.KEY, slots = {ThematicRole.Referent.KEY})
    public static class HashKey {
        public static final String KEY = "cg.core:hash-key";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the preimage string that was hashed to produce a deterministic identifier";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "hash key";
    }

    @ItemSeed(key = LanguageCode.KEY, slots = {ThematicRole.Referent.KEY})
    public static class LanguageCode {
        public static final String KEY = "cg.core:language-code";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "an ISO 639 code identifying a language";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "language code";
    }

    @ItemSeed(key = Canonicalization.KEY, slots = {ThematicRole.Topic.KEY})
    public static class Canonicalization {
        public static final String KEY = "cg.core:canonicalization";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "rules for normalizing and encoding values of a type";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "canonicalization";
    }

    @ItemSeed(key = Monitor.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Monitor {
        public static final String KEY = "cg.core:monitor";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "an observer of system health and resource usage";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "monitor";
    }

    // ==================================================================================
    // SEMEME METADATA PREDICATES
    // ==================================================================================

    @ItemSeed(key = Symbol.KEY, slots = {ThematicRole.Referent.KEY})
    public static class Symbol {
        public static final String KEY = "cg.core:symbol";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a language-neutral symbol representing a concept";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "symbol";
    }

    @ItemSeed(key = IndexWeight.KEY, slots = {ThematicRole.Extent.KEY})
    public static class IndexWeight {
        public static final String KEY = "cg.core:index-weight";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "how heavily to index relation targets using this predicate";
    }

    @ItemSeed(key = AssignedRole.KEY, slots = {ThematicRole.Referent.KEY})
    public static class AssignedRole {
        public static final String KEY = "cg.core:assigned-role";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the thematic role a function word assigns to its object";
    }

    @ItemSeed(key = Facet.KEY, slots = {ThematicRole.Topic.KEY})
    public static class Facet {
        public static final String KEY = "cg.core:facet";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a domain, range, or cardinality constraint on a predicate";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
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
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "qualifier indicating append-only stream access mode";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "stream";
    }

    /** Qualifier: content is stored externally at a filesystem path (compound key with Topic). */
    @ItemSeed(key = External.KEY)
    public static class External {
        public static final String KEY = "cg.core:external";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "qualifier indicating content stored externally at a filesystem path";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "external";
    }

    /** Qualifier: content is encrypted (compound key with Topic). */
    @ItemSeed(key = Encrypted.KEY)
    public static class Encrypted {
        public static final String KEY = "cg.core:encrypted";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "qualifier indicating content is encrypted";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "encrypted";
    }

    // ==================================================================================
    // EXTERNAL ID PREDICATES
    // ==================================================================================

    @ItemSeed(key = CiliId.KEY, slots = {ThematicRole.Referent.KEY})
    public static class CiliId {
        public static final String KEY = "cg.core:cili-id";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a Collaborative Interlingual Index identifier anchoring a concept across languages";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "CILI";
    }

    // ==================================================================================
    // VALUE & OPERATOR PREDICATES
    // ==================================================================================

    @ItemSeed(key = Arity.KEY)
    public static class Arity {
        public static final String KEY = "cg.core:arity";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "number of operands or arguments";
    }

    @ItemSeed(key = Precedence.KEY)
    public static class Precedence {
        public static final String KEY = "cg.core:precedence";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "parsing priority of an operator";
    }

    @ItemSeed(key = Associativity.KEY)
    public static class Associativity {
        public static final String KEY = "cg.core:associativity";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "left or right grouping direction of an operator";
    }

    @ItemSeed(key = Fixity.KEY)
    public static class Fixity {
        public static final String KEY = "cg.core:fixity";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "position of an operator relative to its operands";
    }

    @ItemSeed(key = Category.KEY)
    public static class Category {
        public static final String KEY = "cg.core:category";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "classification or grouping";
    }

    @ItemSeed(key = Bounds.KEY)
    public static class Bounds {
        public static final String KEY = "cg.core:bounds";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "value range constraints";
    }

    @ItemSeed(key = UnitRules.KEY)
    public static class UnitRules {
        public static final String KEY = "cg.core:unit-rules";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "rules governing units for a value type";
    }

    @ItemSeed(key = DimensionFormula.KEY)
    public static class DimensionFormula {
        public static final String KEY = "cg.core:dimension-formula";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "dimensional analysis formula mapping dimensions to exponents";
    }

    @ItemSeed(key = ScaleNumerator.KEY)
    public static class ScaleNumerator {
        public static final String KEY = "cg.core:scale-numerator";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "numerator of a unit's scale factor relative to SI base";
    }

    @ItemSeed(key = ScaleDenominator.KEY)
    public static class ScaleDenominator {
        public static final String KEY = "cg.core:scale-denominator";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "denominator of a unit's scale factor relative to SI base";
    }

    @ItemSeed(key = Lexicon.KEY)
    public static class Lexicon {
        public static final String KEY = "cg.core:lexicon";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a language's word inventory";
    }

    @ItemSeed(key = Lexeme.KEY)
    public static class Lexeme {
        public static final String KEY = "cg.core:lexeme";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word form realizing a sememe in a specific language";
    }

    @ItemSeed(key = DialectOf.KEY)
    public static class DialectOf {
        public static final String KEY = "cg.core:dialect-of";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "regional or dialectal variant of a parent language";
    }

    @ItemSeed(key = Names.KEY)
    public static class Names {
        public static final String KEY = "cg.core:names";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "language-tagged display names";
    }

    // ==================================================================================
    // EVALUATION PREDICATES (control flow for frame evaluation)
    // ==================================================================================

    /** Conditional branching: THEME=condition, RESULT=then-branch, GOAL=else-branch. */
    @ItemSeed(key = Conditional.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Result.KEY, ThematicRole.Goal.KEY})
    public static class Conditional {
        public static final String KEY = "cg.eval:conditional";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "conditional evaluation; if-then-else branching";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "if";
    }

    /** Sequential evaluation: multiple THEME bindings evaluated in order, returns last. */
    @ItemSeed(key = Sequence.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Sequence {
        public static final String KEY = "cg.eval:sequence";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "sequential evaluation; evaluates steps in order";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "sequence";
    }

    /** Let binding: GOAL=name, THEME=value, RESULT=body evaluated in child scope. */
    @ItemSeed(key = Let.KEY, slots = {ThematicRole.Goal.KEY, ThematicRole.Theme.KEY, ThematicRole.Result.KEY})
    public static class Let {
        public static final String KEY = "cg.eval:let";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "scoped variable binding; let-in expression";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "let";
    }

    /** Variable resolution: THEME=name to look up in scope chain. */
    @ItemSeed(key = Resolve.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Resolve {
        public static final String KEY = "cg.eval:resolve";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "resolve a variable name from the scope chain";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "resolve";
    }

    /** Property access: THEME=object, GOAL=property to access. */
    @ItemSeed(key = Access.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Access {
        public static final String KEY = "cg.eval:access";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "access a property on an object";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String word1 = "access";
    }
}
