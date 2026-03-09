package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ActivityEntry and ActivityLog.
 */
class ActivityEntryTest {

    // ==================================================================================
    // ActivityEntry.from() — factory tests
    // ==================================================================================

    @Test
    void fromEmptyResult() {
        var entry = ActivityEntry.from("", null, Eval.EvalResult.empty());
        assertThat(entry.kind()).isEqualTo(ActivityEntry.Kind.EMPTY);
        assertThat(entry.resultText()).isNull();
        assertThat(entry.resultIid()).isNull();
    }

    @Test
    void fromValueResult() {
        var entry = ActivityEntry.from("5 + 2", null, Eval.EvalResult.value(7.0));
        assertThat(entry.kind()).isEqualTo(ActivityEntry.Kind.VALUE);
        assertThat(entry.input()).isEqualTo("5 + 2");
        assertThat(entry.resultText()).isEqualTo("7.0");
        assertThat(entry.isSuccess()).isTrue();
        assertThat(entry.hasResult()).isTrue();
    }

    @Test
    void fromErrorResult() {
        var entry = ActivityEntry.from("bad", null, Eval.EvalResult.error("Unknown: bad"));
        assertThat(entry.kind()).isEqualTo(ActivityEntry.Kind.ERROR);
        assertThat(entry.resultText()).isEqualTo("Unknown: bad");
        assertThat(entry.isSuccess()).isFalse();
    }

    @Test
    void capturesContextIid() {
        ItemID ctx = ItemID.fromString("cg:test/context");
        var entry = ActivityEntry.from("hello", ctx, Eval.EvalResult.value("world"));
        assertThat(entry.contextIid()).isEqualTo(ctx);
    }

    @Test
    void capturesTimestamp() {
        var entry = ActivityEntry.from("test", null, Eval.EvalResult.value(42));
        assertThat(entry.timestamp()).isNotNull();
    }

    // ==================================================================================
    // ActivityLog tests
    // ==================================================================================

    @Test
    void logIsInitiallyEmpty() {
        var log = new ActivityLog();
        assertThat(log.size()).isZero();
        assertThat(log.last()).isEmpty();
    }

    @Test
    void appendAddsEntry() {
        var log = new ActivityLog();
        var entry = ActivityEntry.from("5 + 2", null, Eval.EvalResult.value(7.0));
        log.append(entry);

        assertThat(log.size()).isEqualTo(1);
        assertThat(log.last()).isPresent();
        assertThat(log.last().get().input()).isEqualTo("5 + 2");
    }

    @Test
    void lastForContextFiltersCorrectly() {
        var log = new ActivityLog();
        ItemID ctx1 = ItemID.fromString("cg:test/item1");
        ItemID ctx2 = ItemID.fromString("cg:test/item2");

        log.append(ActivityEntry.from("a", ctx1, Eval.EvalResult.value("alpha")));
        log.append(ActivityEntry.from("b", ctx2, Eval.EvalResult.value("beta")));
        log.append(ActivityEntry.from("c", ctx1, Eval.EvalResult.value("gamma")));

        // Last for ctx1 should be "c", not "a"
        var last1 = log.lastForContext(ctx1);
        assertThat(last1).isPresent();
        assertThat(last1.get().input()).isEqualTo("c");
        assertThat(last1.get().resultText()).isEqualTo("gamma");

        // Last for ctx2 should be "b"
        var last2 = log.lastForContext(ctx2);
        assertThat(last2).isPresent();
        assertThat(last2.get().input()).isEqualTo("b");
    }

    @Test
    void recentActivityReturnsNewestFirst() {
        var log = new ActivityLog();
        log.append(ActivityEntry.from("first", null, Eval.EvalResult.value(1)));
        log.append(ActivityEntry.from("second", null, Eval.EvalResult.value(2)));
        log.append(ActivityEntry.from("third", null, Eval.EvalResult.value(3)));

        var recent = log.recent(2);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).input()).isEqualTo("third");
        assertThat(recent.get(1).input()).isEqualTo("second");
    }

    @Test
    void recentActivityHandlesMoreThanAvailable() {
        var log = new ActivityLog();
        log.append(ActivityEntry.from("only", null, Eval.EvalResult.value(1)));

        var recent = log.recent(10);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).input()).isEqualTo("only");
    }

    @Test
    void toStringShowsInputAndResult() {
        var entry = ActivityEntry.from("5 + 2", null, Eval.EvalResult.value(7.0));
        assertThat(entry.toString()).isEqualTo("5 + 2 → 7.0");
    }

    @Test
    void toStringShowsInputOnlyWhenNoResult() {
        var entry = ActivityEntry.from("exit", null, Eval.EvalResult.empty());
        assertThat(entry.toString()).isEqualTo("exit");
    }
}
