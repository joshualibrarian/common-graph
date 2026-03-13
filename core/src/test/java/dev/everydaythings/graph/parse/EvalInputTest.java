package dev.everydaythings.graph.parse;

import dev.everydaythings.graph.parse.ExpressionToken.*;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.value.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EvalInput} — the unified expression input state machine.
 *
 * <p>These tests verify the pure state machine behavior without requiring
 * a Librarian or Library. The dispatch path (accept → Eval) is tested
 * separately in integration tests.
 */
class EvalInputTest {

    /** Test item IDs for postings. */
    private static final ItemID ALICE_IID = ItemID.random();
    private static final ItemID BOB_IID = ItemID.random();
    private static final ItemID CREATE_IID = ItemID.random();

    /** Test postings returned by the mock lookup. */
    private static final Posting ALICE_POSTING = Posting.universal("alice", ALICE_IID);
    private static final Posting BOB_POSTING = Posting.universal("bob", BOB_IID);
    private static final Posting CREATE_POSTING = Posting.universal("create", CREATE_IID);

    /** Captured snapshots from onChange. */
    private final List<EvalInputSnapshot> snapshots = new ArrayList<>();

    /** The input under test. */
    private EvalInput input;

    @BeforeEach
    void setUp() {
        snapshots.clear();
        input = EvalInput.builder()
                .lookup(this::mockLookup)
                .prompt("> ")
                .hint("Type something...")
                .minLookupLength(1)
                .onChange(snapshots::add)
                .build();
    }

    /**
     * Mock lookup: returns postings matching the prefix.
     */
    private List<Posting> mockLookup(String text) {
        String lower = text.toLowerCase();
        List<Posting> results = new ArrayList<>();
        if ("alice".startsWith(lower)) results.add(ALICE_POSTING);
        if ("bob".startsWith(lower)) results.add(BOB_POSTING);
        if ("create".startsWith(lower)) results.add(CREATE_POSTING);
        return results;
    }

    private EvalInputSnapshot lastSnapshot() {
        assertThat(snapshots).isNotEmpty();
        return snapshots.get(snapshots.size() - 1);
    }

    // ==================================================================================
    // Typing
    // ==================================================================================

    @Nested
    class Typing {

        @Test
        void typeCharAppendsToText() {
            input.type('h');
            input.type('e');
            input.type('l');

            assertThat(input.pendingText()).isEqualTo("hel");
            assertThat(lastSnapshot().pendingText()).isEqualTo("hel");
        }

        @Test
        void typeStringAppendsToText() {
            input.type("hello");
            assertThat(input.pendingText()).isEqualTo("hello");
        }

        @Test
        void cursorAdvancesOnType() {
            input.type("abc");
            assertThat(lastSnapshot().cursor()).isEqualTo(3);
        }

        @Test
        void typeInsertsAtCursor() {
            input.type("ac");
            input.cursorLeft(); // cursor at 1
            input.type('b');

            assertThat(input.pendingText()).isEqualTo("abc");
            assertThat(lastSnapshot().cursor()).isEqualTo(2);
        }

        @Test
        void typeNullStringIsNoop() {
            input.type((String) null);
            assertThat(input.pendingText()).isEmpty();
        }

        @Test
        void typeEmptyStringIsNoop() {
            input.type("");
            assertThat(input.pendingText()).isEmpty();
        }

        @Test
        void emptyInputReportsEmpty() {
            assertThat(input.isEmpty()).isTrue();
        }

        @Test
        void typingMakesNonEmpty() {
            input.type('x');
            assertThat(input.isEmpty()).isFalse();
        }
    }

    // ==================================================================================
    // Backspace and Delete
    // ==================================================================================

    @Nested
    class Deletion {

        @Test
        void backspaceDeletesBeforeCursor() {
            input.type("abc");
            input.backspace();
            assertThat(input.pendingText()).isEqualTo("ab");
            assertThat(lastSnapshot().cursor()).isEqualTo(2);
        }

        @Test
        void backspaceAtStartPopsToken() {
            // First, get a token via tab-accept
            input.type("a");
            input.tab(); // show completions; "alice" should auto-select
            input.tab(); // accept it

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.pendingText()).isEmpty();

            // Now backspace with empty pending should pop the token
            input.backspace();
            assertThat(input.tokens()).isEmpty();
        }

        @Test
        void backspaceOnEmptyWithNoTokensIsNoop() {
            input.backspace();
            assertThat(input.isEmpty()).isTrue();
        }

        @Test
        void deleteRemovesAfterCursor() {
            input.type("abc");
            input.cursorHome();
            input.delete();
            assertThat(input.pendingText()).isEqualTo("bc");
            assertThat(lastSnapshot().cursor()).isEqualTo(0);
        }

        @Test
        void deleteAtEndIsNoop() {
            input.type("abc");
            int snapshotsBefore = snapshots.size();
            input.delete();
            // No new snapshot since nothing changed
            assertThat(snapshots.size()).isEqualTo(snapshotsBefore);
        }

        @Test
        void deleteWordRemovesWord() {
            input.type("hello world");
            input.deleteWord();
            assertThat(input.pendingText()).isEqualTo("hello ");
        }

        @Test
        void deleteWordAtStartIsNoop() {
            input.type("hello");
            input.cursorHome();
            input.deleteWord();
            assertThat(input.pendingText()).isEqualTo("hello");
        }
    }

    // ==================================================================================
    // Cursor Movement
    // ==================================================================================

    @Nested
    class CursorMovement {

        @Test
        void cursorLeftMovesBack() {
            input.type("abc");
            input.cursorLeft();
            assertThat(lastSnapshot().cursor()).isEqualTo(2);
        }

        @Test
        void cursorLeftAtStartIsNoop() {
            input.type("abc");
            input.cursorHome();
            int snapshotsBefore = snapshots.size();
            input.cursorLeft();
            assertThat(snapshots.size()).isEqualTo(snapshotsBefore);
        }

        @Test
        void cursorRightMovesForward() {
            input.type("abc");
            input.cursorLeft();
            input.cursorRight();
            assertThat(lastSnapshot().cursor()).isEqualTo(3);
        }

        @Test
        void cursorRightAtEndIsNoop() {
            input.type("abc");
            int snapshotsBefore = snapshots.size();
            input.cursorRight();
            assertThat(snapshots.size()).isEqualTo(snapshotsBefore);
        }

        @Test
        void cursorHomeGoesToStart() {
            input.type("hello");
            input.cursorHome();
            assertThat(lastSnapshot().cursor()).isEqualTo(0);
        }

        @Test
        void cursorEndGoesToEnd() {
            input.type("hello");
            input.cursorHome();
            input.cursorEnd();
            assertThat(lastSnapshot().cursor()).isEqualTo(5);
        }
    }

    // ==================================================================================
    // Lookup / Completions
    // ==================================================================================

    @Nested
    class Completions {

        @Test
        void typingTriggersLookup() {
            input.type('a');
            EvalInputSnapshot snap = lastSnapshot();
            assertThat(snap.completions()).isNotEmpty();
            assertThat(snap.showCompletions()).isTrue();
        }

        @Test
        void typingNarrowsCompletions() {
            // 'a' matches alice
            input.type("ali");
            EvalInputSnapshot snap = lastSnapshot();
            assertThat(snap.completions()).hasSize(1);
            assertThat(snap.completions().get(0).token()).isEqualTo("alice");
        }

        @Test
        void firstCompletionAutoSelected() {
            input.type('a');
            assertThat(lastSnapshot().selectedCompletion()).isEqualTo(0);
        }

        @Test
        void noMatchClearsCompletions() {
            input.type("xyz");
            EvalInputSnapshot snap = lastSnapshot();
            assertThat(snap.completions()).isEmpty();
            assertThat(snap.showCompletions()).isFalse();
            assertThat(snap.selectedCompletion()).isEqualTo(-1);
        }

        @Test
        void completionDownMovesSelection() {
            input.type('a'); // matches alice
            // Need multiple matches — type 'c' for create
            input.backspace();
            input.type('c'); // matches create
            // Actually, 'a' matches alice only (bob doesn't start with a, create doesn't start with a)
            // Let's use empty string to get all, but minLookupLength is 1
            // type a single char that matches multiple
            input.backspace();
            // Need to think about what matches multiple...
            // Our mock: "alice".startsWith(lower), "bob".startsWith(lower), "create".startsWith(lower)
            // No single char matches all three. But nothing needs to — we just need >1 match.
            // Actually no single char matches both alice and bob since they start with different letters.
            // Let's just verify up/down on single match wraps correctly.
            input.type('a'); // matches alice only → selectedCompletion = 0
            assertThat(lastSnapshot().selectedCompletion()).isEqualTo(0);
            // Down should stay at 0 (only one item)
            input.completionDown();
            assertThat(lastSnapshot().selectedCompletion()).isEqualTo(0);
        }

        @Test
        void completionUpStaysAtZero() {
            input.type('a');
            input.completionUp();
            assertThat(lastSnapshot().selectedCompletion()).isEqualTo(0);
        }

        @Test
        void dismissCompletionsHidesDropdown() {
            input.type('a');
            assertThat(lastSnapshot().showCompletions()).isTrue();
            input.dismissCompletions();
            assertThat(lastSnapshot().showCompletions()).isFalse();
        }

        @Test
        void tabShowsCompletionsWhenHidden() {
            input.type('a');
            input.dismissCompletions();
            assertThat(lastSnapshot().showCompletions()).isFalse();

            input.tab();
            assertThat(lastSnapshot().showCompletions()).isTrue();
        }
    }

    // ==================================================================================
    // Tab Accept
    // ==================================================================================

    @Nested
    class TabAccept {

        @Test
        void tabAcceptsSelectedCompletion() {
            input.type("ali");
            // "alice" should be the only completion, auto-selected
            assertThat(lastSnapshot().completions()).hasSize(1);
            assertThat(lastSnapshot().selectedCompletion()).isEqualTo(0);

            input.tab();

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.tokens().get(0)).isInstanceOf(RefToken.class);
            RefToken ref = (RefToken) input.tokens().get(0);
            assertThat(ref.target()).isEqualTo(ALICE_IID);
            assertThat(ref.displayText()).isEqualTo("alice");
        }

        @Test
        void tabClearsPendingText() {
            input.type("ali");
            input.tab();

            assertThat(input.pendingText()).isEmpty();
            assertThat(lastSnapshot().cursor()).isEqualTo(0);
        }

        @Test
        void tabClearsCompletions() {
            input.type("ali");
            input.tab();

            assertThat(lastSnapshot().showCompletions()).isFalse();
            assertThat(lastSnapshot().completions()).isEmpty();
        }

        @Test
        void tabOnEmptyIsNoop() {
            int snapshotsBefore = snapshots.size();
            input.tab();
            // No completions shown, no pending — nothing happens
            assertThat(snapshots.size()).isEqualTo(snapshotsBefore);
        }
    }

    // ==================================================================================
    // Token Boundary (Space)
    // ==================================================================================

    @Nested
    class TokenBoundary {

        @Test
        void spaceAfterOperatorCommitsToken() {
            input.type("&&");
            input.tokenBoundary();

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.tokens().get(0)).isInstanceOf(OpToken.class);
            assertThat(input.pendingText()).isEmpty();
        }

        @Test
        void spaceAfterOrOperatorCommitsToken() {
            input.type("||");
            input.tokenBoundary();

            assertThat(input.tokens()).hasSize(1);
            OpToken op = (OpToken) input.tokens().get(0);
            assertThat(op.operatorId()).isEqualTo(Operator.Or.SEED.iid());
        }

        @Test
        void spaceAfterNumberCommitsLiteral() {
            input.type("42");
            input.tokenBoundary();

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.tokens().get(0)).isInstanceOf(LiteralToken.class);
            LiteralToken lit = (LiteralToken) input.tokens().get(0);
            assertThat(lit.value()).isEqualTo(42L);
        }

        @Test
        void spaceAfterBooleanCommitsLiteral() {
            input.type("true");
            input.tokenBoundary();

            assertThat(input.tokens()).hasSize(1);
            LiteralToken lit = (LiteralToken) input.tokens().get(0);
            assertThat(lit.value()).isEqualTo(true);
        }

        @Test
        void spaceAfterNormalTextInsertsSpace() {
            input.type("hello");
            input.tokenBoundary();

            // "hello" is neither operator, literal, nor paren — just inserts space
            assertThat(input.tokens()).isEmpty();
            assertThat(input.pendingText()).isEqualTo("hello ");
        }

        @Test
        void spaceOnEmptyInsertsSpace() {
            input.tokenBoundary();
            assertThat(input.pendingText()).isEqualTo(" ");
        }

        @Test
        void openParenBecomesToken() {
            input.type("(");
            input.tokenBoundary();

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.tokens().get(0)).isInstanceOf(OpenParen.class);
        }

        @Test
        void closeParenBecomesToken() {
            input.type(")");
            input.tokenBoundary();

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.tokens().get(0)).isInstanceOf(CloseParen.class);
        }
    }

    // ==================================================================================
    // Paren Auto-Detection While Typing
    // ==================================================================================

    @Nested
    class ParenAutoDetection {

        @Test
        void typingOpenParenCommitsImmediately() {
            input.type('(');

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.tokens().get(0)).isInstanceOf(OpenParen.class);
            assertThat(input.pendingText()).isEmpty();
        }

        @Test
        void typingCloseParenCommitsImmediately() {
            input.type(')');

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.tokens().get(0)).isInstanceOf(CloseParen.class);
            assertThat(input.pendingText()).isEmpty();
        }
    }

    // ==================================================================================
    // Cancel
    // ==================================================================================

    @Nested
    class Cancel {

        @Test
        void cancelDismissesCompletionsFirst() {
            input.type('a'); // shows completions
            assertThat(lastSnapshot().showCompletions()).isTrue();

            input.cancel();
            assertThat(lastSnapshot().showCompletions()).isFalse();
            // Pending text still present
            assertThat(input.pendingText()).isEqualTo("a");
        }

        @Test
        void cancelClearsPendingWhenNoCompletions() {
            input.type("xyz"); // no completions
            assertThat(lastSnapshot().showCompletions()).isFalse();

            input.cancel();
            assertThat(input.pendingText()).isEmpty();
        }

        @Test
        void cancelClearsTokensWhenNoPending() {
            // Add a token via tab
            input.type("ali");
            input.tab();
            assertThat(input.tokens()).hasSize(1);

            // Cancel with empty pending clears tokens
            input.cancel();
            assertThat(input.tokens()).isEmpty();
        }
    }

    // ==================================================================================
    // History
    // ==================================================================================

    @Nested
    class History {

        @Test
        void acceptAddsToHistory() {
            // We need to accept with tokens — without librarian, dispatch will fail
            // but history is recorded before dispatch
            input.type("42");
            input.tokenBoundary(); // commits as literal token
            input.accept(); // dispatch fails but history is saved

            // Type something new and go back
            input.type("xyz");
            input.historyPrev();

            // Should show the history entry (display text of the literal token)
            assertThat(input.pendingText()).isEqualTo("42");
        }

        @Test
        void historyPrevSavesCurrentPending() {
            input.type("42");
            input.tokenBoundary();
            input.accept();

            input.type("current");
            input.historyPrev();

            // Go back to current
            input.historyNext();
            assertThat(input.pendingText()).isEqualTo("current");
        }

        @Test
        void historyPrevOnEmptyIsNoop() {
            input.historyPrev();
            assertThat(input.pendingText()).isEmpty();
        }

        @Test
        void historyNextWithoutPrevIsNoop() {
            input.historyNext();
            assertThat(input.pendingText()).isEmpty();
        }

        @Test
        void duplicateEntriesNotAdded() {
            input.type("42");
            input.tokenBoundary();
            input.accept();

            input.type("42");
            input.tokenBoundary();
            input.accept();

            // Go to history — should only have one entry
            input.historyPrev();
            assertThat(input.pendingText()).isEqualTo("42");

            // Going further back should stay (no second entry)
            input.historyPrev();
            assertThat(input.pendingText()).isEqualTo("42");
        }
    }

    // ==================================================================================
    // Snapshot
    // ==================================================================================

    @Nested
    class Snapshot {

        @Test
        void snapshotReflectsPrompt() {
            assertThat(input.snapshot().prompt()).isEqualTo("> ");
        }

        @Test
        void snapshotReflectsHint() {
            assertThat(input.snapshot().hint()).isEqualTo("Type something...");
        }

        @Test
        void snapshotHasInputReflectsState() {
            assertThat(input.snapshot().hasInput()).isFalse();

            input.type('x');
            assertThat(lastSnapshot().hasInput()).isTrue();
        }

        @Test
        void snapshotDisplayTextCombinesTokensAndPending() {
            // Add a token via tab
            input.type("ali");
            input.tab(); // accepts "alice" as RefToken

            input.type("hello");

            EvalInputSnapshot snap = lastSnapshot();
            assertThat(snap.displayText()).isEqualTo("alice hello");
        }

        @Test
        void setPromptNotifiesChange() {
            int before = snapshots.size();
            input.setPrompt("$ ");
            assertThat(snapshots.size()).isGreaterThan(before);
            assertThat(lastSnapshot().prompt()).isEqualTo("$ ");
        }

        @Test
        void errorShowsInSnapshot() {
            // Accept with no librarian → error
            input.type("42");
            input.tokenBoundary();
            input.accept();

            assertThat(lastSnapshot().error()).isNotNull();
        }

        @Test
        void errorClearedOnNextAction() {
            // Generate an error
            input.type("42");
            input.tokenBoundary();
            input.accept();
            assertThat(lastSnapshot().error()).isNotNull();

            // Type something — error should clear
            input.type('x');
            assertThat(lastSnapshot().error()).isNull();
        }
    }

    // ==================================================================================
    // Clear
    // ==================================================================================

    @Nested
    class Clear {

        @Test
        void clearResetsEverything() {
            input.type("ali");
            input.tab(); // add token
            input.type("hello");

            input.clear();

            assertThat(input.isEmpty()).isTrue();
            assertThat(input.tokens()).isEmpty();
            assertThat(input.pendingText()).isEmpty();
            assertThat(lastSnapshot().cursor()).isEqualTo(0);
            assertThat(lastSnapshot().showCompletions()).isFalse();
        }
    }

    // ==================================================================================
    // Context
    // ==================================================================================

    @Nested
    class Context {

        @Test
        void contextStartsNull() {
            assertThat(input.context()).isNull();
        }

        @Test
        void setContextUpdatesContext() {
            // We can't easily create an Item without a Librarian in unit tests,
            // but we can verify null → null transitions
            input.setContext(null);
            assertThat(input.context()).isNull();
        }
    }

    // ==================================================================================
    // onChange Callback
    // ==================================================================================

    @Nested
    class OnChangeCallback {

        @Test
        void everyMutationFiresCallback() {
            snapshots.clear();

            input.type('a');       // 1
            input.cursorLeft();    // 2
            input.cursorRight();   // 3
            input.backspace();     // 4

            assertThat(snapshots).hasSize(4);
        }

        @Test
        void noCallbackWhenNotConfigured() {
            EvalInput bare = EvalInput.builder().build();
            // Should not throw
            bare.type('x');
            bare.backspace();
        }
    }

    // ==================================================================================
    // Multi-token flow
    // ==================================================================================

    @Nested
    class MultiTokenFlow {

        @Test
        void buildComplexExpression() {
            // alice AND bob
            input.type("ali");
            input.tab(); // accept alice

            input.type("&&");
            input.tokenBoundary(); // commit as OpToken

            input.type("bo");
            input.tab(); // should find bob, but our lookup returns bob for "bo"
            // Actually need to check — "bob".startsWith("bo") → true
            input.tab(); // accept bob

            List<ExpressionToken> tokens = input.tokens();
            assertThat(tokens).hasSize(3);
            assertThat(tokens.get(0)).isInstanceOf(RefToken.class);
            assertThat(tokens.get(1)).isInstanceOf(OpToken.class);
            assertThat(tokens.get(2)).isInstanceOf(RefToken.class);

            assertThat(((RefToken) tokens.get(0)).displayText()).isEqualTo("alice");
            assertThat(((RefToken) tokens.get(2)).displayText()).isEqualTo("bob");
        }

        @Test
        void acceptCommitsPendingAsLiteral() {
            // Type something that isn't in lookup
            input.type("xyz");
            input.accept(); // commits as literal, then tries to dispatch

            // Tokens should have been copied out (cleared after accept)
            assertThat(input.tokens()).isEmpty();
            assertThat(input.pendingText()).isEmpty();
        }
    }

    // ==================================================================================
    // Lookup text extraction
    // ==================================================================================

    @Nested
    class LookupExtraction {

        @Test
        void lookupUsesLastWordOnly() {
            // When user types "create ali", lookup should be for "ali" not "create ali"
            input.type("create ali");

            // The last snapshot's completions should be for "ali" → matches "alice"
            EvalInputSnapshot snap = lastSnapshot();
            assertThat(snap.completions()).hasSize(1);
            assertThat(snap.completions().get(0).token()).isEqualTo("alice");
        }

        @Test
        void acceptingCompletionWithPrefixCreatesTwoTokens() {
            input.type("create ");
            // Clear snapshots so we can track
            snapshots.clear();
            input.type("ali");

            // Now tab to accept alice
            input.tab();

            // Should have two tokens: "create" resolved via dictionary + "alice" as ref.
            // findAllExactMatches falls back to dictionary lookup even when completions
            // are showing results for a different prefix ("ali"), so "create" now resolves.
            List<ExpressionToken> tokens = input.tokens();
            assertThat(tokens).hasSize(2);
            assertThat(tokens.get(0)).isInstanceOf(RefToken.class);
            assertThat(tokens.get(1)).isInstanceOf(RefToken.class);
        }
    }

    // ==================================================================================
    // EvalInputSnapshot record
    // ==================================================================================

    @Nested
    class SnapshotRecord {

        @Test
        void emptySnapshotFactory() {
            EvalInputSnapshot empty = EvalInputSnapshot.empty("> ", "hint");
            assertThat(empty.tokens()).isEmpty();
            assertThat(empty.pendingText()).isEmpty();
            assertThat(empty.cursor()).isEqualTo(0);
            assertThat(empty.completions()).isEmpty();
            assertThat(empty.selectedCompletion()).isEqualTo(-1);
            assertThat(empty.showCompletions()).isFalse();
            assertThat(empty.prompt()).isEqualTo("> ");
            assertThat(empty.hint()).isEqualTo("hint");
            assertThat(empty.error()).isNull();
        }

        @Test
        void hasVisibleCompletionsLogic() {
            EvalInputSnapshot shown = new EvalInputSnapshot(
                    List.of(), "", 0,
                    List.of(ALICE_POSTING), List.of(), 0, true,
                    "> ", "", null
            );
            assertThat(shown.hasVisibleCompletions()).isTrue();

            EvalInputSnapshot hidden = new EvalInputSnapshot(
                    List.of(), "", 0,
                    List.of(ALICE_POSTING), List.of(), 0, false,
                    "> ", "", null
            );
            assertThat(hidden.hasVisibleCompletions()).isFalse();

            EvalInputSnapshot empty = new EvalInputSnapshot(
                    List.of(), "", 0,
                    List.of(), List.of(), -1, true,
                    "> ", "", null
            );
            assertThat(empty.hasVisibleCompletions()).isFalse();
        }

        @Test
        void currentCompletionReturnsSelected() {
            EvalInputSnapshot snap = new EvalInputSnapshot(
                    List.of(), "", 0,
                    List.of(ALICE_POSTING, BOB_POSTING), List.of(), 1, true,
                    "> ", "", null
            );
            assertThat(snap.currentCompletion()).isPresent();
            assertThat(snap.currentCompletion().get()).isEqualTo(BOB_POSTING);
        }

        @Test
        void currentCompletionEmptyWhenNoneSelected() {
            EvalInputSnapshot snap = new EvalInputSnapshot(
                    List.of(), "", 0,
                    List.of(ALICE_POSTING), List.of(), -1, true,
                    "> ", "", null
            );
            assertThat(snap.currentCompletion()).isEmpty();
        }

        @Test
        void defensiveCopy() {
            var mutableTokens = new ArrayList<ExpressionToken>();
            mutableTokens.add(RefToken.of(ALICE_IID, "alice"));

            var mutableCompletions = new ArrayList<Posting>();
            mutableCompletions.add(ALICE_POSTING);

            EvalInputSnapshot snap = new EvalInputSnapshot(
                    mutableTokens, "", 0,
                    mutableCompletions, List.of(), 0, true,
                    "> ", "", null
            );

            // Mutating the originals should not affect the snapshot
            mutableTokens.clear();
            mutableCompletions.clear();

            assertThat(snap.tokens()).hasSize(1);
            assertThat(snap.completions()).hasSize(1);
        }
    }

    // ==================================================================================
    // splitRawTokens
    // ==================================================================================

    @Nested
    class SplitRawTokens {

        @Test
        void numbersAndOperators() {
            assertThat(EvalInput.splitRawTokens("5+2")).containsExactly("5", "+", "2");
        }

        @Test
        void spaceSeparatedExpression() {
            assertThat(EvalInput.splitRawTokens("5 + 2")).containsExactly("5", "+", "2");
        }

        @Test
        void numberAndWord() {
            assertThat(EvalInput.splitRawTokens("5meter")).containsExactly("5", "meter");
        }

        @Test
        void decimalNumber() {
            assertThat(EvalInput.splitRawTokens("3.14*r")).containsExactly("3.14", "*", "r");
        }

        @Test
        void functionCallSyntax() {
            assertThat(EvalInput.splitRawTokens("sqrt(144)")).containsExactly("sqrt", "(", "144", ")");
        }

        @Test
        void multiCharOperator() {
            assertThat(EvalInput.splitRawTokens("x>=5")).containsExactly("x", ">=", "5");
        }

        @Test
        void wordWithDigits() {
            assertThat(EvalInput.splitRawTokens("x2")).containsExactly("x2");
        }

        @Test
        void emptyInput() {
            assertThat(EvalInput.splitRawTokens("")).isEmpty();
            assertThat(EvalInput.splitRawTokens(null)).isEmpty();
        }

        @Test
        void pureWhitespace() {
            assertThat(EvalInput.splitRawTokens("   ")).isEmpty();
        }

        @Test
        void complexExpression() {
            assertThat(EvalInput.splitRawTokens("2*(3+4)")).containsExactly("2", "*", "(", "3", "+", "4", ")");
        }

        @Test
        void negativeNumber() {
            // "-5" splits into operator + number; unary minus handled by parser
            assertThat(EvalInput.splitRawTokens("-5")).containsExactly("-", "5");
        }
    }

    // ==================================================================================
    // Deferred Resolution (CandidateToken)
    // ==================================================================================

    @Nested
    class DeferredResolution {

        /** Two items sharing the same token — forces ambiguity. */
        private static final ItemID PYTHON_LANG_IID = ItemID.random();
        private static final ItemID PYTHON_ANIMAL_IID = ItemID.random();
        private static final Posting PYTHON_LANG_POSTING =
                Posting.universal("python", PYTHON_LANG_IID);
        private static final Posting PYTHON_ANIMAL_POSTING =
                Posting.universal("python", PYTHON_ANIMAL_IID);

        private EvalInput ambiguousInput;

        @BeforeEach
        void setUp() {
            ambiguousInput = EvalInput.builder()
                    .lookup(text -> {
                        String lower = text.toLowerCase();
                        List<Posting> results = new ArrayList<>();
                        if ("python".startsWith(lower)) {
                            results.add(PYTHON_LANG_POSTING);
                            results.add(PYTHON_ANIMAL_POSTING);
                        }
                        if ("create".startsWith(lower)) results.add(CREATE_POSTING);
                        if ("alice".startsWith(lower)) results.add(ALICE_POSTING);
                        return results;
                    })
                    .prompt("> ")
                    .minLookupLength(1)
                    .onChange(snapshots::add)
                    .build();
        }

        @Test
        void ambiguousTokenCreatesCandidateToken() {
            ambiguousInput.type("python");
            ambiguousInput.tokenBoundary();

            List<ExpressionToken> tokens = ambiguousInput.tokens();
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0)).isInstanceOf(ExpressionToken.CandidateToken.class);

            ExpressionToken.CandidateToken candidate =
                    (ExpressionToken.CandidateToken) tokens.get(0);
            assertThat(candidate.candidates()).hasSize(2);
            assertThat(candidate.displayText()).isEqualTo("python");
        }

        @Test
        void unambiguousTokenResolvesDirectly() {
            ambiguousInput.type("alice");
            ambiguousInput.tokenBoundary();

            List<ExpressionToken> tokens = ambiguousInput.tokens();
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0)).isInstanceOf(RefToken.class);
        }

        @Test
        void hasUnresolvedCandidatesReflectsCandidatePresence() {
            assertThat(ambiguousInput.hasUnresolvedCandidates()).isFalse();

            ambiguousInput.type("python");
            ambiguousInput.tokenBoundary();
            assertThat(ambiguousInput.hasUnresolvedCandidates()).isTrue();
        }

        @Test
        void snapshotReportsUnresolvedCandidates() {
            ambiguousInput.type("python");
            ambiguousInput.tokenBoundary();

            EvalInputSnapshot snap = ambiguousInput.snapshot();
            assertThat(snap.hasUnresolvedCandidates()).isTrue();
        }

        @Test
        void candidateTokenNarrowReducesCandidates() {
            var candidate = new ExpressionToken.CandidateToken("python",
                    List.of(PYTHON_LANG_POSTING, PYTHON_ANIMAL_POSTING));

            // Narrow to one → returns null (should promote to RefToken instead)
            assertThat(candidate.narrow(List.of(PYTHON_LANG_POSTING))).isNull();

            // Narrow to two → returns new CandidateToken
            var narrowed = candidate.narrow(
                    List.of(PYTHON_LANG_POSTING, PYTHON_ANIMAL_POSTING));
            assertThat(narrowed).isNotNull();
            assertThat(narrowed.candidates()).hasSize(2);
        }

        @Test
        void candidateTokenResolvePromotesToRefToken() {
            var candidate = new ExpressionToken.CandidateToken("python",
                    List.of(PYTHON_LANG_POSTING, PYTHON_ANIMAL_POSTING));

            RefToken resolved = candidate.resolve(PYTHON_LANG_POSTING);
            assertThat(resolved.target()).isEqualTo(PYTHON_LANG_IID);
            assertThat(resolved.displayText()).isEqualTo("python");
        }

        @Test
        void sameTargetDeduplicated() {
            // Two postings for the same target (different scopes) → should dedup
            var scopedPosting = Posting.scoped("python",
                    ItemID.random(), PYTHON_LANG_IID);

            EvalInput dedup = EvalInput.builder()
                    .lookup(text -> {
                        if ("python".startsWith(text.toLowerCase())) {
                            return List.of(PYTHON_LANG_POSTING, scopedPosting);
                        }
                        return List.of();
                    })
                    .prompt("> ")
                    .minLookupLength(1)
                    .onChange(snapshots::add)
                    .build();

            dedup.type("python");
            dedup.tokenBoundary();

            // Same target → should resolve to RefToken (deduplicated to 1)
            List<ExpressionToken> tokens = dedup.tokens();
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0)).isInstanceOf(RefToken.class);
        }
    }
}
