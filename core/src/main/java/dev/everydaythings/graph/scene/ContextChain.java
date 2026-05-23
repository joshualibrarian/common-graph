package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordered chain of entities the {@link SceneResolver} consults when resolving
 * a variable reference (a {@code ?}-mode target) in a scene binding.
 *
 * <p>The chain runs from most-specific to most-general.  For a Window
 * rendering item X on session S held by librarian L (with a user U
 * eventually), the chain is:
 *
 * <pre>
 *   contextItem (= X) → user → session → librarian
 * </pre>
 *
 * <p>The first chain entry whose manifest-record carries a binding with the
 * looked-up role wins; the binding's target is returned.  If no entry matches,
 * the lookup returns {@link Optional#empty()} and the resolver leaves the
 * original {@code ?}-mode target in place (unresolved — surfacing the missing
 * binding loud-but-rendering).
 *
 * <p>Variables are just bindings whose role IS the variable sememe.  No
 * CONFIG wrapper; no separate registry.  The chain walks records' top-level
 * bindings by role with no qualifiers — variables that need qualifiers can
 * use {@link #lookupByCompoundKey(CompoundKey)} instead.
 */
public final class ContextChain {

    private final List<Item> entries;

    /**
     * Build a chain from a list of entries in resolution order (most-specific
     * first).  Null entries are dropped — convenient for callers that pass a
     * potentially-empty user slot.
     */
    public ContextChain(List<Item> entries) {
        Objects.requireNonNull(entries, "entries");
        List<Item> filtered = new java.util.ArrayList<>(entries.size());
        for (Item item : entries) {
            if (item != null) filtered.add(item);
        }
        this.entries = List.copyOf(filtered);
    }

    /** The entries in resolution order; most-specific first.  Read-only view. */
    public List<Item> entries() {
        return entries;
    }

    /**
     * Walk the chain looking for a record binding whose role is {@code role}
     * and which has no qualifiers.  Returns the binding's target on the first
     * match; empty when no entry has the binding.
     */
    public Optional<Object> lookupByRole(ItemRef role) {
        Objects.requireNonNull(role, "role");
        return lookupByCompoundKey(CompoundKey.of(role));
    }

    /**
     * Walk the chain looking for a record binding whose compound key
     * (role + qualifiers) matches {@code key}.  Returns the binding's target
     * on the first match.
     */
    public Optional<Object> lookupByCompoundKey(CompoundKey key) {
        Objects.requireNonNull(key, "key");
        for (Item item : entries) {
            Manifest manifest = item.current();
            if (manifest == null) continue;
            for (Record record : manifest.records()) {
                Optional<Binding> hit = record.binding(key);
                if (hit.isPresent()) {
                    return Optional.ofNullable(hit.get().target());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Collect every {@link SceneVocabulary.Style Style} record binding seen
     * along the chain.  Returns the targets (the style bodies themselves), in
     * chain-order (most-specific first) and within each entry, in their
     * binding-index order.  The resolver uses this list to apply cascading
     * style rules to the scene tree.
     */
    public List<Body> collectStyles() {
        CompoundKey styleKey = CompoundKey.of(ItemRef.iid(SceneVocabulary.Style.KEY));
        List<Body> out = new ArrayList<>();
        for (Item item : entries) {
            Manifest manifest = item.current();
            if (manifest == null) continue;
            for (Record record : manifest.records()) {
                List<Binding> matches = new ArrayList<>(record.bindings(styleKey));
                matches.sort((a, b) -> Long.compare(
                        a.index() == null ? 0L : a.index(),
                        b.index() == null ? 0L : b.index()));
                for (Binding b : matches) {
                    if (b.target() instanceof Body body) out.add(body);
                }
            }
        }
        return out;
    }
}
