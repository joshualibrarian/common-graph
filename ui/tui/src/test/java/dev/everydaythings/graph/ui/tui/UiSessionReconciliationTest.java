package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import dev.everydaythings.graph.ui.LocalSession;
import dev.everydaythings.graph.ui.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice-3 integration: an {@code ITEM_VIEW} frame submitted to a librarian
 * <i>after</i> {@code UiSession.startUi} has already run should propagate
 * through the dispatch → session-listener → surface-reconciliation chain,
 * resulting in a new window appearing on the surface without any explicit
 * {@code addWindow} call from the caller.
 *
 * <p>Setup:
 * <ul>
 *   <li>Mint a session (writes one bootstrap ITEM_VIEW frame).</li>
 *   <li>{@code startUi("tui")} (enumerates the one frame; surface has 1 window).</li>
 *   <li>Submit a second ITEM_VIEW frame targeting a different theme item.</li>
 * </ul>
 *
 * <p>Expected: after the second frame submits, the surface has 2 windows —
 * one for each ITEM_VIEW frame addressed to this session.
 */
class UiSessionReconciliationTest {

    @Test
    @DisplayName("New ITEM_VIEW frames trigger surface reconciliation; window count grows")
    void itemViewFrameTriggersReconciliation() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        LocalSession session = LocalSession.mint(lib);
        try {
            session.startUi("tui");
            assertThat(session.attachedWindows())
                    .as("startUi attaches exactly one window for the bootstrap ITEM_VIEW")
                    .hasSize(1);

            // Open a view of a different item.  session.openView publishes
            // a librarian-signed ITEM_VIEW frame; dispatch routes it back to
            // session.handleItemView via role=Location; the listener fires
            // and reconciles.
            ItemRef otherItem = ItemRef.iid("cg.test:viewed-item-b");
            session.openView(otherItem);

            List<Window> windows = session.attachedWindows();
            assertThat(windows)
                    .as("Surface should now hold a window for each ITEM_VIEW frame")
                    .hasSize(2);
            assertThat(windows)
                    .as("One window targets the session itself (bootstrap)")
                    .anyMatch(w -> session.iid().equals(w.itemRef()));
            assertThat(windows)
                    .as("The other window targets the newly-viewed item")
                    .anyMatch(w -> otherItem.equals(w.itemRef()));
        } finally {
            session.stopUi();
        }
    }

    @Test
    @DisplayName("A CLOSE frame removes the corresponding window on reconciliation")
    void closeFrameRemovesMatchingWindow() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        LocalSession session = LocalSession.mint(lib);
        try {
            session.startUi("tui");

            // Open a second view, then close it.
            ItemRef otherItem = ItemRef.iid("cg.test:closeable-item");
            session.openView(otherItem);

            assertThat(session.attachedWindows())
                    .as("After opening the second view, surface has 2 windows")
                    .hasSize(2);

            session.closeView(otherItem);

            List<Window> windowsAfterClose = session.attachedWindows();
            assertThat(windowsAfterClose)
                    .as("CLOSE-matching ITEM_VIEW should be filtered out; bootstrap window remains")
                    .hasSize(1);
            assertThat(windowsAfterClose)
                    .as("Remaining window is the bootstrap view of the session itself")
                    .anyMatch(w -> session.iid().equals(w.itemRef()));
            assertThat(windowsAfterClose)
                    .as("The closed view's window must not be present")
                    .noneMatch(w -> otherItem.equals(w.itemRef()));
        } finally {
            session.stopUi();
        }
    }
}
