package dev.everydaythings.graph.library;

import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.library_old.dictionary.TokenExtractor;
import dev.everydaythings.graph.runtime.LibrarianOld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenIndexDebugTest {

    @Test
    void createVerbIsIndexable() {
        LibrarianOld lib = LibrarianOld.createInMemory();
        var cached = lib.get(ItemID.fromString(CoreVocabulary.Create.KEY));
        System.out.println("Create cached: " + cached.isPresent());
        if (cached.isPresent()) {
            ItemOld item = cached.get();
            System.out.println("Frames: " + item.frames().size());
            for (var f : item.frames()) {
                System.out.println("  key=" + f.frameKey() + " body=" + (f.body() != null));
                if (f.body() != null) {
                    for (var b : f.body().frameBindings()) {
                        System.out.println("    role=" + b.role().encodeText() + " quals=" + b.qualifiers().size());
                    }
                }
            }
            var postings = TokenExtractor.fromItemFrames(item);
            System.out.println("Postings: " + postings.size());
            postings.forEach(p -> System.out.println("  " + p.token()
                    + " features=" + p.features()));
        }
        assertThat(cached).isPresent();
    }
}
