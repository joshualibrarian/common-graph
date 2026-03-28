package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.FrameRecord;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable accumulator flowing through the {@code onFrameAssembled} callback chain.
 *
 * <p>When the evaluator assembles a complete frame, it creates a FrameAssemblyContext
 * and passes it to each participant in order: the predicate sememe, then binding
 * targets in EXPECTS salience order, then the signer, then the session.
 *
 * <p>Participants can:
 * <ul>
 *   <li>Read the assembled frame body and its bindings</li>
 *   <li>Look up resolved binding-target Items via {@link #item(ItemID)}</li>
 *   <li>Set a result via {@link #handled(Object)} to claim the frame</li>
 *   <li>Read results set by previous participants</li>
 * </ul>
 *
 * <p>If any participant calls {@link #handled(Object)}, the pipeline stores
 * the frame body, signs a record, and returns the result. If nobody handles
 * it, the pipeline returns unhandled and the evaluator falls through to the
 * existing FrameEvaluator / {@code @Verb} dispatch path.
 */
@Getter
public class FrameAssemblyContext {

    /** The assembled semantic assertion. */
    private final FrameBody body;

    /** The evaluation scope (librarian + owner). */
    private final Scope scope;

    /** The asserting identity (signs the record). */
    private final Signer signer;

    /** The execution context session (null in headless). */
    private final Item session;

    /** Binding targets resolved to Items by the pipeline (role IID → Item). */
    private final Map<ItemID, Item> resolvedItems;

    /** The signed record (set after handling, when frame is persisted). */
    private FrameRecord record;

    /** The result value set by a participant callback. */
    private Object result;

    /** True if a participant has claimed the frame. */
    private boolean handled;

    public FrameAssemblyContext(FrameBody body, Scope scope, Signer signer, Item session,
                                Map<ItemID, Item> resolvedItems) {
        this.body = body;
        this.scope = scope;
        this.signer = signer;
        this.session = session;
        this.resolvedItems = resolvedItems != null ? resolvedItems : new LinkedHashMap<>();
    }

    /**
     * Mark this frame as handled and set the result.
     *
     * <p>Once handled, the pipeline will store the body and sign a record.
     *
     * @param result the result value (returned to the evaluator)
     */
    public void handled(Object result) {
        this.handled = true;
        this.result = result;
    }

    /**
     * Set the signed record (called by the pipeline after signing).
     */
    public void record(FrameRecord record) {
        this.record = record;
    }

    /**
     * Look up a resolved binding-target Item by role.
     *
     * @param role the thematic role IID
     * @return the resolved Item, or null if the role has no Item target
     */
    public Item item(ItemID role) {
        return resolvedItems.get(role);
    }
}
