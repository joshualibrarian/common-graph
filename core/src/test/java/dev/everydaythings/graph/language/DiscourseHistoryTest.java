package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DiscourseHistory} — pronoun resolution via discourse context.
 */
class DiscourseHistoryTest {

    private static final Item ALICE = new NounSememe(
            "cg:test/alice", Map.of("en", "alice"), Map.of());
    private static final Item BOB = new NounSememe(
            "cg:test/bob", Map.of("en", "bob"), Map.of());
    private static final Item CAROL = new NounSememe(
            "cg:test/carol", Map.of("en", "carol"), Map.of());

    @Test
    void emptyHistoryReturnsEmpty() {
        var history = new DiscourseHistory();
        assertThat(history.mostRecent()).isEmpty();
        assertThat(history.previous()).isEmpty();
    }

    @Test
    void mostRecentReturnsLastPushed() {
        var history = new DiscourseHistory();
        history.push(ALICE);
        history.push(BOB);
        assertThat(history.mostRecent()).contains(BOB);
    }

    @Test
    void previousReturnsSecondMostRecent() {
        var history = new DiscourseHistory();
        history.push(ALICE);
        history.push(BOB);
        assertThat(history.previous()).contains(ALICE);
    }

    @Test
    void previousEmptyWithOnlyOneItem() {
        var history = new DiscourseHistory();
        history.push(ALICE);
        assertThat(history.previous()).isEmpty();
    }

    @Test
    void pushMovesExistingToFront() {
        var history = new DiscourseHistory();
        history.push(ALICE);
        history.push(BOB);
        history.push(ALICE); // re-push alice
        assertThat(history.mostRecent()).contains(ALICE);
        assertThat(history.previous()).contains(BOB);
    }

    @Test
    void resolveIt() {
        var history = new DiscourseHistory();
        history.push(ALICE);
        assertThat(history.resolve(PronounSememe.It.SEED, null)).contains(ALICE);
    }

    @Test
    void resolveThis() {
        var history = new DiscourseHistory();
        history.push(ALICE); // shouldn't matter
        assertThat(history.resolve(PronounSememe.This.SEED, BOB)).contains(BOB);
    }

    @Test
    void resolveLast() {
        var history = new DiscourseHistory();
        history.push(ALICE);
        history.push(BOB);
        assertThat(history.resolve(PronounSememe.Last.SEED, null)).contains(ALICE);
    }

    @Test
    void resolveUnknownPronounReturnsEmpty() {
        var history = new DiscourseHistory();
        history.push(ALICE);
        assertThat(history.resolve(PronounSememe.Any.SEED, null)).isEmpty();
    }

    @Test
    void clearRemovesAll() {
        var history = new DiscourseHistory();
        history.push(ALICE);
        history.push(BOB);
        history.clear();
        assertThat(history.size()).isZero();
        assertThat(history.mostRecent()).isEmpty();
    }
}
