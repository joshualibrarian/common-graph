package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.FrameRecordOld;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.SignerOld;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.runtime.LibrarianOld;
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
 * {@link ItemOld#onFrameAssembled(FrameAssemblyContext)} on each in order:
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
    public FrameAssemblyContext assemble(FrameBodyOld body, Scope scope, SignerOld signer, ItemOld session) {
        LibrarianOld librarian = scope.librarian();

        // Resolve the predicate to a Sememe item
        ItemOld predicateItem = librarian != null
                ? librarian.get(body.predicate(), ItemOld.class).orElse(null)
                : null;

        // Resolve binding targets to Items (skip literals)
        Map<ItemID, ItemOld> resolvedItems = resolveBindingTargets(body, librarian);

        // Build callback order from EXPECTS on the predicate
        List<ItemOld> callbackChain = buildCallbackChain(
                predicateItem, resolvedItems, body, signer, session);

        // Create the context
        FrameAssemblyContext ctx = new FrameAssemblyContext(body, scope, signer, session, resolvedItems);

        // Run callbacks — stop on first handler
        for (ItemOld participant : callbackChain) {
            participant.onFrameAssembled(ctx);
            if (ctx.handled()) break;
        }

        // If handled, persist the frame body (unless it's a command-only action)
        if (ctx.handled() && !ctx.isCommandOnly() && librarian != null) {
            try {
                librarian.storeFrame(body);
                if (signer != null && signer.canSign()) {
                    FrameRecordOld record = FrameRecordOld.create(body, signer);
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
    private Map<ItemID, ItemOld> resolveBindingTargets(FrameBodyOld body, LibrarianOld librarian) {
        Map<ItemID, ItemOld> resolved = new LinkedHashMap<>();
        if (librarian == null || body.frameBindings() == null) return resolved;

        for (Binding binding : body.frameBindings()) {
            ItemID targetId = extractItemId(binding.target());
            if (targetId != null) {
                librarian.get(targetId, ItemOld.class).ifPresent(item -> resolved.put(binding.role(), item));
            }
        }
        return resolved;
    }

    /**
     * Extract an ItemID from a {@link BindingTarget.RefTarget}.
     */
    private ItemID extractItemId(BindingTarget target) {
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
    private List<ItemOld> buildCallbackChain(ItemOld predicateItem, Map<ItemID, ItemOld> resolvedItems,
                                             FrameBodyOld body, SignerOld signer, ItemOld session) {
        LinkedHashSet<ItemOld> chain = new LinkedHashSet<>();

        // 1. Predicate sememe first
        if (predicateItem != null) {
            chain.add(predicateItem);
        }

        // 2. Binding targets in EXPECTS salience order
        if (predicateItem instanceof Sememe sememe) {
            List<ItemID> expectsOrder = sememe.slotRoles();
            for (ItemID role : expectsOrder) {
                ItemOld target = resolvedItems.get(role);
                if (target != null) {
                    chain.add(target);
                }
            }
        }

        // Add any binding targets not covered by EXPECTS (in binding order)
        for (ItemOld target : resolvedItems.values()) {
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
