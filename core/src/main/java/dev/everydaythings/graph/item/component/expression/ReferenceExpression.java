package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.component.ExpressionComponent;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.Optional;

/**
 * A reference expression that points to another value.
 *
 * <p>References can be:
 * <ul>
 *   <li><b>Local</b>: reference to a value in the same item ({@code item = null})</li>
 *   <li><b>Remote</b>: reference to a value in another item ({@code item = ItemID})</li>
 *   <li><b>Binding</b>: reference to a let-bound variable ({@code item = null, handle = varName})</li>
 * </ul>
 *
 * <p>This enables expressions to compose across items:
 * <pre>{@code
 * // Local reference: value in same item
 * ref("total")  // refers to sibling value with handle "total"
 *
 * // Remote reference: value in another item
 * ref(otherItemId, "amount")  // refers to "amount" in otherItemId
 *
 * // Binding reference: let-bound variable
 * // let x = 10 in x + 1
 * // The "x + 1" part uses ref("x") to refer to the bound value
 * }</pre>
 *
 * @param item   The ItemID to reference (null for local/binding references)
 * @param handle The handle/name of the value to reference
 */
public record ReferenceExpression(
        @Canon(order = 0) ItemID item,
        @Canon(order = 1) String handle
) implements Expression, Canonical {

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create a local reference (same item or binding).
     */
    public static ReferenceExpression local(String handle) {
        return new ReferenceExpression(null, handle);
    }

    /**
     * Create a remote reference (another item).
     */
    public static ReferenceExpression remote(ItemID item, String handle) {
        return new ReferenceExpression(item, handle);
    }

    /**
     * Create a reference (convenience method).
     */
    public static ReferenceExpression ref(String handle) {
        return local(handle);
    }

    /**
     * Create a reference (convenience method).
     */
    public static ReferenceExpression ref(ItemID item, String handle) {
        return remote(item, handle);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        // First, check for let-binding (local scope)
        if (item == null) {
            Optional<Object> binding = context.lookup(handle);
            if (binding.isPresent()) {
                return binding.get();
            }
        }

        // Resolve the target item
        Item targetItem;
        if (item == null) {
            // Local reference - use owner
            targetItem = context.owner();
        } else {
            // Remote reference - resolve from graph
            var resolved = context.resolve(item);
            if (resolved.isEmpty()) {
                return null; // Item not found
            }
            targetItem = resolved.get();
        }

        if (targetItem == null) {
            return null;
        }

        // Get the expression component from the target item's content
        HandleID handleId = HandleID.of(handle);
        var exprOpt = targetItem.content().getLive(handleId, ExpressionComponent.class);
        if (exprOpt.isEmpty()) {
            // Try getting any component with that handle
            var anyOpt = targetItem.content().getLive(handleId, Object.class);
            if (anyOpt.isPresent()) {
                Object component = anyOpt.get();
                // If it's an ExpressionComponent, evaluate it
                if (component instanceof ExpressionComponent expr) {
                    return expr.evaluate(context);
                }
                // Otherwise return the component itself
                return component;
            }
            return null;
        }

        // Evaluate the referenced expression
        ExpressionComponent expr = exprOpt.get();
        return expr.evaluate(context);
    }

    @Override
    public ItemID resultType() {
        // Type depends on the referenced value - can't determine statically
        return null;
    }

    @Override
    public String toExpressionString() {
        if (item == null) {
            return handle;
        }
        return item.encodeText() + "." + handle;
    }

    @Override
    public boolean hasDependencies() {
        return true; // Always depends on another value
    }

    /**
     * Check if this is a local reference.
     */
    public boolean isLocal() {
        return item == null;
    }

    @Override
    public boolean referencesLocal(String handle) {
        return isLocal() && this.handle.equals(handle);
    }

    /**
     * Check if this is a remote reference.
     */
    public boolean isRemote() {
        return item != null;
    }
}
