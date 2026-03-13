package dev.everydaythings.graph.policy;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Collection of policies defining access rules for an Item.
 *
 * <p>PolicySet holds multiple policy objects that govern different aspects
 * of item behavior:
 * <ul>
 *   <li><b>Authority</b> - Who can modify the item (owner, maintainers)</li>
 *   <li><b>Replication</b> - How the item should be replicated across nodes</li>
 *   <li><b>Route</b> - How requests to the item should be routed</li>
 *   <li><b>Access</b> - Fine-grained access control rules</li>
 * </ul>
 *
 * <p>Implements {@link Component} for unified Item component model.
 */
@Type(value = "cg:type/policy", glyph = "🛡️", color = 0xC8A064)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Canonical.Canonization
public class PolicySet implements Canonical {

    // ==================================================================================
    // Component Display
    // ==================================================================================

    public String displayToken() {
        return "Policy";
    }

    public boolean isExpandable() {
        return hasAnyPolicy();
    }

    public String colorCategory() {
        return "policy";
    }

    public String displaySubtitle() {
        int count = countActivePolicies();
        return count + " polic" + (count == 1 ? "y" : "ies");
    }

    public String emoji() {
        return "🛡️";  // Shield for policy
    }

    // ==================================================================================
    // Policy Objects
    // ==================================================================================

    /**
     * Authority policy - who can modify the item.
     *
     * <p>Defines ownership and maintainer relationships.
     */
    @Canon(order = 0)
    private AuthorityPolicy authority;

    /**
     * Replication policy - how the item is replicated.
     *
     * <p>Controls which nodes should have copies and sync behavior.
     */
    @Canon(order = 1)
    private ReplicationPolicy replication;

    /**
     * Route policy - how requests are routed.
     *
     * <p>Defines routing preferences and fallback behavior.
     */
    @Canon(order = 2)
    private RoutePolicy route;

    /**
     * Access policy - fine-grained access control rules.
     *
     * <p>Controls <b>distribution</b>: who the Librarian shares this frame with.
     * A private frame (no READ rules) simply isn't replicated.
     */
    @Canon(order = 3)
    @Builder.Default
    private AccessPolicy access = new AccessPolicy();

    /**
     * Encryption policy — cryptographic protection of frame content.
     *
     * <p>Controls whether the frame's bytes are encrypted and to whom.
     * Separate from access policy: access controls distribution (who gets
     * the bytes), encryption controls cryptographic protection (even if
     * someone obtains the bytes, they can't read without the key).
     *
     * <p>Common shorthand: {@code encryptToReaders=true} derives encryption
     * recipients from the access policy's READ rules.
     */
    @Canon(order = 4)
    private EncryptionPolicy encryption;

    // ==================================================================================
    // Policy Query Methods
    // ==================================================================================

    /**
     * Check if any policy is defined.
     */
    public boolean hasAnyPolicy() {
        return authority != null || replication != null || route != null ||
               (access != null && access.hasRules()) ||
               (encryption != null && encryption.isEnabled());
    }

    /**
     * Count the number of active policy types.
     */
    public int countActivePolicies() {
        int count = 0;
        if (authority != null) count++;
        if (replication != null) count++;
        if (route != null) count++;
        if (access != null && access.hasRules()) count++;
        if (encryption != null && encryption.isEnabled()) count++;
        return count;
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create a default policy set (empty, deny-all access).
     */
    public static PolicySet defaultsDenyAll() {
        return PolicySet.builder()
                .access(AccessPolicy.defaultsDenyAll())
                .build();
    }

    // ==================================================================================
    // Nested Policy Classes
    // ==================================================================================

    /**
     * Replication policy - controls how an item is replicated.
     */
    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    @Canonical.Canonization
    public static class ReplicationPolicy implements Canonical {
        /** Minimum number of replicas to maintain. */
        @Canon(order = 0)
        @Builder.Default
        private int minReplicas = 1;

        /** Maximum number of replicas (0 = unlimited). */
        @Canon(order = 1)
        @Builder.Default
        private int maxReplicas = 0;

        /** Preferred nodes for replication. */
        @Canon(order = 2)
        @Builder.Default
        private List<ItemID> preferredNodes = new ArrayList<>();

        /** Whether to replicate to all connected nodes. */
        @Canon(order = 3)
        @Builder.Default
        private boolean broadcast = false;
    }

    /**
     * Route policy - controls how requests are routed.
     */
    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    @Canonical.Canonization
    public static class RoutePolicy implements Canonical {
        /** Primary node for this item. */
        @Canon(order = 0)
        private ItemID primaryNode;

        /** Fallback nodes if primary is unavailable. */
        @Canon(order = 1)
        @Builder.Default
        private List<ItemID> fallbackNodes = new ArrayList<>();

        /** Whether to allow local-only mode (no network). */
        @Canon(order = 2)
        @Builder.Default
        private boolean allowLocalOnly = true;
    }

    /**
     * Encryption policy — whether and to whom a frame's content is encrypted.
     *
     * <p>This is separate from access policy. Access controls distribution
     * (who the Librarian shares bytes with). Encryption controls cryptographic
     * protection (bytes are unreadable without the key, even if obtained).
     *
     * <p>Four combinations:
     * <ul>
     *   <li>Shared + cleartext — public frame</li>
     *   <li>Shared + encrypted — e2e encrypted (replicated, only key holders read)</li>
     *   <li>Private + cleartext — trust-based privacy (not shared, but bytes readable if leaked)</li>
     *   <li>Private + encrypted — maximum protection</li>
     * </ul>
     *
     * <p>The {@code encryptToReaders} shorthand derives encryption recipients
     * from the access policy's READ rules, covering the common case where
     * "who can read" and "who to encrypt to" are the same set.
     */
    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    @Canonical.Canonization
    public static class EncryptionPolicy implements Canonical {

        /**
         * Whether encryption is enabled for this frame.
         */
        @Canon(order = 0)
        @Builder.Default
        private boolean enabled = false;

        /**
         * Derive encryption recipients from the access policy's READ rules.
         *
         * <p>When true, the commit flow resolves all subjects with
         * ALLOW READ from the frame's access policy and encrypts to
         * their encryption public keys. This is the common case.
         *
         * <p>When false (and recipients is non-empty), uses the explicit
         * recipient list instead.
         */
        @Canon(order = 1)
        @Builder.Default
        private boolean encryptToReaders = false;

        /**
         * Explicit encryption recipients (principal ItemIDs).
         *
         * <p>Used when encryption recipients differ from the access policy's
         * readers — e.g., encrypting to a backup service that isn't in the
         * reader list, or encrypting to a specific subset.
         *
         * <p>Ignored when {@code encryptToReaders} is true.
         */
        @Canon(order = 2)
        @Builder.Default
        private List<ItemID> recipients = new ArrayList<>();

        /**
         * AEAD algorithm override. When null, uses the system default (AES-256-GCM).
         */
        @Canon(order = 3)
        private String algorithm;

        /**
         * Check if encryption is active (enabled with recipients or encryptToReaders).
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Check if this policy has explicit recipients.
         */
        public boolean hasExplicitRecipients() {
            return recipients != null && !recipients.isEmpty();
        }

        /** Encrypt to specific recipients. */
        public static EncryptionPolicy toRecipients(List<ItemID> recipients) {
            return EncryptionPolicy.builder()
                    .enabled(true)
                    .recipients(recipients)
                    .build();
        }

        /** Encrypt to whoever has READ access. */
        public static EncryptionPolicy toReaders() {
            return EncryptionPolicy.builder()
                    .enabled(true)
                    .encryptToReaders(true)
                    .build();
        }

        /** No encryption. */
        public static EncryptionPolicy none() {
            return EncryptionPolicy.builder().build();
        }
    }

    /**
     * Access policy - fine-grained access control rules.
     */
    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    @Canonical.Canonization
    public static class AccessPolicy implements Canonical {
        public enum Effect { ALLOW, DENY }
        public enum Action { READ, APPEND, DECLARE, CLOSE, ARCHIVE, PUBLISH }

        /** Optional inheritance (reuse shared policy Items). */
        @Canon(order = 0)
        @Builder.Default
        private List<ItemID> extendsPolicies = new ArrayList<>();

        /** Default when no rule matches. */
        @Canon(order = 1)
        @Builder.Default
        private Effect defaultEffect = Effect.DENY;

        /** Ordered rules, first-match wins. */
        @Canon(order = 2)
        @Builder.Default
        private List<Rule> rules = new ArrayList<>();

        /**
         * Check if this policy has any rules.
         */
        public boolean hasRules() {
            return rules != null && !rules.isEmpty();
        }

        /**
         * Create a deny-all access policy.
         */
        public static AccessPolicy defaultsDenyAll() {
            return AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .build();
        }

        // ==================================================================================
        // Rule and related classes
        // ==================================================================================

        @Getter @Setter @Builder
        @NoArgsConstructor @AllArgsConstructor
        @Canonical.Canonization
        public static class Rule implements Canonical {
            @Canon(order = 0)
            private Subject subject;         // "owner" | "participants" | "hosts" | "any" | explicit ItemID string
            @Canon(order = 1)
            private Action action;
            @Canon(order = 2)
            private Target target;           // handle selector (null = whole item)
            @Canon(order = 3)
            private Effect effect;
            @Canon(order = 4)
            private Map<String, String> when; // optional conditions (time, device, trust)
        }

        @Getter @Setter @Builder
        @NoArgsConstructor @AllArgsConstructor
        @Canonical.Canonization
        public static class Subject implements Canonical {
            @Canon(order = 0)
            private String who;
        }

        @Getter @Setter @Builder
        @NoArgsConstructor @AllArgsConstructor
        @Canonical.Canonization
        public static class Target implements Canonical {
            public enum Kind { REGEX, GLOB }
            @Canon(order = 0)
            private Kind kind;
            @Canon(order = 1)
            private String pattern;
            private transient Pattern compiled;

            public boolean matches(String handle) {
                if (pattern == null) return true;
                if (compiled == null) {
                    compiled = (kind == Kind.GLOB ? Glob.toRegex(pattern) : Pattern.compile(pattern));
                }
                return compiled.matcher(handle == null ? "" : handle).matches();
            }
        }
    }
}
