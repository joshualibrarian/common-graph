package dev.everydaythings.graph.dispatch;

import dev.everydaythings.graph.item.ItemOld;

/**
 * Marker return type for creation results.
 *
 * <p>When the assembly pipeline or a verb handler returns a Created instance,
 * the dispatch pipeline treats it as a creation result (cache the item,
 * register tokens, navigate).
 *
 * @param item the newly created item
 * @param type the type item that was the creation target
 */
public record Created(ItemOld item, ItemOld type) {}
