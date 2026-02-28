package dev.everydaythings.graph.ui.host;

import com.sun.jna.Pointer;
import dev.everydaythings.graph.runtime.Host;
import lombok.extern.log4j.Log4j2;

import static dev.everydaythings.graph.ui.host.ObjCRuntime.*;

/**
 * macOS system tray implementation using NSStatusItem + NSMenu.
 *
 * <p>The tray is a native system integration point, not a rendering surface.
 * It uses a standard NSMenu to show host status and quick actions. Actual
 * item viewing happens in sessions.
 *
 * <p>Architecture:
 * <ul>
 *   <li><b>Icon</b>: NSStatusItem with a template image in the menu bar</li>
 *   <li><b>Menu</b>: native NSMenu showing host info, running librarians,
 *       connected sessions, and quick actions</li>
 * </ul>
 *
 * <p>No session is involved — the Host item owns the tray directly.
 *
 * <p>All Cocoa calls happen via JNA to the Objective-C runtime (libobjc.dylib).
 * No JNI compilation needed.
 *
 * <p>Threading: with {@code -XstartOnFirstThread}, the GLFW thread IS the
 * Cocoa main thread. Calls from the event loop are safe. Background thread
 * calls must use {@code dispatch_async(dispatch_get_main_queue(), ...)}.
 */
@Log4j2
class MacOsHostPresence implements HostPresence {

    private static final double NS_VARIABLE_STATUS_ITEM_LENGTH = -1.0;

    // =========================================================================
    // Host item
    // =========================================================================

    private final Host hostItem;

    // =========================================================================
    // Native Cocoa objects
    // =========================================================================

    /** The native status item in the menu bar. */
    private Pointer statusItem;

    /** The menu shown when the status item is clicked. */
    private Pointer menu;

    /** Action target for menu item callbacks. Must be kept alive (prevent GC). */
    private Pointer actionTarget;

    /** Callback references. Must be kept alive (prevent GC). */
    private ActionCallback quitCallback;

    // =========================================================================
    // Constructor
    // =========================================================================

    MacOsHostPresence(Host hostItem) {
        this.hostItem = hostItem;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public void show() {
        try {
            ObjCRuntime.ensureLoaded();
            createStatusItem();
            rebuildMenu();
            logger.info("macOS system tray icon created (host: {})", hostItem.hostname());
        } catch (Exception e) {
            logger.error("Failed to create macOS system tray", e);
        }
    }

    @Override
    public void invalidate() {
        rebuildMenu();
    }

    @Override
    public void hide() {
        if (statusItem != null) {
            Pointer statusBar = send(cls("NSStatusBar"), "systemStatusBar");
            send(statusBar, "removeStatusItem:", statusItem);
            statusItem = null;
            logger.info("macOS system tray icon removed");
        }
    }

    @Override
    public void destroy() {
        hide();
        menu = null;
        actionTarget = null;
        quitCallback = null;
    }

    // =========================================================================
    // NSStatusItem setup
    // =========================================================================

    /**
     * Create the NSStatusItem in the system menu bar.
     */
    private void createStatusItem() {
        Pointer statusBar = send(cls("NSStatusBar"), "systemStatusBar");
        statusItem = send(statusBar, "statusItemWithLength:", NS_VARIABLE_STATUS_ITEM_LENGTH);

        // TODO: bundle a 18x18 template PNG and use nsTemplateImage()
        Pointer button = send(statusItem, "button");
        send(button, "setTitle:", nsString("CG"));
        send(statusItem, "setVisible:", true);
    }

    // =========================================================================
    // NSMenu
    // =========================================================================

    /**
     * Build or rebuild the native menu from the host item's current state.
     *
     * <p>Menu structure:
     * <pre>
     *   Host: machinename
     *   ─────────────────
     *   Librarian: abc123...   (IID truncated)
     *     Sessions: 0
     *   ─────────────────
     *   Quit
     * </pre>
     *
     * <p>As the host item gains components (librarians, sessions, users),
     * this menu will grow to reflect them. For now it shows basic status.
     */
    private void rebuildMenu() {
        if (statusItem == null) return;

        menu = send(send(cls("NSMenu"), "alloc"), "init");

        // Host header
        String hostName = hostItem.hostname() != null ? hostItem.hostname() : "Unknown Host";
        addMenuItem(menu, "Host: " + hostName, null, null);

        addSeparator(menu);

        // Librarian info
        String iid = hostItem.iid() != null ? hostItem.iid().encodeText() : "—";
        if (iid.length() > 24) {
            iid = iid.substring(0, 24) + "\u2026";
        }
        addMenuItem(menu, "Librarian: " + iid, null, null);

        addSeparator(menu);

        // Quit action
        quitCallback = (self, selector, sender) -> {
            logger.info("Quit requested from tray menu");
            System.exit(0);
        };
        actionTarget = createActionTarget(
                "CGTrayMenuTarget", "quitClicked:", quitCallback);
        addMenuItem(menu, "Quit Common Graph", actionTarget, "quitClicked:");

        // Attach menu to status item
        send(statusItem, "setMenu:", menu);
    }

    // =========================================================================
    // Menu helpers
    // =========================================================================

    /**
     * Add a menu item. If target/action are null, the item is informational (disabled).
     */
    private void addMenuItem(Pointer menu, String title,
                             Pointer target, String action) {
        Pointer item = send(
                send(cls("NSMenuItem"), "alloc"),
                "initWithTitle:action:keyEquivalent:",
                nsString(title),
                action != null ? sel(action) : Pointer.NULL,
                nsString(""));

        if (target != null) {
            send(item, "setTarget:", target);
        } else {
            // Informational item — disable it so it shows as label text
            send(item, "setEnabled:", false);
        }

        send(menu, "addItem:", item);
    }

    /**
     * Add a separator line to the menu.
     */
    private void addSeparator(Pointer menu) {
        Pointer separator = send(cls("NSMenuItem"), "separatorItem");
        send(menu, "addItem:", separator);
    }
}
