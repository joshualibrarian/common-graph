package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.FrameRecord;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Runs the frame assembly callback chain.
 *
 * <p>Given an assembled FrameBody, resolves all participants and calls
 * {@link Item#onFrameAssembled(FrameAssemblyContext)} on each in order:
 * <ol>
 *   <li>The predicate sememe (knows overall semantics)</li>
 *   <li>Binding targets in EXPECTS salience order (skip literals)</li>
 *   <li>The signer (contextual participant, always present)</li>
 *   <li>The session (contextual participant, always present)</li>
 * </ol>
 *
 * <p>If any participant calls {@link FrameAssemblyContext#handled(Object)},
 * the frame body is stored in the library and a signed FrameRecord is created.
 * If nobody handles the frame, the context is returned unhandled and the
 * evaluator falls through to the existing dispatch path.
 */
@Log4j2
public class FrameAssemblyPipeline {

    /**
     * Run the assembly pipeline for a frame.
     *
     * @param body    the assembled FrameBody
     * @param scope   evaluation scope (librarian + owner)
     * @param signer  the asserting identity
     * @param session the execution context session (may be null)
     * @return the assembly context (check {@code ctx.handled()} for result)
     */
    public FrameAssemblyContext assemble(FrameBody body, Scope scope, Signer signer, Item session) {
        Librarian librarian = scope.librarian();

        // Resolve the predicate to a Sememe item
        Item predicateItem = librarian != null
                ? librarian.get(body.predicate(), Item.class).orElse(null)
                : null;

        // Resolve binding targets to Items (skip literals)
        Map<ItemID, Item> resolvedItems = resolveBindingTargets(body, librarian);

        // Build callback order from EXPECTS on the predicate
        List<Item> callbackChain = buildCallbackChain(
                predicateItem, resolvedItems, body, signer, session);

        // Create the context
        FrameAssemblyContext ctx = new FrameAssemblyContext(body, scope, signer, session, resolvedItems);

        // Run callbacks — stop on first handler
        for (Item participant : callbackChain) {
            participant.onFrameAssembled(ctx);
            if (ctx.handled()) break;
        }

        // If handled, persist the frame body (unless it's a command-only action)
        if (ctx.handled() && !ctx.isCommandOnly() && librarian != null) {
            try {
                librarian.storeFrame(body);
                if (signer != null && signer.canSign()) {
                    FrameRecord record = FrameRecord.create(body, signer);
                    ctx.record(record);
                }
            } catch (Exception e) {
                logger.warn("Failed to persist assembled frame: {}", e.getMessage());
            }
        }

        return ctx;
    }

    /**
     * Resolve binding targets to Items, keyed by role.
     */
    private Map<ItemID, Item> resolveBindingTargets(FrameBody body, Librarian librarian) {
        Map<ItemID, Item> resolved = new LinkedHashMap<>();
        if (librarian == null || body.frameBindings() == null) return resolved;

        for (Binding binding : body.frameBindings()) {
            ItemID targetId = extractItemId(binding.target());
            if (targetId != null) {
                librarian.get(targetId, Item.class).ifPresent(item -> resolved.put(binding.role(), item));
            }
        }
        return resolved;
    }

    /**
     * Extract an ItemID from a BindingTarget (IidTarget or RefTarget).
     */
    private ItemID extractItemId(BindingTarget target) {
        if (target instanceof BindingTarget.IidTarget iid) return iid.iid();
        if (target instanceof BindingTarget.RefTarget ref) return ref.asItemId();
        return null;
    }

    /**
     * Build the callback chain in the correct order.
     *
     * <p>Order: predicate → binding targets in EXPECTS order → signer → session.
     * Deduplicates: if the signer or session is already in the chain as a binding
     * target, it won't be called twice.
     */
    private List<Item> buildCallbackChain(Item predicateItem, Map<ItemID, Item> resolvedItems,
                                          FrameBody body, Signer signer, Item session) {
        LinkedHashSet<Item> chain = new LinkedHashSet<>();

        // 1. Predicate sememe first
        if (predicateItem != null) {
            chain.add(predicateItem);
        }

        // 2. Binding targets in EXPECTS salience order
        if (predicateItem instanceof Sememe sememe) {
            List<ItemID> expectsOrder = sememe.slotRoles();
            for (ItemID role : expectsOrder) {
                Item target = resolvedItems.get(role);
                if (target != null) {
                    chain.add(target);
                }
            }
        }

        // Add any binding targets not covered by EXPECTS (in binding order)
        for (Item target : resolvedItems.values()) {
            chain.add(target);
        }

        // 3. Signer (contextual, always present)
        if (signer != null) {
            chain.add(signer);
        }

        // 4. Session (contextual, always present)
        if (session != null) {
            chain.add(session);
        }

        return new ArrayList<>(chain);
    }
}
