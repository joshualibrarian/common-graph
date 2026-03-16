package dev.everydaythings.graph.dispatch;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.Sememe;

/**
 * Marker return type for verb handlers that create items.
 *
 * <p>When a verb handler returns a Created instance, the dispatch
 * pipeline knows to treat it as a creation result (don't navigate,
 * cache the item, register tokens) without checking verb identity.
 *
 * @param item the newly created item
 * @param type the Sememe that was the creation target (carries tokens for discoverability)
 */
public record Created(Item item, Sememe type) {}
