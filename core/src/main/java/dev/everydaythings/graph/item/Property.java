package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.action.ActionResult;

import java.util.List;
import java.util.stream.Stream;

/**
 * Unified interface for anything within an Item that can be navigated to and operated on.
 *
 * <p>An Item is a tree of Properties. Each Property can have:
 * <ul>
 *   <li><b>Sub-properties</b> - navigate via {@link #property(String)}</li>
 *   <li><b>Value</b> - read/write via {@link #get()}/{@link #set(Object)}</li>
 *   <li><b>Collection operations</b> - {@link #add(String, Object)}/{@link #remove(String)}</li>
 *   <li><b>Actions</b> - domain operations specific to this property</li>
 * </ul>
 *
 * <p>This enables uniform path-based access:
 * <pre>
 * components.vault.encrypt "secret"   // navigate to vault, dispatch encrypt action
 * policy.defaultEffect set ALLOW      // navigate to defaultEffect, set value
 * relations add ...                   // add to relations collection
 * </pre>
 *
 * <p>The universal operations are:
 * <ul>
 *   <li>{@code get} - read the property value</li>
 *   <li>{@code set} - write the property value</li>
 *   <li>{@code add} - add to a collection property</li>
 *   <li>{@code remove} - remove from a collection property</li>
 *   <li>{@code list} - enumerate sub-properties (via {@link #properties()})</li>
 * </ul>
 *
 * <p>Any other operation is dispatched as a domain action via the owner Item's vocabulary.
 */
public interface Property {

    // ==================================================================================
    // Navigation
    // ==================================================================================

    /**
     * Get a sub-property by name.
     *
     * @param name The property name
     * @return The sub-property, or null if not found
     */
    default Property property(String name) {
        return null;
    }

    /**
     * List the names of sub-properties.
     *
     * @return Stream of property names
     */
    default Stream<String> properties() {
        return Stream.empty();
    }

    // ==================================================================================
    // Value Access (for leaf/scalar properties)
    // ==================================================================================

    /**
     * Get this property's value.
     *
     * <p>For leaf properties, returns the actual value.
     * For collection properties, may return the collection itself or a summary.
     *
     * @return The value
     */
    default Object get() {
        return this;
    }

    /**
     * Set this property's value.
     *
     * @param value The new value
     * @throws UnsupportedOperationException if this property is read-only
     */
    default void set(Object value) {
        throw new UnsupportedOperationException("Property is read-only");
    }

    // ==================================================================================
    // Collection Access (for collection properties)
    // ==================================================================================

    /**
     * Check if this property is a collection.
     */
    default boolean isCollection() {
        return false;
    }

    /**
     * Add an entry to this collection property.
     *
     * @param key   The key/name for the entry (may be null for list-like collections)
     * @param value The value to add
     * @throws UnsupportedOperationException if this property doesn't support add
     */
    default void add(String key, Object value) {
        throw new UnsupportedOperationException("Property doesn't support add");
    }

    /**
     * Remove an entry from this collection property.
     *
     * @param key The key/name of the entry to remove
     * @throws UnsupportedOperationException if this property doesn't support remove
     */
    default void remove(String key) {
        throw new UnsupportedOperationException("Property doesn't support remove");
    }

    /**
     * Get the size of this collection property.
     *
     * @return The number of entries, or 0 if not a collection
     */
    default int size() {
        return 0;
    }

    // ==================================================================================
    // Dispatch
    // ==================================================================================

    /**
     * Dispatch a command to this property.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Check universal operations (get/set/add/remove/list)</li>
     *   <li>Check sub-properties (returns the property itself for navigation)</li>
     *   <li>Fail with unknown command</li>
     * </ol>
     *
     * @param command The command name
     * @param args    The arguments
     * @return The result
     */
    default ActionResult dispatch(String command, List<String> args) {
        // 1. Try universal operations
        if (isUniversalOp(command)) {
            return dispatchUniversal(command, args);
        }

        // 2. Try sub-property navigation (returns the property for UI to handle)
        Property sub = property(command);
        if (sub != null) {
            return ActionResult.success(sub);
        }

        // 3. Unknown command
        return ActionResult.failure(new IllegalArgumentException("Unknown command: " + command));
    }

    /**
     * Check if this is a universal operation.
     */
    static boolean isUniversalOp(String op) {
        return op != null && switch (op) {
            case "get", "set", "add", "remove", "list" -> true;
            default -> false;
        };
    }

    /**
     * Dispatch a universal operation to this property.
     *
     * <p>Handles: get, set, add, remove, list.
     * Domain actions should be handled by the owner Item via its Vocabulary.
     *
     * @param operation The operation name
     * @param args      The arguments
     * @return The result
     */
    default ActionResult dispatchUniversal(String operation, List<String> args) {
        return switch (operation) {
            case "get" -> ActionResult.success(get());
            case "set" -> {
                if (args.isEmpty()) {
                    yield ActionResult.failure(new IllegalArgumentException("set requires a value"));
                }
                try {
                    set(parseValue(args.get(0)));
                    yield ActionResult.success(null);
                } catch (Exception e) {
                    yield ActionResult.failure(e);
                }
            }
            case "add" -> {
                if (args.isEmpty()) {
                    yield ActionResult.failure(new IllegalArgumentException("add requires arguments"));
                }
                try {
                    String key = args.size() > 1 ? args.get(0) : null;
                    Object value = args.size() > 1 ? parseValue(args.get(1)) : parseValue(args.get(0));
                    add(key, value);
                    yield ActionResult.success(null);
                } catch (Exception e) {
                    yield ActionResult.failure(e);
                }
            }
            case "remove" -> {
                if (args.isEmpty()) {
                    yield ActionResult.failure(new IllegalArgumentException("remove requires a key"));
                }
                try {
                    remove(args.get(0));
                    yield ActionResult.success(null);
                } catch (Exception e) {
                    yield ActionResult.failure(e);
                }
            }
            case "list" -> ActionResult.success(properties().toList());
            default -> ActionResult.failure(new IllegalArgumentException("Unknown universal operation: " + operation));
        };
    }

    /**
     * Parse a string argument into a typed value.
     * Override for custom parsing.
     */
    default Object parseValue(String arg) {
        // Try to parse as common types
        if (arg.equalsIgnoreCase("true")) return true;
        if (arg.equalsIgnoreCase("false")) return false;
        if (arg.equalsIgnoreCase("null")) return null;
        try {
            return Long.parseLong(arg);
        } catch (NumberFormatException e) {
            // Not a number
        }
        try {
            return Double.parseDouble(arg);
        } catch (NumberFormatException e) {
            // Not a double
        }
        // Default to string
        return arg;
    }
}
