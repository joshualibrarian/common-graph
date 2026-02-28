package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RocksTokenDictionary.
 */
class RocksTokenDictionaryTest {

    @TempDir
    Path tempDir;

    private RocksTokenDictionary tokenIndex;

    @BeforeEach
    void setUp() {
        tokenIndex = RocksTokenDictionary.open(tempDir.resolve("token.rocks"));
    }

    @AfterEach
    void tearDown() {
        if (tokenIndex != null) {
            tokenIndex.close();
        }
    }

    @Test
    void indexAndLookupSimpleToken() {
        ItemID item = ItemID.random();
        Posting posting = Posting.universal("millimeter", item);

        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.index(posting, tx);
        });

        List<Posting> results = tokenIndex.lookup("millimeter").toList();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).target()).isEqualTo(item);
        assertThat(results.get(0).token()).isEqualTo("millimeter");
    }

    @Test
    void lookupIsCaseInsensitive() {
        ItemID item = ItemID.random();
        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.index(Posting.universal("Meter", item), tx);
        });

        List<Posting> results = tokenIndex.lookup("meter").toList();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).target()).isEqualTo(item);

        // Also works with uppercase query
        results = tokenIndex.lookup("METER").toList();
        assertThat(results).hasSize(1);
    }

    @Test
    void prefixSearch() {
        ItemID meterItem = ItemID.random();
        ItemID milliItem = ItemID.random();
        ItemID otherItem = ItemID.random();

        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.index(Posting.universal("meter", meterItem), tx);
            tokenIndex.index(Posting.universal("millimeter", milliItem), tx);
            tokenIndex.index(Posting.universal("second", otherItem), tx);
        });

        List<Posting> results = tokenIndex.prefix("m", 10).toList();
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Posting::token).containsExactlyInAnyOrder("meter", "millimeter");
    }

    @Test
    void scopedLookup() {
        ItemID universalItem = ItemID.random();
        ItemID localItem = ItemID.random();
        ItemID scopeItem = ItemID.random();

        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.index(Posting.universal("test", universalItem), tx);
            tokenIndex.index(Posting.scoped("test", scopeItem, localItem), tx);
        });

        // No-scope lookup returns ALL postings for that token
        List<Posting> allResults = tokenIndex.lookup("test").toList();
        assertThat(allResults).hasSize(2);

        // Scoped lookup only returns scoped posting
        List<Posting> scopedResults = tokenIndex.lookup("test", scopeItem).toList();
        assertThat(scopedResults).hasSize(1);
        assertThat(scopedResults.get(0).target()).isEqualTo(localItem);

        // Null scope lookup returns only universal postings
        List<Posting> universalResults = tokenIndex.lookup("test", (ItemID) null).toList();
        assertThat(universalResults).hasSize(1);
        assertThat(universalResults.get(0).target()).isEqualTo(universalItem);
    }

    @Test
    void layeredLookup() {
        ItemID globalItem = ItemID.random();
        ItemID principalItem = ItemID.random();
        ItemID principal = ItemID.random();

        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.index(Posting.universal("alias", globalItem), tx);
            tokenIndex.index(Posting.scoped("alias", principal, principalItem), tx);
        });

        // Layered lookup: principal first, then global
        List<Posting> results = tokenIndex.lookup("alias", principal, null).toList();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).target()).isEqualTo(principalItem);
        assertThat(results.get(1).target()).isEqualTo(globalItem);
    }

    @Test
    void removePosting() {
        ItemID item = ItemID.random();
        Posting posting = Posting.universal("removeme", item);

        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.index(posting, tx);
        });

        assertThat(tokenIndex.lookup("removeme").toList()).hasSize(1);

        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.remove(posting, tx);
        });

        assertThat(tokenIndex.lookup("removeme").toList()).isEmpty();
    }

    @Test
    void removeAllPostingsForItem() {
        ItemID item = ItemID.random();
        List<Posting> postings = new ArrayList<>();
        postings.add(Posting.universal("token1", item));
        postings.add(Posting.universal("token2", item));
        postings.add(Posting.universal("token3", item));

        tokenIndex.runInWriteTransaction(tx -> {
            for (Posting p : postings) {
                tokenIndex.index(p, tx);
            }
        });

        assertThat(tokenIndex.lookup("token1").toList()).hasSize(1);
        assertThat(tokenIndex.lookup("token2").toList()).hasSize(1);
        assertThat(tokenIndex.lookup("token3").toList()).hasSize(1);

        // Remove all postings for the item
        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.removeAll(postings.stream(), tx);
        });

        assertThat(tokenIndex.lookup("token1").toList()).isEmpty();
        assertThat(tokenIndex.lookup("token2").toList()).isEmpty();
        assertThat(tokenIndex.lookup("token3").toList()).isEmpty();
    }

    @Test
    void persistsAcrossReopen() {
        ItemID item = ItemID.random();

        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.index(Posting.universal("persistent", item), tx);
        });

        // Close and reopen
        tokenIndex.close();
        tokenIndex = RocksTokenDictionary.open(tempDir.resolve("token.rocks"));

        // Should still find the posting
        List<Posting> results = tokenIndex.lookup("persistent").toList();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).target()).isEqualTo(item);
    }

    @Test
    void multiplePostingsForSameToken() {
        ItemID item1 = ItemID.random();
        ItemID item2 = ItemID.random();
        ItemID item3 = ItemID.random();

        tokenIndex.runInWriteTransaction(tx -> {
            tokenIndex.index(Posting.universal("shared", item1, 0.5f), tx);
            tokenIndex.index(Posting.universal("shared", item2, 1.0f), tx);
            tokenIndex.index(Posting.universal("shared", item3, 0.8f), tx);
        });

        List<Posting> results = tokenIndex.lookup("shared").toList();
        assertThat(results).hasSize(3);

        // Should be sorted by weight descending
        assertThat(results.get(0).weight()).isEqualTo(1.0f);
        assertThat(results.get(1).weight()).isEqualTo(0.8f);
        assertThat(results.get(2).weight()).isEqualTo(0.5f);
    }
}
