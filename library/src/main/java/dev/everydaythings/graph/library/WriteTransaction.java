package dev.everydaythings.graph.library;

public interface WriteTransaction extends AutoCloseable {
    void commit();           // idempotent
    void rollback();         // optional: auto if close() without commit
//    boolean isCommitted();

    @Override
    void close();  // auto-rollback if not committed
}