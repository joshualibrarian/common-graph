package dev.everydaythings.graph.value.address;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Email-style address space: {@code local@domain}.
 *
 * <p>Format: {@code localpart@domain.tld}
 * <ul>
 *   <li>localpart - the local identifier (before @)</li>
 *   <li>domain - the domain name (after @)</li>
 * </ul>
 *
 * <p>Authority model: Domain owners control their namespace.
 * Resolution requires contacting the domain to resolve the local part.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code alice@example.com}</li>
 *   <li>{@code bob@mail.example.org}</li>
 *   <li>{@code support@company.co.uk}</li>
 * </ul>
 */
@Type(value = AtDomain.KEY, glyph = "📧")
public class AtDomain extends AddressSpace {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg.address:at-domain";


    /** The address space item - use for predicates and type references. */
    public static final AtDomain ITEM = new AtDomain(KEY, "Email Address");

    // ==================================================================================
    // SYNTAX
    // ==================================================================================

    private static final Pattern PATTERN = Pattern.compile(
            "^([^@\\s]+)@([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+)$"
    );

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Type seed constructor - creates a minimal AtDomain for use as type seed.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected AtDomain(ItemID typeId) {
        super(typeId);
    }

    private AtDomain(String key, String name) {
        super(key, name);
    }

    /**
     * Hydration constructor - reconstructs an AtDomain from a stored manifest.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    private AtDomain(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // ADDRESS SPACE IMPLEMENTATION
    // ==================================================================================

    @Override
    public Pattern syntaxPattern() {
        return PATTERN;
    }

    @Override
    public Optional<ParsedAddress> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Matcher m = PATTERN.matcher(raw.trim());
        if (!m.matches()) return Optional.empty();
        return Optional.of(new Parsed(m.group(1), m.group(2)));
    }

    @Override
    public int score(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        Matcher m = PATTERN.matcher(raw.trim());
        if (!m.matches()) return -1;
        // Score based on structure: more domain depth = higher confidence
        int dots = (int) raw.chars().filter(ch -> ch == '.').count();
        int localLen = m.group(1).length();
        return 10 + dots * 2 + Math.min(5, localLen);
    }

    // ==================================================================================
    // PARSED ADDRESS
    // ==================================================================================

    /**
     * A parsed at-domain address with local and domain parts.
     */
    public record Parsed(String local, String domain) implements ParsedAddress {

        public Parsed {
            Objects.requireNonNull(local, "local");
            Objects.requireNonNull(domain, "domain");
        }

        @Override
        public AddressSpace space() {
            return ITEM;
        }

        @Override
        public String raw() {
            return local + "@" + domain;
        }

        @Override
        public String canonical() {
            // Normalize: lowercase domain (local part is case-sensitive per RFC)
            return local + "@" + domain.toLowerCase();
        }

        /**
         * Get the top-level domain.
         */
        public String tld() {
            int lastDot = domain.lastIndexOf('.');
            return lastDot >= 0 ? domain.substring(lastDot + 1) : domain;
        }
    }
}
