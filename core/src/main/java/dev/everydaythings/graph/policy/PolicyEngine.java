package dev.everydaythings.graph.policy;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.policy.PolicySet.AccessPolicy;

public final class PolicyEngine {
    public interface Resolver {
        boolean isOwner(ItemID item, ItemID subj);
        boolean isParticipant(ItemID item, ItemID subj);
        boolean isHost(ItemID item, ItemID subj);
        boolean equalsId(String idOrAddr, ItemID subj);
        // optional: evaluate conditions (time windows, device posture, trust)
        default boolean condition(String k, String v, ItemID item, ItemID subj) {
            return true;
        }
    }

    public static boolean check(AccessPolicy p, Resolver r, ItemID item, ItemID subj,
                                AccessPolicy.Action action, String handle) {
        if (p == null) p = AccessPolicy.defaultsDenyAll();
        if (p.rules() != null) {
            for (var rule : p.rules()) {
                if (!subjectMatches(rule.subject(), r, item, subj)) continue;
                if (rule.action() != action) continue;
                if (rule.target() != null && !rule.target().matches(handle)) continue;
                if (!conditionsOk(rule, r, item, subj)) continue;

                return rule.effect() == AccessPolicy.Effect.ALLOW;
            }
        }

        return p.defaultEffect() == AccessPolicy.Effect.ALLOW;
    }

    private static boolean subjectMatches(AccessPolicy.Subject s, Resolver r, ItemID item, ItemID subj) {
        return switch (s.who()) {
            case "any" -> true;
            case "owner" -> r.isOwner(item, subj);
            case "participants" -> r.isParticipant(item, subj);
            case "hosts" -> r.isHost(item, subj);
            default -> r.equalsId(s.who(), subj);
        };
    }

    private static boolean conditionsOk(AccessPolicy.Rule rule, Resolver r, ItemID item, ItemID subj) {
        var when = rule.when();
        if (when == null) return true;

        for (var e : when.entrySet())
            if (!r.condition(e.getKey(), e.getValue(), item, subj))
                return false;

        return true;
    }
}
