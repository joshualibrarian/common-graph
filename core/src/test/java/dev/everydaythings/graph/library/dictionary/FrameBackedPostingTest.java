package dev.everydaythings.graph.library.dictionary;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FrameBackedPostingTest {

    @Test
    void fromBody_producesFrameBackedPostingWithFeatures() {
        ItemID sememeId = ItemID.random();
        ItemID predicate = ItemID.fromString(CoreVocabulary.Lexeme.KEY);

        // Build a LEXEME frame: NAME:[ENGLISH, VERB, LEMMA] → "create"
        List<Binding> bindings = List.of(
                FrameBody.homeBinding(sememeId),
                new Binding(
                        ThematicRole.Name.IID,
                        List.of(
                                new FrameKey.Sememe(Language.ENGLISH),
                                new FrameKey.Sememe(PartOfSpeech.Verb.IID),
                                new FrameKey.Sememe(GrammaticalFeature.Lemma.IID)
                        ),
                        Literal.ofText("testword"),
                        true, true
                )
        );
        FrameBody body = new FrameBody(predicate, bindings);

        List<Posting> postings = TokenExtractor.fromBody(body);

        assertThat(postings).hasSize(1);
        Posting p = postings.getFirst();
        assertThat(p.token()).isEqualTo("testword");
        assertThat(p.target()).isEqualTo(sememeId);
        assertThat(p.scope()).isEqualTo(Language.ENGLISH);
        assertThat(p.features()).containsExactlyInAnyOrder(
                PartOfSpeech.Verb.IID,
                GrammaticalFeature.Lemma.IID
        );
        assertThat(p.isFrameBacked()).isTrue();
        assertThat(p.body()).isSameAs(body);
        assertThat(p.bindingIndex()).isEqualTo(1);
    }

    @Test
    void roundTrip_indexAndLookup_preservesFeatures() {
        Library lib = Library.memory();
        ItemID sememeId = ItemID.random();
        ItemID predicate = ItemID.fromString(CoreVocabulary.Lexeme.KEY);

        // Build a LEXEME frame with VERB feature
        FrameBody body = new FrameBody(predicate, List.of(
                FrameBody.homeBinding(sememeId),
                new Binding(
                        ThematicRole.Name.IID,
                        List.of(
                                new FrameKey.Sememe(Language.ENGLISH),
                                new FrameKey.Sememe(PartOfSpeech.Verb.IID),
                                new FrameKey.Sememe(GrammaticalFeature.Lemma.IID)
                        ),
                        Literal.ofText("testverb"),
                        true, true
                )
        ));

        // Store body in object store
        lib.storeFrameBody(body);

        // Extract and index posting
        List<Posting> postings = TokenExtractor.fromBody(body);
        assertThat(postings).hasSize(1);

        var tokenDict = lib.tokenDictionary().orElseThrow();
        tokenDict.runInWriteTransaction(tx -> {
            for (Posting p : postings) {
                tokenDict.index(p, tx);
            }
        });

        // Lookup with body resolver
        java.util.function.Function<ContentID, Optional<FrameBody>> resolver =
                hash -> lib.loadFrameBody(hash);
        List<Posting> results = tokenDict.lookup("testverb", resolver).toList();

        assertThat(results).hasSize(1);
        Posting result = results.getFirst();
        assertThat(result.token()).isEqualTo("testverb");
        assertThat(result.target()).isEqualTo(sememeId);
        assertThat(result.scope()).isEqualTo(Language.ENGLISH);
        assertThat(result.features()).contains(PartOfSpeech.Verb.IID);
        assertThat(result.isFrameBacked()).isTrue();

        lib.close();
    }

    @Test
    void directPosting_isNotFrameBacked() {
        Posting p = Posting.universal("symbol", ItemID.random());
        assertThat(p.isFrameBacked()).isFalse();
        assertThat(p.body()).isNull();
        assertThat(p.bindingIndex()).isEqualTo(-1);
    }

    @Test
    void withWeight_preservesAllFields() {
        ItemID target = ItemID.random();
        Posting original = Posting.scoped("test", Language.ENGLISH, target,
                0.5f, Set.of(PartOfSpeech.Verb.IID));
        Posting merged = Posting.withWeight(original, 0.9f);

        assertThat(merged.token()).isEqualTo(original.token());
        assertThat(merged.scope()).isEqualTo(original.scope());
        assertThat(merged.target()).isEqualTo(original.target());
        assertThat(merged.features()).isEqualTo(original.features());
        assertThat(merged.weight()).isEqualTo(0.9f);
    }

    @Test
    void createVerb_endToEnd_resolvesWithFeatures() {
        Librarian lib = Librarian.createInMemory();

        // Look up "create" through the token dictionary with body resolver
        var tokenDict = lib.tokenIndex();
        assertThat(tokenDict).isNotNull();
        java.util.function.Function<ContentID, Optional<FrameBody>> resolver =
                hash -> lib.library().loadFrameBody(hash);
        var postings = tokenDict.lookup("create", resolver, Language.ENGLISH).toList();

        assertThat(postings).as("'create' should resolve in English scope").isNotEmpty();

        Posting createPosting = postings.stream()
                .filter(p -> p.token().equals("create"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("'create' posting not found"));

        assertThat(createPosting.features())
                .as("'create' should have VERB feature")
                .contains(PartOfSpeech.Verb.IID);
        assertThat(createPosting.scope()).isEqualTo(Language.ENGLISH);
    }
}
