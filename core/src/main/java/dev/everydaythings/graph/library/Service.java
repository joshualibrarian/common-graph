package dev.everydaythings.graph.library;

import dev.everydaythings.graph.item.Link;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A manageable service with lifecycle control.
 *
 * <p>Services have a status (STOPPED, STARTING, RUNNING, STOPPING, ERROR)
 * and can be started/stopped. File-backed services expose their path.
 *
 * <p>All library stores (ItemStore, LibraryIndex, ItemDirectory, TokenDictionary)
 * are services, allowing unified management via the control panel UI.
 *
 * <p>Services provide display methods for self-rendering in the UI:
 * <ul>
 *   <li>{@link #emoji()} - Status-aware icon</li>
 *   <li>{@link #displayToken()} - Service name</li>
 *   <li>{@link #displaySubtitle()} - Status text</li>
 * </ul>
 *
 * <p>Services render via the unified Scene pipeline - use SceneCompiler.compile(service)
 * to get a View, then render with the appropriate RenderOutput.
 */
public interface Service extends AutoCloseable {

    /**
     * Service lifecycle status.
     */
    enum Status {
        /** Service is not running */
        STOPPED,

        /** Service is starting up */
        STARTING,

        /** Service is running and healthy */
        RUNNING,

        /** Service is shutting down */
        STOPPING,

        /** Service encountered an error */
        ERROR;

        /**
         * Get the status icon.
         */
        public String icon() {
            return switch (this) {
                case STOPPED -> "⏹";
                case STARTING -> "⏳";
                case RUNNING -> "▶";
                case STOPPING -> "⏳";
                case ERROR -> "⚠";
            };
        }

        /**
         * Check if this is a transitional status.
         */
        public boolean isTransitional() {
            return this == STARTING || this == STOPPING;
        }

        /**
         * Check if the service can accept commands in this status.
         */
        public boolean canStart() {
            return this == STOPPED || this == ERROR;
        }

        public boolean canStop() {
            return this == RUNNING;
        }
    }

    // ==================================================================================
    // Status
    // ==================================================================================

    /**
     * Get the current service status.
     *
     * @return the current status
     */
    Status status();

    /**
     * Get the last error if status is ERROR.
     *
     * @return the error, or empty if no error
     */
    default Optional<Throwable> lastError() {
        return Optional.empty();
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    /**
     * Start the service.
     *
     * <p>This is idempotent - calling start() on a running service does nothing.
     *
     * @throws IllegalStateException if the service cannot be started
     */
    void start();

    /**
     * Stop the service.
     *
     * <p>This is idempotent - calling stop() on a stopped service does nothing.
     * Equivalent to {@link #close()}.
     */
    void stop();

    /**
     * Restart the service (stop then start).
     */
    default void restart() {
        stop();
        start();
    }

    /**
     * Dispatch an action by name.
     *
     * <p>This is a bridge method for widget action dispatch.
     *
     * @param action The action name ("start", "stop", "restart")
     * @return true if the action was handled
     */
    default boolean dispatchAction(String action) {
        switch (action) {
            case "start" -> { start(); return true; }
            case "stop" -> { stop(); return true; }
            case "restart" -> { restart(); return true; }
            default -> { return false; }
        }
    }

    /**
     * Close is equivalent to stop.
     */
    @Override
    default void close() {
        stop();
    }

    // ==================================================================================
    // Path (for file-backed services)
    // ==================================================================================

    /**
     * Get the storage path for file-backed services.
     *
     * @return the path, or empty for in-memory services
     */
    default Optional<Path> path() {
        return Optional.empty();
    }

    // ==================================================================================
    // Display Methods - Self-Rendering
    // ==================================================================================

    /**
     * Services are not addressable graph nodes by default.
     */
    default Link link() {
        return null;
    }

    /**
     * Services can be expanded to show details.
     */
    default boolean isExpandable() {
        return true;
    }

    /**
     * Status-aware emoji.
     */
    default String emoji() {
        return status().icon();
    }

    /**
     * Service name from class.
     */
    default String displayToken() {
        return getClass().getSimpleName();
    }

    /**
     * Status as subtitle.
     */
    default String displaySubtitle() {
        Status s = status();
        String base = s.name().toLowerCase();

        // Add path hint for file-backed services
        Optional<Path> p = path();
        if (p.isPresent()) {
            String pathStr = p.get().toString();
            if (pathStr.length() > 30) {
                pathStr = "..." + pathStr.substring(pathStr.length() - 27);
            }
            return base + " • " + pathStr;
        }

        return base;
    }

    /**
     * Color based on status.
     */
    default String colorCategory() {
        return switch (status()) {
            case RUNNING -> "service-running";
            case ERROR -> "service-error";
            default -> "service";
        };
    }
}
