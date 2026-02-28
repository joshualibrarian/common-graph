package dev.everydaythings.graph.policy;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.policy.PolicySet.AccessPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class PolicyMerge {
    /** Load and merge: parents (in order) then local. Local defaultEffect wins if set. */
    public static AccessPolicy merge(AccessPolicy local,
                                     Function<ItemID, AccessPolicy> loader) {
        List<AccessPolicy.Rule> rules = new ArrayList<>();
        AccessPolicy.Effect defaultEffect = AccessPolicy.Effect.DENY;

        if (local != null && local.extendsPolicies() != null) {
            for (var pid : local.extendsPolicies()) {
                var p = loader.apply(pid);
                if (p == null) continue;
                if (p.rules() != null) rules.addAll(p.rules());
                defaultEffect = p.defaultEffect(); // starting base
            }
        }

        if (local != null && local.rules() != null) rules.addAll(local.rules());
        if (local != null) defaultEffect = local.defaultEffect();

        return AccessPolicy.builder()
                .rules(rules)
                .defaultEffect(defaultEffect)
                .build();
    }
}
