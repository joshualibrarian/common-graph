package dev.everydaythings.graph.item;


import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the field-level {@code @Seed.Property} annotation, the nested
 * {@code @Seed.Body} annotation, and the {@code index} field on {@code @Seed.Binding}.
 *
 * <p>The nested {@code Fixture*} classes are picked up by bootstrap classpath
 * scanning. They use the {@code cg.test:} canonical-key prefix so they don't
 * collide with production vocabulary.
 */
class SeedPropertyTest {

    // Keys for the test seed items defined below. Defined here as constants so
    // tests reference them by name rather than by string literal.
    static final String SIMPLE_KEY  = "cg.test:simple-property";
    static final String INDEXED_KEY = "cg.test:indexed-binding";

    static final String PRED_FOO = "cg.test:foo";
    static final String PRED_BAR = "cg.test:bar";
    static final String PRED_BAZ = "cg.test:baz";
    static final String PRED_CHILD = "cg.test:child";

    static Librarian lib;

    @BeforeAll
    static void bootstrap() {
        lib = Librarian.inMemory();
        lib.bootstrap();
    }

    // ==================================================================================
    // Test seed classes — picked up by classpath scan at bootstrap
    // ==================================================================================

    /** A seed with @Seed.Property fields carrying literal targets. */
    @Seed.Item(key = FixtureSimple.KEY)
    public static final class FixtureSimple {
        public static final String KEY = SIMPLE_KEY;
        private FixtureSimple() {}

        @Seed.Property(role = PRED_FOO)
        static final String foo = "the-foo-value";

        @Seed.Property(role = PRED_BAR)
        static final long bar = 42L;

        @Seed.Property(role = PRED_BAZ)
        static final boolean baz = true;
    }

    /** A seed whose @Seed.Item.bindings entries carry the index field. */
    @Seed.Item(key = FixtureIndexed.KEY, bindings = {
            @Seed.Binding(role = PRED_CHILD, ref = SIMPLE_KEY, index = 0),
            @Seed.Binding(role = PRED_CHILD, ref = INDEXED_KEY, index = 1),
            @Seed.Binding(role = PRED_CHILD, ref = SIMPLE_KEY, index = 2)
    })
    public static final class FixtureIndexed {
        public static final String KEY = INDEXED_KEY;
        private FixtureIndexed() {}
    }

    // ==================================================================================
    // Tests
    // ==================================================================================

    @Nested
    @DisplayName("@Seed.Property — literal field values become manifest bindings")
    class SimpleProperty {

        @Test
        @DisplayName("string field becomes text-target binding")
        void stringField() {
            Manifest m = manifestFor(ItemRef.iid(FixtureSimple.KEY));
            Optional<Binding> b = m.binding(CompoundKey.of(ItemRef.fromString(PRED_FOO)));
            assertThat(b).isPresent();
            assertThat(b.get().target()).isEqualTo("the-foo-value");
        }

        @Test
        @DisplayName("long field becomes integer-target binding")
        void longField() {
            Manifest m = manifestFor(ItemRef.iid(FixtureSimple.KEY));
            Optional<Binding> b = m.binding(CompoundKey.of(ItemRef.fromString(PRED_BAR)));
            assertThat(b).isPresent();
            assertThat(b.get().target()).isEqualTo(42L);
        }

        @Test
        @DisplayName("boolean field becomes bool-target binding")
        void boolField() {
            Manifest m = manifestFor(ItemRef.iid(FixtureSimple.KEY));
            Optional<Binding> b = m.binding(CompoundKey.of(ItemRef.fromString(PRED_BAZ)));
            assertThat(b).isPresent();
            assertThat(b.get().target()).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("@Seed.Binding.index — ordinal position on manifest bindings")
    class IndexedBindings {

        @Test
        @DisplayName("each indexed @Seed.Binding entry carries its index into the resulting Binding")
        void indicesPropagate() {
            Manifest m = manifestFor(ItemRef.iid(FixtureIndexed.KEY));
            List<Binding> children = m.body().bindings().stream()
                    .filter(b -> b.role().equals(ItemRef.fromString(PRED_CHILD)))
                    .toList();
            assertThat(children).hasSize(3);
            // Each must carry its declared index.
            assertThat(children.stream().map(Binding::index))
                    .containsExactlyInAnyOrder(0L, 1L, 2L);
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private Manifest manifestFor(ItemRef iid) {
        List<DatumRef> cids = lib.library().manifestCidsForItem(iid);
        assertThat(cids).as("no manifest persisted for %s", iid).isNotEmpty();
        return lib.fetchManifest(cids.get(0)).orElseThrow();
    }
}
