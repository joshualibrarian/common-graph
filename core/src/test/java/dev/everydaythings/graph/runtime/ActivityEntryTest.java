package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ActivityEntry and SessionItem activity log.
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
    // SessionItem activity log
    // ==================================================================================

    @Test
    void sessionLogIsInitiallyEmpty() {
        var session = new SessionItem();
        assertThat(session.activityCount()).isZero();
        assertThat(session.lastActivity()).isEmpty();
    }

    @Test
    void logActivityAppendsEntry() {
        var session = new SessionItem();
        var entry = ActivityEntry.from("5 + 2", null, Eval.EvalResult.value(7.0));
        session.logActivity(entry);

        assertThat(session.activityCount()).isEqualTo(1);
        assertThat(session.lastActivity()).isPresent();
        assertThat(session.lastActivity().get().input()).isEqualTo("5 + 2");
    }

    @Test
    void lastActivityForContextFiltersCorrectly() {
        var session = new SessionItem();
        ItemID ctx1 = ItemID.fromString("cg:test/item1");
        ItemID ctx2 = ItemID.fromString("cg:test/item2");

        session.logActivity(ActivityEntry.from("a", ctx1, Eval.EvalResult.value("alpha")));
        session.logActivity(ActivityEntry.from("b", ctx2, Eval.EvalResult.value("beta")));
        session.logActivity(ActivityEntry.from("c", ctx1, Eval.EvalResult.value("gamma")));

        // Last for ctx1 should be "c", not "a"
        var last1 = session.lastActivityForContext(ctx1);
        assertThat(last1).isPresent();
        assertThat(last1.get().input()).isEqualTo("c");
        assertThat(last1.get().resultText()).isEqualTo("gamma");

        // Last for ctx2 should be "b"
        var last2 = session.lastActivityForContext(ctx2);
        assertThat(last2).isPresent();
        assertThat(last2.get().input()).isEqualTo("b");
    }

    @Test
    void recentActivityReturnsNewestFirst() {
        var session = new SessionItem();
        session.logActivity(ActivityEntry.from("first", null, Eval.EvalResult.value(1)));
        session.logActivity(ActivityEntry.from("second", null, Eval.EvalResult.value(2)));
        session.logActivity(ActivityEntry.from("third", null, Eval.EvalResult.value(3)));

        var recent = session.recentActivity(2);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).input()).isEqualTo("third");
        assertThat(recent.get(1).input()).isEqualTo("second");
    }

    @Test
    void recentActivityHandlesMoreThanAvailable() {
        var session = new SessionItem();
        session.logActivity(ActivityEntry.from("only", null, Eval.EvalResult.value(1)));

        var recent = session.recentActivity(10);
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
