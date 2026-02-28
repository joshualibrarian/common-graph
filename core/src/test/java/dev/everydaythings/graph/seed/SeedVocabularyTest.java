package dev.everydaythings.graph.seed;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Link;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.TreeLink;
import dev.everydaythings.graph.item.component.ComponentEntry;
import dev.everydaythings.graph.item.component.SurfaceTemplateComponent;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.NounSememe;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.VerbSememe;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.mapdb.MapDBItemStore;
import dev.everydaythings.graph.library.SeedVocabulary;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.Dimension;
import dev.everydaythings.graph.value.Unit;
import dev.everydaythings.graph.value.ValueType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SeedVocabulary and @Item.Seed annotation.
 */
class SeedVocabularyTest {

    private static ItemStore store;

    @BeforeAll
    static void bootstrap() {
        store = MapDBItemStore.memory();
        SeedVocabulary.bootstrap(store);
    }

    // ==================================================================================
    // Helper Methods
    // ==================================================================================

    private boolean hasManifest(ItemID iid) {
        return store.manifests(iid).findFirst().isPresent();
    }

    private boolean hasImplementedByRelation(ItemID typeId) {
        return store.relations(typeId)
                .anyMatch(r -> r.predicate().equals(Sememe.IMPLEMENTED_BY.iid()));
    }

    private List<Manifest> allManifestsOfType(ItemID typeId) {
        List<Manifest> result = new ArrayList<>();
        for (Manifest m : store.manifests(null).toList()) {
            if (typeId.equals(m.type())) {
                result.add(m);
            }
        }
        return result;
    }

    // ==================================================================================
    // Tests
    // ==================================================================================

    @Test
    void bootstrapIncludesDimensions() {
        // Dimension type should have IMPLEMENTED_BY relation
        assertThat(hasImplementedByRelation(ItemID.fromString(Dimension.KEY))).isTrue();

        // Dimension seed instances should have manifests
        assertThat(hasManifest(Dimension.LENGTH.iid())).isTrue();
        assertThat(hasManifest(Dimension.TIME.iid())).isTrue();
        assertThat(hasManifest(Dimension.MASS.iid())).isTrue();
    }

    @Test
    void bootstrapIncludesSememes() {
        // Sememe is now abstract sealed — concrete subclass types get IMPLEMENTED_BY
        assertThat(hasImplementedByRelation(ItemID.fromString(VerbSememe.KEY))).isTrue();
        assertThat(hasImplementedByRelation(ItemID.fromString(NounSememe.KEY))).isTrue();

        // Core sememes should have manifests
        assertThat(hasManifest(Sememe.AUTHOR.iid())).isTrue();
        assertThat(hasManifest(Sememe.TITLE.iid())).isTrue();
        assertThat(hasManifest(Sememe.DESCRIPTION.iid())).isTrue();
    }

    @Test
    void seedItemsAreDiscoverableByType() {
        // All Dimension seeds
        var dimensions = allManifestsOfType(ItemID.fromString(Dimension.KEY));
        assertThat(dimensions).hasSizeGreaterThanOrEqualTo(7); // 7 SI base dimensions

        // Sememe seeds are now distributed across subclass types
        var verbSememes = allManifestsOfType(ItemID.fromString(VerbSememe.KEY));
        var nounSememes = allManifestsOfType(ItemID.fromString(NounSememe.KEY));
        int totalSememes = verbSememes.size() + nounSememes.size();
        assertThat(totalSememes).isGreaterThanOrEqualTo(8); // verbs + nouns from Sememe + Host
    }

    @Test
    void bootstrapIncludesUnits() {
        // Unit type should have IMPLEMENTED_BY relation
        assertThat(hasImplementedByRelation(ItemID.fromString(Unit.KEY))).isTrue();

        // Core units should have manifests
        assertThat(hasManifest(Unit.METER.iid())).isTrue();
        assertThat(hasManifest(Unit.SECOND.iid())).isTrue();
        assertThat(hasManifest(Unit.KILOGRAM.iid())).isTrue();
        assertThat(hasManifest(Unit.INCH.iid())).isTrue();

        // Derived units
        assertThat(hasManifest(Unit.NEWTON.iid())).isTrue();
        assertThat(hasManifest(Unit.JOULE.iid())).isTrue();
    }

    @Test
    void unitsHaveCorrectDimensions() {
        // Length units should have LENGTH dimension
        assertThat(Unit.METER.hasDimension(Dimension.LENGTH)).isTrue();
        assertThat(Unit.METER.hasDimension(Dimension.TIME)).isFalse();

        // Time units should have TIME dimension
        assertThat(Unit.SECOND.hasDimension(Dimension.TIME)).isTrue();
        assertThat(Unit.SECOND.hasDimension(Dimension.LENGTH)).isFalse();

        // Velocity has both LENGTH and TIME
        assertThat(Unit.METER_PER_SECOND.hasDimension(Dimension.LENGTH)).isTrue();
        assertThat(Unit.METER_PER_SECOND.hasDimension(Dimension.TIME)).isTrue();
        assertThat(Unit.METER_PER_SECOND.exponent(Dimension.LENGTH)).isEqualTo(1);
        assertThat(Unit.METER_PER_SECOND.exponent(Dimension.TIME)).isEqualTo(-1);
    }

    @Test
    void unitsCanBeConverted() {
        // 1000 mm = 1 m
        double meters = Unit.MILLIMETER.convert(1000, Unit.METER);
        assertThat(meters).isEqualTo(1.0);

        // 3600 seconds = 1 hour
        double hours = Unit.SECOND.convert(3600, Unit.HOUR);
        assertThat(hours).isEqualTo(1.0);
    }

    @Test
    void unitsAreDiscoverableByType() {
        var units = allManifestsOfType(ItemID.fromString(Unit.KEY));
        assertThat(units).hasSizeGreaterThanOrEqualTo(15); // length + time + mass + derived
    }

    @Test
    void bootstrapIncludesValueTypes() {
        // ValueType type should have IMPLEMENTED_BY relation
        assertThat(hasImplementedByRelation(ItemID.fromString(ValueType.KEY))).isTrue();

        // Core value types should have manifests
        assertThat(hasManifest(ValueType.BOOLEAN.iid())).isTrue();
        assertThat(hasManifest(ValueType.TEXT.iid())).isTrue();
        assertThat(hasManifest(ValueType.DECIMAL.iid())).isTrue();
        assertThat(hasManifest(ValueType.RATIONAL.iid())).isTrue();
        assertThat(hasManifest(ValueType.COUNT.iid())).isTrue();
        assertThat(hasManifest(ValueType.INTEGER.iid())).isTrue();
        assertThat(hasManifest(ValueType.ENDPOINT.iid())).isTrue();
        assertThat(hasManifest(ValueType.IP.iid())).isTrue();
        assertThat(hasManifest(ValueType.QUANTITY.iid())).isTrue();
    }

    @Test
    void valueTypesAreDiscoverableByType() {
        var valueTypes = allManifestsOfType(ItemID.fromString(ValueType.KEY));
        assertThat(valueTypes).hasSizeGreaterThanOrEqualTo(12);
    }

    @Test
    void literalTypeConstantsMatchValueTypeSeeds() {
        // Verify that Literal.TYPE_* constants resolve to actual ValueType seeds
        assertThat(Literal.TYPE_TEXT).isEqualTo(ValueType.TEXT.iid());
        assertThat(Literal.TYPE_BOOLEAN).isEqualTo(ValueType.BOOLEAN.iid());
        assertThat(Literal.TYPE_INTEGER).isEqualTo(ValueType.INTEGER.iid());
        assertThat(Literal.TYPE_INSTANT).isEqualTo(ValueType.INSTANT.iid());

        // Verify these seeds have manifests in the store
        assertThat(hasManifest(Literal.TYPE_TEXT)).isTrue();
        assertThat(hasManifest(Literal.TYPE_BOOLEAN)).isTrue();
        assertThat(hasManifest(Literal.TYPE_INTEGER)).isTrue();
        assertThat(hasManifest(Literal.TYPE_INSTANT)).isTrue();
    }

    @Test
    void typeItemsHaveSurfaceTemplateWithDisplayFields() {
        // Item type should have a unified surface template with display fields
        ItemID itemTypeId = ItemID.fromString(Item.KEY);
        Manifest manifest = store.manifests(itemTypeId).findFirst().orElse(null);
        assertThat(manifest).isNotNull();

        ComponentEntry surfaceEntry = manifest.components().stream()
                .filter(e -> e.handle().equals(SurfaceTemplateComponent.HANDLE))
                .findFirst()
                .orElse(null);
        assertThat(surfaceEntry).as("Item type should have surface template component").isNotNull();
        assertThat(surfaceEntry.payload().snapshotCid()).as("Surface template should have CID").isNotNull();

        // Content should be retrievable from store
        assertThat(store.content(surfaceEntry.payload().snapshotCid()))
                .as("Surface template content should be stored").isPresent();

        // Should NOT have a separate "display" handle
        HandleID displayHandle = HandleID.of("display");
        boolean hasDisplay = manifest.components().stream()
                .anyMatch(e -> e.handle().equals(displayHandle));
        assertThat(hasDisplay).as("Should not have a separate display component").isFalse();
    }

    @Test
    void bootstrapReturnsFullyPopulatedItems() {
        // bootstrap() should return the same items that were stored (with components)
        ItemStore testStore = MapDBItemStore.memory();
        List<Item> seedItems = SeedVocabulary.bootstrap(testStore);

        assertThat(seedItems).isNotEmpty();

        // Find the Item type seed (should have unified surface template)
        Item itemTypeSeed = seedItems.stream()
                .filter(i -> i.iid().equals(ItemID.fromString(Item.KEY)))
                .findFirst()
                .orElse(null);
        assertThat(itemTypeSeed).as("bootstrap() should return Item type seed").isNotNull();

        assertThat(itemTypeSeed.content().get(SurfaceTemplateComponent.HANDLE))
                .as("Returned seed should have surface template entry").isPresent();
    }

    @Test
    void cachedSeedItemsHaveComponents() {
        // Items cached via Librarian should have their components (not bare copies)
        Librarian librarian = Librarian.createInMemory();
        ItemID itemTypeId = ItemID.fromString(Item.KEY);

        // Get directly from cache (this is the path that was broken before)
        Item cached = librarian.library().getCached(itemTypeId).orElse(null);
        assertThat(cached).as("Item type should be in cache").isNotNull();

        assertThat(cached.content().get(SurfaceTemplateComponent.HANDLE))
                .as("Cached seed should have surface template entry").isPresent();
    }

    @Test
    void roundTripHydrationFromManifest() throws Exception {
        // Load Item type manifest from store and hydrate via constructor
        // (simulates the Librarian.get() -> hydrateItem() path, bypassing cache)
        Librarian librarian = Librarian.createInMemory();
        ItemID itemTypeId = ItemID.fromString(Item.KEY);

        // Get manifest from the librarian's own store (same store that has the content)
        ItemStore libStore = librarian.library().primaryStore().orElseThrow();
        Manifest manifest = libStore.manifests(itemTypeId).findFirst().orElse(null);
        assertThat(manifest).as("Item type manifest should exist in store").isNotNull();

        // Hydrate via the protected (Librarian, Manifest) constructor using reflection
        var ctor = Item.class.getDeclaredConstructor(Librarian.class, Manifest.class);
        ctor.setAccessible(true);
        Item item = (Item) ctor.newInstance(librarian, manifest);

        // Verify component entry exists
        assertThat(item.content().get(SurfaceTemplateComponent.HANDLE))
                .as("Should have surface template component entry").isPresent();

        // Verify live instance was hydrated (not just entry)
        assertThat(item.content().hasLive(SurfaceTemplateComponent.HANDLE))
                .as("Surface template should have live instance").isTrue();

        // Verify live instance is the correct type
        assertThat(item.content().getLive(SurfaceTemplateComponent.HANDLE))
                .isPresent()
                .get()
                .isInstanceOf(SurfaceTemplateComponent.class);

        // Verify display fields are populated on the hydrated component
        var stc = item.content().getLive(
                SurfaceTemplateComponent.HANDLE, SurfaceTemplateComponent.class).orElse(null);
        assertThat(stc).isNotNull();
        assertThat(stc.glyph()).as("Display glyph should survive round-trip").isNotNull();
        assertThat(stc.typeName()).as("Type name should survive round-trip").isNotNull();

        // Verify children(INSPECT) returns links for components
        List<Link> inspectChildren = item.children(TreeLink.ChildMode.INSPECT);
        assertThat(inspectChildren).as("Inspect mode should show component links").isNotEmpty();

        // Should include surface template link
        boolean hasSurface = inspectChildren.stream()
                .anyMatch(link -> link.path().map(p -> p.equals("/" + SurfaceTemplateComponent.HANDLE.encodeText())).orElse(false));
        assertThat(hasSurface).as("Inspect children should include surface template").isTrue();
    }
}
