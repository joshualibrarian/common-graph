package dev.everydaythings.graph.runtime.librarian;

import dev.everydaythings.graph.Handles;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import dev.everydaythings.graph.scene.ContextChain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies chain-routed dispatch: when a handler archetype's
 * {@code @Seed.Handler} declares no {@code role=}, the dispatcher walks
 * the {@link ContextChain} for a live instance of the handler's archetype
 * — rather than reading a routing role off the frame (role-routed) or
 * scanning the cache + materializing (singleton fallback).
 *
 * <p>The fixture is a synthetic "Foo handles Bar" pairing.  Foo's handler
 * is no-role, so PIVOT lands absent on the generated HANDLES frame body;
 * dispatch is forced down the chain-routed path.
 */
@DisplayName("Chain-routed dispatch")
class ChainRoutedDispatchTest {

    static final String BAR_KEY = "cg.test:chain-routed-bar";
    static final String FOO_KEY = "cg.test:chain-routed-foo";
    static final String FOO_CODE_KEY = "cg.test:chain-routed-foo-code";

    private static Librarian lib;

    @BeforeAll
    static void bootstrap() {
        lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();
    }

    @Test
    @DisplayName("dispatch walks the chain for an instance of the handler archetype")
    void chainEntryReceivesFrame() {
        AtomicInteger fired = new AtomicInteger();
        Foo foo = new Foo(ItemRef.iid("cg.test:chain-routed-foo-1"), lib, fired);
        // Deliberately NOT calling lib.register(foo) — chain-routed dispatch
        // must reach the instance via the chain alone, not the cache.

        ContextChain chain = new ContextChain(List.of(foo, lib));
        Body barBody = Body.of(ItemRef.iid(BAR_KEY), List.of());

        List<Frame> responses = chain.dispatch(Frame.of(barBody, List.of()));

        assertThat(fired.get())
                .as("Foo.handleBar should fire on the chain-supplied instance")
                .isEqualTo(1);
        assertThat(responses)
                .as("handler response should bubble up through chain.dispatch")
                .hasSize(1);
    }

    @Test
    @DisplayName("two Foo instances in different chains receive their own frames")
    void chainSpecificityIsolatesInstances() {
        AtomicInteger firedA = new AtomicInteger();
        AtomicInteger firedB = new AtomicInteger();
        Foo a = new Foo(ItemRef.iid("cg.test:chain-routed-foo-a"), lib, firedA);
        Foo b = new Foo(ItemRef.iid("cg.test:chain-routed-foo-b"), lib, firedB);

        ContextChain chainA = new ContextChain(List.of(a, lib));
        ContextChain chainB = new ContextChain(List.of(b, lib));
        Body barBody = Body.of(ItemRef.iid(BAR_KEY), List.of());

        chainA.dispatch(Frame.of(barBody, List.of()));

        assertThat(firedA.get()).as("a's chain dispatches to a").isEqualTo(1);
        assertThat(firedB.get()).as("a's chain must not touch b").isZero();

        chainB.dispatch(Frame.of(barBody, List.of()));

        assertThat(firedA.get()).as("a stays at one after b's chain dispatches").isEqualTo(1);
        assertThat(firedB.get()).as("b's chain dispatches to b").isEqualTo(1);
    }

    @Test
    @DisplayName("chain without a handler instance falls back to fresh materialization")
    void chainMissFallsBackToMaterialization() {
        // The chain has no Foo — only the librarian.  Chain lookup misses;
        // cache scan misses (no registered Foo); dispatch materializes a
        // fresh Foo via the code manifest's IMPLEMENTATION binding.  This
        // preserves singleton operator semantics: a plain
        // librarian.submit(operatorFrame) still reaches a freshly-built
        // operator even when nothing is in the chain or cache.
        ContextChain libOnly = ContextChain.singleton(lib);
        Body barBody = Body.of(ItemRef.iid(BAR_KEY), List.of());

        List<Frame> responses = libOnly.dispatch(Frame.of(barBody, List.of()));

        // The materialized Foo uses the (ItemRef, Librarian) constructor,
        // so its `fired` counter is null — the handler runs and returns the
        // marker response, but no test counter increments.  The proof is
        // the non-empty response.
        assertThat(responses)
                .as("singleton-fallback path delivers and returns the marker response")
                .hasSize(1);
    }

    // ==================================================================================
    // Test fixture — picked up by classpath scan at bootstrap.
    // ==================================================================================

    @Seed.Item(key = FOO_KEY)
    @Seed.Embodies(key = FOO_CODE_KEY, archetype = FOO_KEY)
    public static class Foo extends Item {

        private final AtomicInteger fired;

        /** Constructor used by dispatch materialization (singleton fallback). */
        public Foo(ItemRef iid, Librarian librarian) {
            super(iid, librarian);
            this.fired = null;
        }

        Foo(ItemRef iid, Librarian librarian, AtomicInteger fired) {
            super(iid, librarian);
            this.fired = fired;
        }

        @Override
        public ItemRef archetype() {
            return ItemRef.fromString(FOO_KEY);
        }

        @Handles(predicate = BAR_KEY)
        public Frame handleBar(Frame frame) {
            if (fired != null) fired.incrementAndGet();
            return Frame.of(Body.of(ItemRef.iid(BAR_KEY), List.of()), List.of());
        }
    }
}
