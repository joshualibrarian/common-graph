package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.NounSememe;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.SceneMode;
import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Slow — full librarian bootstrap + filesystem setup")
class ResolveItemHandleTest {

    @Test
    void traceResolveItemHandle(@TempDir Path tempDir) throws Exception {
        Librarian lib = Librarian.createInMemory();

        // Get the TITLE sememe IID
        ItemID titleIid = NounSememe.Title.SEED.iid();
        System.out.println("TITLE sememe IID: " + titleIid.encodeText());

        // Try to resolve TITLE
        Optional<Item> titleResolved = lib.get(titleIid, Item.class);
        System.out.println("\n=== TITLE Sememe ===");
        System.out.println("  resolved: " + titleResolved.isPresent());
        if (titleResolved.isPresent()) {
            Item i = titleResolved.get();
            var info = i.displayInfo();
            System.out.println("  class: " + i.getClass().getName());
            System.out.println("  class.simpleName: " + i.getClass().getSimpleName());
            System.out.println("  displayToken: " + i.displayToken());
            System.out.println("  info.name: " + info.name());
            System.out.println("  info.typeName: " + info.typeName());
            System.out.println("  info.displayName: " + info.displayName());
            System.out.println("  info.iconText: " + info.effectiveIconText());
        }

        // Try to resolve the librarian itself (acts as a user/signer)
        ItemID libIid = lib.iid();
        System.out.println("\n=== Librarian (as subject) ===");
        System.out.println("  IID: " + libIid.encodeText());
        Optional<Item> libResolved = lib.get(libIid, Item.class);
        System.out.println("  resolved: " + libResolved.isPresent());
        if (libResolved.isPresent()) {
            Item i = libResolved.get();
            var info = i.displayInfo();
            System.out.println("  class: " + i.getClass().getName());
            System.out.println("  class.simpleName: " + i.getClass().getSimpleName());
            System.out.println("  displayToken: " + i.displayToken());
            System.out.println("  info.name: " + info.name());
            System.out.println("  info.typeName: " + info.typeName());
            System.out.println("  info.displayName: " + info.displayName());
            System.out.println("  info.iconText: " + info.effectiveIconText());
        }

        // Compile a frame body WITH resolver
        FrameBody body = FrameBody.of(titleIid, libIid,
                java.util.Map.of(ThematicRole.Target.SEED.iid(), Literal.ofText("joshua")));

        System.out.println("\n=== Compiling FrameBody with resolver ===");
        View view = SceneCompiler.compile(body, SceneMode.FULL,
                iid -> lib.get(iid, Item.class));
        assertThat(view).isNotNull();
        assertThat(view.root()).isNotNull();

        // Compile WITHOUT resolver for comparison
        System.out.println("\n=== Compiling FrameBody WITHOUT resolver ===");
        View view2 = SceneCompiler.compile(body, SceneMode.FULL);
        assertThat(view2).isNotNull();

        lib.close();
    }
}
