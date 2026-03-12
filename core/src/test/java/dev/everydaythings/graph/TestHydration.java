package dev.everydaythings.graph;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.runtime.Host;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("slow")
public class TestHydration {

    @Test
    void testLibrarianTypeHydration(@TempDir Path testDir) {
        System.out.println("Test directory: " + testDir);

        try (Librarian lib = Librarian.open(testDir)) {
            System.out.println("Librarian opened: " + lib.iid());

            // Try to get the Librarian type seed
            ItemID librarianType = ItemID.fromString("cg:type/librarian");
            System.out.println("Looking up: " + librarianType.encodeText());

            Optional<Item> item = lib.get(librarianType, Item.class);
            if (item.isPresent()) {
                System.out.println("FOUND: " + item.get().getClass().getSimpleName());
            } else {
                System.out.println("NOT FOUND for Librarian type!");
            }

            // Try to get the Host type seed
            ItemID hostType = ItemID.fromString("cg:type/host");
            System.out.println("Looking up: " + hostType.encodeText());

            Optional<Item> hostItem = lib.get(hostType, Item.class);
            if (hostItem.isPresent()) {
                System.out.println("FOUND: " + hostItem.get().getClass().getSimpleName());
            } else {
                System.out.println("NOT FOUND for Host type!");
            }

            // These should be found
            assertThat(item).as("Librarian type should be found").isPresent();
            assertThat(hostItem).as("Host type should be found").isPresent();
        }
    }
}
