package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemOld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DiscourseHistory} — pronoun resolution via discourse context.
 */
class DiscourseHistoryTest {

    private static final ItemOld ALICE = new Sememe("cg:test/alice").gloss("en", "alice");
    private static final ItemOld BOB = new Sememe("cg:test/bob").gloss("en", "bob");
    private static final ItemOld CAROL = new Sememe("cg:test/carol").gloss("en", "carol");

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
        assertThat(history.resolve(new Sememe.It(), null)).contains(ALICE);
    }

    @Test
    void resolveThis() {
        var history = new DiscourseHistory();
        history.push(ALICE); // shouldn't matter
        assertThat(history.resolve(new Sememe.This(), BOB)).contains(BOB);
    }

//    @Test
//    void resolveLast() {
//        var history = new DiscourseHistory();
//        history.push(ALICE);
//        history.push(BOB);
//        assertThat(history.resolve(Sememe.Last.SEED, null)).contains(ALICE);
//    }

//    @Test
//    void resolveUnknownPronounReturnsEmpty() {
//        var history = new DiscourseHistory();
//        history.push(ALICE);
//        assertThat(history.resolve(Sememe.Any.SEED, null)).isEmpty();
//    }

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
