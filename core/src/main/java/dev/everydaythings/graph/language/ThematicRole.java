package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;
import dev.everydaythings.graph.language.CoreVocabulary;

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
@Implements(ThematicRole.KEY)
@ItemSeed(key = ThematicRole.KEY)
public class ThematicRole extends Sememe {

    public static final String KEY = "cg.sememe:role";

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
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant who initiates and carries out an event intentionally";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"agent"};
    }

    /** The entity undergoing a change of state, location, or condition. [VN: Patient, LIRICS: Patient] */
    @ItemSeed(key = Patient.KEY)
    public static class Patient {
        public static final String KEY = "cg.role:patient";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant that is affected, changed, or consumed by the event";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"patient"};
    }

    /** A participant being located, moved, or existing in a state; not structurally changed. [VN: Theme, LIRICS: Theme] */
    @ItemSeed(key = Theme.KEY)
    public static class Theme {
        public static final String KEY = "cg.role:theme";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant that is located, moved, or exists in a state without being changed";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"theme"};
    }

    /** A participant who perceives, feels, or undergoes a cognitive or emotional state. [VN: Experiencer] */
    @ItemSeed(key = Experiencer.KEY)
    public static class Experiencer {
        public static final String KEY = "cg.role:experiencer";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant who perceives, feels, or undergoes a mental or emotional state";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"experiencer"};
    }

    /** A participant that triggers a perception or emotional response in an experiencer. [VN: Stimulus] */
    @ItemSeed(key = Stimulus.KEY)
    public static class Stimulus {
        public static final String KEY = "cg.role:stimulus";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant that unintentionally arouses a mental or emotional response";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"stimulus"};
    }

    /** The central participant in a state; in a fixed position or condition throughout. [VN: Pivot, LIRICS: Pivot] */
    @ItemSeed(key = Pivot.KEY)
    public static class Pivot {
        public static final String KEY = "cg.role:pivot";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the central participant in a state, in a fixed position or condition throughout";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"pivot"};
    }

    /** A non-intentional initiator of an event. [VN: Cause, LIRICS: Cause] */
    @ItemSeed(key = Cause.KEY)
    public static class Cause {
        public static final String KEY = "cg.role:cause";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant that initiates an event without intentionality or consciousness";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"cause"};
    }

    // ==================================================================================
    // SEED INSTANCES — endpoint and directional roles
    // ==================================================================================

    /** The non-locative, non-temporal end-point of an action. [VN: Goal, LIRICS: Goal] */
    @ItemSeed(key = Goal.KEY)
    public static class Goal {
        public static final String KEY = "cg.role:goal";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the abstract end-point or target of an action";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"goal"};
    }

    /** The physical end-point of a motion event. [VN: Destination, LIRICS: Final Location] */
    @ItemSeed(key = Destination.KEY)
    public static class Destination {
        public static final String KEY = "cg.role:destination";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the physical place where a motion event ends";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"destination"};
    }

    /** The origin or starting point. [VN: Source, LIRICS: Source] */
    @ItemSeed(key = Source.KEY)
    public static class Source {
        public static final String KEY = "cg.role:source";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the origin or starting point of an action or motion";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"source"};
    }

    /** An intermediate place or trajectory between source and goal. [VN: Trajectory, LIRICS: Path] */
    @ItemSeed(key = Path.KEY)
    public static class Path {
        public static final String KEY = "cg.role:path";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the route or trajectory between origin and endpoint";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"path"};
    }

    /** A participant that comes into existence through the event. [VN: Result/Product, LIRICS: Result] */
    @ItemSeed(key = Result.KEY)
    public static class Result {
        public static final String KEY = "cg.role:result";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant that comes into existence through the event";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"result"};
    }

    // ==================================================================================
    // SEED INSTANCES — transfer and benefaction roles
    // ==================================================================================

    /** The animate entity that receives something transferred. [VN: Recipient, LIRICS: Goal (animate)] */
    @ItemSeed(key = Recipient.KEY)
    public static class Recipient {
        public static final String KEY = "cg.role:recipient";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the animate entity that receives something transferred";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"recipient"};
    }

    /** A participant who benefits from or is advantaged by the event. [VN: Beneficiary, LIRICS: Beneficiary] */
    @ItemSeed(key = Beneficiary.KEY)
    public static class Beneficiary {
        public static final String KEY = "cg.role:beneficiary";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant who benefits from or is advantaged by the event";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"beneficiary"};
    }

    /** A secondary agent, intentionally co-participating in the event. [VN: Co-Agent, LIRICS: Partner] */
    @ItemSeed(key = Partner.KEY)
    public static class Partner {
        public static final String KEY = "cg.role:partner";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant intentionally co-involved in the event but not the principal agent";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"partner"};
    }

    // ==================================================================================
    // SEED INSTANCES — instrument, manner, and circumstantial roles
    // ==================================================================================

    /** The tool or means manipulated by an agent. [VN: Instrument, LIRICS: Instrument] */
    @ItemSeed(key = Instrument.KEY)
    public static class Instrument {
        public static final String KEY = "cg.role:instrument";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a tool or means manipulated by an agent to perform an action";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"instrument"};
    }

    /** The way or style in which an action is performed. [LIRICS: Manner] */
    @ItemSeed(key = Manner.KEY)
    public static class Manner {
        public static final String KEY = "cg.role:manner";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the way or style in which an action is performed";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"manner"};
    }

    /** The degree, amount, or measure of change. [VN: Extent, LIRICS: Amount] */
    @ItemSeed(key = Extent.KEY)
    public static class Extent {
        public static final String KEY = "cg.role:extent";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the degree, amount, or measure of change in an event";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"extent"};
    }

    /** A property that an event or state associates with a participant. [VN: Attribute, LIRICS: Attribute] */
    @ItemSeed(key = Attribute.KEY)
    public static class Attribute {
        public static final String KEY = "cg.role:attribute";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a property that an event or state associates with a participant";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"attribute"};
    }

    /** The intended outcome that motivates an intentional action. [LIRICS: Purpose] */
    @ItemSeed(key = Purpose.KEY)
    public static class Purpose {
        public static final String KEY = "cg.role:purpose";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the intended outcome that motivates an intentional action";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"purpose"};
    }

    // ==================================================================================
    // SEED INSTANCES — setting roles (adjuncts)
    // ==================================================================================

    /** The place where an event occurs or a state holds. [VN: Location, LIRICS: Location] */
    @ItemSeed(key = Location.KEY)
    public static class Location {
        public static final String KEY = "cg.role:location";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the place where an event occurs or a state holds";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"location"};
    }

    /** The time when an event occurs or a state holds. [VN: Time, LIRICS: Time] */
    @ItemSeed(key = Time.KEY)
    public static class Time {
        public static final String KEY = "cg.role:time";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the instant or interval during which an event occurs or state holds";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"time"};
    }

    // ==================================================================================
    // SEED INSTANCES — information and naming roles
    // ==================================================================================

    /** The subject of communication or information transfer. [VN: Topic] */
    @ItemSeed(key = Topic.KEY)
    public static class Topic {
        public static final String KEY = "cg.role:topic";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the subject of communication, information transfer, or recorded content";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"topic"};
    }

    /** A name, label, or designation being assigned. [CG extension] */
    @ItemSeed(key = Name.KEY)
    public static class Name {
        public static final String KEY = "cg.role:name";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a name, label, or designation being assigned";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"name"};
    }

    /** The concept being referred to in a metalinguistic frame. [CG extension] */
    @ItemSeed(key = Referent.KEY)
    public static class Referent {
        public static final String KEY = "cg.role:referent";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the concept being referred to or expressed";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"referent"};
    }

    // ==================================================================================
    // SEED INSTANCES — frame-structural role (CG extension)
    // ==================================================================================

    /** The configuration governing a frame's behavior (policy, scene, settings). [CG extension] */
    @ItemSeed(key = Config.KEY)
    public static class Config {
        public static final String KEY = "cg.role:config";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "configuration governing a frame's behavior — policy, scene, settings";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"config"};
    }

    /** Rendering overrides — scene, skin, style. Narrows CONFIG. [CG extension] */
    @ItemSeed(key = Presentation.KEY)
    public static class Presentation {
        public static final String KEY = "cg.role:presentation";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "rendering configuration — scene, skin, style overrides";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"presentation"};
    }

    /** Token declarations — aliases, proper nouns, verb contributions. Narrows CONFIG. [CG extension] */
    @ItemSeed(key = Vocabulary.KEY)
    public static class Vocabulary {
        public static final String KEY = "cg.role:vocabulary";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "vocabulary configuration — token declarations, aliases, proper nouns";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"vocabulary"};
    }

    // ==================================================================================
    // SEED INSTANCES — causal ordering role (CG extension)
    // ==================================================================================

    /** Causal ordering — this frame follows/is-caused-by an earlier frame. [CG extension] */
    @ItemSeed(key = Follows.KEY)
    public static class Follows {
        public static final String KEY = "cg.role:follows";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "causal predecessor — this frame follows/is-caused-by the target frame";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"follows"};
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
     * @return The ThematicRole IID, or null if not found
     */
    public static ItemID fromName(String name) {
        return switch (name) {
            case "AGENT" -> Agent.IID;
            case "PATIENT" -> Patient.IID;
            case "THEME" -> Theme.IID;
            case "EXPERIENCER" -> Experiencer.IID;
            case "STIMULUS" -> Stimulus.IID;
            case "PIVOT" -> Pivot.IID;
            case "CAUSE" -> Cause.IID;
            case "GOAL" -> Goal.IID;
            case "DESTINATION" -> Destination.IID;
            case "SOURCE" -> Source.IID;
            case "PATH" -> Path.IID;
            case "RESULT" -> Result.IID;
            case "RECIPIENT" -> Recipient.IID;
            case "BENEFICIARY" -> Beneficiary.IID;
            case "PARTNER" -> Partner.IID;
            case "INSTRUMENT" -> Instrument.IID;
            case "MANNER" -> Manner.IID;
            case "EXTENT" -> Extent.IID;
            case "ATTRIBUTE" -> Attribute.IID;
            case "PURPOSE" -> Purpose.IID;
            case "LOCATION" -> Location.IID;
            case "TIME" -> Time.IID;
            case "TOPIC" -> Topic.IID;
            case "NAME" -> Name.IID;
            case "REFERENT" -> Referent.IID;
            case "CONFIG" -> Config.IID;
            case "PRESENTATION" -> Presentation.IID;
            case "VOCABULARY" -> Vocabulary.IID;
            case "FOLLOWS" -> Follows.IID;
            default -> null;
        };
    }
}
