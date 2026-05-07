package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.item.Seed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Thematic roles — semantic functions that participants fill in a frame.
 *
 * <p>Sourced from VerbNet 3.x and ISO 24617-4 (LIRICS/SemAF-SR), plus a few
 * Common-Graph extensions for structural needs ({@link Value}, {@link Config},
 * {@link Follows}).
 *
 * <p>The outer class itself is the "role" meta-sememe: {@code cg.sememe:role}
 * identifies the kind-of-thing-that-is-a-role. It's used as a qualifier on
 * {@code EXPECTS[ROLE] → some-role-iid} bindings, declaring "the target is a
 * role-typed thing." Inner classes are specific roles.
 *
 * <p>Each inner class is a pure-data seed sememe — just identity (KEY + IID).
 * Glosses, lexemes, and other descriptive bindings are deferred to the
 * token-dictionary work, when the supporting predicates (Gloss, Lexeme) and
 * qualifier vocabularies (Languages, PartOfSpeech, GrammaticalFeature) are
 * migrated and we can attach them via {@code @Bind}.
 */
@Seed(key = ThematicRole.KEY)
public final class ThematicRole {

    /** Canonical key for the role meta-sememe — the kind-of-thing-that-is-a-role. */
    public static final String KEY = "cg.sememe:role";
    public static final ItemID IID = ItemID.fromString(KEY);

    private ThematicRole() {}

    /** The intentional initiator of an action. */
    @Seed(key = Agent.KEY)
    public static final class Agent {
        public static final String KEY = "cg.role:agent";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Agent() {}
    }

    /** The entity undergoing a change of state, location, or condition. */
    @Seed(key = Patient.KEY)
    public static final class Patient {
        public static final String KEY = "cg.role:patient";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Patient() {}
    }

    /** The entity affected by an action, or the focal participant of a frame. */
    @Seed(key = Theme.KEY)
    public static final class Theme {
        public static final String KEY = "cg.role:theme";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Theme() {}
    }

    /** The participant having an experience or perception. */
    @Seed(key = Experiencer.KEY)
    public static final class Experiencer {
        public static final String KEY = "cg.role:experiencer";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Experiencer() {}
    }

    /** The phenomenon causing a perception or feeling. */
    @Seed(key = Stimulus.KEY)
    public static final class Stimulus {
        public static final String KEY = "cg.role:stimulus";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Stimulus() {}
    }

    /** The grammatical pivot, often used for binary-relation participants. */
    @Seed(key = Pivot.KEY)
    public static final class Pivot {
        public static final String KEY = "cg.role:pivot";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Pivot() {}
    }

    /** The originating cause of an event. */
    @Seed(key = Cause.KEY)
    public static final class Cause {
        public static final String KEY = "cg.role:cause";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Cause() {}
    }

    /** The intended endpoint or aim of an action. */
    @Seed(key = Goal.KEY)
    public static final class Goal {
        public static final String KEY = "cg.role:goal";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Goal() {}
    }

    /** The terminal location of motion. */
    @Seed(key = Destination.KEY)
    public static final class Destination {
        public static final String KEY = "cg.role:destination";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Destination() {}
    }

    /** The originating location or origin of motion. */
    @Seed(key = Source.KEY)
    public static final class Source {
        public static final String KEY = "cg.role:source";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Source() {}
    }

    /** The route or trajectory of motion. */
    @Seed(key = Path.KEY)
    public static final class Path {
        public static final String KEY = "cg.role:path";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Path() {}
    }

    /** The outcome produced by an action. */
    @Seed(key = Result.KEY)
    public static final class Result {
        public static final String KEY = "cg.role:result";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Result() {}
    }

    /** The participant receiving something in a transfer. */
    @Seed(key = Recipient.KEY)
    public static final class Recipient {
        public static final String KEY = "cg.role:recipient";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Recipient() {}
    }

    /** The participant intended to benefit from an action. */
    @Seed(key = Beneficiary.KEY)
    public static final class Beneficiary {
        public static final String KEY = "cg.role:beneficiary";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Beneficiary() {}
    }

    /** A co-participant in a reciprocal relation. */
    @Seed(key = Partner.KEY)
    public static final class Partner {
        public static final String KEY = "cg.role:partner";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Partner() {}
    }

    /** The means by which an action is performed. */
    @Seed(key = Instrument.KEY)
    public static final class Instrument {
        public static final String KEY = "cg.role:instrument";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Instrument() {}
    }

    /** The manner or way in which an action is performed. */
    @Seed(key = Manner.KEY)
    public static final class Manner {
        public static final String KEY = "cg.role:manner";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Manner() {}
    }

    /** The extent or degree to which something occurs. */
    @Seed(key = Extent.KEY)
    public static final class Extent {
        public static final String KEY = "cg.role:extent";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Extent() {}
    }

    /** A property or characteristic. */
    @Seed(key = Attribute.KEY)
    public static final class Attribute {
        public static final String KEY = "cg.role:attribute";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Attribute() {}
    }

    /** The purpose or motivation. */
    @Seed(key = Purpose.KEY)
    public static final class Purpose {
        public static final String KEY = "cg.role:purpose";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Purpose() {}
    }

    /** The setting or place where an event occurs. */
    @Seed(key = Location.KEY)
    public static final class Location {
        public static final String KEY = "cg.role:location";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Location() {}
    }

    /** The temporal setting of an event. */
    @Seed(key = Time.KEY)
    public static final class Time {
        public static final String KEY = "cg.role:time";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Time() {}
    }

    /** The subject matter of a discourse or expression. */
    @Seed(key = Topic.KEY)
    public static final class Topic {
        public static final String KEY = "cg.role:topic";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Topic() {}
    }

    /** Presentation/styling configuration. */
    @Seed(key = Presentation.KEY)
    public static final class Presentation {
        public static final String KEY = "cg.role:presentation";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Presentation() {}
    }

    /** A referenced item (used in compound bindings to point at related items). */
    @Seed(key = Referent.KEY)
    public static final class Referent {
        public static final String KEY = "cg.role:referent";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Referent() {}
    }

    /** Vocabulary configuration scoping. */
    @Seed(key = Vocabulary.KEY)
    public static final class Vocabulary {
        public static final String KEY = "cg.role:vocabulary";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Vocabulary() {}
    }

    /** A literal-typed value carried by a binding (CG extension; common in seed manifests). */
    @Seed(key = Value.KEY)
    public static final class Value {
        public static final String KEY = "cg.role:value";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Value() {}
    }

    /** Configuration scope qualifier (CG extension; for CONFIG bindings on manifests). */
    @Seed(key = Config.KEY)
    public static final class Config {
        public static final String KEY = "cg.role:config";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Config() {}
    }

    /** Successor reference (CG extension; for FOLLOWS-style ordering). */
    @Seed(key = Follows.KEY)
    public static final class Follows {
        public static final String KEY = "cg.role:follows";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Follows() {}
    }
}
