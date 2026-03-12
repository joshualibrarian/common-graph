package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
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
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the doer or initiator of an action"),
                List.of("agent"));
    }

    /** The entity affected, produced, or changed by the action. */
    public static class Patient {
        public static final String KEY = "cg.role:patient";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the entity affected or changed by the action"),
                List.of("patient"));
    }

    /** The content, topic, or subject matter — what the action is about. */
    public static class Theme {
        public static final String KEY = "cg.role:theme";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the content, topic, or subject matter"),
                List.of("theme"));
    }

    /** Where the result goes — the destination or target location. */
    public static class Target {
        public static final String KEY = "cg.role:target";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the destination or target of the action"),
                List.of("target"));
    }

    /** Where something comes from — the origin or source. */
    public static class Source {
        public static final String KEY = "cg.role:source";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the origin or source"),
                List.of("source"));
    }

    /** The tool, method, or means used to perform the action. */
    public static class Instrument {
        public static final String KEY = "cg.role:instrument";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the tool or means used"),
                List.of("instrument"));
    }

    /** The place where something is or happens. */
    public static class Location {
        public static final String KEY = "cg.role:location";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the place where something is or happens"),
                List.of("location"));
    }

    /** The time when something happens. */
    public static class Time {
        public static final String KEY = "cg.role:time";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the time when something happens"),
                List.of("time"));
    }

    /** Who benefits from the action — the recipient or beneficiary. */
    public static class Recipient {
        public static final String KEY = "cg.role:recipient";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the beneficiary or recipient"),
                List.of("recipient"));
    }

    /** The reason or cause of the action. */
    public static class Cause {
        public static final String KEY = "cg.role:cause";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the reason or cause"),
                List.of("cause"));
    }

    /** A companion or co-participant in the action. */
    public static class Comitative {
        public static final String KEY = "cg.role:comitative";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "a companion or co-participant"),
                List.of("comitative"));
    }

    /** A name, label, or designation being assigned. */
    public static class Name {
        public static final String KEY = "cg.role:name";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "a name, label, or designation"),
                List.of("name"));
    }

    /** The concept being referred to in a metalinguistic frame. */
    public static class Referent {
        public static final String KEY = "cg.role:referent";
        @Seed
        public static final ThematicRole SEED = new ThematicRole(KEY,
                Map.of("en", "the concept being referred to or expressed"),
                List.of("referent"));
    }

    // ==================================================================================
    // DEPRECATED ALIASES — backward compatibility
    // ==================================================================================

    /** @deprecated Use {@link Agent#SEED} */
    @Deprecated public static final ThematicRole AGENT = Agent.SEED;
    /** @deprecated Use {@link Patient#SEED} */
    @Deprecated public static final ThematicRole PATIENT = Patient.SEED;
    /** @deprecated Use {@link Theme#SEED} */
    @Deprecated public static final ThematicRole THEME = Theme.SEED;
    /** @deprecated Use {@link Target#SEED} */
    @Deprecated public static final ThematicRole TARGET = Target.SEED;
    /** @deprecated Use {@link Source#SEED} */
    @Deprecated public static final ThematicRole SOURCE = Source.SEED;
    /** @deprecated Use {@link Instrument#SEED} */
    @Deprecated public static final ThematicRole INSTRUMENT = Instrument.SEED;
    /** @deprecated Use {@link Location#SEED} */
    @Deprecated public static final ThematicRole LOCATION = Location.SEED;
    /** @deprecated Use {@link Time#SEED} */
    @Deprecated public static final ThematicRole TIME = Time.SEED;
    /** @deprecated Use {@link Recipient#SEED} */
    @Deprecated public static final ThematicRole RECIPIENT = Recipient.SEED;
    /** @deprecated Use {@link Cause#SEED} */
    @Deprecated public static final ThematicRole CAUSE = Cause.SEED;
    /** @deprecated Use {@link Comitative#SEED} */
    @Deprecated public static final ThematicRole COMITATIVE = Comitative.SEED;
    /** @deprecated Use {@link Name#SEED} */
    @Deprecated public static final ThematicRole NAME = Name.SEED;
    /** @deprecated Use {@link Referent#SEED} */
    @Deprecated public static final ThematicRole REFERENT = Referent.SEED;

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

    /** Seed constructor. */
    public ThematicRole(String canonicalKey, Map<String, String> glosses, List<String> tokens) {
        super(canonicalKey, glosses, Map.of(), tokens);
    }

    /** Runtime constructor (with librarian). */
    protected ThematicRole(Librarian librarian, String canonicalKey,
                   Map<String, String> glosses) {
        super(librarian, canonicalKey, glosses, Map.of());
    }

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
