package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.game.chess.ChessGame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Link;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.runtime.LibrarianHandle;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.item.ItemModel;
import dev.everydaythings.graph.ui.scene.surface.layout.ConstraintSurface;
import dev.everydaythings.graph.ui.text.TuiSurfaceRenderer;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for ItemModel rendering with real Librarian.
 */
class ItemModelRenderingTest {

    @Test
    void rendersItemModelWithRealLibrarian() {
        // Create an in-memory LibrarianHandle (this is how Session does it)
        LibrarianHandle handle = LibrarianHandle.inMemory();

        // The librarian itself is an Item
        Link root = Link.of(handle.iid());

        System.err.println("=== Test Setup ===");
        System.err.println("Librarian IID: " + handle.iid());
        System.err.println("Root link: " + root);

        // Use the standard resolver through LibrarianHandle (same as Session does)
        Function<ItemID, Optional<Item>> resolver = handle::get;

        // Verify we can resolve the librarian
        var resolvedItem = resolver.apply(handle.iid());
        System.err.println("Resolved item: " + resolvedItem);
        assertThat(resolvedItem).isPresent();
        System.err.println("Resolved item class: " + resolvedItem.get().getClass().getSimpleName());
        System.err.println("Resolved item displayToken: " + resolvedItem.get().displayToken());

        // Create ItemModel with the standard resolver
        ItemModel itemModel = new ItemModel(root, resolver);

        System.err.println("\n=== ItemModel State ===");
        System.err.println("Root: " + itemModel.root());
        System.err.println("Context: " + itemModel.context());

        // Compile to surface
        SurfaceSchema surface = itemModel.toSurface();

        System.err.println("\n=== Compiled Surface ===");
        assertThat(surface).isInstanceOf(ConstraintSurface.class);
        ConstraintSurface constraint = (ConstraintSurface) surface;
        System.err.println("Number of children: " + constraint.children().size());

        for (var child : constraint.children()) {
            System.err.println("  Child id='" + child.id() + "' surface=" +
                (child.surface() != null ? child.surface().getClass().getSimpleName() : "null"));
        }

        // Render to text
        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        surface.render(renderer);
        String output = renderer.result();

        System.err.println("\n=== Rendered Output ===");
        System.err.println("---");
        System.err.println(output.isEmpty() ? "(empty)" : output);
        System.err.println("---");
        System.err.println("Output length: " + output.length());

        // We should have some output (header + detail + prompt)
        assertThat(constraint.children()).isNotEmpty();
    }

    @Test
    void rendersChessComponentAfterAddition() {
        Librarian librarian = Librarian.createInMemory();
        Function<ItemID, Optional<Item>> resolver = iid -> librarian.get(iid, Item.class);

        // Use the Librarian itself as the host (same as the real app).
        // The Librarian is already cached.
        Item hostItem = librarian;
        System.err.println("=== Chess Component Test ===");
        System.err.println("Host item: " + hostItem.displayToken() + " (" + hostItem.getClass().getSimpleName() + ")");
        System.err.println("Host IID: " + hostItem.iid().encodeText());
        System.err.println("liveCount before: " + hostItem.content().liveCount());

        // Add chess component (same as Session.addComponentToItem does)
        ChessGame chess = ChessGame.create();
        String componentHandle = "chess";
        hostItem.addComponent(componentHandle, chess);
        System.err.println("liveCount after: " + hostItem.content().liveCount());

        // Verify the live instance is stored
        FrameKey chessKey = FrameKey.literal("chess");
        Optional<Object> live = hostItem.content().getLive(chessKey);
        System.err.println("getLive(chess): " + (live.isPresent() ? live.get().getClass().getName() : "EMPTY"));
        assertThat(live).isPresent();

        // Verify the resolver returns the SAME instance
        Item resolved = resolver.apply(hostItem.iid()).orElseThrow();
        System.err.println("Same instance? " + (resolved == hostItem));
        System.err.println("resolved liveCount: " + resolved.content().liveCount());
        Optional<Object> resolvedLive = resolved.content().getLive(chessKey);
        System.err.println("resolved getLive(chess): " + (resolvedLive.isPresent() ? resolvedLive.get().getClass().getName() : "EMPTY"));

        // Now create the ItemModel and select the chess component
        Link root = Link.of(hostItem.iid());
        ItemModel itemModel = new ItemModel(root, resolver);

        // Simulate what addComponentToItem does: select the component path
        Link componentLink = Link.of(hostItem.iid(), "/" + componentHandle);
        itemModel.select(componentLink);

        System.err.println("\n=== After select ===");
        System.err.println("context.item(): " + itemModel.context().item().encodeText());
        System.err.println("context.path(): " + itemModel.context().path().orElse("<none>"));

        // Compile surface — this triggers detail() → resolveComponentSurface()
        System.err.println("\n=== Compiling surface ===");
        SurfaceSchema surface = itemModel.toSurface();

        assertThat(surface).isInstanceOf(ConstraintSurface.class);
        ConstraintSurface constraint = (ConstraintSurface) surface;

        System.err.println("Number of children: " + constraint.children().size());
        for (var child : constraint.children()) {
            System.err.println("  Child id='" + child.id() + "' surface=" +
                (child.surface() != null ? child.surface().getClass().getSimpleName() : "null"));
        }

        // Render to text to see what comes out
        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        surface.render(renderer);
        String output = renderer.result();

        System.err.println("\n=== Rendered Output ===");
        System.err.println("---");
        System.err.println(output.isEmpty() ? "(empty)" : output);
        System.err.println("---");
    }
}
