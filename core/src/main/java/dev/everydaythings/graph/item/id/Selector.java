package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.Canonical;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A selector identifies a fragment within frame content.
 *
 * <p>Selectors are carried inside {@link Ref} to address byte ranges,
 * time spans, or structural paths within content.
 *
 * <p>In binary (inside a Ref), selector data follows the {@code 0x5B} marker.
 * In text, it appears in brackets: {@code [0..1024]}.
 */
public final class Selector implements Canonical {

    private final byte[] data;

    private Selector(byte[] data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /** Selector with no content (degenerate — equivalent to no selector). */
    public static final Selector EMPTY = new Selector(new byte[0]);

    /** Create a selector from raw binary data. */
    public static Selector fromBinary(byte[] bytes) {
        return new Selector(bytes);
    }

    /** Create a selector from text (e.g., "0..1024"). */
    public static Selector fromText(String text) {
        if (text == null || text.isEmpty()) return EMPTY;
        return new Selector(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Create a byte-range selector. */
    public static Selector byteRange(long start, long end) {
        return fromText(start + ".." + end);
    }

    /** Raw binary representation. */
    public byte[] binary() {
        return data.clone();
    }

    /** Text representation. */
    public String text() {
        if (data.length == 0) return "";
        return new String(data, StandardCharsets.UTF_8);
    }

    /** True if this selector has no content. */
    public boolean isEmpty() {
        return data.length == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Selector other)) return false;
        return Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return isEmpty() ? "[]" : "[" + text() + "]";
    }
}
