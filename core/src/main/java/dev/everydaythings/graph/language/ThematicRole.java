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
 * @see PrepositionSememe
 */
@Type(value = ThematicRole.KEY, glyph = "\uD83C\uDFAD", color = 0xB08DE0)
public class ThematicRole extends NounSememe {

    public static final String KEY = "cg:type/role";

    // ==================================================================================
    // SEED INSTANCES — core thematic roles (inner class pattern)
    // ==================================================================================

    /** The doer or initiator of an action. Usually the signer/caller in dispatch. */
    public static class Agent {
        public static final String KEY = "cg.role:agent";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the doer or initiator of an action")
                .cili("i84938").word(LEMMA, ENG, "agent");
    }

    /** The entity affected, produced, or changed by the action. */
    public static class Patient {
        public static final String KEY = "cg.role:patient";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the entity affected or changed by the action")
                .word(LEMMA, ENG, "patient");
    }

    /** The content, topic, or subject matter — what the action is about. */
    public static class Theme {
        public static final String KEY = "cg.role:theme";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the content, topic, or subject matter")
                .cili("i71142").word(LEMMA, ENG, "theme");
    }

    /** Where the result goes — the destination or target location. */
    public static class Target {
        public static final String KEY = "cg.role:target";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the destination or target of the action")
                .cili("i68253").word(LEMMA, ENG, "target");
    }

    /** Where something comes from — the origin or source. */
    public static class Source {
        public static final String KEY = "cg.role:source";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the origin or source")
                .cili("i81759").word(LEMMA, ENG, "source");
    }

    /** The tool, method, or means used to perform the action. */
    public static class Instrument {
        public static final String KEY = "cg.role:instrument";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the tool or means used")
                .cili("i55129").word(LEMMA, ENG, "instrument");
    }

    /** The place where something is or happens. */
    public static class Location {
        public static final String KEY = "cg.role:location";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the place where something is or happens")
                .cili("i35580").word(LEMMA, ENG, "location");
    }

    /** The time when something happens. */
    public static class Time {
        public static final String KEY = "cg.role:time";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the time when something happens")
                .cili("i117493").word(LEMMA, ENG, "time");
    }

    /** Who benefits from the action — the recipient or beneficiary. */
    public static class Recipient {
        public static final String KEY = "cg.role:recipient";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the beneficiary or recipient")
                .cili("i87243").word(LEMMA, ENG, "recipient");
    }

    /** The reason or cause of the action. */
    public static class Cause {
        public static final String KEY = "cg.role:cause";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the reason or cause")
                .cili("i75195").word(LEMMA, ENG, "cause");
    }

    /** A companion or co-participant in the action. */
    public static class Comitative {
        public static final String KEY = "cg.role:comitative";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a companion or co-participant")
                .word(LEMMA, ENG, "comitative");
    }

    /** A name, label, or designation being assigned. */
    public static class Name {
        public static final String KEY = "cg.role:name";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "a name, label, or designation")
                .cili("i69761").word(LEMMA, ENG, "name");
    }

    /** The concept being referred to in a metalinguistic frame. */
    public static class Referent {
        public static final String KEY = "cg.role:referent";
        @Seed public static final ThematicRole SEED = new ThematicRole(KEY)
                .gloss(ENG, "the concept being referred to or expressed")
                .cili("i71160").word(LEMMA, ENG, "referent");
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
            case "TARGET" -> Target.SEED;
            case "SOURCE" -> Source.SEED;
            case "INSTRUMENT" -> Instrument.SEED;
            case "LOCATION" -> Location.SEED;
            case "TIME" -> Time.SEED;
            case "RECIPIENT" -> Recipient.SEED;
            case "CAUSE" -> Cause.SEED;
            case "COMITATIVE" -> Comitative.SEED;
            case "NAME" -> Name.SEED;
            case "REFERENT" -> Referent.SEED;
            default -> null;
        };
    }
}
