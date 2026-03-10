package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Map;

/**
 * Describes one role slot in a predicate's frame schema.
 *
 * <p>Every predicate (verb, relation type, query pattern) declares what roles
 * participants can fill. An ArgumentSlot describes one such role — which
 * {@link Role} sememe it expects, whether it's required, what type constraint
 * it has, and human-readable descriptions by language.
 *
 * <p>The {@link FrameAssembler} matches preposition-tagged arguments and bare
 * nouns against these slots to build a {@link SemanticFrame}.
 *
 * <p>Implements {@link Canonical} for CBOR persistence — argument slots are
 * stored as part of a predicate's frame schema and survive hydration.
 *
 * @see Role
 * @see SemanticFrame
 * @see FrameAssembler
 */
@Getter
@EqualsAndHashCode
public class ArgumentSlot implements Canonical {

    /** The role sememe this slot expects (references a {@link Role} item). */
    @Canon(order = 0)
    private ItemID role;

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

    public ArgumentSlot(ItemID role, boolean required,
                        ItemID typeConstraint, Map<String, String> descriptions) {
        this.role = role;
        this.required = required;
        this.typeConstraint = typeConstraint;
        this.descriptions = descriptions;
    }

    // ==================================================================================
    // FACTORY METHODS (ItemID-based)
    // ==================================================================================

    public static ArgumentSlot required(ItemID role, String enDescription) {
        return new ArgumentSlot(role, true, null, Map.of("en", enDescription));
    }

    public static ArgumentSlot optional(ItemID role, String enDescription) {
        return new ArgumentSlot(role, false, null, Map.of("en", enDescription));
    }

    public static ArgumentSlot required(ItemID role, ItemID typeConstraint, String enDescription) {
        return new ArgumentSlot(role, true, typeConstraint, Map.of("en", enDescription));
    }

    public static ArgumentSlot optional(ItemID role, ItemID typeConstraint, String enDescription) {
        return new ArgumentSlot(role, false, typeConstraint, Map.of("en", enDescription));
    }

    // ==================================================================================
    // FACTORY METHODS (ThematicRole bridge — deprecated)
    // ==================================================================================

    /** @deprecated Use {@link #required(ItemID, String)} with {@code Role.X.iid()}. */
    @Deprecated
    public static ArgumentSlot required(ThematicRole role, String enDescription) {
        return required(role.iid(), enDescription);
    }

    /** @deprecated Use {@link #optional(ItemID, String)} with {@code Role.X.iid()}. */
    @Deprecated
    public static ArgumentSlot optional(ThematicRole role, String enDescription) {
        return optional(role.iid(), enDescription);
    }

    /** @deprecated Use {@link #required(ItemID, ItemID, String)} with {@code Role.X.iid()}. */
    @Deprecated
    public static ArgumentSlot required(ThematicRole role, ItemID typeConstraint, String enDescription) {
        return required(role.iid(), typeConstraint, enDescription);
    }

    /** @deprecated Use {@link #optional(ItemID, ItemID, String)} with {@code Role.X.iid()}. */
    @Deprecated
    public static ArgumentSlot optional(ThematicRole role, ItemID typeConstraint, String enDescription) {
        return optional(role.iid(), typeConstraint, enDescription);
    }

    // ==================================================================================
    // QUERIES
    // ==================================================================================

    /**
     * Check if this slot's role matches the given role sememe IID.
     */
    public boolean hasRole(ItemID roleIid) {
        return role != null && role.equals(roleIid);
    }

    /**
     * Check if this slot's role matches the given ThematicRole.
     * @deprecated Use {@link #hasRole(ItemID)}.
     */
    @Deprecated
    public boolean hasRole(ThematicRole thematicRole) {
        return hasRole(thematicRole.iid());
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
