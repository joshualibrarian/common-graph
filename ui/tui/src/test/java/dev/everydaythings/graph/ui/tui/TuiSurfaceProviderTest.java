package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.ui.Surface;
import dev.everydaythings.graph.ui.SurfaceRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end smoke for the SurfaceProvider ServiceLoader chain: with the
 * {@code :ui:tui} module on the classpath,
 * {@code SurfaceRegistry.require("tui", session)} yields a working
 * {@link TuiSurface}.
 */
class TuiSurfaceProviderTest {

    private Session newSession() {
        return new Session(ItemRef.iid("cg.test:provider"), Librarian.anonymous());
    }

    @Test
    @DisplayName("SurfaceRegistry.require(\"tui\", session) returns a TuiSurface via ServiceLoader")
    void registryFindsTuiSurface() {
        Surface surface = SurfaceRegistry.require("tui", newSession());
        try {
            assertThat(surface).isInstanceOf(TuiSurface.class);
            assertThat(surface.isOpen()).isFalse();
        } finally {
            surface.close();
        }
    }

    @Test
    @DisplayName("uiMode lookup is case-insensitive")
    void caseInsensitiveLookup() {
        Surface surface = SurfaceRegistry.require("TUI", newSession());
        try {
            assertThat(surface).isInstanceOf(TuiSurface.class);
        } finally {
            surface.close();
        }
    }

    @Test
    @DisplayName("available() includes 'tui'")
    void availableIncludesTui() {
        assertThat(SurfaceRegistry.available()).contains("tui");
    }

    @Test
    @DisplayName("find() returns empty for an unregistered uiMode")
    void unregisteredModeReturnsEmpty() {
        assertThat(SurfaceRegistry.find("nonesuch", newSession())).isEmpty();
    }

    @Test
    @DisplayName("require() throws with available modes listed for an unregistered uiMode")
    void unregisteredModeThrowsHelpful() {
        Session session = newSession();
        assertThatThrownBy(() -> SurfaceRegistry.require("nonesuch", session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nonesuch")
                .hasMessageContaining("tui");
    }
}
