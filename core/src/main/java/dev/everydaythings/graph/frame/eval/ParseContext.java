package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.Sememe;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Context passed to {@link PredicateBehavior#contribute} during parsing.
 *
 * <p>Provides the predicate with enough information to decide how it should
 * participate in parsing: what bindings have been filled, what verb is active,
 * what item is focused, and what language was inferred from the tokens.
 *
 * <p>The token stream itself is not included here yet — it will be added
 * when the unified pipeline (Phase 3+) provides a proper token type.
 * For now, contribute() decisions are based on the accumulated context
 * rather than direct token access.
 */
@Getter
@Builder
public class ParseContext {

    /** Bindings filled so far in the current frame being assembled. */
    private final Map<ItemID, Object> currentBindings;

    /** The verb identified so far (may be null if no verb yet). */
    private final Sememe currentVerb;

    /** The currently focused item (for domain-specific context). */
    private final ItemOld activeItem;

    /** The language inferred from resolved token scopes. */
    private final Language inferredLanguage;

    /** The evaluation scope (librarian, owner, variables). */
    private final Scope scope;
}
