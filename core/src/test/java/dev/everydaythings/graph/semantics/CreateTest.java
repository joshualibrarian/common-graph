package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the CREATE flow end-to-end via annotation-driven seed bootstrap and
 * IMPLEMENTS-frame-driven dispatch.
 *
 * <p>Validation:
 * <ul>
 *   <li>Bootstrap discovers {@code @Seed}, {@code @Embodies}, and {@code @Mints}
 *       classes via classpath scan and persists their seed manifests / IMPLEMENTS
 *       frames as unsigned bodies.</li>
 *   <li>{@link Create} hydrates correctly via the seed manifest's IMPLEMENTATION
 *       binding (combination effect of {@code @Seed} + {@code @Embodies}).</li>
 *   <li>{@code Create.onFrameAssembled} queries IMPLEMENTS frames for the THEME's
 *       concept and instantiates the runnable Java class found there.</li>
 *   <li>The resulting instance is a fully-committed Item with the correct
 *       archetype binding.</li>
 * </ul>
 */
class CreateTest {

    /**
     * Test concept seed — the abstract concept of "test chess." Pure-data seed
     * with EXPECTS bindings declaring it's instantiable (instances should have
     * AGENT and THEME role bindings). Bootstrap validates {@code @Mints(Chess.KEY)}
     * against the presence of EXPECTS.
     */
    @Seed.Item(key = Chess.KEY)
    public static final class Chess {
        public static final String KEY = "cg.test:chess";
        public static final ItemID IID = ItemID.fromString(KEY);

        /** Chess instances expect an AGENT role (the player). */
        @Seed.Frame(predicate = Expects.KEY,
              field = @Seed.Binding(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY}))
        static final ItemID expectsAgent = ThematicRole.Agent.IID;

        /** Chess instances expect a THEME role (e.g., the game/board state). */
        @Seed.Frame(predicate = Expects.KEY,
              field = @Seed.Binding(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY}))
        static final ItemID expectsTheme = ThematicRole.Theme.IID;

        private Chess() {}
    }

    /**
     * Test instance class — Java runtime form of any chess game.
     * {@code @Mints(Chess.KEY)} declares "I implement instances of Chess";
     * bootstrap publishes an IMPLEMENTS frame, and CREATE on Chess finds it.
     */
    @Seed.Mints(key = Chess.KEY)
    public static final class ChessGame extends Item {
        public ChessGame(ItemID iid, Librarian lib) {
            super(iid, lib);
        }

        @Override
        public ItemID archetype() {
            return Chess.IID;
        }
    }

    @Nested
    @DisplayName("Annotation-driven CREATE flow")
    class AnnotationDriven {

        @Test
        @DisplayName("CREATE on a concept with a @Mints class mints an instance of that class")
        void createMintsInstance() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            Body createBody = Body.of(
                    ItemRef.of(Create.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, Chess.IID))
            );
            lib.assembleFrame(createBody, lib);

            // A new chess game manifest now exists, archetypal under Chess.
            List<ContentID> chessManifests = lib.library().manifestCidsForType(Chess.IID);
            assertThat(chessManifests).hasSize(1);

            // The minted item hydrates as ChessGame (its commit auto-injected
            // IMPLEMENTATION → ChessGame.class).
            Manifest manifest = lib.fetchManifest(chessManifests.getFirst()).orElseThrow();
            Item chess = lib.fetchItem(manifest.itemId()).orElseThrow();
            assertThat(chess).isInstanceOf(ChessGame.class);
            assertThat(chess.archetype()).isEqualTo(Chess.IID);
        }

        @Test
        @DisplayName("CREATE on a concept without a @Mints class no-ops")
        void createWithNoImplementationNoOps() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            // Theme role is a pure-data seed — no @Mints class for it.
            Body createBody = Body.of(
                    ItemRef.of(Create.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, ThematicRole.Theme.IID))
            );
            lib.assembleFrame(createBody, lib);

            // Nothing should have been minted under the Theme archetype.
            assertThat(lib.library().manifestCidsForType(ThematicRole.Theme.IID)).isEmpty();
        }

        @Test
        @DisplayName("CREATE without a THEME binding silently no-ops")
        void createWithoutThemeNoOps() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            Body createBody = Body.of(ItemRef.of(Create.IID), List.of());
            lib.assembleFrame(createBody, lib);

            assertThat(lib.library().manifestCidsForType(Chess.IID)).isEmpty();
        }

        @Test
        @DisplayName("Multiple CREATE frames mint multiple distinct instances")
        void multipleCreatesMintMultiple() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            Body createBody = Body.of(
                    ItemRef.of(Create.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, Chess.IID))
            );
            lib.assembleFrame(createBody, lib);
            lib.assembleFrame(createBody, lib);
            lib.assembleFrame(createBody, lib);

            assertThat(lib.library().manifestCidsForType(Chess.IID)).hasSize(3);
        }

        @Test
        @DisplayName("Create seed item hydrates as the Create Java class (combination effect)")
        void createSeedHydratesAsCreate() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            // The Create seed has @Seed + @Embodies for the same key, so its
            // manifest carries IMPLEMENTATION → Create.class. Hydration uses it.
            Item create = lib.fetchItem(Create.IID).orElseThrow();
            assertThat(create).isInstanceOf(Create.class);
        }

        @Test
        @DisplayName("Pure-data seeds (no @Embodies) hydrate as bare Item")
        void pureDataSeedHydratesAsBareItem() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            // ThematicRole.Theme is just @Seed, no @Embodies. Hydrates as bare Item.
            Item theme = lib.fetchItem(ThematicRole.Theme.IID).orElseThrow();
            assertThat(theme.getClass()).isEqualTo(Item.class);
        }
    }
}
