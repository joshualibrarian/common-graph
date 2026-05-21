package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.SchemaVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.Bool;
import dev.everydaythings.graph.value.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@code @Seed.Property} on a static field whose value is a
 * {@link SchemaRef} (or {@link TypeRef}) flows through the new HashID family
 * cleanly: the binding's target on the manifest is the SchemaRef itself, not
 * a coerced ItemRef.  This is the mechanism operators will use to declare
 * {@code Returns = !Bool} etc.
 */
class ReturnsBindingTest {

    static Librarian lib;

    @BeforeAll
    static void bootstrap() {
        lib = Librarian.inMemory();
        lib.bootstrap();
    }

    /**
     * A test seed declaring an operator-style contract:
     *   Returns = !Bool      (a SchemaRef target — Bool is a Value archetype)
     *   InputType = ?Color   (a TypeRef target — would express input expectations later)
     */
    @Seed.Item(key = FixtureMatcherReturn.KEY)
    public static final class FixtureMatcherReturn {
        public static final String KEY = "cg.test:matcher-return-fixture";
        private FixtureMatcherReturn() {}

        @Seed.Property(role = SchemaVocabulary.Returns.KEY)
        static final SchemaRef returnType = SchemaRef.iid(Bool.KEY);

        @Seed.Property(role = SchemaVocabulary.Required.KEY)
        static final TypeRef inputType = TypeRef.iid(Color.KEY);
    }

    @Test
    @DisplayName("Returns = !Bool binding's target is a SchemaRef, not coerced to ItemRef")
    void schemaRefSurvivesAsBindingTarget() {
        Manifest m = manifestFor(ItemRef.iid(FixtureMatcherReturn.KEY));
        Optional<Binding> b = m.binding(CompoundKey.of(ItemRef.iid(SchemaVocabulary.Returns.KEY)));
        assertThat(b).isPresent();
        assertThat(b.get().target())
                .isInstanceOf(SchemaRef.class)
                .isEqualTo(SchemaRef.iid(Bool.KEY));
    }

    @Test
    @DisplayName("?Color binding's target is a TypeRef, preserved through SeedProcessor")
    void typeRefSurvivesAsBindingTarget() {
        Manifest m = manifestFor(ItemRef.iid(FixtureMatcherReturn.KEY));
        Optional<Binding> b = m.binding(CompoundKey.of(ItemRef.iid(SchemaVocabulary.Required.KEY)));
        assertThat(b).isPresent();
        assertThat(b.get().target())
                .isInstanceOf(TypeRef.class)
                .isEqualTo(TypeRef.iid(Color.KEY));
    }

    @Test
    @DisplayName("SchemaRef and underlying ItemRef share the same IID multihash")
    void schemaRefSharesIidWithItemRef() {
        SchemaRef sr = SchemaRef.iid(Bool.KEY);
        ItemRef ir = ItemRef.iid(Bool.KEY);
        assertThat(sr.multihash()).containsExactly(ir.multihash());
    }

    private Manifest manifestFor(ItemRef iid) {
        List<DatumRef> cids = lib.library().manifestCidsForItem(iid);
        assertThat(cids).as("no manifest persisted for %s", iid).isNotEmpty();
        return lib.fetchManifest(cids.get(0)).orElseThrow();
    }
}
