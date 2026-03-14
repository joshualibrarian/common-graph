package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item.Seed;

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

    public static class ImplementedBy {
        public static final String KEY = "cg.type:implemented-by";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "is implemented by; has its design applied by")
                .cili("i33787")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    // ==================================================================================
    // ACTION VERBS
    // ==================================================================================

    public static class Create {
        public static final String KEY = "cg.verb:create";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "make or cause to be or to become").cili("i29849")
                .word(Sememe.LEMMA, Sememe.ENG, "create").word(Sememe.LEMMA, Sememe.ENG, "new").word(Sememe.LEMMA, Sememe.ENG, "make")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .slot(ThematicRole.Name.KEY).slot(ThematicRole.Partner.KEY)
                .slot(ThematicRole.Source.KEY);
    }

    public static class Get {
        public static final String KEY = "cg.verb:get";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "go or come after and bring or take back").cili("i28895")
                .word(Sememe.LEMMA, Sememe.ENG, "get").word(Sememe.LEMMA, Sememe.ENG, "retrieve")
                .word(Sememe.LEMMA, Sememe.ENG, "fetch").word(Sememe.LEMMA, Sememe.ENG, "lookup")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Put {
        public static final String KEY = "cg.verb:put";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "find a place for and put away for storage").cili("i33146")
                .word(Sememe.LEMMA, Sememe.ENG, "put").word(Sememe.LEMMA, Sememe.ENG, "store")
                .word(Sememe.LEMMA, Sememe.ENG, "add").word(Sememe.LEMMA, Sememe.ENG, "insert")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Remove {
        public static final String KEY = "cg.verb:remove";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "remove something concrete, as by lifting, pushing, or taking off").cili("i22577")
                .word(Sememe.LEMMA, Sememe.ENG, "remove").word(Sememe.LEMMA, Sememe.ENG, "delete").word(Sememe.LEMMA, Sememe.ENG, "drop")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class ListVerb {
        public static final String KEY = "cg.verb:list";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "enumerate; list").cili("i26334")
                .word(Sememe.LEMMA, Sememe.ENG, "list").word(Sememe.LEMMA, Sememe.ENG, "enumerate")
                .word(Sememe.LEMMA, Sememe.ENG, "all").word(Sememe.LEMMA, Sememe.ENG, "tail").word(Sememe.LEMMA, Sememe.ENG, "latest")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Import {
        public static final String KEY = "cg.verb:import";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "transfer electronic data into a database or document").cili("i32905")
                .word(Sememe.LEMMA, Sememe.ENG, "import").word(Sememe.LEMMA, Sememe.ENG, "ingest").word(Sememe.LEMMA, Sememe.ENG, "load")
                .slot(ThematicRole.Source.KEY);
    }

    public static class Query {
        public static final String KEY = "cg.verb:query";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "pose a question").cili("i25610")
                .word(Sememe.LEMMA, Sememe.ENG, "query").word(Sememe.LEMMA, Sememe.ENG, "search")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Find {
        public static final String KEY = "cg.verb:find";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "find items related by a predicate").cili("i33164")
                .word(Sememe.LEMMA, Sememe.ENG, "find").word(Sememe.LEMMA, Sememe.ENG, "lookup")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Recipient.KEY)
                .slot(ThematicRole.Source.KEY);
    }

    public static class Show {
        public static final String KEY = "cg.verb:show";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "make visible or apparent").cili("i32454")
                .word(Sememe.LEMMA, Sememe.ENG, "show").word(Sememe.LEMMA, Sememe.ENG, "display").word(Sememe.LEMMA, Sememe.ENG, "view")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Help {
        public static final String KEY = "cg.verb:help";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "give help or assistance; be of service").cili("i34433")
                .word(Sememe.LEMMA, Sememe.ENG, "help").word(Sememe.LEMMA, Sememe.ENG, "assist").word(Sememe.LEMMA, Sememe.ENG, "commands")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Edit {
        public static final String KEY = "cg.verb:edit";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "prepare for publication or presentation by correcting, revising, or adapting").cili("i22726")
                .word(Sememe.LEMMA, Sememe.ENG, "edit").word(Sememe.LEMMA, Sememe.ENG, "modify").word(Sememe.LEMMA, Sememe.ENG, "change")
                .slot(ThematicRole.Patient.KEY);
    }

    public static class Count {
        public static final String KEY = "cg.verb:count";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "determine the number or amount of").cili("i26340")
                .word(Sememe.LEMMA, Sememe.ENG, "count").word(Sememe.LEMMA, Sememe.ENG, "size")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Describe {
        public static final String KEY = "cg.verb:describe";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "give an account or representation of in words").cili("i26422")
                .word(Sememe.LEMMA, Sememe.ENG, "describe").word(Sememe.LEMMA, Sememe.ENG, "status").word(Sememe.LEMMA, Sememe.ENG, "info")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Inspect {
        public static final String KEY = "cg.verb:inspect";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "look over carefully").cili("i32580")
                .word(Sememe.LEMMA, Sememe.ENG, "inspect").word(Sememe.LEMMA, Sememe.ENG, "examine")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Cd {
        public static final String KEY = "cg.verb:cd";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "change directory; navigate to")
                .word(Sememe.LEMMA, Sememe.ENG, "cd").word(Sememe.LEMMA, Sememe.ENG, "go").word(Sememe.LEMMA, Sememe.ENG, "enter")
                .slot(ThematicRole.Goal.KEY);
    }

    public static class Exit {
        public static final String KEY = "cg.session:exit";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "exit the session").cili("i31816")
                .word(Sememe.LEMMA, Sememe.ENG, "exit").word(Sememe.LEMMA, Sememe.ENG, "quit").word(Sememe.LEMMA, Sememe.ENG, "q");
    }

    public static class Back {
        public static final String KEY = "cg.session:back";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "go back to previous item")
                .symbol("..").word(Sememe.LEMMA, Sememe.ENG, "back").word(Sememe.LEMMA, Sememe.ENG, "pop");
    }

    public static class Serve {
        public static final String KEY = "cg.verb:serve";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "work for or be a servant to").cili("i96785")
                .word(Sememe.LEMMA, Sememe.ENG, "serve").word(Sememe.LEMMA, Sememe.ENG, "use")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Invite {
        public static final String KEY = "cg.verb:invite";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "request someone's participation").cili("i32987")
                .word(Sememe.LEMMA, Sememe.ENG, "invite")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Authenticate {
        public static final String KEY = "cg.session:authenticate";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "prove identity by demonstrating possession of private key").cili("i25047")
                .word(Sememe.LEMMA, Sememe.ENG, "authenticate").word(Sememe.LEMMA, Sememe.ENG, "auth").word(Sememe.LEMMA, Sememe.ENG, "login")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Switch {
        public static final String KEY = "cg.session:switch";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "change the active user for the current view").cili("i22420")
                .word(Sememe.LEMMA, Sememe.ENG, "switch").word(Sememe.LEMMA, Sememe.ENG, "as")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Rename {
        public static final String KEY = "cg.verb:rename";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "assign a new name to").cili("i25424")
                .word(Sememe.LEMMA, Sememe.ENG, "rename").word(Sememe.LEMMA, Sememe.ENG, "name")
                .slot(ThematicRole.Theme.KEY);
    }

    // ==================================================================================
    // METADATA PREDICATES
    // ==================================================================================

    public static class Author {
        public static final String KEY = "cg.core:author";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "the creator or originator of a work")
                .cili("i90183")
                .slot(ThematicRole.Agent.KEY);
    }

    public static class CreatedAt {
        public static final String KEY = "cg.core:created-at";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "the time at which something was created")
                .cili("i36666")
                .slot(ThematicRole.Time.KEY);
    }

    public static class ModifiedAt {
        public static final String KEY = "cg.core:modified-at";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "the time at which something was last modified")
                .cili("i22389")
                .slot(ThematicRole.Time.KEY);
    }

    public static class Title {
        public static final String KEY = "cg.core:title";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "the name or title of something")
                .cili("i69816")
                .indexWeight(1000)
                .slot(ThematicRole.Referent.KEY);
    }

    public static class Description {
        public static final String KEY = "cg.core:description";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a textual description of something")
                .cili("i71841")
                .indexWeight(500)
                .slot(ThematicRole.Topic.KEY);
    }

    public static class Slot {
        public static final String KEY = "cg.core:slot";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a position in a frame that expects a particular role")
                .cili("i69534")
                .word(Sememe.LEMMA, Sememe.ENG, "slot")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class LexemeSeed {
        public static final String KEY = "cg.core:lexeme";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a word-meaning mapping in a language's lexicon")
                .cili("i69622")
                .word(Sememe.LEMMA, Sememe.ENG, "lexeme")
                .slot(ThematicRole.Referent.KEY);
    }

    public static class Frequency {
        public static final String KEY = "cg.core:frequency";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "how often something occurs")
                .word(Sememe.LEMMA, Sememe.ENG, "frequency")
                .cili("i73785")
                .slot(ThematicRole.Topic.KEY);
    }

    public static class Provenance {
        public static final String KEY = "cg.core:provenance";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "the origin or source of information")
                .word(Sememe.LEMMA, Sememe.ENG, "provenance")
                .cili("i77490")
                .slot(ThematicRole.Source.KEY);
    }

    public static class Activity {
        public static final String KEY = "cg.core:activity";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a record of actions or events")
                .word(Sememe.LEMMA, Sememe.ENG, "activity")
                .slot(ThematicRole.Theme.KEY)
                .slot(ThematicRole.Topic.KEY)
                .cili("i30955");
    }

    // ==================================================================================
    // INFRASTRUCTURE PREDICATES
    // ==================================================================================

    public static class Library {
        public static final String KEY = "cg.core:library";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a collection of stored items; local persistent storage")
                .word(Sememe.LEMMA, Sememe.ENG, "library")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Vault {
        public static final String KEY = "cg.core:vault";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a secure store for private keys and secrets")
                .word(Sememe.LEMMA, Sememe.ENG, "vault")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class KeyHistory {
        public static final String KEY = "cg.core:key-history";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a public key history stream recording key lifecycle events")
                .word(Sememe.LEMMA, Sememe.ENG, "key history")
                .slot(ThematicRole.Theme.KEY)
                .slot(ThematicRole.Topic.KEY);
    }

    public static class CertHistory {
        public static final String KEY = "cg.core:cert-history";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a certificate log tracking issued attestations")
                .word(Sememe.LEMMA, Sememe.ENG, "cert history")
                .slot(ThematicRole.Theme.KEY)
                .slot(ThematicRole.Topic.KEY);
    }

    public static class HashKey {
        public static final String KEY = "cg.core:hash-key";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "the preimage string that was hashed to produce a deterministic identifier")
                .word(Sememe.LEMMA, Sememe.ENG, "hash key")
                .slot(ThematicRole.Referent.KEY);
    }

    public static class LanguageCode {
        public static final String KEY = "cg.core:language-code";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "an ISO 639 code identifying a language")
                .word(Sememe.LEMMA, Sememe.ENG, "language code")
                .slot(ThematicRole.Referent.KEY);
    }

    public static class Canonicalization {
        public static final String KEY = "cg.core:canonicalization";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "rules for normalizing and encoding values of a type")
                .word(Sememe.LEMMA, Sememe.ENG, "canonicalization")
                .slot(ThematicRole.Topic.KEY);
    }

    public static class Monitor {
        public static final String KEY = "cg.core:monitor";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "an observer of system health and resource usage")
                .word(Sememe.LEMMA, Sememe.ENG, "monitor")
                .slot(ThematicRole.Theme.KEY);
    }

    // ==================================================================================
    // SEMEME METADATA PREDICATES
    // ==================================================================================

    public static class Symbol {
        public static final String KEY = "cg.core:symbol";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a language-neutral symbol representing a concept")
                .word(Sememe.LEMMA, Sememe.ENG, "symbol")
                .slot(ThematicRole.Referent.KEY);
    }

    public static class IndexWeight {
        public static final String KEY = "cg.core:index-weight";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "how heavily to index relation targets using this predicate")
                .slot(ThematicRole.Extent.KEY);
    }

    public static class AssignedRole {
        public static final String KEY = "cg.core:assigned-role";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "the thematic role a function word assigns to its object")
                .slot(ThematicRole.Referent.KEY);
    }

    public static class Facet {
        public static final String KEY = "cg.core:facet";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a domain, range, or cardinality constraint on a predicate")
                .word(Sememe.LEMMA, Sememe.ENG, "facet")
                .slot(ThematicRole.Topic.KEY);
    }

    // ==================================================================================
    // EXTERNAL ID PREDICATES
    // ==================================================================================

    public static class CiliId {
        public static final String KEY = "cg.core:cili-id";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.NOUN)
                .gloss(Sememe.ENG, "a Collaborative Interlingual Index identifier anchoring a concept across languages")
                .word(Sememe.LEMMA, Sememe.ENG, "CILI")
                .slot(ThematicRole.Referent.KEY);
    }
}
