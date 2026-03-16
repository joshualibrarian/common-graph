package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.game.chess.ChessItem;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
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
        Ref root = Ref.of(handle.iid());

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
    void rendersChessItemDirectly() {
        Librarian librarian = Librarian.createInMemory();
        Function<ItemID, Optional<Item>> resolver = iid -> librarian.get(iid, Item.class);

        // Create ChessItem — a proper Item, not a component
        ChessItem chess = new ChessItem(librarian);
        librarian.library().cache(chess);

        System.err.println("=== Chess Item Test ===");
        System.err.println("Chess item: " + chess.displayToken() + " (" + chess.getClass().getSimpleName() + ")");
        System.err.println("Chess IID: " + chess.iid().encodeText());

        // Create ItemModel for the chess item
        Ref root = Ref.of(chess.iid());
        ItemModel itemModel = new ItemModel(root, resolver);

        System.err.println("\n=== ItemModel State ===");
        System.err.println("Root: " + itemModel.root());
        System.err.println("Context: " + itemModel.context());

        // Compile surface
        SurfaceSchema surface = itemModel.toSurface();

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
    }
}
