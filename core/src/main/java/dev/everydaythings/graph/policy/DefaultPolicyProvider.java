package dev.everydaythings.graph.policy;

import dev.everydaythings.graph.item.ItemOld;

/**
 * Provider of default policies for items.
 */
public interface DefaultPolicyProvider {

    /**
     * Get the default policy set for an item.
     *
     * @param item The item to get defaults for
     * @return The default policy set
     */
    PolicySet defaultsFor(ItemOld item);
}