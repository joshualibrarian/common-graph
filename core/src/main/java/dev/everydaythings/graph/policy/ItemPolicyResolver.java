package dev.everydaythings.graph.policy;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.component.Roster;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Resolves policy subjects against an Item's live state.
 *
 * <p>Maps the abstract subject strings used in {@link PolicySet.AccessPolicy.Rule}
 * to concrete identity checks:
 *
 * <ul>
 *   <li>{@code "owner"} — checks {@link AuthorityPolicy#ownerId()} on the item's policy</li>
 *   <li>{@code "participants"} — checks the item's {@link Roster} component membership</li>
 *   <li>{@code "hosts"} — checks if the subject is the local librarian/host</li>
 *   <li>Explicit ID string — compares directly against the subject's encoded text</li>
 * </ul>
 *
 * <p>Construct with the target Item and optionally the local host's ItemID:
 * <pre>{@code
 * var resolver = new ItemPolicyResolver(item, librarian.iid());
 * boolean allowed = PolicyEngine.check(
 *         item.policy().access(), resolver, item.iid(),
 *         callerId, Action.READ, "chat");
 * }</pre>
 */
public class ItemPolicyResolver implements PolicyEngine.Resolver {

    private final Item item;
    private final ItemID hostId;

    /**
     * @param item   the item whose policy is being evaluated
     * @param hostId the local librarian's IID (for "hosts" subject matching), or null
     */
    public ItemPolicyResolver(Item item, ItemID hostId) {
        this.item = item;
        this.hostId = hostId;
    }

    /**
     * Resolver without host identity (isHost always returns false).
     */
    public ItemPolicyResolver(Item item) {
        this(item, null);
    }

    @Override
    public boolean isOwner(ItemID itemId, ItemID subj) {
        if (subj == null) return false;

        PolicySet policy = item.policy();
        if (policy == null) return false;

        AuthorityPolicy auth = policy.authority();
        if (auth == null) return false;

        return subj.equals(auth.ownerId());
    }

    @Override
    public boolean isParticipant(ItemID itemId, ItemID subj) {
        if (subj == null) return false;

        Roster roster = findRoster();
        if (roster == null) return false;

        return roster.isMember(subj);
    }

    @Override
    public boolean isHost(ItemID itemId, ItemID subj) {
        if (subj == null || hostId == null) return false;
        return hostId.equals(subj);
    }

    @Override
    public boolean equalsId(String idOrAddr, ItemID subj) {
        if (subj == null || idOrAddr == null) return false;
        return subj.encodeText().equals(idOrAddr);
    }

    // ---

    private Roster findRoster() {
        if (item == null || item.content() == null) return null;

        // Try alias first (most items register roster under "roster")
        var hid = item.content().resolveAlias("roster");
        if (hid.isPresent()) {
            return item.content().getLive(hid.get(), Roster.class).orElse(null);
        }

        // Fallback: scan for any Roster instance
        Roster[] found = new Roster[1];
        item.content().forEachLive(Roster.class, r -> {
            if (found[0] == null) found[0] = r;
        });
        return found[0];
    }
}
