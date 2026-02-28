package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.Type;
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
@Type(value = SememeGloss.KEY, glyph = "\uD83D\uDCD6")
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
@Getter
public class SememeGloss implements Canonical {

    public static final String KEY = "cg:type/sememe-gloss";

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

    /**
     * Compute the component handle key for a gloss in the given language.
     *
     * <p>Uses "gloss/" + language code to ensure one gloss per language per sememe.
     *
     * @param languageCode ISO 639-3 code (e.g., "eng")
     * @return handle key (e.g., "gloss/eng")
     */
    public static String handleKeyFor(String languageCode) {
        return "gloss/" + languageCode;
    }
}
