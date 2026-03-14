package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
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
@Type(value = ThematicRole.KEY, glyph = "\uD83C\uDFAD", color = 0xB08DE0)
public class ThematicRole extends Sememe {

    public static final String KEY = "cg:type/role";

    // ==================================================================================
    // SEED INSTANCES — core participant roles
    //
    // Aligned with VerbNet 3.x and ISO 24617-4 (LIRICS/SemAF-SR).
    // No CILIs — thematic roles are frame-theoretic concepts, not WordNet synsets.
    // ==================================================================================

    /** The intentional initiator of an action. [VN: Agent, LIRICS: Agent] */
    public static class Agent {
        public static final String KEY = "cg.role:agent";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant who initiates and carries out an event intentionally")
                .word(LEMMA, ENG, "agent");
    }

    /** The entity undergoing a change of state, location, or condition. [VN: Patient, LIRICS: Patient] */
    public static class Patient {
        public static final String KEY = "cg.role:patient";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that is affected, changed, or consumed by the event")
                .word(LEMMA, ENG, "patient");
    }

    /** A participant being located, moved, or existing in a state; not structurally changed. [VN: Theme, LIRICS: Theme] */
    public static class Theme {
        public static final String KEY = "cg.role:theme";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that is located, moved, or exists in a state without being changed")
                .word(LEMMA, ENG, "theme");
    }

    /** A participant who perceives, feels, or undergoes a cognitive or emotional state. [VN: Experiencer] */
    public static class Experiencer {
        public static final String KEY = "cg.role:experiencer";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant who perceives, feels, or undergoes a mental or emotional state")
                .word(LEMMA, ENG, "experiencer");
    }

    /** A participant that triggers a perception or emotional response in an experiencer. [VN: Stimulus] */
    public static class Stimulus {
        public static final String KEY = "cg.role:stimulus";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that unintentionally arouses a mental or emotional response")
                .word(LEMMA, ENG, "stimulus");
    }

    /** The central participant in a state; in a fixed position or condition throughout. [VN: Pivot, LIRICS: Pivot] */
    public static class Pivot {
        public static final String KEY = "cg.role:pivot";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the central participant in a state, in a fixed position or condition throughout")
                .word(LEMMA, ENG, "pivot");
    }

    /** A non-intentional initiator of an event. [VN: Cause, LIRICS: Cause] */
    public static class Cause {
        public static final String KEY = "cg.role:cause";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that initiates an event without intentionality or consciousness")
                .word(LEMMA, ENG, "cause");
    }

    // ==================================================================================
    // SEED INSTANCES — endpoint and directional roles
    // ==================================================================================

    /** The non-locative, non-temporal end-point of an action. [VN: Goal, LIRICS: Goal] */
    public static class Goal {
        public static final String KEY = "cg.role:goal";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the abstract end-point or target of an action")
                .word(LEMMA, ENG, "goal");
    }

    /** The physical end-point of a motion event. [VN: Destination, LIRICS: Final Location] */
    public static class Destination {
        public static final String KEY = "cg.role:destination";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the physical place where a motion event ends")
                .word(LEMMA, ENG, "destination");
    }

    /** The origin or starting point. [VN: Source, LIRICS: Source] */
    public static class Source {
        public static final String KEY = "cg.role:source";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the origin or starting point of an action or motion")
                .word(LEMMA, ENG, "source");
    }

    /** An intermediate place or trajectory between source and goal. [VN: Trajectory, LIRICS: Path] */
    public static class Path {
        public static final String KEY = "cg.role:path";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the route or trajectory between origin and endpoint")
                .word(LEMMA, ENG, "path");
    }

    /** A participant that comes into existence through the event. [VN: Result/Product, LIRICS: Result] */
    public static class Result {
        public static final String KEY = "cg.role:result";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant that comes into existence through the event")
                .word(LEMMA, ENG, "result");
    }

    // ==================================================================================
    // SEED INSTANCES — transfer and benefaction roles
    // ==================================================================================

    /** The animate entity that receives something transferred. [VN: Recipient, LIRICS: Goal (animate)] */
    public static class Recipient {
        public static final String KEY = "cg.role:recipient";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the animate entity that receives something transferred")
                .word(LEMMA, ENG, "recipient");
    }

    /** A participant who benefits from or is advantaged by the event. [VN: Beneficiary, LIRICS: Beneficiary] */
    public static class Beneficiary {
        public static final String KEY = "cg.role:beneficiary";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant who benefits from or is advantaged by the event")
                .word(LEMMA, ENG, "beneficiary");
    }

    /** A secondary agent, intentionally co-participating in the event. [VN: Co-Agent, LIRICS: Partner] */
    public static class Partner {
        public static final String KEY = "cg.role:partner";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a participant intentionally co-involved in the event but not the principal agent")
                .word(LEMMA, ENG, "partner");
    }

    // ==================================================================================
    // SEED INSTANCES — instrumental, manner, and circumstantial roles
    // ==================================================================================

    /** The tool or means manipulated by an agent. [VN: Instrument, LIRICS: Instrument] */
    public static class Instrument {
        public static final String KEY = "cg.role:instrument";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a tool or means manipulated by an agent to perform an action")
                .word(LEMMA, ENG, "instrument");
    }

    /** The way or style in which an action is performed. [LIRICS: Manner] */
    public static class Manner {
        public static final String KEY = "cg.role:manner";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the way or style in which an action is performed")
                .word(LEMMA, ENG, "manner");
    }

    /** The degree, amount, or measure of change. [VN: Extent, LIRICS: Amount] */
    public static class Extent {
        public static final String KEY = "cg.role:extent";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the degree, amount, or measure of change in an event")
                .word(LEMMA, ENG, "extent");
    }

    /** A property that an event or state associates with a participant. [VN: Attribute, LIRICS: Attribute] */
    public static class Attribute {
        public static final String KEY = "cg.role:attribute";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a property that an event or state associates with a participant")
                .word(LEMMA, ENG, "attribute");
    }

    /** The intended outcome that motivates an intentional action. [LIRICS: Purpose] */
    public static class Purpose {
        public static final String KEY = "cg.role:purpose";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the intended outcome that motivates an intentional action")
                .word(LEMMA, ENG, "purpose");
    }

    // ==================================================================================
    // SEED INSTANCES — setting roles (adjuncts)
    // ==================================================================================

    /** The place where an event occurs or a state holds. [VN: Location, LIRICS: Location] */
    public static class Location {
        public static final String KEY = "cg.role:location";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the place where an event occurs or a state holds")
                .word(LEMMA, ENG, "location");
    }

    /** The time when an event occurs or a state holds. [VN: Time, LIRICS: Time] */
    public static class Time {
        public static final String KEY = "cg.role:time";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the instant or interval during which an event occurs or state holds")
                .word(LEMMA, ENG, "time");
    }

    // ==================================================================================
    // SEED INSTANCES — information and naming roles
    // ==================================================================================

    /** The subject of communication or information transfer. [VN: Topic] */
    public static class Topic {
        public static final String KEY = "cg.role:topic";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the subject of communication, information transfer, or recorded content")
                .word(LEMMA, ENG, "topic");
    }

    /** A name, label, or designation being assigned. [CG extension] */
    public static class Name {
        public static final String KEY = "cg.role:name";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a name, label, or designation being assigned")
                .word(LEMMA, ENG, "name");
    }

    /** The concept being referred to in a metalinguistic frame. [CG extension] */
    public static class Referent {
        public static final String KEY = "cg.role:referent";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the concept being referred to or expressed")
                .word(LEMMA, ENG, "referent");
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
        super(canonicalKey, PartOfSpeech.NOUN);
    }

    /** Seed constructor (no sources). */
    public ThematicRole(String canonicalKey, Map<String, String> glosses, List<String> tokens) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, Map.of(), tokens);
    }

    /** Seed constructor (with sources for CILI). */
    public ThematicRole(String canonicalKey, Map<String, String> glosses,
                        Map<String, String> sources, List<String> tokens) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, sources, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected ThematicRole(Librarian librarian, String canonicalKey,
                   Map<String, String> glosses) {
        super(librarian, canonicalKey, PartOfSpeech.NOUN, glosses, Map.of());
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

    /**
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
            default -> null;
        };
    }
}
