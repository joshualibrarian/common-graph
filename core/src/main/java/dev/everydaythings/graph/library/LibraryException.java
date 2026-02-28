package dev.everydaythings.graph.library;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.VersionID;

/**
 * Base exception for all Library operations.
 *
 * <p>Nested static classes provide specific exception types while keeping
 * class clutter down. Catch {@code LibraryException} for broad handling,
 * or catch specific nested types for fine-grained control.
 *
 * <pre>{@code
 * try {
 *     library.getManifest(iid, vid);
 * } catch (LibraryException.NotFound e) {
 *     // handle missing item
 * } catch (LibraryException e) {
 *     // handle any library error
 * }
 * }</pre>
 */
public class LibraryException extends RuntimeException {

    public LibraryException(String message) {
        super(message);
    }

    public LibraryException(String message, Throwable cause) {
        super(message, cause);
    }

    // ==================================================================================
    // Store Exceptions
    // ==================================================================================

    /** Item or version not found in store. */
    public static class NotFound extends LibraryException {
        public NotFound(String message) {
            super(message);
        }

        public NotFound(ItemID iid) {
            super("Item not found: " + iid);
        }

        public NotFound(ItemID iid, VersionID vid) {
            super("Manifest not found: " + iid + "@" + vid);
        }
    }

    /** Item already exists when trying to create. */
    public static class AlreadyExists extends LibraryException {
        public AlreadyExists(String message) {
            super(message);
        }

        public AlreadyExists(ItemID iid) {
            super("Item already exists: " + iid);
        }
    }

    /** Store is closed or unavailable. */
    public static class StoreClosed extends LibraryException {
        public StoreClosed(String message) {
            super(message);
        }

        public StoreClosed() {
            super("Store is closed");
        }
    }

    // ==================================================================================
    // Directory Exceptions
    // ==================================================================================

    /** Item location not found in directory. */
    public static class LocationNotFound extends LibraryException {
        public LocationNotFound(String message) {
            super(message);
        }

        public LocationNotFound(ItemID iid) {
            super("Location not found for: " + iid);
        }
    }

    /** Store not registered in directory. */
    public static class StoreNotRegistered extends LibraryException {
        public StoreNotRegistered(String message) {
            super(message);
        }
    }

    // ==================================================================================
    // Index Exceptions
    // ==================================================================================

    /** Index operation failed. */
    public static class IndexError extends LibraryException {
        public IndexError(String message) {
            super(message);
        }

        public IndexError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ==================================================================================
    // I/O and Corruption
    // ==================================================================================

    /** Low-level I/O error. */
    public static class IOError extends LibraryException {
        public IOError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Data corruption detected. */
    public static class Corrupted extends LibraryException {
        public Corrupted(String message) {
            super(message);
        }

        public Corrupted(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ==================================================================================
    // Transaction Exceptions
    // ==================================================================================

    /** Transaction failed to commit. */
    public static class CommitFailed extends LibraryException {
        public CommitFailed(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
