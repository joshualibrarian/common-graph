package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.id.ItemID;

/**
 * Term-level binding emitted by the semantic parser.
 *
 * <p>Contains both coarse runtime role ({@link BindingRole}) and optional
 * thematic-role assignment when a term filled a verb argument slot.
 */
public record TermBinding(
        int tokenIndex,
        String surface,
        BindingRole bindingRole,
        ThematicRole thematicRole,
        ItemID targetId,
        boolean consumed
) {}

