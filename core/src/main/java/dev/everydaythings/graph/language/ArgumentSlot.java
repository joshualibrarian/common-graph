package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Map;

/**
 * Describes one argument slot in a verb's valency frame.
 *
 * <p>Each verb declares what roles it needs filled (its argument structure).
 * An ArgumentSlot describes one such role — what thematic role it expects,
 * whether it's required, what type constraint it has, and human-readable
 * descriptions by language.
 *
 * <p>The assembler matches preposition-tagged arguments and bare nouns
 * against these slots to build a {@code SemanticFrame}.
 *
 * <p>Implements {@link Canonical} for CBOR persistence — argument slots are
 * stored as part of a verb's content and survive hydration.
 */
@Getter
@EqualsAndHashCode
public class ArgumentSlot implements Canonical {

    /** The thematic role this slot expects (THEME, TARGET, etc.). */
    @Canon(order = 0)
    private ThematicRole role;

    /** Whether this argument must be provided. */
    @Canon(order = 1)
    private boolean required;

    /** Item type that the argument must be (null = any type). */
    @Canon(order = 2)
    private ItemID typeConstraint;

    /** Human-readable descriptions by language ("en" → "what to create"). */
    @Canon(order = 3)
    private Map<String, String> descriptions;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** No-arg constructor for Canonical framework. */
    ArgumentSlot() {}

    public ArgumentSlot(ThematicRole role, boolean required,
                        ItemID typeConstraint, Map<String, String> descriptions) {
        this.role = role;
        this.required = required;
        this.typeConstraint = typeConstraint;
        this.descriptions = descriptions;
    }

    // ==================================================================================
    // FACTORY METHODS
    // ==================================================================================

    /**
     * Create a required argument slot with an English description.
     */
    public static ArgumentSlot required(ThematicRole role, String enDescription) {
        return new ArgumentSlot(role, true, null, Map.of("en", enDescription));
    }

    /**
     * Create an optional argument slot with an English description.
     */
    public static ArgumentSlot optional(ThematicRole role, String enDescription) {
        return new ArgumentSlot(role, false, null, Map.of("en", enDescription));
    }

    /**
     * Create a required argument slot with a type constraint and English description.
     */
    public static ArgumentSlot required(ThematicRole role, ItemID typeConstraint, String enDescription) {
        return new ArgumentSlot(role, true, typeConstraint, Map.of("en", enDescription));
    }

    /**
     * Create an optional argument slot with a type constraint and English description.
     */
    public static ArgumentSlot optional(ThematicRole role, ItemID typeConstraint, String enDescription) {
        return new ArgumentSlot(role, false, typeConstraint, Map.of("en", enDescription));
    }

    // ==================================================================================
    // OBJECT METHODS
    // ==================================================================================

    @Override
    public String toString() {
        return "ArgumentSlot[" + role +
                (required ? ", required" : ", optional") +
                (typeConstraint != null ? ", type=" + typeConstraint : "") +
                ", " + descriptions + "]";
    }
}
