package dev.everydaythings.graph.dispatch;

import dev.everydaythings.graph.item.Item;

/**
 * Marker return type for verb handlers that create items.
 *
 * <p>When a verb handler returns a Created instance, the dispatch
 * pipeline knows to treat it as a creation result (don't navigate,
 * cache the item, register tokens) without checking verb identity.
 */
public record Created(Item item) {}
