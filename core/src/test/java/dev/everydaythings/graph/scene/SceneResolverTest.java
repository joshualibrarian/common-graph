package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SceneResolver — variable lookup via ContextChain")
class SceneResolverTest {

    private static final ItemRef TEXT_ROLE = ItemRef.iid(SceneVocabulary.Text.KEY);
    private static final ItemRef CHILDREN_ROLE = ItemRef.iid(SceneVocabulary.Children.KEY);

    /**
     * Construct a chain-eligible Item: persists a minimal manifest body + an
     * unsigned record carrying {@code recordBindings}, then fetches the item
     * back from the librarian so its {@code current()} returns the persisted
     * manifest.  Mirrors the SeedProcessor pattern at test scale.
     */
    private static Item itemWithRecordBindings(Librarian lib,
                                                ItemRef itemIid,
                                                List<Binding> recordBindings) {
        Body manifestBody = Body.of(
                ItemRef.iid(CoreVocabulary.Archetype.KEY),
                List.of(Binding.ref(Manifest.ITEM_ID, itemIid)));
        lib.persist(manifestBody);
        Record record = Record.unsigned(DatumRef.of(manifestBody.datumId()), recordBindings);
        lib.persist(record);
        return lib.fetchItem(itemIid).orElseThrow();
    }

    @Nested
    @DisplayName("ContextChain")
    class ContextChainTests {

        @Test
        @DisplayName("lookupByRole finds a binding on the first chain entry that has it")
        void firstMatchWins() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            ItemRef role = ItemRef.iid("cg.test:my-var");

            Item specific = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:specific"),
                    List.of(Binding.literal(role, "specific-value")));
            Item general = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:general"),
                    List.of(Binding.literal(role, "general-value")));

            ContextChain chain = new ContextChain(List.of(specific, general));

            assertThat(chain.lookupByRole(role))
                    .as("Most-specific chain entry wins")
                    .contains("specific-value");
        }

        @Test
        @DisplayName("lookupByRole walks past entries without the binding")
        void walksPastMissingBindings() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            ItemRef role = ItemRef.iid("cg.test:my-var");

            Item specificEmpty = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:specific-empty"),
                    List.of(Binding.literal(ItemRef.iid("cg.test:unrelated"), "other")));
            Item general = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:general"),
                    List.of(Binding.literal(role, "general-value")));

            ContextChain chain = new ContextChain(List.of(specificEmpty, general));

            assertThat(chain.lookupByRole(role)).contains("general-value");
        }

        @Test
        @DisplayName("lookupByRole returns empty when no chain entry has the binding")
        void emptyWhenNoneMatch() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());

            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:no-match"),
                    List.of(Binding.literal(ItemRef.iid("cg.test:other-role"), "x")));

            ContextChain chain = new ContextChain(List.of(ctxItem));

            assertThat(chain.lookupByRole(ItemRef.iid("cg.test:absent-var"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("SceneResolver")
    class ResolverTests {

        @Test
        @DisplayName("Substitutes ?-mode target with the chain-resolved value")
        void substitutesVariable() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            ItemRef variableRole = ItemRef.iid("cg.test:greeting");
            String resolvedValue = "hello-from-the-chain";

            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx"),
                    List.of(Binding.literal(variableRole, resolvedValue)));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body scene = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.ref(TEXT_ROLE, TypeRef.iid("cg.test:greeting"))));

            Body resolved = SceneResolver.resolve(scene, chain);

            Optional<Binding> textBinding = resolved.binding(CompoundKey.of(TEXT_ROLE));
            assertThat(textBinding).isPresent();
            assertThat(textBinding.get().target()).isEqualTo(resolvedValue);
        }

        @Test
        @DisplayName("Leaves ?-mode targets unresolved when the chain has no matching binding")
        void unresolvedTargetSurvives() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:empty-ctx"),
                    List.of());  // no bindings
            ContextChain chain = new ContextChain(List.of(ctxItem));

            TypeRef unresolvable = TypeRef.iid("cg.test:nothing-bound");
            Body scene = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.ref(TEXT_ROLE, unresolvable)));

            Body resolved = SceneResolver.resolve(scene, chain);

            assertThat(resolved.binding(CompoundKey.of(TEXT_ROLE)).orElseThrow().target())
                    .as("Unresolved ?-mode target is preserved, not silently dropped")
                    .isEqualTo(unresolvable);
        }

        @Test
        @DisplayName("Recurses into nested Body targets (e.g., Container's Children)")
        void recursesIntoChildren() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            ItemRef variableRole = ItemRef.iid("cg.test:nested-text");

            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx-nested"),
                    List.of(Binding.literal(variableRole, "nested-resolved")));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body childText = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.ref(TEXT_ROLE, TypeRef.iid("cg.test:nested-text"))));
            Body container = Body.of(
                    ItemRef.iid(SceneContainer.KEY),
                    List.of(new Binding(CompoundKey.of(CHILDREN_ROLE), childText, 0L)));

            Body resolved = SceneResolver.resolve(container, chain);

            Body resolvedChild = (Body) resolved.binding(CompoundKey.of(CHILDREN_ROLE))
                    .orElseThrow().target();
            assertThat(resolvedChild.binding(CompoundKey.of(TEXT_ROLE)).orElseThrow().target())
                    .isEqualTo("nested-resolved");
        }

        @Test
        @DisplayName("Literal (@-mode) ItemRef targets pass through unchanged")
        void literalReferencesPassThrough() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            ItemRef literalTarget = ItemRef.iid("cg.test:literal-iid");

            // The chain has a record binding whose role happens to share the
            // same iid as the literal target.  A literal (@-mode) reference
            // should NOT be resolved as a variable; only ?-mode triggers it.
            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx-literal"),
                    List.of(Binding.literal(literalTarget, "should-not-substitute")));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body scene = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.ref(TEXT_ROLE, literalTarget)));   // @-mode

            Body resolved = SceneResolver.resolve(scene, chain);

            assertThat(resolved.binding(CompoundKey.of(TEXT_ROLE)).orElseThrow().target())
                    .as("Literal ItemRef target survives resolution unchanged")
                    .isEqualTo(literalTarget);
        }
    }

    @Nested
    @DisplayName("Operator dispatch")
    class OperatorDispatch {

        @Test
        @DisplayName("?-mode target resolving to an operator frame dispatches and substitutes the result")
        void operatorFrameResolves() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            // Variable target: a Between frame (true when 128 is in [0, 255]).
            ItemRef variableRole = ItemRef.iid("cg.test:in-range");
            Body betweenFrame = Body.of(
                    ItemRef.iid(dev.everydaythings.graph.operator.compare.Between.KEY),
                    List.of(
                            Binding.literal(ItemRef.iid(ThematicRole.Source.KEY), 0L),
                            Binding.literal(ItemRef.iid(ThematicRole.Goal.KEY), 255L),
                            Binding.literal(ItemRef.iid(ThematicRole.Theme.KEY), 128L)));

            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx-operator"),
                    List.of(new Binding(CompoundKey.of(variableRole), betweenFrame, null)));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body scene = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.ref(TEXT_ROLE,
                            dev.everydaythings.graph.ref.TypeRef.iid("cg.test:in-range"))));

            Body resolved = SceneResolver.resolve(scene, chain);

            assertThat(resolved.binding(CompoundKey.of(TEXT_ROLE)).orElseThrow().target())
                    .as("Resolver should dispatch Between and substitute its Bool result (true)")
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("?-mode target resolving to a non-operator body passes through as-is")
        void nonOperatorBodyPassesThrough() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            lib.bootstrap();

            // Variable target: a SceneText body — not an operator; should pass through.
            ItemRef variableRole = ItemRef.iid("cg.test:embedded-scene");
            Body sceneTextValue = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.literal(TEXT_ROLE, "literal-scene-text")));

            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx-nonop"),
                    List.of(new Binding(CompoundKey.of(variableRole), sceneTextValue, null)));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body scene = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.ref(ItemRef.iid("cg.test:slot"),
                            dev.everydaythings.graph.ref.TypeRef.iid("cg.test:embedded-scene"))));

            Body resolved = SceneResolver.resolve(scene, chain);

            Object target = resolved.binding(CompoundKey.of(ItemRef.iid("cg.test:slot")))
                    .orElseThrow().target();
            assertThat(target)
                    .as("Non-operator body resolves to the body itself, not dispatched")
                    .isInstanceOf(Body.class);
        }
    }

    @Nested
    @DisplayName("Style cascade")
    class StyleCascade {

        private static final ItemRef STYLE_ROLE   = ItemRef.iid(SceneVocabulary.Style.KEY);
        private static final ItemRef PATTERN_ROLE = ItemRef.iid(SceneVocabulary.Pattern.KEY);
        private static final ItemRef CLASSES_ROLE = ItemRef.iid(SceneVocabulary.Classes.KEY);
        private static final ItemRef ID_ROLE      = ItemRef.iid(SceneVocabulary.Id.KEY);
        private static final ItemRef FORMAT_ROLE  = ItemRef.iid(SceneVocabulary.Format.KEY);

        /** Build a style body: {Pattern → query, plus apply-bindings}. */
        private static Body styleBody(Body queryPattern, Binding... applyBindings) {
            List<Binding> bindings = new ArrayList<>();
            bindings.add(Binding.qualified(PATTERN_ROLE, List.of(), queryPattern));
            for (Binding b : applyBindings) bindings.add(b);
            return Body.of(ItemRef.iid(SceneVocabulary.SceneStyle.KEY), bindings);
        }

        /** Build a class-match query body: ?SceneNode [Classes->className]. */
        private static Body classQuery(String className) {
            return Body.of(
                    TypeRef.of(ItemRef.iid(SceneNode.KEY)),
                    List.of(Binding.literal(CLASSES_ROLE, className)));
        }

        @Test
        @DisplayName("Style with matching class pattern merges its properties onto the node")
        void classStyleMerges() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            Body style = styleBody(classQuery("card"),
                    Binding.literal(FORMAT_ROLE, "applied-format"));
            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx-class-style"),
                    List.of(new Binding(CompoundKey.of(STYLE_ROLE), style, 0L)));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body scene = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(
                            Binding.literal(CLASSES_ROLE, "card"),
                            Binding.literal(TEXT_ROLE, "hello")));

            Body resolved = SceneResolver.resolve(scene, chain);

            assertThat(resolved.binding(CompoundKey.of(FORMAT_ROLE)).orElseThrow().target())
                    .as("Style's Format binding should be merged onto the matching node")
                    .isEqualTo("applied-format");
            assertThat(resolved.binding(CompoundKey.of(TEXT_ROLE)).orElseThrow().target())
                    .as("Inline Text binding should be preserved")
                    .isEqualTo("hello");
        }

        @Test
        @DisplayName("Style with non-matching pattern does NOT add its properties")
        void nonMatchingStyleIsSkipped() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            Body style = styleBody(classQuery("other-class"),
                    Binding.literal(FORMAT_ROLE, "should-not-apply"));
            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx-no-match"),
                    List.of(new Binding(CompoundKey.of(STYLE_ROLE), style, 0L)));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body scene = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.literal(CLASSES_ROLE, "card")));

            Body resolved = SceneResolver.resolve(scene, chain);

            assertThat(resolved.binding(CompoundKey.of(FORMAT_ROLE)))
                    .as("Non-matching style should not contribute its bindings")
                    .isEmpty();
        }

        @Test
        @DisplayName("Inline binding wins over a cascaded style binding for the same key")
        void inlineWinsOverCascade() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            Body style = styleBody(classQuery("card"),
                    Binding.literal(TEXT_ROLE, "from-style"));
            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx-inline-wins"),
                    List.of(new Binding(CompoundKey.of(STYLE_ROLE), style, 0L)));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body scene = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(
                            Binding.literal(CLASSES_ROLE, "card"),
                            Binding.literal(TEXT_ROLE, "inline-wins")));

            Body resolved = SceneResolver.resolve(scene, chain);

            assertThat(resolved.binding(CompoundKey.of(TEXT_ROLE)).orElseThrow().target())
                    .isEqualTo("inline-wins");
        }

        @Test
        @DisplayName("Style cascade applies recursively to children")
        void cascadeAppliesToChildren() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            Body style = styleBody(classQuery("card"),
                    Binding.literal(FORMAT_ROLE, "child-format"));
            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx-recursive"),
                    List.of(new Binding(CompoundKey.of(STYLE_ROLE), style, 0L)));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body child = Body.of(
                    ItemRef.iid(SceneText.KEY),
                    List.of(Binding.literal(CLASSES_ROLE, "card")));
            Body container = Body.of(
                    ItemRef.iid(SceneContainer.KEY),
                    List.of(new Binding(CompoundKey.of(CHILDREN_ROLE), child, 0L)));

            Body resolved = SceneResolver.resolve(container, chain);

            Body resolvedChild = (Body) resolved.binding(CompoundKey.of(CHILDREN_ROLE))
                    .orElseThrow().target();
            assertThat(resolvedChild.binding(CompoundKey.of(FORMAT_ROLE)).orElseThrow().target())
                    .as("Style should cascade through container into its children")
                    .isEqualTo("child-format");
        }

        @Test
        @DisplayName("matchType pattern matches the exact archetype (e.g., ?SceneText)")
        void matchTypePattern() {
            Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
            // Query ?SceneText [] — matches only SceneText nodes, regardless of classes/id.
            Body sceneTextOnlyQuery = Body.of(
                    TypeRef.of(ItemRef.iid(SceneText.KEY)), List.of());
            Body style = styleBody(sceneTextOnlyQuery,
                    Binding.literal(FORMAT_ROLE, "text-only"));
            Item ctxItem = itemWithRecordBindings(lib,
                    ItemRef.iid("cg.test:ctx-type-match"),
                    List.of(new Binding(CompoundKey.of(STYLE_ROLE), style, 0L)));
            ContextChain chain = new ContextChain(List.of(ctxItem));

            Body textNode = Body.of(ItemRef.iid(SceneText.KEY), List.of());
            Body containerNode = Body.of(
                    ItemRef.iid(SceneContainer.KEY),
                    List.of(new Binding(CompoundKey.of(CHILDREN_ROLE), textNode, 0L)));

            Body resolved = SceneResolver.resolve(containerNode, chain);

            Body resolvedChild = (Body) resolved.binding(CompoundKey.of(CHILDREN_ROLE))
                    .orElseThrow().target();
            assertThat(resolvedChild.binding(CompoundKey.of(FORMAT_ROLE)))
                    .as("?SceneText matches the SceneText child")
                    .isPresent();
            assertThat(resolved.binding(CompoundKey.of(FORMAT_ROLE)))
                    .as("?SceneText does NOT match the SceneContainer parent")
                    .isEmpty();
        }
    }
}
