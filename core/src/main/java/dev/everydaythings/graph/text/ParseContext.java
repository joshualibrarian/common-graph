package dev.everydaythings.graph.text;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.text.TokenLattice.TokenSpan;
import lombok.Value;
import lombok.With;

import java.util.List;

/**
 * Per-round context passed to participants during a parse.
 *
 * <p>Each round, the engine builds a fresh {@link ParseContext} carrying the running
 * draft, the active anchors, and a reference to the orchestrating item. Participants
 * read from this context and return their delta — a {@link FrameMap} expressing their
 * picture of what the frame should look like. They cannot modify the context they
 * receive; both {@link FrameMap} and {@link AnchorTable} are immutable, and the engine
 * is the only thing that constructs new {@code ParseContext} instances.
 *
 * <p>The text being parsed is on the {@code draft} ({@code ctx.draft().text()}) so it
 * isn't duplicated. In round 1, the draft is initialized with the input text and
 * empty everything else; subsequent rounds inherit the text via merge.
 *
 * <p>The engine manages anchor lifecycle (token anchors recomputed each round from the
 * lattice; user anchors carried forward across keystrokes while their text-spans
 * survive) and exposes the current {@code AnchorTable} via this context. Participants
 * read; the engine writes (by constructing new contexts).
 */
@Value @With
public class ParseContext {

    /** The orchestrator's running consensus from prior rounds. Carries the input text. */
    FrameMap draft;

    /** Anchored participants and user clarifications for this round. Read-only to participants. */
    AnchorTable anchors;

    /** The item that owns the prompt receiving input — the orchestrator. */
    Item orchestrator;

    /**
     * Resolved tokens in best-path order from the {@link TokenLattice}. Whitespace is
     * already filtered out at tokenization. Participants typically use this to find
     * neighboring tokens of their own anchor — e.g., a binary operator finds its left
     * and right operands by index in this list.
     */
    List<TokenSpan> tokens;
}
