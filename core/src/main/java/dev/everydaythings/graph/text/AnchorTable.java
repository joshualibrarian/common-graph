package dev.everydaythings.graph.text;

import dev.everydaythings.graph.item.Item;
import lombok.Value;
import lombok.With;

import java.util.List;

/**
 * The anchored state carried inside a {@link FrameMap}.
 *
 * <p>Anchors are the only state maintained between consensus rounds and between
 * keystrokes. Each anchor ties a parse-relevant artifact to a text span; when the
 * span no longer corresponds to the current input, the anchor dies and whatever it
 * anchored leaves the participant set.
 *
 * <p>The AnchorTable is held inside the FrameMap and is only meaningful to the
 * consensus engine. External code generally doesn't interact with it directly.
 *
 * <p>Two kinds of anchors are tracked:
 * <ul>
 *   <li><b>Token anchors</b> — active (code-bearing, parse-overriding) participating
 *       items kept alive by the tokens that resolved them. Pure-data sememes are
 *       <i>not</i> in this table; they're queried via the librarian when needed and
 *       never instantiated.</li>
 *   <li><b>User anchors</b> — explicit user clarifications (dropdown picks, completion
 *       choices) anchored to the text span they were given for. While the span
 *       survives, the clarification re-emits as a max-weight contribution each round.</li>
 * </ul>
 *
 * <p>Standing participants (the orchestrator, Session, Librarian, vocabulary-scope
 * Languages) are not tracked here — they're always in the participant set regardless
 * of text content.
 */
@Value @With
public class AnchorTable {

    /** Active in-memory items participating because their anchor span(s) still match the input text. */
    List<TokenAnchor> tokenAnchors;

    /** User-provided clarifications anchored to text-spans, surviving until the span is deleted. */
    List<UserAnchor> userAnchors;

    public static AnchorTable empty() {
        return new AnchorTable(List.of(), List.of());
    }

    public boolean isEmpty() {
        return tokenAnchors.isEmpty() && userAnchors.isEmpty();
    }

    /**
     * An active in-memory item kept alive by the spans that resolved it. Most resolutions
     * are multi-character single-token (one span); multi-word resolutions (e.g.
     * "Buenos Aires" as one sememe) span multiple tokens and so multiple spans.
     */
    @Value @With
    public static class TokenAnchor {
        List<TextSpan> spans;
        Item participant;
    }

    /**
     * A user clarification carried forward across rounds and across keystrokes as long
     * as the anchor span survives. The clarification is itself a FrameMap fragment that
     * the User-participant re-emits at max confidence each round.
     */
    @Value @With
    public static class UserAnchor {
        TextSpan span;
        FrameMap clarification;
    }
}
