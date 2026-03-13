package dev.everydaythings.graph.value.address;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.ValueType;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An AddressSpace defines a namespace for addresses that resolve to Items or Signers.
 *
 * <p>AddressSpaces are both:
 * <ul>
 *   <li><b>ValueTypes</b> - addresses can be stored as typed literals</li>
 *   <li><b>Predicates</b> - used in relations: {@code subject -> AddressSpace -> "address-string"}</li>
 * </ul>
 *
 * <p>Each AddressSpace has:
 * <ul>
 *   <li><b>Syntax</b> - pattern for parsing/validating addresses</li>
 *   <li><b>Authority model</b> - who controls namespace segments</li>
 *   <li><b>Resolution</b> - how to find the target from an address</li>
 * </ul>
 *
 * <p>Concrete address spaces are discovered via {@code @Type} annotations:
 * <pre>{@code
 * @Type("cg.address:at-domain")
 * public class AtDomain extends AddressSpace { ... }
 * }</pre>
 *
 * <p>Example address spaces:
 * <ul>
 *   <li>{@code AtDomain} - email-style {@code local@domain}</li>
 *   <li>{@code Tilde} - path-style {@code ~host/path/to/item}</li>
 *   <li>{@code E164Phone} - phone numbers {@code +1-555-123-4567}</li>
 *   <li>{@code JavaClass} - Java FQCNs {@code com.example.MyClass}</li>
 * </ul>
 *
 * @see ValueType
 */
@Type(value = AddressSpace.KEY, glyph = "📍")
public abstract class AddressSpace extends ValueType {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/address-space";


    // ==================================================================================
    // CONSTRUCTOR
    // ==================================================================================

    /**
     * Type seed constructor - creates a minimal AddressSpace for use as type seed.
     *
     * <p>Used by SeedStore to create type items for address spaces.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected AddressSpace(ItemID typeId) {
        super(typeId);
    }

    /**
     * Create an AddressSpace with a canonical key and name.
     *
     * @param canonicalKey the unique key (e.g., "cg.address:at-domain")
     * @param name human-readable name (e.g., "Email Address")
     */
    protected AddressSpace(String canonicalKey, String name) {
        super(canonicalKey, name, null, null, null);
    }

    /**
     * Hydration constructor - reconstructs an AddressSpace from a stored manifest.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    protected AddressSpace(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // ABSTRACT METHODS - Subclasses must implement
    // ==================================================================================

    /**
     * The syntax pattern for addresses in this space.
     *
     * <p>Used for validation and parsing hints. May return null if
     * the syntax is too complex for a simple regex.
     */
    public abstract Pattern syntaxPattern();

    /**
     * Parse an address string in this space.
     *
     * @param raw the raw address string
     * @return the parsed address, or empty if invalid
     */
    public abstract Optional<ParsedAddress> parse(String raw);

    /**
     * Validate an address string without fully parsing it.
     *
     * @param raw the raw address string
     * @return true if the string is a valid address in this space
     */
    public boolean isValid(String raw) {
        if (raw == null || raw.isBlank()) return false;
        Pattern p = syntaxPattern();
        return p != null && p.matcher(raw).matches();
    }

    /**
     * Score how likely a raw string is to be an address in this space.
     *
     * <p>Used for "best guess" parsing when the address space is unknown.
     * Higher scores indicate higher confidence.
     *
     * @param raw the raw string to score
     * @return score (negative = not a match, higher = more confident)
     */
    public int score(String raw) {
        return isValid(raw) ? 10 : -1;
    }

    // ==================================================================================
    // PARSED ADDRESS
    // ==================================================================================

    /**
     * A parsed address with its structural components.
     *
     * <p>Each AddressSpace defines its own ParsedAddress structure
     * with relevant fields (local/domain, host/path, etc.).
     */
    public interface ParsedAddress {
        /**
         * The address space this address belongs to.
         */
        AddressSpace space();

        /**
         * The original raw string.
         */
        String raw();

        /**
         * Canonical string form (may differ from raw due to normalization).
         */
        String canonical();
    }

    // ==================================================================================
    // UTILITY
    // ==================================================================================

    /**
     * Try to parse an address, returning empty if invalid.
     * Alias for {@link #parse(String)} for fluent usage.
     */
    public Optional<ParsedAddress> tryParse(String raw) {
        return parse(raw);
    }

    /**
     * Parse an address, throwing if invalid.
     *
     * @param raw the raw address string
     * @return the parsed address
     * @throws IllegalArgumentException if the address is invalid
     */
    public ParsedAddress parseOrThrow(String raw) {
        return parse(raw).orElseThrow(() ->
                new IllegalArgumentException("Invalid " + name() + " address: " + raw));
    }
}
