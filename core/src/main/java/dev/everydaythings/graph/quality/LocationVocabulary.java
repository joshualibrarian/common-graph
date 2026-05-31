package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.PartOfSpeech;

/**
 * Location vocabulary — the {@link Place} meta-archetype and the sememes
 * naming kinds of places (dwelling, workplace, birthplace, ...) and parts of
 * an address (street, locality, region, ...) or a geographic coordinate
 * (latitude, longitude, altitude).
 *
 * <h3>Place sememes</h3>
 *
 * <p>Anything whose underlying concept is itself a place gets {@code head =
 * Place}.  This includes the abstract "address" concept ({@link ReachableAt}),
 * the dwelling and workplace concepts ({@link Home}, {@link Workplace}), the
 * specialized address sub-concepts ({@link MailingAddress}, {@link
 * Headquarters}), biographical places ({@link Birthplace}), and the
 * place-shaped parts of an address ({@link Locality}, {@link Region},
 * {@link Country}, {@link POBox}, {@link ExtendedAddress}).
 *
 * <p>A place sememe carries its meaning regardless of where it appears in a
 * frame.  {@code Person → [Home] → PostalAddress} uses Home as predicate;
 * {@code Person → [REACHABLE_AT [Home]] → PhoneNumber} uses Home as
 * qualifier.  Same sememe, different frame positions.
 *
 * <h3>Plain address / coordinate parts</h3>
 *
 * <p>Sememes naming labels or codes that aren't themselves places — {@link
 * Street} (the street-name label), {@link PostalCode}, {@link Latitude},
 * {@link Longitude}, {@link Altitude} — default to {@code head = Archetype}.
 *
 * <h3>Cultural flexibility</h3>
 *
 * <p>Cultures with different address formats use a different subset of the
 * binding roles inside a {@code PostalAddress}.  Japanese addresses sequence
 * locality before street (rendering concern, not data); Indian addresses use
 * distinct locality / sub-locality bindings; UK addresses use
 * {@link ExtendedAddress} for county.  Same data shape, different bindings
 * populated.
 */
public final class LocationVocabulary {

    private LocationVocabulary() {}

    // ==================================================================================
    // Meta-archetype
    // ==================================================================================

    /**
     * Place — the archetype of kinds-of-place sememes (dwelling, workplace,
     * headquarters, birthplace, ...).  Each place sememe declares this as
     * its head.
     *
     * <p>Place sememes carry their meaning regardless of where they appear
     * in a frame: as the predicate of an assertion, as a qualifier on a
     * binding, or as a binding role inside a structured value.
     */
    @Seed.Item(key = Place.KEY)
    @Seed.Gloss(english = "the archetype of kinds-of-place sememes (dwelling, workplace, "
                       + "birthplace, headquarters, locality, region, ...)")
    @Seed.Lexeme(english = "place", pos = PartOfSpeech.Noun.KEY)
    public static final class Place {
        public static final String KEY = "cg.archetype:place";
        private Place() {}
    }

    // ==================================================================================
    // Place sememes — the "address" concept and its specializations.
    // ==================================================================================

    /**
     * ReachableAt — the abstract "address" concept: a place where a subject
     * can be found or communicated with.
     *
     * <p>In head-of-frame position, it relates a subject to a typed
     * {@code Identifier} (EmailAddress, PhoneNumber, URL, PostalAddress):
     * {@code Person → [ReachableAt] → EmailAddress("alice@example.com")}.
     * The channel is determined by the target's type; the same predicate
     * covers email, phone, fax, web, and postal mail.  Context qualifiers
     * (Home, Workplace, Mobile, Fax, Preferred) narrow which channel.
     *
     * <p>Grounded in OEWN synset oewn-08508037-n (CILI {@code i81677}):
     * "the place where a person or organization can be found or communicated
     * with" (address).
     */
    @Seed.Item(key = ReachableAt.KEY, head = Place.KEY)
    @Seed.Cili("i81677")
    @Seed.Gloss(english = "the place where a person or organization can be found or "
                       + "communicated with")
    @Seed.Lexeme(english = "address", pos = PartOfSpeech.Noun.KEY)
    public static final class ReachableAt {
        public static final String KEY = "cg.sememe:address";
        private ReachableAt() {}
    }

    /**
     * Home — a dwelling; the place where someone lives.
     *
     * <p>As predicate: {@code Person → [Home] → PostalAddress}.  As
     * qualifier: {@code Person → [REACHABLE_AT [Home]] → PhoneNumber} (the
     * home phone).
     *
     * <p>Grounded in OEWN synset oewn-03264208-n (CILI {@code i53274}):
     * "housing that someone is living in" (dwelling, home, domicile, abode,
     * habitation).
     */
    @Seed.Item(key = Home.KEY, head = Place.KEY)
    @Seed.Cili("i53274")
    @Seed.Gloss(english = "housing that someone is living in")
    @Seed.Lexeme(english = {"home", "dwelling", "domicile", "abode", "habitation"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class Home {
        public static final String KEY = "cg.sememe:dwelling";
        private Home() {}
    }

    /**
     * Workplace — a place where work is done.
     *
     * <p>As predicate: {@code Person → [Workplace] → PostalAddress}.  As
     * qualifier: {@code Person → [REACHABLE_AT [Workplace]] → PhoneNumber}
     * (the work phone).
     *
     * <p>Grounded in OEWN synset oewn-04609402-n (CILI {@code i61259}):
     * "a place where work is done" (workplace, work).
     */
    @Seed.Item(key = Workplace.KEY, head = Place.KEY)
    @Seed.Cili("i61259")
    @Seed.Gloss(english = "a place where work is done")
    @Seed.Lexeme(english = {"workplace", "work"}, pos = PartOfSpeech.Noun.KEY)
    public static final class Workplace {
        public static final String KEY = "cg.sememe:workplace";
        private Workplace() {}
    }

    /**
     * MailingAddress — the address where a subject receives mail.  Distinct
     * from where the subject lives or works: a mailing address is often a
     * PO Box, a lawyer's office, an employer's address, or any forwarding
     * arrangement.
     *
     * <p>Grounded in OEWN synset oewn-08508255-n (CILI {@code i81678}):
     * "the address where a person or organization can be communicated with"
     * (mailing address) — a hyponym of {@link ReachableAt}'s grounding.
     */
    @Seed.Item(key = MailingAddress.KEY, head = Place.KEY)
    @Seed.Cili("i81678")
    @Seed.Gloss(english = "the address where a person or organization can be communicated "
                       + "with by mail")
    @Seed.Lexeme(english = "mailing address", pos = PartOfSpeech.Noun.KEY)
    public static final class MailingAddress {
        public static final String KEY = "cg.sememe:mailing-address";
        private MailingAddress() {}
    }

    /**
     * Birthplace — where a person was born.  Biographical, not contact info.
     *
     * <p>Grounded in OEWN synset oewn-08527371-n (CILI {@code i81772}):
     * "the place where someone was born" (birthplace, place of birth).
     */
    @Seed.Item(key = Birthplace.KEY, head = Place.KEY)
    @Seed.Cili("i81772")
    @Seed.Gloss(english = "the place where someone was born")
    @Seed.Lexeme(english = {"birthplace", "place of birth"}, pos = PartOfSpeech.Noun.KEY)
    public static final class Birthplace {
        public static final String KEY = "cg.sememe:birthplace";
        private Birthplace() {}
    }

    /**
     * Headquarters — the primary administrative office of an organization.
     *
     * <p>Grounded in OEWN synset oewn-03509867-n (CILI {@code i54720}):
     * "the office that serves as the administrative center of an enterprise"
     * (headquarters, central office, main office, home office).
     */
    @Seed.Item(key = Headquarters.KEY, head = Place.KEY)
    @Seed.Cili("i54720")
    @Seed.Gloss(english = "the office that serves as the administrative center of an "
                       + "enterprise")
    @Seed.Lexeme(english = {"headquarters", "central office", "main office", "home office"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class Headquarters {
        public static final String KEY = "cg.sememe:headquarters";
        private Headquarters() {}
    }

    // ==================================================================================
    // Address-part sememes that name kinds of places (locality, region, country)
    // or sub-places (extended-address, PO box).
    // ==================================================================================

    /**
     * Extended address — secondary address info (suite, apt, floor, unit).
     * The apartment / suite IS a place (a sub-unit of a larger building).
     */
    @Seed.Item(key = ExtendedAddress.KEY, head = Place.KEY)
    @Seed.Gloss(english = "secondary address information such as suite, apartment, "
                       + "floor, or unit number")
    @Seed.Lexeme(english = {"extended address", "apartment", "suite", "unit"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class ExtendedAddress {
        public static final String KEY = "cg.sememe:extended-address";
        private ExtendedAddress() {}
    }

    /**
     * Post Office Box.  A slot at a post office — itself a small place.
     * When present, typically replaces the street address for delivery
     * purposes.
     */
    @Seed.Item(key = POBox.KEY, head = Place.KEY)
    @Seed.Gloss(english = "a Post Office Box, a numbered slot at a postal facility used "
                       + "in place of a street address for postal delivery")
    @Seed.Lexeme(english = {"post office box", "PO box"}, pos = PartOfSpeech.Noun.KEY)
    public static final class POBox {
        public static final String KEY = "cg.sememe:po-box";
        private POBox() {}
    }

    /** Locality — city, town, village, or other settlement. */
    @Seed.Item(key = Locality.KEY, head = Place.KEY)
    @Seed.Gloss(english = "a locality — a city, town, village, or other settlement")
    @Seed.Lexeme(english = {"locality", "city", "town", "village"}, pos = PartOfSpeech.Noun.KEY)
    public static final class Locality {
        public static final String KEY = "cg.sememe:locality";
        private Locality() {}
    }

    /** Region — state, province, prefecture, or equivalent sub-national division. */
    @Seed.Item(key = Region.KEY, head = Place.KEY)
    @Seed.Gloss(english = "a sub-national region — a state, province, prefecture, county, "
                       + "or equivalent administrative division")
    @Seed.Lexeme(english = {"region", "state", "province", "prefecture"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class Region {
        public static final String KEY = "cg.sememe:region";
        private Region() {}
    }

    /** Country.  A sovereign state. */
    @Seed.Item(key = Country.KEY, head = Place.KEY)
    @Seed.Gloss(english = "a country — a sovereign state or nation")
    @Seed.Lexeme(english = "country", pos = PartOfSpeech.Noun.KEY)
    public static final class Country {
        public static final String KEY = "cg.sememe:country";
        private Country() {}
    }

    // ==================================================================================
    // Address / coordinate parts that name labels or codes (not places themselves).
    // ==================================================================================

    /**
     * Street address line — the road name and house number portion of a
     * postal address.  Names a label, not a place; the road itself is a
     * different concept.
     */
    @Seed.Item(key = Street.KEY)
    @Seed.Gloss(english = "the street address line of a postal address, typically "
                       + "comprising house number and thoroughfare name")
    @Seed.Lexeme(english = {"street address", "street"}, pos = PartOfSpeech.Noun.KEY)
    public static final class Street {
        public static final String KEY = "cg.sememe:street-address";
        private Street() {}
    }

    /** Postal / ZIP code — a routing code, not a place. */
    @Seed.Item(key = PostalCode.KEY)
    @Seed.Gloss(english = "a postal code — a routing identifier used to direct mail to a "
                       + "particular area (ZIP code, postcode, PIN code)")
    @Seed.Lexeme(english = {"postal code", "ZIP code", "postcode"},
                 pos = PartOfSpeech.Noun.KEY)
    public static final class PostalCode {
        public static final String KEY = "cg.sememe:postal-code";
        private PostalCode() {}
    }

    /** Latitude, in decimal degrees (-90 to +90). */
    @Seed.Item(key = Latitude.KEY)
    @Seed.Gloss(english = "the latitude coordinate of a point on the Earth's surface, "
                       + "in decimal degrees north (positive) or south (negative) of the equator")
    @Seed.Lexeme(english = "latitude", pos = PartOfSpeech.Noun.KEY)
    public static final class Latitude {
        public static final String KEY = "cg.sememe:latitude";
        private Latitude() {}
    }

    /** Longitude, in decimal degrees (-180 to +180). */
    @Seed.Item(key = Longitude.KEY)
    @Seed.Gloss(english = "the longitude coordinate of a point on the Earth's surface, "
                       + "in decimal degrees east (positive) or west (negative) of the prime meridian")
    @Seed.Lexeme(english = "longitude", pos = PartOfSpeech.Noun.KEY)
    public static final class Longitude {
        public static final String KEY = "cg.sememe:longitude";
        private Longitude() {}
    }

    /** Altitude / elevation, typically in meters above mean sea level. */
    @Seed.Item(key = Altitude.KEY)
    @Seed.Gloss(english = "the altitude or elevation of a point, typically in meters above "
                       + "mean sea level")
    @Seed.Lexeme(english = {"altitude", "elevation"}, pos = PartOfSpeech.Noun.KEY)
    public static final class Altitude {
        public static final String KEY = "cg.sememe:altitude";
        private Altitude() {}
    }
}
