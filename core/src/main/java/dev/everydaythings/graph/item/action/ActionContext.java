package dev.everydaythings.graph.item.action;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.Optional;

/**
 * Context provided to action methods during invocation.
 *
 * <p>ActionContext provides actions with:
 * <ul>
 *   <li>Caller identity - who invoked the action</li>
 *   <li>Target item - the item this action is operating on</li>
 *   <li>Sibling access - access to other components on the same item</li>
 *   <li>System access - the librarian for broader operations</li>
 * </ul>
 *
 * <p>Usage in action methods:
 * <pre>{@code
 * @Verb(value = "cg.verb:move", doc = "Make a chess move")
 * public void move(ActionContext ctx, String notation) {
 *     // Check caller is a player
 *     Roster roster = ctx.sibling("roster", Roster.class)
 *         .orElseThrow(() -> new IllegalStateException("No roster"));
 *     if (!roster.hasRole(ctx.caller(), "player")) {
 *         throw new SecurityException("Only players can move");
 *     }
 *     // ... make the move
 * }
 * }</pre>
 */
public interface ActionContext {

    /**
     * The ItemID of the caller invoking this action.
     *
     * <p>This identifies who is performing the action. For signed requests,
     * this is the signer's IID. For anonymous/local requests, this may be
     * the host's IID or a special anonymous identifier.
     *
     * @return The caller's ItemID
     */
    ItemID caller();

    /**
     * The Signer if the caller has signing capability.
     *
     * <p>Returns empty if:
     * <ul>
     *   <li>The request is anonymous/unsigned</li>
     *   <li>The caller's signer is not available locally</li>
     * </ul>
     *
     * @return The caller's Signer, or empty
     */
    Optional<Signer> callerSigner();

    /**
     * The item this action is being invoked on.
     *
     * @return The target item
     */
    Item item();

    /**
     * The item's IID (convenience method).
     *
     * @return The target item's IID
     */
    default ItemID itemId() {
        return item().iid();
    }

    /**
     * Access a sibling component by handle.
     *
     * <p>This allows actions to coordinate with other components on the
     * same item. For example, a chess "move" action might need to check
     * the "roster" component to verify the caller is a player.
     *
     * @param handle The component handle
     * @param type The expected component type
     * @return The component, or empty if not found or wrong type
     */
    <T> Optional<T> sibling(String handle, Class<T> type);

    /**
     * The librarian for system-level operations.
     *
     * <p>Use this for:
     * <ul>
     *   <li>Creating new items</li>
     *   <li>Querying relations</li>
     *   <li>Storing content</li>
     *   <li>Other cross-item operations</li>
     * </ul>
     *
     * @return The librarian, or null for seed items
     */
    Librarian librarian();

    /**
     * The store for this item's storage operations.
     *
     * <p>This returns the appropriate ItemStore for the current item:
     * <ul>
     *   <li>For most items: the librarian's composite store</li>
     *   <li>For items with WorkingTreeStore: the item's local store</li>
     * </ul>
     *
     * <p>Use this for storing log entries, payloads, and other content
     * that belongs to this item.
     *
     * @return The item's store
     */
    ItemStore store();

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create an ActionContext for invoking an action.
     *
     * <p>Uses the librarian's store by default.
     *
     * @param caller The caller's ItemID
     * @param callerSigner The caller's Signer (optional)
     * @param item The target item
     * @param librarian The librarian
     * @return A new ActionContext
     */
    static ActionContext of(ItemID caller, Signer callerSigner, Item item, Librarian librarian) {
        // Default: use librarian's store
        return of(caller, callerSigner, item, librarian, librarian != null ? librarian.primaryStore().orElse(null) : null);
    }

    /**
     * Create an ActionContext with an explicit store.
     *
     * <p>Use this when the item has its own WorkingTreeStore or other
     * storage that differs from the librarian's default store.
     *
     * @param caller The caller's ItemID
     * @param callerSigner The caller's Signer (optional)
     * @param item The target item
     * @param librarian The librarian
     * @param store The store for this item's operations
     * @return A new ActionContext
     */
    static ActionContext of(ItemID caller, Signer callerSigner, Item item, Librarian librarian, ItemStore store) {
        return new ActionContext() {
            @Override
            public ItemID caller() {
                return caller;
            }

            @Override
            public Optional<Signer> callerSigner() {
                return Optional.ofNullable(callerSigner);
            }

            @Override
            public Item item() {
                return item;
            }

            @Override
            public <T> Optional<T> sibling(String handle, Class<T> type) {
                // TODO: Implement component lookup from item
                // This needs Item to expose component access by handle
                return Optional.empty();
            }

            @Override
            public Librarian librarian() {
                return librarian;
            }

            @Override
            public ItemStore store() {
                return store;
            }
        };
    }
}
