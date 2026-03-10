package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.id.ItemID;

/**
 * Term-level binding emitted by the semantic parser.
 *
 * <p>Contains both coarse runtime role ({@link BindingRole}) and optional
 * role assignment (as an {@link ItemID} referencing a {@link Role} sememe)
 * when a term filled a verb argument slot.
 */
public record TermBinding(
        int tokenIndex,
        String surface,
        BindingRole bindingRole,
        ItemID roleId,
        ItemID targetId,
        boolean consumed
) {}

