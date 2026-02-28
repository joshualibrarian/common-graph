package dev.everydaythings.graph.library.dictionary;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TokenExtractor}, focusing on path mount token extraction.
 */
class TokenExtractorTest {

    private static final ItemID ITEM_ID = ItemID.random();

    // ==================================================================================
    // fromPathMount — basic extraction
    // ==================================================================================

    @Test
    void fromPathMount_simpleRoot_extractsLeafToken() {
        List<Posting> postings = TokenExtractor.fromPathMount(ITEM_ID, "/chess");

        assertThat(postings).hasSize(1);
        assertThat(postings.getFirst().token()).isEqualTo("chess");
        assertThat(postings.getFirst().target()).isEqualTo(ITEM_ID);
        assertThat(postings.getFirst().weight()).isEqualTo(TokenExtractor.PATH_MOUNT_WEIGHT);
    }

    @Test
    void fromPathMount_nestedPath_extractsLeafAndIntermediates() {
        List<Posting> postings = TokenExtractor.fromPathMount(ITEM_ID, "/documents/notes");

        assertThat(postings).hasSize(2);

        // Leaf ("notes") at full mount weight
        Posting leaf = postings.stream()
                .filter(p -> p.token().equals("notes"))
                .findFirst().orElseThrow();
        assertThat(leaf.weight()).isEqualTo(TokenExtractor.PATH_MOUNT_WEIGHT);
        assertThat(leaf.target()).isEqualTo(ITEM_ID);

        // Intermediate ("documents") at lower weight
        Posting intermediate = postings.stream()
                .filter(p -> p.token().equals("documents"))
                .findFirst().orElseThrow();
        assertThat(intermediate.weight()).isEqualTo(1.1f);
        assertThat(intermediate.target()).isEqualTo(ITEM_ID);
    }

    @Test
    void fromPathMount_deepPath_extractsAllSegments() {
        List<Posting> postings = TokenExtractor.fromPathMount(ITEM_ID, "/a/b/c");

        assertThat(postings).hasSize(3);

        // Leaf "c" at full weight
        assertThat(postings.stream().filter(p -> p.token().equals("c")).findFirst().orElseThrow()
                .weight()).isEqualTo(TokenExtractor.PATH_MOUNT_WEIGHT);

        // Intermediates "a" and "b" at 1.1f
        assertThat(postings.stream().filter(p -> p.token().equals("a")).findFirst().orElseThrow()
                .weight()).isEqualTo(1.1f);
        assertThat(postings.stream().filter(p -> p.token().equals("b")).findFirst().orElseThrow()
                .weight()).isEqualTo(1.1f);
    }

    // ==================================================================================
    // fromPathMount — postings are scoped to the owning item
    // ==================================================================================

    @Test
    void fromPathMount_postingsAreScopedToItem() {
        List<Posting> postings = TokenExtractor.fromPathMount(ITEM_ID, "/chess");

        assertThat(postings).allMatch(Posting::isLocal);
        assertThat(postings).allMatch(p -> p.scope().equals(ITEM_ID));
    }

    // ==================================================================================
    // fromPathMount — weight is higher than normal content tokens
    // ==================================================================================

    @Test
    void fromPathMount_weightExceedsContentTokenWeights() {
        // Normal content tokens max at 1.0f (names, titles)
        assertThat(TokenExtractor.PATH_MOUNT_WEIGHT).isGreaterThan(1.0f);
    }

    // ==================================================================================
    // fromPathMount — edge cases
    // ==================================================================================

    @Test
    void fromPathMount_nullInputs_returnsEmpty() {
        assertThat(TokenExtractor.fromPathMount(null, "/chess")).isEmpty();
        assertThat(TokenExtractor.fromPathMount(ITEM_ID, null)).isEmpty();
        assertThat(TokenExtractor.fromPathMount(ITEM_ID, "")).isEmpty();
        assertThat(TokenExtractor.fromPathMount(ITEM_ID, "   ")).isEmpty();
    }

    @Test
    void fromPathMount_rootSlashOnly_returnsEmpty() {
        assertThat(TokenExtractor.fromPathMount(ITEM_ID, "/")).isEmpty();
    }

    @Test
    void fromPathMount_noLeadingSlash_stillWorks() {
        List<Posting> postings = TokenExtractor.fromPathMount(ITEM_ID, "chess");

        assertThat(postings).hasSize(1);
        assertThat(postings.getFirst().token()).isEqualTo("chess");
    }

    @Test
    void fromPathMount_tokensAreNormalized() {
        // Posting.global normalizes tokens (lowercase, trim, NFC)
        List<Posting> postings = TokenExtractor.fromPathMount(ITEM_ID, "/Chess");

        assertThat(postings).hasSize(1);
        assertThat(postings.getFirst().token()).isEqualTo("chess");
    }
}
