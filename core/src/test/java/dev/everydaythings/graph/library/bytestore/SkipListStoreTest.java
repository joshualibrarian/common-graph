package dev.everydaythings.graph.library.bytestore;

import dev.everydaythings.graph.library.skiplist.SkipListStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MemoryStore")
class SkipListStoreTest {

    @Getter
    enum TestCF implements ColumnSchema {
        DATA("data", null, KeyEncoder.RAW),
        INDEX("index", 4, KeyEncoder.RAW);

        private final String schemaName;
        private final Integer prefixLen;
        private final KeyEncoder[] keyComposition;

        TestCF(String name, Integer prefixLen, KeyEncoder... keys) {
            this.schemaName = name;
            this.prefixLen = prefixLen;
            this.keyComposition = keys;
        }
    }

    // Simple implementation for tests
    static class TestStore implements SkipListStore<TestCF> {
        @Getter private final SkipListStore.Opened<TestCF> opened = SkipListStore.create(TestCF.class);
    }

    @Test
    @DisplayName("basic put/get/delete")
    void basicOperations() {
        try (var store = new TestStore()) {
            byte[] key = "hello".getBytes();
            byte[] value = "world".getBytes();

            // Put and get
            store.db(TestCF.DATA).key(key).put(value);
            assertThat(store.db(TestCF.DATA).key(key).get()).isEqualTo(value);
            assertThat(store.db(TestCF.DATA).key(key).exists()).isTrue();

            // Delete
            store.db(TestCF.DATA).key(key).delete();
            assertThat(store.db(TestCF.DATA).key(key).get()).isNull();
            assertThat(store.db(TestCF.DATA).key(key).exists()).isFalse();
        }
    }

    @Test
    @DisplayName("prefix iteration")
    void prefixIteration() {
        try (var store = new TestStore()) {
            // Insert keys with common prefix
            store.db(TestCF.INDEX).key("user:1".getBytes()).put("alice".getBytes());
            store.db(TestCF.INDEX).key("user:2".getBytes()).put("bob".getBytes());
            store.db(TestCF.INDEX).key("user:3".getBytes()).put("carol".getBytes());
            store.db(TestCF.INDEX).key("item:1".getBytes()).put("apple".getBytes());

            // Prefix scan for "user:"
            List<String> users = new ArrayList<>();
            try (var it = store.iterate(TestCF.INDEX, "user:".getBytes())) {
                while (it.hasNext()) {
                    var kv = it.next();
                    users.add(new String(kv.value(), StandardCharsets.UTF_8));
                }
            }

            assertThat(users).containsExactly("alice", "bob", "carol");
        }
    }

    @Test
    @DisplayName("transactions commit")
    void transactionCommit() {
        try (var store = new TestStore()) {
            try (var tx = store.beginTransaction()) {
                store.db(TestCF.DATA).transaction(tx).key("a".getBytes()).put("1".getBytes());
                store.db(TestCF.DATA).transaction(tx).key("b".getBytes()).put("2".getBytes());
                tx.commit();
            }

            assertThat(store.db(TestCF.DATA).key("a".getBytes()).get()).isEqualTo("1".getBytes());
            assertThat(store.db(TestCF.DATA).key("b".getBytes()).get()).isEqualTo("2".getBytes());
        }
    }

    @Test
    @DisplayName("transactions rollback")
    void transactionRollback() {
        try (var store = new TestStore()) {
            store.db(TestCF.DATA).key("existing".getBytes()).put("value".getBytes());

            try (var tx = store.beginTransaction()) {
                store.db(TestCF.DATA).transaction(tx).key("new".getBytes()).put("data".getBytes());
                tx.rollback();
            }

            assertThat(store.db(TestCF.DATA).key("existing".getBytes()).get()).isEqualTo("value".getBytes());
            assertThat(store.db(TestCF.DATA).key("new".getBytes()).get()).isNull();
        }
    }

    @Test
    @DisplayName("lexicographic byte ordering")
    void lexicographicOrdering() {
        try (var store = new TestStore()) {
            // Insert in random order
            store.db(TestCF.INDEX).key(new byte[]{0x00}).put("first".getBytes());
            store.db(TestCF.INDEX).key(new byte[]{(byte) 0xFF}).put("last".getBytes());
            store.db(TestCF.INDEX).key(new byte[]{0x7F}).put("middle".getBytes());

            // Iterate all - should be in lexicographic order
            List<String> values = new ArrayList<>();
            try (var it = store.iterate(TestCF.INDEX, new byte[0])) {
                while (it.hasNext()) {
                    values.add(new String(it.next().value(), StandardCharsets.UTF_8));
                }
            }

            assertThat(values).containsExactly("first", "middle", "last");
        }
    }
}
