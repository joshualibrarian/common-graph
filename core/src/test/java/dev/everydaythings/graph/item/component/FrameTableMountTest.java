package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.mount.Mount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FrameTable mount-based tree navigation methods.
 */
@DisplayName("FrameTable mount navigation")
class FrameTableMountTest {

    private FrameTable table;
    private ItemID docType;

    @BeforeEach
    void setUp() {
        table = new FrameTable();
        docType = ItemID.fromString("cg:type/document");
    }

    private FrameEntry entryWithMount(String handleName, String path) {
        FrameEntry entry = FrameEntry.builder()
                .handle(HandleID.of(handleName))
                .type(docType)
                .identity(true)
                .build();
        entry.addMount(new Mount.PathMount(path));
        table.add(entry);
        return entry;
    }

    @Test
    @DisplayName("roots returns entries with depth-1 path mounts")
    void rootsReturnsDepthOneEntries() {
        entryWithMount("docs", "/documents");
        entryWithMount("settings", "/settings");
        entryWithMount("notes", "/documents/notes"); // not root

        assertThat(table.roots()).hasSize(2);
        assertThat(table.roots()).extracting(e -> e.primaryPathMount().path())
                .containsExactlyInAnyOrder("/documents", "/settings");
    }

    @Test
    @DisplayName("atPath finds entry at exact path")
    void atPathFindsEntry() {
        FrameEntry docs = entryWithMount("docs", "/documents");

        assertThat(table.atPath("/documents")).isPresent();
        assertThat(table.atPath("/documents").get()).isSameAs(docs);
        assertThat(table.atPath("/nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("children returns immediate children")
    void childrenReturnsImmediate() {
        entryWithMount("docs", "/documents");
        entryWithMount("notes", "/documents/notes");
        entryWithMount("deep", "/documents/notes/deep"); // too deep
        entryWithMount("settings", "/settings"); // sibling

        var children = table.children("/documents");
        assertThat(children).hasSize(1);
        assertThat(children.get(0).primaryPathMount().path()).isEqualTo("/documents/notes");
    }

    @Test
    @DisplayName("descendants returns all nested entries")
    void descendantsReturnsNested() {
        entryWithMount("docs", "/documents");
        entryWithMount("notes", "/documents/notes");
        entryWithMount("deep", "/documents/notes/deep");
        entryWithMount("settings", "/settings");

        var desc = table.descendants("/documents").toList();
        assertThat(desc).hasSize(2);
    }

    @Test
    @DisplayName("hasChildren detects children")
    void hasChildrenDetects() {
        entryWithMount("docs", "/documents");
        entryWithMount("notes", "/documents/notes");

        assertThat(table.hasChildren("/documents")).isTrue();
        assertThat(table.hasChildren("/documents/notes")).isFalse();
        assertThat(table.hasChildren("/")).isTrue();
    }

    @Test
    @DisplayName("mounted returns only entries with path mounts")
    void mountedFilters() {
        entryWithMount("docs", "/documents");

        // Entry without mount
        FrameEntry unmounted = FrameEntry.builder()
                .handle(HandleID.of("internal"))
                .type(docType)
                .identity(true)
                .build();
        table.add(unmounted);

        assertThat(table.mounted().count()).isEqualTo(1);
        assertThat(table.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("entry with multiple mounts appears in multiple lookups")
    void multiplePathMounts() {
        FrameEntry entry = FrameEntry.builder()
                .handle(HandleID.of("shared"))
                .type(docType)
                .identity(true)
                .build();
        entry.addMount(new Mount.PathMount("/primary"));
        entry.addMount(new Mount.PathMount("/alias"));
        table.add(entry);

        assertThat(table.atPath("/primary")).isPresent();
        assertThat(table.atPath("/alias")).isPresent();
        assertThat(table.roots()).hasSize(1); // same entry, counted once
    }

    @Test
    @DisplayName("pathForHandle returns primary path mount")
    void pathForHandleReturnsPrimaryPath() {
        entryWithMount("docs", "/documents");

        assertThat(table.pathForHandle(HandleID.of("docs"))).hasValue("/documents");
        assertThat(table.pathForHandle(HandleID.of("nonexistent"))).isEmpty();
    }

    @Test
    @DisplayName("pathForHandle returns empty for unmounted entry")
    void pathForHandleEmptyWhenNoMount() {
        FrameEntry unmounted = FrameEntry.builder()
                .handle(HandleID.of("internal"))
                .type(docType)
                .identity(true)
                .build();
        table.add(unmounted);

        assertThat(table.pathForHandle(HandleID.of("internal"))).isEmpty();
    }

    // ==================================================================================
    // childrenAt() — Virtual Directory Synthesis
    // ==================================================================================

    @Test
    @DisplayName("childrenAt synthesizes virtual directories from deep mounts")
    void childrenAt_synthesizesVirtualDirs() {
        // Mount at /bar/foo/funk/bob.txt — implies /bar is a virtual directory at root
        entryWithMount("bob", "/bar/foo/funk/bob.txt");

        List<FrameTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).hasSize(1);
        assertThat(rootChildren.get(0).segment()).isEqualTo("bar");
        assertThat(rootChildren.get(0).fullPath()).isEqualTo("/bar");
        assertThat(rootChildren.get(0).isVirtual()).isTrue();
    }

    @Test
    @DisplayName("childrenAt returns real entries at exact depth")
    void childrenAt_realEntriesAtExactDepth() {
        entryWithMount("docs", "/documents");
        entryWithMount("settings", "/settings");

        List<FrameTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).hasSize(2);
        assertThat(rootChildren).allMatch(c -> !c.isVirtual());
        assertThat(rootChildren).extracting(FrameTable.PathChild::segment)
                .containsExactlyInAnyOrder("documents", "settings");
    }

    @Test
    @DisplayName("childrenAt real entry overrides virtual at same path")
    void childrenAt_realOverridesVirtual() {
        // Deep mount implies /docs as virtual
        entryWithMount("notes", "/docs/notes");
        // Real mount at /docs overrides the virtual
        entryWithMount("docs", "/docs");

        List<FrameTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).hasSize(1);
        assertThat(rootChildren.get(0).segment()).isEqualTo("docs");
        assertThat(rootChildren.get(0).isVirtual()).isFalse();
        assertThat(rootChildren.get(0).entry()).isNotNull();
    }

    @Test
    @DisplayName("childrenAt navigates nested virtual directories")
    void childrenAt_nestedVirtualDirs() {
        entryWithMount("bob", "/bar/foo/funk/bob.txt");
        entryWithMount("ted", "/bar/foo/dank/ted.txt");

        // /bar has one child: foo (virtual)
        List<FrameTable.PathChild> barChildren = table.childrenAt("/bar");
        assertThat(barChildren).hasSize(1);
        assertThat(barChildren.get(0).segment()).isEqualTo("foo");
        assertThat(barChildren.get(0).isVirtual()).isTrue();

        // /bar/foo has two children: funk, dank (both virtual)
        List<FrameTable.PathChild> fooChildren = table.childrenAt("/bar/foo");
        assertThat(fooChildren).hasSize(2);
        assertThat(fooChildren).extracting(FrameTable.PathChild::segment)
                .containsExactlyInAnyOrder("funk", "dank");
        assertThat(fooChildren).allMatch(FrameTable.PathChild::isVirtual);

        // /bar/foo/funk has one child: bob.txt (real)
        List<FrameTable.PathChild> funkChildren = table.childrenAt("/bar/foo/funk");
        assertThat(funkChildren).hasSize(1);
        assertThat(funkChildren.get(0).segment()).isEqualTo("bob.txt");
        assertThat(funkChildren.get(0).isVirtual()).isFalse();
    }

    @Test
    @DisplayName("childrenAt returns empty when no mounts exist")
    void childrenAt_emptyWhenNoMounts() {
        List<FrameTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).isEmpty();
    }

    @Test
    @DisplayName("childrenAt returns empty for leaf path")
    void childrenAt_emptyForLeafPath() {
        entryWithMount("bob", "/bar/bob.txt");

        List<FrameTable.PathChild> leafChildren = table.childrenAt("/bar/bob.txt");
        assertThat(leafChildren).isEmpty();
    }

    @Test
    @DisplayName("childrenAt mixes real and virtual at same level")
    void childrenAt_mixedRealAndVirtual() {
        // /docs is a real mount
        entryWithMount("docs", "/docs");
        // /archive/old.txt implies /archive as virtual
        entryWithMount("old", "/archive/old.txt");

        List<FrameTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).hasSize(2);

        FrameTable.PathChild docsChild = rootChildren.stream()
                .filter(c -> c.segment().equals("docs")).findFirst().orElseThrow();
        FrameTable.PathChild archiveChild = rootChildren.stream()
                .filter(c -> c.segment().equals("archive")).findFirst().orElseThrow();

        assertThat(docsChild.isVirtual()).isFalse();
        assertThat(archiveChild.isVirtual()).isTrue();
    }
}
