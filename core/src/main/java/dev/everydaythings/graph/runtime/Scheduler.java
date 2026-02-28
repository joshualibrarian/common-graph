package dev.everydaythings.graph.runtime;

import java.io.Closeable;
import java.time.Duration;

public interface Scheduler extends Closeable {
    void submit(Runnable task);

    void scheduleAtFixedRate(Runnable task, Duration period);

    @Override
    default void close() {
    }
}
