package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.Locale;

/**
 * A natural language with its lexicon.
 *
 * <p>Language items contain:
 * <ul>
 *   <li>Language code (ISO 639-3, 3 letters)</li>
 *   <li>Lexicon - word→sememe mappings for this language</li>
 * </ul>
 *
 * <p>All ~7,000 ISO 639-3 languages are seeded at bootstrap as Language items
 * with deterministic IIDs derived from {@code "cg:language/<code>"}. Subclasses
 * (e.g., English) can add language-specific import logic.
 */
@Type(value = Language.KEY, glyph = "🗣️", color = 0xE08050)
public class Language extends Item {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/language";

    /** The English Language Item IID — used to scope seed English lexemes. */
    public static final ItemID ENGLISH = ItemID.fromString("cg:language/eng");

    // ==================================================================================
    // FIELDS
    // ==================================================================================

    /** ISO 639 language code (2 or 3 letter). */
    @Getter
    @ContentField(handleKey = "code")
    protected String languageCode;

    /** The lexicon for this language. */
    @Getter
    @ContentField
    protected Lexicon lexicon;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create a language from a Java Locale.
     *
     * @param librarian The librarian for storage
     * @param locale    The Java locale for this language
     */
    public Language(Librarian librarian, Locale locale) {
        super(librarian);
        this.languageCode = locale.getISO3Language();  // Use ISO 639-3 (3-letter)
        this.lexicon = Lexicon.forLanguage(this.iid());
    }

    /**
     * Type seed constructor - creates a minimal Language for use as type seed.
     *
     * <p>Used by SeedStore to create type items. Does NOT create a Lexicon
     * since type seeds are just markers, not functional language instances.
     *
     * @param iid The type's ItemID
     */
    public Language(ItemID iid) {
        super(iid);
        // Type seeds don't have content - just the type marker
    }

    /**
     * Create a language with specific IID and language code.
     *
     * <p>Used for seeding language items from ISO 639-3 codes.
     * Does NOT create a Lexicon — seed items are minimal markers.
     * The Lexicon is created when the language is hydrated or used functionally.
     *
     * @param iid          The item ID (e.g., ItemID.fromString("cg:language/eng"))
     * @param languageCode The ISO 639-3 code (e.g., "eng")
     */
    public Language(ItemID iid, String languageCode) {
        super(iid);
        this.languageCode = languageCode;
        // No lexicon for seed items — avoids encoding cost and IllegalStateException
        // since Lexicon is not @Type/Canonical/simple-serializable
    }

    /**
     * Create a language with specific IID (for seed items with content).
     *
     * @param iid    The item ID
     * @param locale The Java locale
     */
    public Language(ItemID iid, Locale locale) {
        super(iid);
        this.languageCode = locale.getISO3Language();
        this.lexicon = Lexicon.forLanguage(iid);
    }

    /**
     * Hydration constructor - reconstructs a Language from a stored manifest.
     *
     * <p>Fields are bound via reflection in the base class hydrate() method.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    protected Language(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
    }

    // ==================================================================================
    // STATIC HELPERS
    // ==================================================================================

    /**
     * Compute the deterministic IID for a language from its ISO 639-3 code.
     *
     * @param iso639_3 The 3-letter language code (e.g., "eng", "spa", "jpn")
     * @return The deterministic ItemID
     */
    public static ItemID iidFor(String iso639_3) {
        return ItemID.fromString("cg:language/" + iso639_3);
    }
}
