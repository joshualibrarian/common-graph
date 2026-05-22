package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.ui.LocalSession;
import dev.everydaythings.graph.ui.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that {@code UiSession.startUi} enumerates
 * {@code ITEM_VIEW} frames addressed to the session and attaches a
 * {@link Window} per frame, with a scene supplier wired through
 * {@link dev.everydaythings.graph.scene.SceneCascade}.
 *
 * <p>The bootstrap frame written by {@link LocalSession#mint} carries
 * {@code Theme=sessionIid, Location=sessionIid}; the cascade for
 * {@code sessionIid} walks Session-archetype → Archetype and returns
 * Archetype's terminal placeholder scene.  After startUi, the surface
 * should hold exactly one window whose itemRef matches the session's iid
 * and whose scene supplier yields that placeholder body.
 */
class UiSessionEnumerationTest {

    @Test
    @DisplayName("startUi attaches a Window for the bootstrap ITEM_VIEW frame")
    void enumeratesBootstrapItemView() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        LocalSession session = LocalSession.mint(lib);
        try {
            session.startUi("tui");

            List<Window> windows = session.attachedWindows();
            assertThat(windows)
                    .as("Bootstrap ITEM_VIEW(self) should produce exactly one window")
                    .hasSize(1);
            assertThat(windows.get(0).itemRef())
                    .as("Window's itemRef should be the session's own iid (Theme=sessionIid)")
                    .isEqualTo(session.iid());
        } finally {
            session.stopUi();
        }
    }

    @Test
    @DisplayName("Enumerated window's scene supplier yields the cascade-resolved scene")
    void enumeratedSupplierWalksTheCascade() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        LocalSession session = LocalSession.mint(lib);
        try {
            session.startUi("tui");

            Window window = session.attachedWindows().get(0);
            Body scene = window.sceneSupplier().get();

            // Session has no own CONFIG[Presentation]; cascade falls through
            // Session-archetype → Archetype → terminal SceneText.
            assertThat(scene.headRef())
                    .as("Cascade falls through to Archetype's SceneText placeholder")
                    .isEqualTo(ItemRef.iid(SceneText.KEY));
        } finally {
            session.stopUi();
        }
    }
}
