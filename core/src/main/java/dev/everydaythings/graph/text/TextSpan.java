package dev.everydaythings.graph.text;

import lombok.Value;
import lombok.With;

/**
 * Half-open text position: {@code [start, end)}.
 *
 * <p><b>Offsets are grapheme cluster boundaries per UAX #29</b>, not bytes or code points.
 * A grapheme cluster is what a user perceives as a single character: an ASCII letter,
 * a CJK ideograph, an emoji ZWJ sequence (e.g. 👨‍👩‍👧‍👦 is one grapheme), a base letter with
 * combining marks, a regional flag pair, etc. Use ICU's
 * {@code BreakIterator.getCharacterInstance(locale)} to walk grapheme boundaries when
 * computing or applying spans.
 *
 * <p>This unit aligns with cursor positioning, click selection, and chip highlighting in
 * the UI, which is what spans exist to support.
 *
 * <p>Single shared class across the text pipeline — used by {@link FrameMap}'s parts,
 * {@link AnchorTable}'s anchors, and {@code TokenLattice}'s token spans. One concept,
 * one type, one definition of grapheme-cluster offsets everywhere.
 */
@Value @With
public class TextSpan {
    int start;
    int end;

    public int length() {
        return end - start;
    }

    public boolean contains(int position) {
        return position >= start && position < end;
    }

    public boolean overlaps(TextSpan other) {
        return start < other.end && other.start < end;
    }
}
