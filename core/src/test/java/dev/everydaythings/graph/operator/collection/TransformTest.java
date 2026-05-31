package dev.everydaythings.graph.operator.collection;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import dev.everydaythings.graph.scene.ContextChain;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Transform operator's per-item template expansion:
 * <ul>
 *   <li>Each source item becomes the context's most-specific entry while
 *       the template resolves, so {@code ?}-mode TypeRefs inside the
 *       template find that item's bindings first.</li>
 *   <li>The result is a {@code List<Body>}, one entry per source item,
 *       which the resolver's collection-expansion path then splats into
 *       sibling bindings under whatever role contained the Transform.</li>
 * </ul>
 */
@DisplayName("Transform — per-item template expansion")
class TransformTest {

    private static final String NAME_KEY = "cg.test:name";
    private static final String REACTIONS_KEY = "cg.test:reactions";

    @Test
    @DisplayName("evaluates template once per source item with each item pushed to chain")
    void perItemContextPush() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        // Two source bodies, each carrying a Name binding the template can read.
        Body alice = Body.of(
                ItemRef.iid("cg.test:reaction"),
                List.of(Binding.literal(ItemRef.iid(NAME_KEY), "alice")));
        Body bob = Body.of(
                ItemRef.iid("cg.test:reaction"),
                List.of(Binding.literal(ItemRef.iid(NAME_KEY), "bob")));
        List<Body> source = List.of(alice, bob);

        // Provider body — pushed into the chain, it carries the source list
        // under the reactions-role key so a TypeRef lookup finds it.
        Body provider = Body.of(
                ItemRef.iid("cg.test:provider"),
                List.of(new Binding(ItemRef.iid(REACTIONS_KEY), source)));

        // Template: SceneText whose Text binding is a TypeRef to the
        // per-iteration item's Name binding.
        Body template = Body.of(
                ItemRef.iid(SceneText.KEY),
                List.of(new Binding(ItemRef.iid(SceneVocabulary.Text.KEY), TypeRef.iid(NAME_KEY))));

        // Transform frame: THEME=?reactions, INSTRUMENT=template.
        Body transformBody = Body.of(
                ItemRef.iid(Transform.KEY),
                List.of(
                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), TypeRef.iid(REACTIONS_KEY)),
                        new Binding(ItemRef.iid(ThematicRole.Instrument.KEY), template)));

        // Fetch the Transform operator and invoke directly.
        Transform op = (Transform) lib.fetchItem(ItemRef.iid(Transform.KEY)).orElseThrow();
        ContextChain chain = ContextChain.singleton(lib).pushing(provider);

        Object result = op.evaluate(Frame.of(transformBody, List.of()), chain);

        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Body> expanded = (List<Body>) result;
        assertThat(expanded).hasSize(2);
        assertThat(textOf(expanded.get(0))).isEqualTo("alice");
        assertThat(textOf(expanded.get(1))).isEqualTo("bob");
    }

    @Test
    @DisplayName("source that resolves to non-Collection yields empty list (no iteration)")
    void nonCollectionSourceYieldsEmpty() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        // Provider where the source key resolves to a plain string (not a list).
        Body provider = Body.of(
                ItemRef.iid("cg.test:provider"),
                List.of(Binding.literal(ItemRef.iid(REACTIONS_KEY), "not-a-list")));

        Body template = Body.of(ItemRef.iid(SceneText.KEY), List.of());
        Body transformBody = Body.of(
                ItemRef.iid(Transform.KEY),
                List.of(
                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), TypeRef.iid(REACTIONS_KEY)),
                        new Binding(ItemRef.iid(ThematicRole.Instrument.KEY), template)));

        Transform op = (Transform) lib.fetchItem(ItemRef.iid(Transform.KEY)).orElseThrow();
        ContextChain chain = ContextChain.singleton(lib).pushing(provider);

        Object result = op.evaluate(Frame.of(transformBody, List.of()), chain);

        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).isEmpty();
    }

    @Test
    @DisplayName("ContextChain.resolveBody splats Transform's List result into multiple bindings")
    void resolverSplatsCollectionIntoSiblings() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        Body alice = Body.of(
                ItemRef.iid("cg.test:reaction"),
                List.of(Binding.literal(ItemRef.iid(NAME_KEY), "alice")));
        Body bob = Body.of(
                ItemRef.iid("cg.test:reaction"),
                List.of(Binding.literal(ItemRef.iid(NAME_KEY), "bob")));
        Body carol = Body.of(
                ItemRef.iid("cg.test:reaction"),
                List.of(Binding.literal(ItemRef.iid(NAME_KEY), "carol")));
        List<Body> source = List.of(alice, bob, carol);

        Body provider = Body.of(
                ItemRef.iid("cg.test:provider"),
                List.of(new Binding(ItemRef.iid(REACTIONS_KEY), source)));

        Body template = Body.of(
                ItemRef.iid(SceneText.KEY),
                List.of(new Binding(ItemRef.iid(SceneVocabulary.Text.KEY), TypeRef.iid(NAME_KEY))));

        // Container with one Children binding whose target is a Transform.
        // After resolveBody, the container should have THREE Children bindings
        // (one per source item), each a SceneText with the right Text.
        Body container = Body.of(
                ItemRef.iid("cg.archetype:scene-container"),
                List.of(Binding.indexed(ItemRef.iid(SceneVocabulary.Children.KEY), Body.of(
                                ItemRef.iid(Transform.KEY),
                                List.of(
                                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), TypeRef.iid(REACTIONS_KEY)),
                                        new Binding(ItemRef.iid(ThematicRole.Instrument.KEY), template))),
                        0L)));

        ContextChain chain = ContextChain.singleton(lib).pushing(provider);
        Body resolved = chain.resolveBody(container);

        CompoundKey childrenKey = CompoundKey.of(ItemRef.iid(SceneVocabulary.Children.KEY));
        // Sort by index — bindings are stored in canonical (hash) order, but
        // the meaningful sibling sequence is by their index field.
        List<Binding> children = resolved.bindings(childrenKey).stream()
                .sorted(java.util.Comparator.comparingLong(
                        b -> b.index() == null ? Long.MAX_VALUE : b.index()))
                .toList();
        assertThat(children)
                .as("Transform's three-element result should splat into three Children bindings")
                .hasSize(3);
        assertThat(textOf((Body) children.get(0).target())).isEqualTo("alice");
        assertThat(textOf((Body) children.get(1).target())).isEqualTo("bob");
        assertThat(textOf((Body) children.get(2).target())).isEqualTo("carol");
    }

    private static String textOf(Body sceneText) {
        return sceneText.binding(CompoundKey.of(ItemRef.iid(SceneVocabulary.Text.KEY)))
                .map(Binding::target)
                .filter(t -> t instanceof String)
                .map(String.class::cast)
                .orElse(null);
    }
}
