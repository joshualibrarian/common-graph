package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.function.Consumer;

/**
 * Unified navigation state shared across all UI modes (GUI, TUI, CLI).
 *
 * <p>This model class tracks:
 * <ul>
 *   <li>{@code rootItem} - The Item forming the tree root (what we're "in")</li>
 *   <li>{@code commandContext} - Where commands dispatch to (rootItem or a selected child node)</li>
 *   <li>{@code history} - Navigation stack for back navigation</li>
 *   <li>{@code viewMode} - META vs PRESENTATION rendering</li>
 *   <li>{@code commandHistory} - Previous commands for UP/DOWN recall</li>
 * </ul>
 *
 * <p>Follows the same observer pattern as {@link dev.everydaythings.graph.parse.ExpressionInputState}.
 */
@Log4j2
public class NavigationContext {

    /**
     * View mode for tree rendering.
     *
     * <p>PRESENTATION: Shows the mounted tree - how the item presents its content.
     * <p>META: Shows the raw structure - components, relations, actions, mounts, policies.
     */
    public enum ViewMode {
        PRESENTATION,
        META
    }

    // Navigation state
    private Item rootItem;
    private Object commandContext;  // Can be Item or any displayable object
    private final Deque<Item> history = new ArrayDeque<>();
    private ViewMode viewMode = ViewMode.META;

    // Librarian for resolution
    private Librarian librarian;

    // Command history for UP/DOWN recall
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;  // -1 = not navigating history

    // Listeners for UI binding
    private final List<Consumer<NavigationContext>> listeners = new ArrayList<>();

    /**
     * Create a NavigationContext rooted at the given item.
     */
    public NavigationContext(Item root) {
        this.rootItem = root;
        this.commandContext = root;
        if (root instanceof Librarian lib) {
            this.librarian = lib;
        }
    }

    /**
     * Create a NavigationContext with explicit librarian.
     */
    public NavigationContext(Item root, Librarian librarian) {
        this.rootItem = root;
        this.commandContext = root;
        this.librarian = librarian;
    }

    // ==================================================================================
    // Navigation
    // ==================================================================================

    /**
     * Navigate to a different item, pushing current to history.
     */
    public void navigateTo(Item item) {
        if (item == null || item == rootItem) {
            return;
        }

        // Push current to history
        history.push(rootItem);

        // Navigate
        rootItem = item;
        commandContext = item;

        logger.debug("Navigated to: {}", item.displayToken());
        notifyListeners();
    }

    /**
     * Navigate to an item resolved from a Posting.
     */
    public void navigateTo(Posting posting) {
        if (posting == null) {
            return;
        }
        resolve(posting.target()).ifPresent(this::navigateTo);
    }

    /**
     * Set the command context (selection within current root).
     * Does not push to history.
     */
    public void setContext(Object node) {
        if (node == null) {
            node = rootItem;
        }
        if (node != commandContext) {
            commandContext = node;
            notifyListeners();
        }
    }

    /**
     * Go back to previous item in history.
     * Bound to Escape key.
     */
    public void goBack() {
        if (!history.isEmpty()) {
            rootItem = history.pop();
            commandContext = rootItem;
            logger.debug("Back to: {}", rootItem.displayToken());
            notifyListeners();
        }
    }

    /**
     * Navigate up to the librarian (root of all items).
     */
    public void goUp() {
        if (librarian != null && rootItem != librarian) {
            navigateTo(librarian);
        }
    }

    /**
     * Check if back navigation is possible.
     */
    public boolean canGoBack() {
        return !history.isEmpty();
    }

    // ==================================================================================
    // View Mode
    // ==================================================================================

    /**
     * Get the current view mode.
     */
    public ViewMode viewMode() {
        return viewMode;
    }

    /**
     * Set the view mode.
     */
    public void setViewMode(ViewMode mode) {
        if (mode != null && mode != viewMode) {
            viewMode = mode;
            notifyListeners();
        }
    }

    /**
     * Toggle between META and PRESENTATION view modes.
     */
    public void toggleViewMode() {
        viewMode = (viewMode == ViewMode.META) ? ViewMode.PRESENTATION : ViewMode.META;
        notifyListeners();
    }

    // ==================================================================================
    // Resolution
    // ==================================================================================

    /**
     * Resolve an ItemID to an Item.
     */
    public Optional<Item> resolve(ItemID iid) {
        if (librarian == null || iid == null) {
            return Optional.empty();
        }

        var result = librarian.dispatch("library.get", List.of(iid.encodeText()));
        if (result.success()) {
            Object value = result.value();
            if (value instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof Item item) {
                return Optional.of(item);
            } else if (value instanceof Item item) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Lookup items by text query.
     */
    @SuppressWarnings("unchecked")
    public List<Posting> lookup(String text) {
        if (librarian == null || text == null || text.length() < 2) {
            return List.of();
        }

        var result = librarian.dispatch("library.lookup", List.of(text));
        if (result.success() && result.value() instanceof List<?> list) {
            return (List<Posting>) list;
        }
        return List.of();
    }

    // ==================================================================================
    // State Accessors
    // ==================================================================================

    /**
     * The item forming the tree root.
     */
    public Item rootItem() {
        return rootItem;
    }

    /**
     * Where commands dispatch to (may be rootItem or a selected child).
     */
    public Object commandContext() {
        return commandContext;
    }

    /**
     * The librarian for lookups.
     */
    public Librarian librarian() {
        return librarian;
    }

    /**
     * Set the librarian (if not set via constructor).
     */
    public void setLibrarian(Librarian librarian) {
        this.librarian = librarian;
    }

    /**
     * Human-readable path string for display.
     */
    public String contextPath() {
        if (commandContext == rootItem) {
            return rootItem.displayToken();
        }
        String contextToken = commandContext instanceof Item item
                ? item.displayToken()
                : commandContext.toString();
        return rootItem.displayToken() + " > " + contextToken;
    }

    // ==================================================================================
    // Command History (UP/DOWN recall)
    // ==================================================================================

    /**
     * Add a command to history.
     */
    public void addToHistory(String command) {
        if (command != null && !command.isBlank()) {
            // Don't add duplicates at the end
            if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
                commandHistory.add(command);
            }
            // Reset navigation index
            historyIndex = -1;
        }
    }

    /**
     * Get previous command (UP arrow).
     * Returns null if at start of history.
     */
    public String previousCommand() {
        if (commandHistory.isEmpty()) {
            return null;
        }

        if (historyIndex == -1) {
            // Start from end
            historyIndex = commandHistory.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        }

        return commandHistory.get(historyIndex);
    }

    /**
     * Get next command (DOWN arrow).
     * Returns null if at end of history.
     */
    public String nextCommand() {
        if (commandHistory.isEmpty() || historyIndex == -1) {
            return null;
        }

        if (historyIndex < commandHistory.size() - 1) {
            historyIndex++;
            return commandHistory.get(historyIndex);
        } else {
            // Past end - return to current input
            historyIndex = -1;
            return null;
        }
    }

    /**
     * Reset history navigation (called when user types new input).
     */
    public void resetHistoryNavigation() {
        historyIndex = -1;
    }

    /**
     * Get the full command history.
     */
    public List<String> commandHistory() {
        return Collections.unmodifiableList(commandHistory);
    }

    // ==================================================================================
    // Observer Pattern
    // ==================================================================================

    /**
     * Add a listener to be notified on state changes.
     */
    public void addListener(Consumer<NavigationContext> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a listener.
     */
    public void removeListener(Consumer<NavigationContext> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Consumer<NavigationContext> listener : listeners) {
            try {
                listener.accept(this);
            } catch (Exception e) {
                logger.warn("Listener error", e);
            }
        }
    }
}
