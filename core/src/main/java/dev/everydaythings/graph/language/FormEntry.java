package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;

import java.util.*;

/**
 * An irregular inflected form of a word, keyed by a set of grammatical features.
 *
 * <p>A FormEntry maps a combination of {@link GrammaticalFeature} sememes to
 * a surface form. For example, the English verb "run" has these overrides:
 * <ul>
 *   <li>{@code {PAST} → "ran"}</li>
 *   <li>{@code {PARTICIPLE, PAST} → "run"}</li>
 *   <li>{@code {PARTICIPLE, PRESENT} → "running"}</li>
 * </ul>
 *
 * <p>Regular forms (like "runs" for {THIRD_PERSON, SINGULAR, PRESENT}) are
 * NOT stored — the {@link Language} subclass computes those algorithmically.
 * Only irregular forms that differ from the regular rule need entries.
 *
 * <p>Features are stored as a sorted list of {@link ItemID}s referencing
 * {@link GrammaticalFeature} sememes, ensuring deterministic CBOR encoding.
 *
 * @see Lexeme
 * @see GrammaticalFeature
 */
@Getter
@Canonical.Canonization
public class FormEntry implements Canonical {

    /** Sorted list of feature IIDs (deterministic for hashing). */
    @Canon(order = 0)
    private List<ItemID> features;

    /** The inflected surface form. */
    @Canon(order = 1)
    private String form;

    /** No-arg constructor for Canonical decoding. */
    @SuppressWarnings("unused")
    private FormEntry() {}

    public FormEntry(List<ItemID> features, String form) {
        this.features = List.copyOf(features);
        this.form = Objects.requireNonNull(form);
    }

    /**
     * Create from a set of features (sorted automatically for deterministic encoding).
     */
    public static FormEntry of(Set<ItemID> features, String form) {
        List<ItemID> sorted = new ArrayList<>(features);
        sorted.sort(Comparator.comparing(ItemID::encodeText));
        return new FormEntry(sorted, form);
    }

    /**
     * Create from feature sememe instances.
     */
    public static FormEntry of(String form, GrammaticalFeature... features) {
        Set<ItemID> featureIds = new HashSet<>();
        for (GrammaticalFeature f : features) {
            featureIds.add(f.iid());
        }
        return of(featureIds, form);
    }

    /**
     * Get the features as a set for matching.
     */
    public Set<ItemID> featureSet() {
        return new HashSet<>(features);
    }

    /**
     * Check if this entry matches the given feature set.
     */
    public boolean matches(Set<ItemID> query) {
        return featureSet().equals(query);
    }

    @Override
    public String toString() {
        return "FormEntry[" + features + " → " + form + "]";
    }
}
