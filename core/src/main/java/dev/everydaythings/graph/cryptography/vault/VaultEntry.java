package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.item.BodyBinder;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * VaultEntry — the base class for archetype-typed records stored in a vault.
 *
 * <p>Sibling to {@link dev.everydaythings.graph.item.Item Item} but
 * structurally simpler.  VaultEntry shares Item's archetype machinery
 * ({@code @Seed.Item} declares the entry kind; {@code @Seed.Property}
 * instance fields are populated from the body's bindings via
 * {@link BodyBinder}), but vault entries do NOT live in the content-addressed
 * CG store, do NOT carry their own IID, are NOT signed as items, and have
 * no manifest of accumulated frames.  The vault is a separate data plane
 * optimized for secret material.
 *
 * <p>Each entry has:
 * <ul>
 *   <li>An {@link EntryId} — stable identifier within the owning vault,
 *       opaque, vault-minted, stable across version-chained updates.</li>
 *   <li>An optional {@code theme} ({@link ItemRef}) — the context this
 *       entry is about (a service IID for an authentication entry, a peer
 *       IID for a conversation, {@code null} for the root identity).</li>
 *   <li>A {@link Body} carrying the entry's bindings — both the declared
 *       fields (mapped to typed Java fields via {@code @Seed.Property})
 *       and arbitrary free-form bindings the caller has attached.</li>
 * </ul>
 *
 * <p>Concrete entry archetypes extend this class:
 * <pre>{@code
 * @Seed.Item(key = Identity.KEY)
 * public final class Identity extends VaultEntry {
 *     @Seed.Property(role = ...) SigningKey currentSigning;
 *     @Seed.Property(role = ...) SigningKey nextSigning;
 *     // ...
 * }
 * }</pre>
 *
 * <p>Typed key-handle fields (such as {@link SigningKey} /
 * {@link KeyAgreementKey}) carry only public material plus an operation
 * lambda that routes to the vault.  Private bytes never appear in entry
 * state, by construction.
 */
public abstract class VaultEntry {

    /** Stable identifier within the owning vault. */
    protected final EntryId id;

    /**
     * The context this entry is about, or {@code null} when the entry IS the
     * subject (root identity).  An authentication entry's theme is the
     * service's IID; a conversation entry's theme is the peer's IID.
     */
    protected final ItemRef theme;

    /**
     * The underlying body carrying all bindings — both declared
     * ({@code @Seed.Property} fields) and free-form.  Set by
     * {@link #bindBody(Body)} during hydration.
     */
    protected Body body;

    /**
     * Construct an empty entry.  The vault calls {@link #bindBody(Body)}
     * after construction to populate fields from stored bindings.
     */
    protected VaultEntry(EntryId id, ItemRef theme) {
        this.id = Objects.requireNonNull(id, "id");
        this.theme = theme;
    }

    public final EntryId id() {
        return id;
    }

    /** The entry's context (service, peer, principal), or empty for root identity. */
    public final Optional<ItemRef> theme() {
        return Optional.ofNullable(theme);
    }

    /** The full body carrying all bindings. */
    public final Body body() {
        return body;
    }

    /**
     * The archetype IID for this entry's class.  Subclasses override to
     * return the IID of their {@code @Seed.Item}-declared archetype.
     *
     * <p>This is parallel to {@link dev.everydaythings.graph.item.Item#archetype()},
     * but for the vault's data plane rather than the content-addressed
     * store.
     */
    public abstract ItemRef archetype();

    /**
     * Set this entry's body and hydrate {@code @Seed.Property} instance
     * fields from its bindings.  Called by the vault during entry
     * construction or load.
     */
    public void bindBody(Body body) {
        this.body = Objects.requireNonNull(body, "body");
        BodyBinder.bind(this, body);
    }

    // ==================================================================================
    // Free-form binding access
    // ==================================================================================

    /**
     * Get a binding's target by its compound key, or empty if not present.
     *
     * <p>Used to read bindings that aren't part of the entry's declared
     * archetype schema — user-added notes, attachments, custom fields, etc.
     * Declared schema fields are exposed as typed Java fields on the
     * concrete entry subclass; this accessor reaches into the body for
     * everything else.
     */
    public final Optional<Object> binding(CompoundKey key) {
        if (body == null) return Optional.empty();
        for (Object node : body.entries()) {
            if (node instanceof Binding b && b.key().equals(key)) {
                return Optional.ofNullable(b.target());
            }
        }
        return Optional.empty();
    }

    /**
     * All bindings whose compound key starts with the given head sememe.
     * Useful for collecting multi-binding sets (recovery codes, retained
     * pre-keys, free-form notes).
     */
    public final List<Object> bindings(HashID head) {
        if (body == null) return List.of();
        return body.entries().stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .filter(b -> b.key().head().equals(head))
                .map(Binding::target)
                .toList();
    }
}
