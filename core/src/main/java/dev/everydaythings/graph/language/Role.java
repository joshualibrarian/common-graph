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
 *   <li>Shakespeare fills the {@link #AGENT} role (the doer)</li>
 *   <li>Hamlet fills the {@link #PATIENT} role (the thing affected)</li>
 *   <li>London fills the {@link #LOCATION} role (where it happened)</li>
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
 * @see ArgumentSlot
 * @see SemanticFrame
 * @see PrepositionSememe
 */
@Type(value = Role.KEY, glyph = "\uD83C\uDFAD", color = 0xB08DE0)
public class Role extends NounSememe {

    public static final String KEY = "cg:type/role";

    // ==================================================================================
    // SEED INSTANCES — core thematic roles
    // ==================================================================================

    /** The doer or initiator of an action. Usually the signer/caller in dispatch. */
    @Seed
    public static final Role AGENT = new Role(
            "cg.role:agent",
            Map.of("en", "the doer or initiator of an action"),
            List.of("agent")
    );

    /** The entity affected, produced, or changed by the action. */
    @Seed
    public static final Role PATIENT = new Role(
            "cg.role:patient",
            Map.of("en", "the entity affected or changed by the action"),
            List.of("patient")
    );

    /** The content, topic, or subject matter — what the action is about. */
    @Seed
    public static final Role THEME = new Role(
            "cg.role:theme",
            Map.of("en", "the content, topic, or subject matter"),
            List.of("theme")
    );

    /** Where the result goes — the destination or target location. */
    @Seed
    public static final Role TARGET = new Role(
            "cg.role:target",
            Map.of("en", "the destination or target of the action"),
            List.of("target")
    );

    /** Where something comes from — the origin or source. */
    @Seed
    public static final Role SOURCE = new Role(
            "cg.role:source",
            Map.of("en", "the origin or source"),
            List.of("source")
    );

    /** The tool, method, or means used to perform the action. */
    @Seed
    public static final Role INSTRUMENT = new Role(
            "cg.role:instrument",
            Map.of("en", "the tool or means used"),
            List.of("instrument")
    );

    /** The place where something is or happens. */
    @Seed
    public static final Role LOCATION = new Role(
            "cg.role:location",
            Map.of("en", "the place where something is or happens"),
            List.of("location")
    );

    /** The time when something happens. */
    @Seed
    public static final Role TIME = new Role(
            "cg.role:time",
            Map.of("en", "the time when something happens"),
            List.of("time")
    );

    /** Who benefits from the action — the recipient or beneficiary. */
    @Seed
    public static final Role RECIPIENT = new Role(
            "cg.role:recipient",
            Map.of("en", "the beneficiary or recipient"),
            List.of("recipient")
    );

    /** The reason or cause of the action. */
    @Seed
    public static final Role CAUSE = new Role(
            "cg.role:cause",
            Map.of("en", "the reason or cause"),
            List.of("cause")
    );

    /** A companion or co-participant in the action. */
    @Seed
    public static final Role COMITATIVE = new Role(
            "cg.role:comitative",
            Map.of("en", "a companion or co-participant"),
            List.of("comitative")
    );

    /** A name, label, or designation being assigned. */
    @Seed
    public static final Role NAME = new Role(
            "cg.role:name",
            Map.of("en", "a name, label, or designation"),
            List.of("name")
    );

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected Role(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected Role(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Seed constructor. */
    public Role(String canonicalKey, Map<String, String> glosses, List<String> tokens) {
        super(canonicalKey, glosses, Map.of(), tokens);
    }

    /** Runtime constructor (with librarian). */
    protected Role(Librarian librarian, String canonicalKey,
                   Map<String, String> glosses) {
        super(librarian, canonicalKey, glosses, Map.of());
    }
}
