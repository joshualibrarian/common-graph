package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.component.Component;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.VocabularySurface;

import java.util.*;
import java.util.stream.Stream;

/**
 * Collects and indexes available verbs for an item.
 *
 * <p>Vocabulary uses Sememe IDs for language-agnostic dispatch.
 *
 * <p>Lookup flow:
 * <pre>{@code
 * User types: "crear"  (Spanish for "create")
 *     ↓
 * TokenDictionary.lookup("crear")
 *     ↓
 * Returns: Posting(sememeId=cg.verb:create, ...)
 *     ↓
 * Vocabulary.lookup(cg.verb:create)
 *     ↓
 * Returns: VerbEntry(method=actionCreate, ...)
 *     ↓
 * Invoke: target.actionCreate(ctx)
 * }</pre>
 *
 * <p>Verbs are collected from two sources:
 * <ol>
 *   <li><b>Item verbs</b> - from {@code @Verb} on the item class</li>
 *   <li><b>Component verbs</b> - from {@code @Verb} on component classes</li>
 * </ol>
 */
@Type(value = "cg:type/vocabulary", glyph = "📖", color = 0x64B4C8)
@Canonical.Canonization
@Scene(as = VocabularySurface.class)
public class Vocabulary implements Canonical, Component, Property, Iterable<VerbEntry> {

    // ==================================================================================
    // Component Display
    // ==================================================================================

    @Override
    public String emoji() {
        return "📖";
    }

    @Override
    public String displayToken() {
        return "Vocabulary";
    }

    @Override
    public boolean isExpandable() {
        return !isEmpty();
    }

    @Override
    public String colorCategory() {
        return "vocabulary";
    }

    @Override
    public String displaySubtitle() {
        int count = size();
        return count + " verb" + (count == 1 ? "" : "s");
    }

    // ==================================================================================
    // Property Implementation
    // ==================================================================================

    @Override
    public Property property(String name) {
        // Look up verb by sememe key
        ItemID sememeId = ItemID.fromString(name);
        return lookup(sememeId)
                .filter(v -> v instanceof Property)
                .map(v -> (Property) v)
                .orElse(null);
    }

    @Override
    public Stream<String> properties() {
        return bySememeId.keySet().stream().map(ItemID::encodeText);
    }


    // ==================================================================================
    // Serialized Custom Layer
    // ==================================================================================

    /** Per-item verb aliases (e.g., "make" → CREATE). Serialized. */
    @Canon(order = 0)
    private List<VerbAlias> aliases = new ArrayList<>();

    /** Per-item parameter presets. Serialized. */
    @Canon(order = 1)
    private List<VerbPreset> presets = new ArrayList<>();

    // ==================================================================================
    // Transient Runtime Index (rebuilt from annotations, NOT serialized)
    // ==================================================================================

    /** Verbs indexed by Sememe ID (primary index) */
    private transient final Map<ItemID, VerbEntry> bySememeId = new LinkedHashMap<>();

    /** Verbs indexed by component handle (for component verbs) */
    private transient final Map<String, List<VerbEntry>> byComponentHandle = new LinkedHashMap<>();

    /** Expression macros: trigger token → expression string (rebuilt from EntryVocabulary). */
    private transient final Map<String, String> expressions = new LinkedHashMap<>();

    /** Local postings for component handles (transient, rebuilt from component entries). */
    private transient final Map<String, Posting> localPostings = new LinkedHashMap<>();

    // ==================================================================================
    // Add Methods
    // ==================================================================================

    /**
     * Add a verb entry to the vocabulary.
     *
     * @param entry The verb entry to add
     * @throws IllegalArgumentException if a verb with the same Sememe ID already exists
     */
    public void add(VerbEntry entry) {
        Objects.requireNonNull(entry, "entry");
        ItemID sememeId = entry.sememeId();

        if (bySememeId.containsKey(sememeId)) {
            VerbEntry existing = bySememeId.get(sememeId);
            if (existing.source() == entry.source()) {
                // Same source — allow override (item can redefine a verb)
            } else if (entry.source() == VerbSpec.VerbSource.COMPONENT) {
                // Component verb overrides item verb — component specialization wins
            } else {
                throw new IllegalArgumentException(
                        "Duplicate verb sememe: " + sememeId + " (existing: " + existing.source() +
                        ", new: " + entry.source() + ")");
            }
        }

        bySememeId.put(sememeId, entry);

        // Index by component handle if applicable
        if (entry.componentHandle() != null) {
            byComponentHandle
                    .computeIfAbsent(entry.componentHandle(), k -> new ArrayList<>())
                    .add(entry);
        }
    }

    /**
     * Add all verbs from a VerbSpec list (typically from ItemSchema).
     */
    public void addAll(List<VerbSpec> specs, Item owner) {
        for (VerbSpec spec : specs) {
            add(VerbEntry.itemVerb(spec, owner));
        }
    }

    // ==================================================================================
    // Lookup Methods
    // ==================================================================================

    /**
     * Look up a verb by its Sememe ID.
     *
     * @param sememeId The Sememe ID to look up
     * @return The verb entry, or empty if not found
     */
    public Optional<VerbEntry> lookup(ItemID sememeId) {
        return Optional.ofNullable(bySememeId.get(sememeId));
    }

    /**
     * Look up a verb by a token (any language).
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Local aliases (per-item custom mappings)</li>
     *   <li>TokenDictionary (global sememe resolution)</li>
     * </ol>
     *
     * @param token The token to look up (e.g., "create", "crear", "make")
     * @param librarian The librarian providing TokenDictionary access
     * @return The verb entry, or empty if not found
     */
    public Optional<VerbEntry> lookupToken(String token, Librarian librarian) {
        if (token == null) {
            return Optional.empty();
        }

        // 1. Check local aliases first
        for (VerbAlias alias : aliases) {
            if (alias.token().equalsIgnoreCase(token)) {
                VerbEntry entry = bySememeId.get(alias.sememeId());
                if (entry != null) return Optional.of(entry);
            }
        }

        // 2. Fall through to TokenDictionary
        if (librarian == null) {
            return Optional.empty();
        }

        var tokenDict = librarian.tokenIndex();
        if (tokenDict == null) {
            return Optional.empty();
        }

        // Find first posting that matches a verb in this vocabulary
        return tokenDict.lookup(token)
                .filter(posting -> bySememeId.containsKey(posting.target()))
                .findFirst()
                .map(posting -> bySememeId.get(posting.target()));
    }

    /**
     * Check if this vocabulary has a verb for the given Sememe.
     */
    public boolean hasVerb(ItemID sememeId) {
        return bySememeId.containsKey(sememeId);
    }

    // ==================================================================================
    // Filtering Methods
    // ==================================================================================

    /**
     * Get all verbs in this vocabulary.
     */
    public Stream<VerbEntry> all() {
        return bySememeId.values().stream();
    }

    /**
     * Get all item-level verbs.
     */
    public Stream<VerbEntry> itemVerbs() {
        return all().filter(v -> v.source() == VerbSpec.VerbSource.ITEM);
    }

    /**
     * Get all component-level verbs.
     */
    public Stream<VerbEntry> componentVerbs() {
        return all().filter(v -> v.source() == VerbSpec.VerbSource.COMPONENT);
    }

    /**
     * Get verbs for a specific component.
     */
    public Stream<VerbEntry> verbsFor(String componentHandle) {
        List<VerbEntry> entries = byComponentHandle.get(componentHandle);
        return entries != null ? entries.stream() : Stream.empty();
    }

    // ==================================================================================
    // Utility Methods
    // ==================================================================================

    @Override
    public Iterator<VerbEntry> iterator() {
        return bySememeId.values().iterator();
    }

    public int size() {
        return bySememeId.size();
    }

    public boolean isEmpty() {
        return bySememeId.isEmpty();
    }

    /**
     * Clear runtime verb maps (preserves custom layer: aliases and presets).
     *
     * <p>Called during vocabulary regeneration — annotation-derived verbs are
     * rebuilt but user customizations survive.
     */
    public void clear() {
        bySememeId.clear();
        byComponentHandle.clear();
        expressions.clear();
        localPostings.clear();
    }

    /**
     * Clear everything including the custom layer.
     */
    public void clearAll() {
        clear();
        aliases.clear();
        presets.clear();
    }

    // ==================================================================================
    // Custom Layer (Aliases & Presets)
    // ==================================================================================

    public List<VerbAlias> aliases() {
        return Collections.unmodifiableList(aliases);
    }

    public List<VerbPreset> presets() {
        return Collections.unmodifiableList(presets);
    }

    public void addAlias(String token, ItemID sememeId) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(sememeId, "sememeId");
        // Remove existing alias for same token
        aliases.removeIf(a -> a.token().equalsIgnoreCase(token));
        aliases.add(new VerbAlias(token, sememeId));
    }

    public void removeAlias(String token) {
        aliases.removeIf(a -> a.token().equalsIgnoreCase(token));
    }

    public void addPreset(VerbPreset preset) {
        Objects.requireNonNull(preset, "preset");
        presets.add(preset);
    }

    /**
     * Register an expression macro (trigger token → expression string).
     */
    public void addExpression(String token, String expression) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(expression, "expression");
        expressions.put(token.toLowerCase(), expression);
    }

    /**
     * Look up an expression macro by trigger token.
     */
    public Optional<String> lookupExpression(String token) {
        if (token == null) return Optional.empty();
        return Optional.ofNullable(expressions.get(token.toLowerCase()));
    }

    /**
     * Whether this vocabulary has any user customizations (aliases or presets).
     */
    public boolean hasCustomizations() {
        return !aliases.isEmpty() || !presets.isEmpty();
    }

    // ==================================================================================
    // Local Postings (component handles, transient)
    // ==================================================================================

    /**
     * Register a local posting for a component handle.
     *
     * <p>These are transient entries rebuilt from component entries on the item.
     * They provide completions and token resolution for handles like "x", "y",
     * "chess" — anything added via {@code addComponent()}.
     */
    public void addLocalPosting(Posting posting) {
        localPostings.put(posting.token().toLowerCase(), posting);
    }

    /**
     * Remove a local posting by token.
     */
    public void removeLocalPosting(String token) {
        localPostings.remove(token.toLowerCase());
    }

    /**
     * Find local postings matching a prefix.
     */
    public List<Posting> prefixMatch(String prefix) {
        String lower = prefix.toLowerCase();
        return localPostings.entrySet().stream()
                .filter(e -> e.getKey().startsWith(lower))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Find exact local posting match.
     */
    public Optional<Posting> exactMatch(String token) {
        return Optional.ofNullable(localPostings.get(token.toLowerCase()));
    }

    // ==================================================================================
    // Merge Support
    // ==================================================================================

    /**
     * Merge two vocabularies into a new vocabulary.
     *
     * <p>The second vocabulary takes precedence on conflicts.
     */
    public static Vocabulary merge(Vocabulary base, Vocabulary overlay) {
        Vocabulary merged = new Vocabulary();

        if (base != null) {
            for (VerbEntry entry : base) {
                merged.add(entry);
            }
            merged.aliases.addAll(base.aliases);
            merged.presets.addAll(base.presets);
        }

        if (overlay != null) {
            for (VerbEntry entry : overlay) {
                // Remove existing if any, then add
                merged.bySememeId.remove(entry.sememeId());
                merged.add(entry);
            }
            // Overlay aliases replace base aliases with same token
            for (VerbAlias alias : overlay.aliases) {
                merged.aliases.removeIf(a -> a.token().equalsIgnoreCase(alias.token()));
                merged.aliases.add(alias);
            }
            merged.presets.addAll(overlay.presets);
        }

        return merged;
    }

    @Override
    public String toString() {
        return "Vocabulary[" + size() + " verbs]";
    }
}
