package dev.everydaythings.graph.ui.host;

/**
 * No-op implementation for platforms without system tray support
 * (headless, unknown OS, mobile where notification is used instead).
 */
class NoOpHostPresence implements HostPresence {

    @Override public void show() {}
    @Override public void invalidate() {}
    @Override public void hide() {}
    @Override public void destroy() {}
}
