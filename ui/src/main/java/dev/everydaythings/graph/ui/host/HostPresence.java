package dev.everydaythings.graph.ui.host;

import dev.everydaythings.graph.runtime.Host;
import dev.everydaythings.graph.ui.input.Platform;

/**
 * System-level presence for the host machine — tray icon on desktop,
 * notification on mobile, etc.
 *
 * <p>The host presence is per-machine, not per-librarian or per-session.
 * No session is involved — the Host item owns the tray directly. All
 * librarians running on the host appear as components under the host item.
 * The tray popup renders the host item's surface via Skia — same surface
 * system as everything else in CG.
 *
 * <p>Architecture:
 * <ul>
 *   <li><b>Icon</b>: native platform tray icon (NSStatusItem on macOS)</li>
 *   <li><b>Popup</b>: the host item's surface, rendered off-screen by Skia
 *       into a native popup container (NSPopover on macOS)</li>
 * </ul>
 *
 * <p>The host item's surface schema defines the tray popup layout. Librarians,
 * sessions, authenticated users, and views are all components/mounts on the
 * host item — the popup just renders what's there.
 *
 * <p>Lifecycle: create once at host startup, call {@link #invalidate()} when
 * the host item changes, destroy on shutdown.
 */
public interface HostPresence {

    /** Show the tray icon. */
    void show();

    /**
     * Signal that the host item's state has changed and the popup
     * content needs re-rendering on next display.
     */
    void invalidate();

    /** Remove the tray icon. */
    void hide();

    /** Clean up native resources. */
    void destroy();

    /**
     * Create a HostPresence for the current platform.
     *
     * @param hostItem the Host item whose state is shown in the tray menu
     */
    static HostPresence create(Host hostItem) {
        return switch (Platform.current()) {
            case MACOS -> new MacOsHostPresence(hostItem);
            default -> new NoOpHostPresence();
        };
    }
}
