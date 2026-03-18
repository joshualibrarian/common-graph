package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * A thematic role sememe — defines the semantic function of a participant in a frame.
 *
 * <p>Thematic roles (theta roles) describe what part a participant plays in
 * an event or relation. For example, in "Shakespeare wrote Hamlet in London":
 * <ul>
 *   <li>Shakespeare fills the {@link Agent#SEED AGENT} role (the doer)</li>
 *   <li>Hamlet fills the {@link Patient#SEED PATIENT} role (the thing affected)</li>
 *   <li>London fills the {@link Location#SEED LOCATION} role (where it happened)</li>
 * </ul>
 *
 * <p>Roles are <b>sememes</b> — language-agnostic concepts referenced by ItemID.
 * Prepositions map to roles (English "by" → AGENT, "in" → LOCATION/TIME),
 * and predicates declare their frame schema as a list of role slots.
 *
 * <p>Not all languages share the same roles. Most languages distinguish
 * agent and patient, but some languages have roles others lack (e.g.,
 * evidentiality-related roles). New roles can be added as seed vocabulary
 * without changing any code.
 *
 * <p>Each role is declared as an inner class with a compile-time constant
 * {@code KEY} string and a {@code @Seed} instance. The KEY fields are
 * safe for use in static initializers (no circular init) because Java
 * inlines {@code static final String} literals at the call site (JLS §12.4.1).
 *
 * @see SemanticFrame
 * @see PrepositionVocabulary
 */
@Implements(ThematicRole.TypeSeed.KEY)
public class ThematicRole extends Sememe {

    public static final String KEY = TypeSeed.KEY;

    @ItemSeed(key = TypeSeed.KEY)
    public static class TypeSeed {
        public static final String KEY = "cg.sememe:role";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "a semantic role that a participant fills in a frame")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "role");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a semantic role that a participant fills in a frame";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "role";
    }

    // ==================================================================================
    // SEED INSTANCES — core participant roles
    //
    // Aligned with VerbNet 3.x and ISO 24617-4 (LIRICS/SemAF-SR).
    // No CILIs — thematic roles are frame-theoretic concepts, not WordNet synsets.
    // ==================================================================================

    /** The intentional initiator of an action. [VN: Agent, LIRICS: Agent] */
    @ItemSeed(key = Agent.KEY)
    public static class Agent {
        public static final String KEY = "cg.role:agent";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant who initiates and carries out an event intentionally")
                .word(LEMMA, ENG, "agent");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant who initiates and carries out an event intentionally";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "agent";
    }

    /** The entity undergoing a change of state, location, or condition. [VN: Patient, LIRICS: Patient] */
    @ItemSeed(key = Patient.KEY)
    public static class Patient {
        public static final String KEY = "cg.role:patient";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that is affected, changed, or consumed by the event")
                .word(LEMMA, ENG, "patient");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant that is affected, changed, or consumed by the event";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "patient";
    }

    /** A participant being located, moved, or existing in a state; not structurally changed. [VN: Theme, LIRICS: Theme] */
    @ItemSeed(key = Theme.KEY)
    public static class Theme {
        public static final String KEY = "cg.role:theme";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that is located, moved, or exists in a state without being changed")
                .word(LEMMA, ENG, "theme");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant that is located, moved, or exists in a state without being changed";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "theme";
    }

    /** A participant who perceives, feels, or undergoes a cognitive or emotional state. [VN: Experiencer] */
    @ItemSeed(key = Experiencer.KEY)
    public static class Experiencer {
        public static final String KEY = "cg.role:experiencer";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant who perceives, feels, or undergoes a mental or emotional state")
                .word(LEMMA, ENG, "experiencer");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant who perceives, feels, or undergoes a mental or emotional state";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "experiencer";
    }

    /** A participant that triggers a perception or emotional response in an experiencer. [VN: Stimulus] */
    @ItemSeed(key = Stimulus.KEY)
    public static class Stimulus {
        public static final String KEY = "cg.role:stimulus";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that unintentionally arouses a mental or emotional response")
                .word(LEMMA, ENG, "stimulus");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant that unintentionally arouses a mental or emotional response";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "stimulus";
    }

    /** The central participant in a state; in a fixed position or condition throughout. [VN: Pivot, LIRICS: Pivot] */
    @ItemSeed(key = Pivot.KEY)
    public static class Pivot {
        public static final String KEY = "cg.role:pivot";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the central participant in a state, in a fixed position or condition throughout")
                .word(LEMMA, ENG, "pivot");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the central participant in a state, in a fixed position or condition throughout";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "pivot";
    }

    /** A non-intentional initiator of an event. [VN: Cause, LIRICS: Cause] */
    @ItemSeed(key = Cause.KEY)
    public static class Cause {
        public static final String KEY = "cg.role:cause";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that initiates an event without intentionality or consciousness")
                .word(LEMMA, ENG, "cause");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant that initiates an event without intentionality or consciousness";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "cause";
    }

    // ==================================================================================
    // SEED INSTANCES — endpoint and directional roles
    // ==================================================================================

    /** The non-locative, non-temporal end-point of an action. [VN: Goal, LIRICS: Goal] */
    @ItemSeed(key = Goal.KEY)
    public static class Goal {
        public static final String KEY = "cg.role:goal";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the abstract end-point or target of an action")
                .word(LEMMA, ENG, "goal");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the abstract end-point or target of an action";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "goal";
    }

    /** The physical end-point of a motion event. [VN: Destination, LIRICS: Final Location] */
    @ItemSeed(key = Destination.KEY)
    public static class Destination {
        public static final String KEY = "cg.role:destination";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the physical place where a motion event ends")
                .word(LEMMA, ENG, "destination");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the physical place where a motion event ends";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "destination";
    }

    /** The origin or starting point. [VN: Source, LIRICS: Source] */
    @ItemSeed(key = Source.KEY)
    public static class Source {
        public static final String KEY = "cg.role:source";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the origin or starting point of an action or motion")
                .word(LEMMA, ENG, "source");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the origin or starting point of an action or motion";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "source";
    }

    /** An intermediate place or trajectory between source and goal. [VN: Trajectory, LIRICS: Path] */
    @ItemSeed(key = Path.KEY)
    public static class Path {
        public static final String KEY = "cg.role:path";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the route or trajectory between origin and endpoint")
                .word(LEMMA, ENG, "path");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the route or trajectory between origin and endpoint";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "path";
    }

    /** A participant that comes into existence through the event. [VN: Result/Product, LIRICS: Result] */
    @ItemSeed(key = Result.KEY)
    public static class Result {
        public static final String KEY = "cg.role:result";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that comes into existence through the event")
                .word(LEMMA, ENG, "result");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant that comes into existence through the event";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "result";
    }

    // ==================================================================================
    // SEED INSTANCES — transfer and benefaction roles
    // ==================================================================================

    /** The animate entity that receives something transferred. [VN: Recipient, LIRICS: Goal (animate)] */
    @ItemSeed(key = Recipient.KEY)
    public static class Recipient {
        public static final String KEY = "cg.role:recipient";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the animate entity that receives something transferred")
                .word(LEMMA, ENG, "recipient");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the animate entity that receives something transferred";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "recipient";
    }

    /** A participant who benefits from or is advantaged by the event. [VN: Beneficiary, LIRICS: Beneficiary] */
    @ItemSeed(key = Beneficiary.KEY)
    public static class Beneficiary {
        public static final String KEY = "cg.role:beneficiary";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant who benefits from or is advantaged by the event")
                .word(LEMMA, ENG, "beneficiary");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant who benefits from or is advantaged by the event";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "beneficiary";
    }

    /** A secondary agent, intentionally co-participating in the event. [VN: Co-Agent, LIRICS: Partner] */
    @ItemSeed(key = Partner.KEY)
    public static class Partner {
        public static final String KEY = "cg.role:partner";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant intentionally co-involved in the event but not the principal agent")
                .word(LEMMA, ENG, "partner");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant intentionally co-involved in the event but not the principal agent";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "partner";
    }

    // ==================================================================================
    // SEED INSTANCES — instrument, manner, and circumstantial roles
    // ==================================================================================

    /** The tool or means manipulated by an agent. [VN: Instrument, LIRICS: Instrument] */
    @ItemSeed(key = Instrument.KEY)
    public static class Instrument {
        public static final String KEY = "cg.role:instrument";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a tool or means manipulated by an agent to perform an action")
                .word(LEMMA, ENG, "instrument");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a tool or means manipulated by an agent to perform an action";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "instrument";
    }

    /** The way or style in which an action is performed. [LIRICS: Manner] */
    @ItemSeed(key = Manner.KEY)
    public static class Manner {
        public static final String KEY = "cg.role:manner";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the way or style in which an action is performed")
                .word(LEMMA, ENG, "manner");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the way or style in which an action is performed";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "manner";
    }

    /** The degree, amount, or measure of change. [VN: Extent, LIRICS: Amount] */
    @ItemSeed(key = Extent.KEY)
    public static class Extent {
        public static final String KEY = "cg.role:extent";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the degree, amount, or measure of change in an event")
                .word(LEMMA, ENG, "extent");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the degree, amount, or measure of change in an event";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "extent";
    }

    /** A property that an event or state associates with a participant. [VN: Attribute, LIRICS: Attribute] */
    @ItemSeed(key = Attribute.KEY)
    public static class Attribute {
        public static final String KEY = "cg.role:attribute";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a property that an event or state associates with a participant")
                .word(LEMMA, ENG, "attribute");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a property that an event or state associates with a participant";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "attribute";
    }

    /** The intended outcome that motivates an intentional action. [LIRICS: Purpose] */
    @ItemSeed(key = Purpose.KEY)
    public static class Purpose {
        public static final String KEY = "cg.role:purpose";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the intended outcome that motivates an intentional action")
                .word(LEMMA, ENG, "purpose");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the intended outcome that motivates an intentional action";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "purpose";
    }

    // ==================================================================================
    // SEED INSTANCES — setting roles (adjuncts)
    // ==================================================================================

    /** The place where an event occurs or a state holds. [VN: Location, LIRICS: Location] */
    @ItemSeed(key = Location.KEY)
    public static class Location {
        public static final String KEY = "cg.role:location";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the place where an event occurs or a state holds")
                .word(LEMMA, ENG, "location");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the place where an event occurs or a state holds";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "location";
    }

    /** The time when an event occurs or a state holds. [VN: Time, LIRICS: Time] */
    @ItemSeed(key = Time.KEY)
    public static class Time {
        public static final String KEY = "cg.role:time";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the instant or interval during which an event occurs or state holds")
                .word(LEMMA, ENG, "time");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the instant or interval during which an event occurs or state holds";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "time";
    }

    // ==================================================================================
    // SEED INSTANCES — information and naming roles
    // ==================================================================================

    /** The subject of communication or information transfer. [VN: Topic] */
    @ItemSeed(key = Topic.KEY)
    public static class Topic {
        public static final String KEY = "cg.role:topic";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the subject of communication, information transfer, or recorded content")
                .word(LEMMA, ENG, "topic");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the subject of communication, information transfer, or recorded content";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "topic";
    }

    /** A name, label, or designation being assigned. [CG extension] */
    @ItemSeed(key = Name.KEY)
    public static class Name {
        public static final String KEY = "cg.role:name";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a name, label, or designation being assigned")
                .word(LEMMA, ENG, "name");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a name, label, or designation being assigned";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "name";
    }

    /** The concept being referred to in a metalinguistic frame. [CG extension] */
    @ItemSeed(key = Referent.KEY)
    public static class Referent {
        public static final String KEY = "cg.role:referent";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the concept being referred to or expressed")
                .word(LEMMA, ENG, "referent");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the concept being referred to or expressed";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "referent";
    }

    // ==================================================================================
    // SEED INSTANCES — frame-structural role (CG extension)
    // ==================================================================================

    /** The configuration governing a frame's behavior (policy, scene, settings). [CG extension] */
    @ItemSeed(key = Config.KEY)
    public static class Config {
        public static final String KEY = "cg.role:config";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "configuration governing a frame's behavior — policy, scene, settings")
                .word(LEMMA, ENG, "config");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "configuration governing a frame's behavior — policy, scene, settings";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "config";
    }

    /** Rendering overrides — scene, skin, style. Narrows CONFIG. [CG extension] */
    @ItemSeed(key = Presentation.KEY)
    public static class Presentation {
        public static final String KEY = "cg.role:presentation";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "rendering configuration — scene, skin, style overrides")
                .word(LEMMA, ENG, "presentation");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "rendering configuration — scene, skin, style overrides";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "presentation";
    }

    /** Token declarations — aliases, proper nouns, verb contributions. Narrows CONFIG. [CG extension] */
    @ItemSeed(key = Vocabulary.KEY)
    public static class Vocabulary {
        public static final String KEY = "cg.role:vocabulary";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "vocabulary configuration — token declarations, aliases, proper nouns")
                .word(LEMMA, ENG, "vocabulary");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "vocabulary configuration — token declarations, aliases, proper nouns";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "vocabulary";
    }

    // ==================================================================================
    // SEED INSTANCES — causal ordering role (CG extension)
    // ==================================================================================

    /** Causal ordering — this frame follows/is-caused-by an earlier frame. [CG extension] */
    @ItemSeed(key = Follows.KEY)
    public static class Follows {
        public static final String KEY = "cg.role:follows";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "causal predecessor — this frame follows/is-caused-by the target frame")
                .word(LEMMA, ENG, "follows");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "causal predecessor — this frame follows/is-caused-by the target frame";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY,
                pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "follows";
    }

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected ThematicRole(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected ThematicRole(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Fluent seed constructor. */
    public ThematicRole(String canonicalKey) {
        super(canonicalKey);
    }

    /** Seed constructor (no sources). */
    public ThematicRole(String canonicalKey, Map<String, String> glosses, List<String> tokens) {
        super(canonicalKey, glosses, Map.of(), tokens);
    }

    /** Seed constructor (with sources for CILI). */
    public ThematicRole(String canonicalKey, Map<String, String> glosses,
                        Map<String, String> sources, List<String> tokens) {
        super(canonicalKey, glosses, sources, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected ThematicRole(Librarian librarian, String canonicalKey,
                   Map<String, String> glosses) {
        super(librarian, canonicalKey, glosses, Map.of());
    }

    // ==================================================================================
    // COVARIANT OVERRIDES (fluent chaining returns ThematicRole)
    // ==================================================================================

    @Override public ThematicRole gloss(String lang, String text) { super.gloss(lang, text); return this; }
    @Override public ThematicRole word(Sememe form, String lang, String surface) { super.word(form, lang, surface); return this; }
    @Override public ThematicRole cili(String id) { super.cili(id); return this; }
    @Override public ThematicRole symbol(String s) { super.symbol(s); return this; }
    @Override public ThematicRole indexWeight(int weight) { super.indexWeight(weight); return this; }

    // ==================================================================================
    // LOOKUP
    // ==================================================================================

    /** TODO: we need to talk about this switch... why do we need it?  Those tokens should be assigned to those sememes via the token dictionary and normal lookup path... not with this special switch?
     * Look up a ThematicRole by its constant name (e.g., "THEME", "AGENT").
     *
     * <p>Used by {@code @Param(role="THEME")} annotation processing to
     * resolve string role names to ThematicRole seed instances.
     *
     * @param name The uppercase constant name
     * @return The ThematicRole seed, or null if not found
     */
    public static ThematicRole fromName(String name) {
        return switch (name) {
            case "AGENT" -> Agent.SEED;
            case "PATIENT" -> Patient.SEED;
            case "THEME" -> Theme.SEED;
            case "EXPERIENCER" -> Experiencer.SEED;
            case "STIMULUS" -> Stimulus.SEED;
            case "PIVOT" -> Pivot.SEED;
            case "CAUSE" -> Cause.SEED;
            case "GOAL" -> Goal.SEED;
            case "DESTINATION" -> Destination.SEED;
            case "SOURCE" -> Source.SEED;
            case "PATH" -> Path.SEED;
            case "RESULT" -> Result.SEED;
            case "RECIPIENT" -> Recipient.SEED;
            case "BENEFICIARY" -> Beneficiary.SEED;
            case "PARTNER" -> Partner.SEED;
            case "INSTRUMENT" -> Instrument.SEED;
            case "MANNER" -> Manner.SEED;
            case "EXTENT" -> Extent.SEED;
            case "ATTRIBUTE" -> Attribute.SEED;
            case "PURPOSE" -> Purpose.SEED;
            case "LOCATION" -> Location.SEED;
            case "TIME" -> Time.SEED;
            case "TOPIC" -> Topic.SEED;
            case "NAME" -> Name.SEED;
            case "REFERENT" -> Referent.SEED;
            case "CONFIG" -> Config.SEED;
            case "PRESENTATION" -> Presentation.SEED;
            case "VOCABULARY" -> Vocabulary.SEED;
            case "FOLLOWS" -> Follows.SEED;
            default -> null;
        };
    }
}
