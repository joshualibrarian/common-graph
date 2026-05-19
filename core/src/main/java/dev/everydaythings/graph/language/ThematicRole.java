package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;

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
@Seed.Item(key = ThematicRole.KEY)
public final class ThematicRole {

    /** Canonical key for the role meta-sememe — the kind-of-thing-that-is-a-role. */
    public static final String KEY = "cg.sememe:role";

    private ThematicRole() {}

    /** The intentional initiator of an action. */
    @Seed.Item(key = Agent.KEY, head = ThematicRole.KEY)
    public static final class Agent {
        public static final String KEY = "cg.role:agent";
        private Agent() {}
    }

    /** The entity undergoing a change of state, location, or condition. */
    @Seed.Item(key = Patient.KEY, head = ThematicRole.KEY)
    public static final class Patient {
        public static final String KEY = "cg.role:patient";
        private Patient() {}
    }

    /** The entity affected by an action, or the focal participant of a frame. */
    @Seed.Item(key = Theme.KEY, head = ThematicRole.KEY)
    public static final class Theme {
        public static final String KEY = "cg.role:theme";
        private Theme() {}
    }

    /** The participant having an experience or perception. */
    @Seed.Item(key = Experiencer.KEY, head = ThematicRole.KEY)
    public static final class Experiencer {
        public static final String KEY = "cg.role:experiencer";
        private Experiencer() {}
    }

    /** The phenomenon causing a perception or feeling. */
    @Seed.Item(key = Stimulus.KEY, head = ThematicRole.KEY)
    public static final class Stimulus {
        public static final String KEY = "cg.role:stimulus";
        private Stimulus() {}
    }

    /** The grammatical pivot, often used for binary-relation participants. */
    @Seed.Item(key = Pivot.KEY, head = ThematicRole.KEY)
    public static final class Pivot {
        public static final String KEY = "cg.role:pivot";
        private Pivot() {}
    }

    /** The originating cause of an event. */
    @Seed.Item(key = Cause.KEY, head = ThematicRole.KEY)
    public static final class Cause {
        public static final String KEY = "cg.role:cause";
        private Cause() {}
    }

    /** The intended endpoint or aim of an action. */
    @Seed.Item(key = Goal.KEY, head = ThematicRole.KEY)
    public static final class Goal {
        public static final String KEY = "cg.role:goal";
        private Goal() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = Value.KEY,
                      qualifiers = {Language.English.KEY,
                                    PartOfSpeech.Preposition.KEY,
                                    GrammaticalFeature.Lemma.KEY}))
        static final String englishPreposition = "to";
    }

    /** The terminal location of motion. */
    @Seed.Item(key = Destination.KEY, head = ThematicRole.KEY)
    public static final class Destination {
        public static final String KEY = "cg.role:destination";
        private Destination() {}
    }

    /** The originating location or origin of motion. */
    @Seed.Item(key = Source.KEY, head = ThematicRole.KEY)
    public static final class Source {
        public static final String KEY = "cg.role:source";
        private Source() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = Value.KEY,
                      qualifiers = {Language.English.KEY,
                                    PartOfSpeech.Preposition.KEY,
                                    GrammaticalFeature.Lemma.KEY}))
        static final String englishPreposition = "from";
    }

    /** The route or trajectory of motion. */
    @Seed.Item(key = Path.KEY, head = ThematicRole.KEY)
    public static final class Path {
        public static final String KEY = "cg.role:path";
        private Path() {}
    }

    /** The outcome produced by an action. */
    @Seed.Item(key = Result.KEY, head = ThematicRole.KEY)
    public static final class Result {
        public static final String KEY = "cg.role:result";
        private Result() {}
    }

    /** The participant receiving something in a transfer. */
    @Seed.Item(key = Recipient.KEY, head = ThematicRole.KEY)
    public static final class Recipient {
        public static final String KEY = "cg.role:recipient";
        private Recipient() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = Value.KEY,
                      qualifiers = {Language.English.KEY,
                                    PartOfSpeech.Preposition.KEY,
                                    GrammaticalFeature.Lemma.KEY}))
        static final String englishPreposition = "for";
    }

    /** The participant intended to benefit from an action. */
    @Seed.Item(key = Beneficiary.KEY, head = ThematicRole.KEY)
    public static final class Beneficiary {
        public static final String KEY = "cg.role:beneficiary";
        private Beneficiary() {}
    }

    /** A co-participant in a reciprocal relation. */
    @Seed.Item(key = Partner.KEY, head = ThematicRole.KEY)
    public static final class Partner {
        public static final String KEY = "cg.role:partner";
        private Partner() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = Value.KEY,
                      qualifiers = {Language.English.KEY,
                                    PartOfSpeech.Preposition.KEY,
                                    GrammaticalFeature.Lemma.KEY}))
        static final String englishPreposition = "with";
    }

    /** The means by which an action is performed. */
    @Seed.Item(key = Instrument.KEY, head = ThematicRole.KEY)
    public static final class Instrument {
        public static final String KEY = "cg.role:instrument";
        private Instrument() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = Value.KEY,
                      qualifiers = {Language.English.KEY,
                                    PartOfSpeech.Preposition.KEY,
                                    GrammaticalFeature.Lemma.KEY}))
        static final String englishPreposition = "with";
    }

    /** The manner or way in which an action is performed. */
    @Seed.Item(key = Manner.KEY, head = ThematicRole.KEY)
    public static final class Manner {
        public static final String KEY = "cg.role:manner";
        private Manner() {}
    }

    /** The extent or degree to which something occurs. */
    @Seed.Item(key = Extent.KEY, head = ThematicRole.KEY)
    public static final class Extent {
        public static final String KEY = "cg.role:extent";
        private Extent() {}
    }

    /** A property or characteristic. */
    @Seed.Item(key = Attribute.KEY, head = ThematicRole.KEY)
    public static final class Attribute {
        public static final String KEY = "cg.role:attribute";
        private Attribute() {}
    }

    /** The purpose or motivation. */
    @Seed.Item(key = Purpose.KEY, head = ThematicRole.KEY)
    public static final class Purpose {
        public static final String KEY = "cg.role:purpose";
        private Purpose() {}
    }

    /** The setting or place where an event occurs. */
    @Seed.Item(key = Location.KEY, head = ThematicRole.KEY)
    public static final class Location {
        public static final String KEY = "cg.role:location";
        private Location() {}
    }

    /** The temporal setting of an event. */
    @Seed.Item(key = Time.KEY, head = ThematicRole.KEY)
    public static final class Time {
        public static final String KEY = "cg.role:time";
        private Time() {}
    }

    /** The subject matter of a discourse or expression. */
    @Seed.Item(key = Topic.KEY, head = ThematicRole.KEY)
    public static final class Topic {
        public static final String KEY = "cg.role:topic";
        private Topic() {}
    }

    /** Presentation/styling configuration. */
    @Seed.Item(key = Presentation.KEY, head = ThematicRole.KEY)
    public static final class Presentation {
        public static final String KEY = "cg.role:presentation";
        private Presentation() {}
    }

    /** A referenced item (used in compound bindings to point at related items). */
    @Seed.Item(key = Referent.KEY, head = ThematicRole.KEY)
    public static final class Referent {
        public static final String KEY = "cg.role:referent";
        private Referent() {}
    }

    /** Vocabulary configuration scoping. */
    @Seed.Item(key = Vocabulary.KEY, head = ThematicRole.KEY)
    public static final class Vocabulary {
        public static final String KEY = "cg.role:vocabulary";
        private Vocabulary() {}
    }

    /** A literal-typed value carried by a binding (CG extension; common in seed manifests). */
    @Seed.Item(key = Value.KEY, head = ThematicRole.KEY)
    public static final class Value {
        public static final String KEY = "cg.role:value";
        private Value() {}
    }

    /** Configuration scope qualifier (CG extension; for CONFIG bindings on manifests). */
    @Seed.Item(key = Config.KEY, head = ThematicRole.KEY)
    public static final class Config {
        public static final String KEY = "cg.role:config";
        private Config() {}
    }

    /** Successor reference (CG extension; for FOLLOWS-style ordering). */
    @Seed.Item(key = Follows.KEY, head = ThematicRole.KEY)
    public static final class Follows {
        public static final String KEY = "cg.role:follows";
        private Follows() {}
    }
}
