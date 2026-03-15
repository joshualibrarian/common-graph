package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.mount.Mount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EndorsementsTable mount-based tree navigation methods.
 */
@DisplayName("EndorsementsTable mount navigation")
class EndorsementsTableMountTest {

    private EndorsementsTable table;
    private ItemID docType;

    @BeforeEach
    void setUp() {
        table = new EndorsementsTable();
        docType = ItemID.fromString("cg:type/document");
    }

    private Frame addFrameWithMount(String handleName, String path) {
        FrameKey key = FrameKey.literal(handleName);
        Frame frame = Frame.snapshot(key, docType, null, true);
        table.add(frame, List.of(new Mount.PathMount(path)));
        return frame;
    }

    @Test
    @DisplayName("roots returns frames with depth-1 path mounts")
    void rootsReturnsDepthOneEntries() {
        addFrameWithMount("docs", "/documents");
        addFrameWithMount("settings", "/settings");
        addFrameWithMount("notes", "/documents/notes"); // not root

        assertThat(table.roots()).hasSize(2);
        assertThat(table.roots()).extracting(f -> table.primaryPathMount(f.frameKey()).path())
                .containsExactlyInAnyOrder("/documents", "/settings");
    }

    @Test
    @DisplayName("atPath finds frame at exact path")
    void atPathFindsEntry() {
        Frame docs = addFrameWithMount("docs", "/documents");

        assertThat(table.atPath("/documents")).isPresent();
        assertThat(table.atPath("/documents").get()).isSameAs(docs);
        assertThat(table.atPath("/nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("children returns immediate children")
    void childrenReturnsImmediate() {
        addFrameWithMount("docs", "/documents");
        addFrameWithMount("notes", "/documents/notes");
        addFrameWithMount("deep", "/documents/notes/deep"); // too deep
        addFrameWithMount("settings", "/settings"); // sibling

        var children = table.children("/documents");
        assertThat(children).hasSize(1);
        assertThat(table.primaryPathMount(children.get(0).frameKey()).path())
                .isEqualTo("/documents/notes");
    }

    @Test
    @DisplayName("descendants returns all nested frames")
    void descendantsReturnsNested() {
        addFrameWithMount("docs", "/documents");
        addFrameWithMount("notes", "/documents/notes");
        addFrameWithMount("deep", "/documents/notes/deep");
        addFrameWithMount("settings", "/settings");

        var desc = table.descendants("/documents").toList();
        assertThat(desc).hasSize(2);
    }

    @Test
    @DisplayName("hasChildren detects children")
    void hasChildrenDetects() {
        addFrameWithMount("docs", "/documents");
        addFrameWithMount("notes", "/documents/notes");

        assertThat(table.hasChildren("/documents")).isTrue();
        assertThat(table.hasChildren("/documents/notes")).isFalse();
        assertThat(table.hasChildren("/")).isTrue();
    }

    @Test
    @DisplayName("mounted returns only frames with path mounts")
    void mountedFilters() {
        addFrameWithMount("docs", "/documents");

        // Frame without mount
        Frame unmounted = Frame.snapshot(FrameKey.literal("internal"), docType, null, true);
        table.add(unmounted);

        assertThat(table.mounted().count()).isEqualTo(1);
        assertThat(table.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("frame with multiple mounts appears in multiple lookups")
    void multiplePathMounts() {
        Frame frame = Frame.snapshot(FrameKey.literal("shared"), docType, null, true);
        table.add(frame, List.of(
                new Mount.PathMount("/primary"),
                new Mount.PathMount("/alias")));

        assertThat(table.atPath("/primary")).isPresent();
        assertThat(table.atPath("/alias")).isPresent();
        assertThat(table.roots()).hasSize(1); // same frame, counted once
    }

    @Test
    @DisplayName("pathForKey returns primary path mount")
    void pathForKeyReturnsPrimaryPath() {
        addFrameWithMount("docs", "/documents");

        assertThat(table.pathForKey(FrameKey.literal("docs"))).hasValue("/documents");
        assertThat(table.pathForKey(FrameKey.literal("nonexistent"))).isEmpty();
    }

    @Test
    @DisplayName("pathForKey returns empty for unmounted frame")
    void pathForKeyEmptyWhenNoMount() {
        Frame unmounted = Frame.snapshot(FrameKey.literal("internal"), docType, null, true);
        table.add(unmounted);

        assertThat(table.pathForKey(FrameKey.literal("internal"))).isEmpty();
    }

    // ==================================================================================
    // childrenAt() — Virtual Directory Synthesis
    // ==================================================================================

    @Test
    @DisplayName("childrenAt synthesizes virtual directories from deep mounts")
    void childrenAt_synthesizesVirtualDirs() {
        // Mount at /bar/foo/funk/bob.txt — implies /bar is a virtual directory at root
        addFrameWithMount("bob", "/bar/foo/funk/bob.txt");

        List<EndorsementsTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).hasSize(1);
        assertThat(rootChildren.get(0).segment()).isEqualTo("bar");
        assertThat(rootChildren.get(0).fullPath()).isEqualTo("/bar");
        assertThat(rootChildren.get(0).isVirtual()).isTrue();
    }

    @Test
    @DisplayName("childrenAt returns real frames at exact depth")
    void childrenAt_realEntriesAtExactDepth() {
        addFrameWithMount("docs", "/documents");
        addFrameWithMount("settings", "/settings");

        List<EndorsementsTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).hasSize(2);
        assertThat(rootChildren).allMatch(c -> !c.isVirtual());
        assertThat(rootChildren).extracting(EndorsementsTable.PathChild::segment)
                .containsExactlyInAnyOrder("documents", "settings");
    }

    @Test
    @DisplayName("childrenAt real frame overrides virtual at same path")
    void childrenAt_realOverridesVirtual() {
        // Deep mount implies /docs as virtual
        addFrameWithMount("notes", "/docs/notes");
        // Real mount at /docs overrides the virtual
        addFrameWithMount("docs", "/docs");

        List<EndorsementsTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).hasSize(1);
        assertThat(rootChildren.get(0).segment()).isEqualTo("docs");
        assertThat(rootChildren.get(0).isVirtual()).isFalse();
        assertThat(rootChildren.get(0).frame()).isNotNull();
    }

    @Test
    @DisplayName("childrenAt navigates nested virtual directories")
    void childrenAt_nestedVirtualDirs() {
        addFrameWithMount("bob", "/bar/foo/funk/bob.txt");
        addFrameWithMount("ted", "/bar/foo/dank/ted.txt");

        // /bar has one child: foo (virtual)
        List<EndorsementsTable.PathChild> barChildren = table.childrenAt("/bar");
        assertThat(barChildren).hasSize(1);
        assertThat(barChildren.get(0).segment()).isEqualTo("foo");
        assertThat(barChildren.get(0).isVirtual()).isTrue();

        // /bar/foo has two children: funk, dank (both virtual)
        List<EndorsementsTable.PathChild> fooChildren = table.childrenAt("/bar/foo");
        assertThat(fooChildren).hasSize(2);
        assertThat(fooChildren).extracting(EndorsementsTable.PathChild::segment)
                .containsExactlyInAnyOrder("funk", "dank");
        assertThat(fooChildren).allMatch(EndorsementsTable.PathChild::isVirtual);

        // /bar/foo/funk has one child: bob.txt (real)
        List<EndorsementsTable.PathChild> funkChildren = table.childrenAt("/bar/foo/funk");
        assertThat(funkChildren).hasSize(1);
        assertThat(funkChildren.get(0).segment()).isEqualTo("bob.txt");
        assertThat(funkChildren.get(0).isVirtual()).isFalse();
    }

    @Test
    @DisplayName("childrenAt returns empty when no mounts exist")
    void childrenAt_emptyWhenNoMounts() {
        List<EndorsementsTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).isEmpty();
    }

    @Test
    @DisplayName("childrenAt returns empty for leaf path")
    void childrenAt_emptyForLeafPath() {
        addFrameWithMount("bob", "/bar/bob.txt");

        List<EndorsementsTable.PathChild> leafChildren = table.childrenAt("/bar/bob.txt");
        assertThat(leafChildren).isEmpty();
    }

    @Test
    @DisplayName("childrenAt mixes real and virtual at same level")
    void childrenAt_mixedRealAndVirtual() {
        // /docs is a real mount
        addFrameWithMount("docs", "/docs");
        // /archive/old.txt implies /archive as virtual
        addFrameWithMount("old", "/archive/old.txt");

        List<EndorsementsTable.PathChild> rootChildren = table.childrenAt("/");
        assertThat(rootChildren).hasSize(2);

        EndorsementsTable.PathChild docsChild = rootChildren.stream()
                .filter(c -> c.segment().equals("docs")).findFirst().orElseThrow();
        EndorsementsTable.PathChild archiveChild = rootChildren.stream()
                .filter(c -> c.segment().equals("archive")).findFirst().orElseThrow();

        assertThat(docsChild.isVirtual()).isFalse();
        assertThat(archiveChild.isVirtual()).isTrue();
    }
}
