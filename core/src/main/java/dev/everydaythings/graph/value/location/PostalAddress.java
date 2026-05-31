package dev.everydaythings.graph.value.location;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.quality.LocationVocabulary;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PostalAddress — a compound Value carrying the parts of a postal address.
 * Each part is an optional binding on a role sememe from
 * {@link LocationVocabulary}.
 *
 * <p>Use whichever parts apply.  Western addresses use Street + Locality +
 * Region + PostalCode + Country.  Apartments and units add ExtendedAddress.
 * PO Boxes use POBox in place of (or alongside) Street.  Cultures with
 * different address shapes populate the appropriate subset.
 *
 * <p>Content-addressed: two PostalAddresses with identical bindings produce
 * identical CIDs.  Identical mailing addresses dedup naturally.
 *
 * <p>Like {@code Name}, this is a structured Value, not an Identifier.
 * Rendering a PostalAddress to text is locale-dependent (line order, where
 * the postal code goes, country abbreviations), so there's no single
 * canonical text form to expose via {@code @Encode}.
 */
@Seed.Item(key = PostalAddress.KEY, head = Value.KEY)
@Seed.Gloss(english = "a compound value carrying the parts of a postal address "
                   + "(street, locality, region, postal code, country, ...); cultures "
                   + "use whichever subset applies")
@Seed.Lexeme(english = {"postal address", "mailing address"}, pos = PartOfSpeech.Noun.KEY)
public final class PostalAddress extends Value {

    public static final String KEY = "cg.archetype:postal-address";

    private PostalAddress(List<Binding> bindings) {
        super(ItemRef.iid(KEY), bindings);
    }

    // ==================================================================================
    // Construction
    // ==================================================================================

    /** Fluent builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Typed view over an existing Body whose head is the PostalAddress
     * archetype.
     */
    public static PostalAddress from(dev.everydaythings.graph.datum.Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof PostalAddress addr) return addr;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the PostalAddress archetype: " + body.headRef());
        }
        return new PostalAddress(new ArrayList<>(body.bindings()));
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    public Optional<String> street()          { return part(LocationVocabulary.Street.KEY); }
    public Optional<String> extendedAddress() { return part(LocationVocabulary.ExtendedAddress.KEY); }
    public Optional<String> poBox()           { return part(LocationVocabulary.POBox.KEY); }
    public Optional<String> locality()        { return part(LocationVocabulary.Locality.KEY); }
    public Optional<String> region()          { return part(LocationVocabulary.Region.KEY); }
    public Optional<String> postalCode()      { return part(LocationVocabulary.PostalCode.KEY); }
    public Optional<String> country()         { return part(LocationVocabulary.Country.KEY); }

    private Optional<String> part(String roleKey) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : bindings()) {
            if (role.equals(b.role())
                    && b.qualifiers().isEmpty()
                    && b.target() instanceof String s) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    /** Fluent builder.  Null and empty parts are silently dropped. */
    public static final class Builder {
        private final List<Binding> bindings = new ArrayList<>();

        public Builder street(String text)          { return part(LocationVocabulary.Street.KEY, text); }
        public Builder extendedAddress(String text) { return part(LocationVocabulary.ExtendedAddress.KEY, text); }
        public Builder poBox(String text)           { return part(LocationVocabulary.POBox.KEY, text); }
        public Builder locality(String text)        { return part(LocationVocabulary.Locality.KEY, text); }
        public Builder region(String text)          { return part(LocationVocabulary.Region.KEY, text); }
        public Builder postalCode(String text)      { return part(LocationVocabulary.PostalCode.KEY, text); }
        public Builder country(String text)         { return part(LocationVocabulary.Country.KEY, text); }

        /** Add a custom part by role key — escape hatch for culture-specific parts. */
        public Builder part(String roleKey, String text) {
            if (text == null || text.isEmpty()) return this;
            bindings.add(Binding.literal(ItemRef.iid(roleKey), text));
            return this;
        }

        public PostalAddress build() {
            return new PostalAddress(new ArrayList<>(bindings));
        }
    }
}
