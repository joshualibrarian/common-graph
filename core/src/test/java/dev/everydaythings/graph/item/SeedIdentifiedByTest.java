package dev.everydaythings.graph.item;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.value.identifier.Identifier;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.identifier.CILIID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@code @Seed.IdentifiedBy} end-to-end through SeedProcessor:
 * a test seed declares two identifier fields (CILI and a notional ISBN),
 * bootstrap emits IDENTIFIED_BY frames referencing persisted Identifier
 * bodies, and the resulting frames are queryable from the graph.
 */
@DisplayName("@Seed.IdentifiedBy bootstrap")
class SeedIdentifiedByTest {

    /**
     * Test seed: a sememe carrying a CILI identifier via @Seed.IdentifiedBy.
     * Lives in the test source so the production bootstrap picks it up via
     * classpath scan (test classpath includes both main and test).
     */
    @Seed.Item(key = TestSeedWithCili.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class TestSeedWithCili {
        public static final String KEY = "cg.test:seed-with-cili";
        private TestSeedWithCili() {}

        @Seed.IdentifiedBy(type = CILIID.KEY)
        static final String CILI = "i12345";
    }

    @Test
    @DisplayName("@Seed.IdentifiedBy emits IDENTIFIED_BY frame with the typed body inlined")
    void emitsIdentifiedByFrameWithInlineBody() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ItemRef seedIid = ItemRef.iid(TestSeedWithCili.KEY);
        ItemRef identifiedBy = ItemRef.iid(Identifier.KEY);
        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);

        // Find frames themed to the seed.
        List<DatumRef> framesAboutSeed = lib.library()
                .bodyCidsForReferenceBinding(themeRole, seedIid);
        assertThat(framesAboutSeed).isNotEmpty();

        // Filter to IDENTIFIED_BY-headed bodies.
        List<Body> identifiedByBodies = framesAboutSeed.stream()
                .map(cid -> lib.library().fetchBody(cid))
                .flatMap(Optional::stream)
                .filter(b -> identifiedBy.equals(b.head()))
                .toList();

        assertThat(identifiedByBodies)
                .as("Expected one IDENTIFIED_BY frame about the test seed")
                .hasSize(1);

        Body frame = identifiedByBodies.get(0);

        // The VALUE binding's target IS the inlined CILIID body (not a DatumRef).
        Body inlineCili = null;
        for (Binding b : frame.bindings()) {
            if (b.role().equals(valueRole) && b.target() instanceof Body body) {
                inlineCili = body;
                break;
            }
        }
        assertThat(inlineCili)
                .as("Expected a VALUE binding whose target is the inlined CILIID body")
                .isNotNull();

        assertThat(inlineCili.head()).isEqualTo(ItemRef.iid(CILIID.KEY));
        assertThat(inlineCili.isAtomic()).isTrue();
        assertThat(inlineCili.atomicContent()).contains("i12345");
    }

    @Test
    @DisplayName("Inlined CILIID body has the same CID as CILIID.fromText built in-memory")
    void cidIsDeterministic() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        CILIID inMemory = CILIID.fromText("i12345");
        DatumRef expectedCid = inMemory.datumId();

        // Pull the inline body out of the IDENTIFIED_BY frame and verify
        // its CID matches.  The atomic body's CID is deterministic from its
        // canonical content, whether inlined or separately persisted.
        ItemRef seedIid = ItemRef.iid(TestSeedWithCili.KEY);
        ItemRef identifiedBy = ItemRef.iid(Identifier.KEY);
        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);

        Body inlineCili = lib.library()
                .bodyCidsForReferenceBinding(themeRole, seedIid)
                .stream()
                .map(cid -> lib.library().fetchBody(cid))
                .flatMap(Optional::stream)
                .filter(b -> identifiedBy.equals(b.head()))
                .flatMap(b -> b.bindings().stream())
                .filter(bd -> bd.role().equals(valueRole) && bd.target() instanceof Body)
                .map(bd -> (Body) bd.target())
                .findFirst()
                .orElseThrow();

        assertThat(inlineCili.datumId()).isEqualTo(expectedCid);
    }
}
