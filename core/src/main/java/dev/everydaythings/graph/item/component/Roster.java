package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.item.id.ItemID;

import java.time.Instant;
import java.util.*;

/**
 * Tracks principals (users/signers) present within this item.
 *
 * <p>Roster is one of the four core container components:
 * <ul>
 *   <li><b>Roster</b> - principals present here (this)</li>
 *   <li><b>Surface</b> - 2D layout of contents</li>
 *   <li><b>Space</b> - 3D environment for contents</li>
 *   <li><b>Model</b> - 3D object representation (icon)</li>
 * </ul>
 *
 * <p>Containment itself is expressed through reference entries in the
 * ComponentTable, not a separate component.
 *
 * <p>Any item can have a Roster, tracking who is "in" this item.
 * Examples: members of a group, participants in a chat room,
 * collaborators on a document, attendees at an event.
 *
 * <p>Unlike reference entries (which track contained items), Roster tracks
 * principals who have <i>presence</i> - they're participating,
 * not just stored here.
 */
@Type(value = Roster.KEY, glyph = "👥")
public class Roster implements Component {

    public static final String KEY = "cg:type/roster";

    /** Members with their membership info */
    private final Map<ItemID, Membership> members;

    /**
     * Create an empty roster.
     */
    public Roster() {
        this.members = new LinkedHashMap<>();  // Preserve insertion order
    }

    /**
     * Factory method to create an empty roster.
     */
    public static Roster create() {
        return new Roster();
    }

    // ==================================================================================
    // Membership Info
    // ==================================================================================

    /**
     * Information about a principal's membership.
     */
    public static class Membership {
        private final ItemID principalId;
        private final Instant joined;
        private String role;  // e.g., "owner", "admin", "member", "guest"
        private Instant lastSeen;

        public Membership(ItemID principalId) {
            this.principalId = principalId;
            this.joined = Instant.now();
            this.role = "member";
            this.lastSeen = this.joined;
        }

        public Membership(ItemID principalId, String role) {
            this.principalId = principalId;
            this.joined = Instant.now();
            this.role = role;
            this.lastSeen = this.joined;
        }

        public ItemID principalId() { return principalId; }
        public Instant joined() { return joined; }
        public String role() { return role; }
        public Instant lastSeen() { return lastSeen; }

        public void setRole(String role) { this.role = role; }
        public void touch() { this.lastSeen = Instant.now(); }
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /**
     * Get all member IDs.
     */
    public Set<ItemID> memberIds() {
        return Collections.unmodifiableSet(members.keySet());
    }

    /**
     * Get all memberships.
     */
    public Collection<Membership> memberships() {
        return Collections.unmodifiableCollection(members.values());
    }

    /**
     * Get membership info for a principal.
     */
    public Optional<Membership> getMembership(ItemID principalId) {
        return Optional.ofNullable(members.get(principalId));
    }

    /**
     * Check if a principal is a member.
     */
    public boolean isMember(ItemID principalId) {
        return members.containsKey(principalId);
    }

    /**
     * Get the number of members.
     */
    public int size() {
        return members.size();
    }

    /**
     * Check if the roster is empty.
     */
    public boolean isEmpty() {
        return members.isEmpty();
    }

    // ==================================================================================
    // Mutations
    // ==================================================================================

    /**
     * Add a principal to the roster.
     *
     * @param principalId The principal to add
     * @return The membership (new or existing)
     */
    public Membership add(ItemID principalId) {
        return members.computeIfAbsent(principalId, Membership::new);
    }

    /**
     * Add a principal with a specific role.
     *
     * @param principalId The principal to add
     * @param role The role to assign
     * @return The membership
     */
    public Membership add(ItemID principalId, String role) {
        Membership membership = members.computeIfAbsent(principalId, Membership::new);
        membership.setRole(role);
        return membership;
    }

    /**
     * Remove a principal from the roster.
     *
     * @param principalId The principal to remove
     * @return true if removed, false if not present
     */
    public boolean remove(ItemID principalId) {
        return members.remove(principalId) != null;
    }

    /**
     * Update a principal's role.
     *
     * @param principalId The principal
     * @param role The new role
     * @return true if updated, false if not a member
     */
    public boolean setRole(ItemID principalId, String role) {
        Membership membership = members.get(principalId);
        if (membership == null) {
            return false;
        }
        membership.setRole(role);
        return true;
    }

    /**
     * Touch a principal (update last seen).
     *
     * @param principalId The principal
     */
    public void touch(ItemID principalId) {
        Membership membership = members.get(principalId);
        if (membership != null) {
            membership.touch();
        }
    }

    /**
     * Clear all members from the roster.
     */
    public void clear() {
        members.clear();
    }

    // ==================================================================================
    // Query
    // ==================================================================================

    /**
     * Get members with a specific role.
     */
    public List<ItemID> membersWithRole(String role) {
        return members.entrySet().stream()
                .filter(e -> role.equals(e.getValue().role()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Get the owner(s) of this roster.
     */
    public List<ItemID> owners() {
        return membersWithRole("owner");
    }

    /**
     * Get admins of this roster.
     */
    public List<ItemID> admins() {
        return membersWithRole("admin");
    }
}
