package dev.everydaythings.graph.encoding;

import java.util.function.Consumer;

/**
 * Handle to an in-progress streaming parse.
 *
 * <p>Returned by {@link Encoding#parseStream(Consumer, Consumer)}.  Callers
 * feed bytes via {@link #feed(byte[])} as they arrive from a
 * {@link dev.everydaythings.graph.network.tunnel.Tunnel} (or any other byte
 * source); the parser buffers internally and dispatches whole top-level
 * values to the consumer registered at construction time.  A single
 * {@code feed} call may produce zero, one, or many dispatches depending on
 * the byte chunking.
 *
 * <p>Implements {@link Consumer Consumer&lt;byte[]&gt;} so it can be wired
 * directly into
 * {@link dev.everydaythings.graph.network.tunnel.Tunnel#onReceive(Consumer)}.
 */
public interface StreamParser extends AutoCloseable, Consumer<byte[]> {

    /**
     * Append a chunk of bytes to the parser's buffer; dispatch any whole
     * top-level values that complete as a result.  Partial values stay
     * buffered until further bytes arrive.
     */
    void feed(byte[] bytes);

    /**
     * Convenience: {@link Consumer} signature delegates to {@link #feed}.
     * Lets a parser be passed directly to {@code tunnel.onReceive(parser)}.
     */
    @Override
    default void accept(byte[] bytes) {
        feed(bytes);
    }

    /**
     * Release parser resources.  Any bytes buffered but not yet forming a
     * complete value are discarded.
     */
    @Override
    void close();
}
