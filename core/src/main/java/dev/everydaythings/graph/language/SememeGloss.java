package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;

/**
 * A per-language gloss (definition) for a Sememe.
 *
 * <p>SememeGloss is a component attached to a Sememe Item, one per language.
 * Each language import adds its own gloss as a new component on the sememe,
 * creating a new version. This makes glosses:
 * <ul>
 *   <li>Versioned — each change is a new commit</li>
 *   <li>Per-language — "en" and "ja" glosses are separate components</li>
 *   <li>Revertable — can roll back a bad gloss edit</li>
 *   <li>Attributable — signed by whoever contributed it</li>
 * </ul>
 *
 * <p>The component handle is derived from the language code (e.g., "gloss/eng"),
 * so each language gets exactly one gloss slot on a sememe.
 */
@Implements(SememeGloss.KEY)
@ItemSeed(key = SememeGloss.KEY)
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
@Getter
public class SememeGloss implements Canonical {

    public static final String KEY = "cg.sememe:sememe-gloss";

    /** The language this gloss is in (e.g., Language.ENGLISH). */
    @Canon(order = 0)
    private final ItemID language;

    /** The gloss text (definition of the sememe in this language). */
    @Canon(order = 1)
    private final String text;

    public SememeGloss(ItemID language, String text) {
        this.language = language;
        this.text = text;
    }

    /** No-arg constructor for Canonical decoding. */
    @SuppressWarnings("unused")
    private SememeGloss() {
        this.language = null;
        this.text = null;
    }
}
