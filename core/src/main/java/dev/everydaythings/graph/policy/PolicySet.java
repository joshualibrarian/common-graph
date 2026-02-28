package dev.everydaythings.graph.policy;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.component.Component;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Property;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
public class PolicySet implements Canonical, Component, Property {

    // ==================================================================================
    // Component Display
    // ==================================================================================

    @Override
    public String displayToken() {
        return "Policy";
    }

    @Override
    public boolean isExpandable() {
        return hasAnyPolicy();
    }

    @Override
    public String colorCategory() {
        return "policy";
    }

    @Override
    public String displaySubtitle() {
        int count = countActivePolicies();
        return count + " polic" + (count == 1 ? "y" : "ies");
    }

    @Override
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
     */
    @Canon(order = 3)
    @Builder.Default
    private AccessPolicy access = new AccessPolicy();

    // ==================================================================================
    // Policy Query Methods
    // ==================================================================================

    /**
     * Check if any policy is defined.
     */
    public boolean hasAnyPolicy() {
        return authority != null || replication != null || route != null ||
               (access != null && access.hasRules());
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
        return count;
    }

    // ==================================================================================
    // Property Implementation
    // ==================================================================================

    @Override
    public Property property(String name) {
        return switch (name) {
            case "authority" -> null; // TODO: wrap as Property
            case "replication" -> null;
            case "route" -> null;
            case "access" -> null;
            default -> null;
        };
    }

    @Override
    public Stream<String> properties() {
        List<String> props = new ArrayList<>();
        if (authority != null) props.add("authority");
        if (replication != null) props.add("replication");
        if (route != null) props.add("route");
        if (access != null && access.hasRules()) props.add("access");
        return props.stream();
    }

    @Override
    public boolean isCollection() {
        return false;
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
        public static class Rule {
            private Subject subject;         // "owner" | "participants" | "hosts" | "any" | explicit ItemID string
            private Action action;
            private Target target;           // handle selector (null = whole item)
            private Effect effect;
            private Map<String, String> when; // optional conditions (time, device, trust)
        }

        @Getter @Setter @Builder
        @NoArgsConstructor @AllArgsConstructor
        public static class Subject {
            private String who;
        }

        @Getter @Setter @Builder
        @NoArgsConstructor @AllArgsConstructor
        public static class Target {
            public enum Kind { REGEX, GLOB }
            private Kind kind;
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
