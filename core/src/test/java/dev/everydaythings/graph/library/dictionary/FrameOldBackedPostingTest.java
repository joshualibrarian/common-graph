package dev.everydaythings.graph.library.dictionary;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.CompoundKey.FrameToken;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.library.LibraryOld;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.LibrarianOld;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FrameOldBackedPostingTest {

    private static Posting testPosting(String token, ItemID target) {
        return testPosting(token, null, target, 1.0f);
    }

    private static Posting testPosting(String token, ItemID scope, ItemID target, float weight) {
        List<FrameToken> quals = scope != null
                ? List.of(new CompoundKey.Sememe(scope)) : List.of();
        FrameBodyOld body = new FrameBodyOld(CoreVocabulary.Lexeme.IID, List.of(
                FrameBodyOld.homeBinding(target),
                new Binding(ThematicRole.Value.IID, quals, Literal.ofText(token), true, true)
        ));
        return Posting.fromFrame(body, 1, weight);
    }

    @Test
    void fromBody_producesFrameBackedPostingWithFeatures() {
        ItemID sememeId = ItemID.random();
        ItemID predicate = ItemID.fromString(CoreVocabulary.Lexeme.KEY);

        // Build a LEXEME frame: NAME:[ENGLISH, VERB, LEMMA] → "create"
        List<Binding> bindings = List.of(
                FrameBodyOld.homeBinding(sememeId),
                new Binding(
                        ThematicRole.Value.IID,
                        List.of(
                                new CompoundKey.Sememe(Language.ENGLISH),
                                new CompoundKey.Sememe(PartOfSpeech.Verb.IID),
                                new CompoundKey.Sememe(GrammaticalFeature.Lemma.IID)
                        ),
                        Literal.ofText("testword"),
                        true, true
                )
        );
        FrameBodyOld body = new FrameBodyOld(predicate, bindings);

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
        assertThat(p.body()).isSameAs(body);
        assertThat(p.bindingIndex()).isEqualTo(1);
    }

    @Test
    void roundTrip_indexAndLookup_preservesFeatures() {
        LibraryOld lib = LibraryOld.memory();
        ItemID sememeId = ItemID.random();
        ItemID predicate = ItemID.fromString(CoreVocabulary.Lexeme.KEY);

        // Build a LEXEME frame with VERB feature
        FrameBodyOld body = new FrameBodyOld(predicate, List.of(
                FrameBodyOld.homeBinding(sememeId),
                new Binding(
                        ThematicRole.Value.IID,
                        List.of(
                                new CompoundKey.Sememe(Language.ENGLISH),
                                new CompoundKey.Sememe(PartOfSpeech.Verb.IID),
                                new CompoundKey.Sememe(GrammaticalFeature.Lemma.IID)
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
        java.util.function.Function<ContentID, Optional<FrameBodyOld>> resolver =
                hash -> lib.loadFrameBody(hash);
        List<Posting> results = tokenDict.lookup("testverb", resolver).toList();

        assertThat(results).hasSize(1);
        Posting result = results.getFirst();
        assertThat(result.token()).isEqualTo("testverb");
        assertThat(result.target()).isEqualTo(sememeId);
        assertThat(result.scope()).isEqualTo(Language.ENGLISH);
        assertThat(result.features()).contains(PartOfSpeech.Verb.IID);

        lib.close();
    }

    @Test
    void withWeight_preservesAllFields() {
        ItemID target = ItemID.random();
        Posting original = testPosting("test", Language.ENGLISH, target, 0.5f);
        Posting merged = Posting.withWeight(original, 0.9f);

        assertThat(merged.token()).isEqualTo(original.token());
        assertThat(merged.scope()).isEqualTo(original.scope());
        assertThat(merged.target()).isEqualTo(original.target());
        assertThat(merged.features()).isEqualTo(original.features());
        assertThat(merged.weight()).isEqualTo(0.9f);
    }

    @Test
    void createVerb_endToEnd_resolvesWithFeatures() {
        LibrarianOld lib = LibrarianOld.createInMemory();

        // Look up "create" through the token dictionary with body resolver
        var tokenDict = lib.tokenIndex();
        assertThat(tokenDict).isNotNull();
        java.util.function.Function<ContentID, Optional<FrameBodyOld>> resolver =
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

    @Test
    void titlePredicate_resolvesInTokenDictionary() {
        LibrarianOld lib = LibrarianOld.createInMemory();

        var tokenDict = lib.tokenIndex();
        assertThat(tokenDict).isNotNull();
        java.util.function.Function<ContentID, Optional<FrameBodyOld>> resolver =
                hash -> lib.library().loadFrameBody(hash);

        // "title" should resolve as a noun
        var titlePostings = tokenDict.lookup("title", resolver, Language.ENGLISH).toList();
        assertThat(titlePostings).as("'title' should resolve in English scope").isNotEmpty();

        // "titled" should resolve as a past-tense verb
        var titledPostings = tokenDict.lookup("titled", resolver, Language.ENGLISH).toList();
        assertThat(titledPostings).as("'titled' should resolve in English scope").isNotEmpty();

        Posting titledPosting = titledPostings.stream()
                .filter(p -> p.token().equals("titled"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("'titled' posting not found"));

        assertThat(titledPosting.target()).isEqualTo(CoreVocabulary.Title.IID);
        assertThat(titledPosting.features())
                .as("'titled' should have VERB and PAST features")
                .contains(PartOfSpeech.Verb.IID, GrammaticalFeature.Past.IID);
    }
}
