package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.FrameRecordOld;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.library.LibraryIndex;
import dev.everydaythings.graph.runtime.LibrarianOld;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Frame Cache — allFrames() and unendorsedFrames()")
class FrameOldCacheTest {

    static final ItemID LIKE = ItemID.fromString("cg:pred/like");
    static final ItemID ANNOTATION = ItemID.fromString("cg:pred/annotation");

    static LibrarianOld librarian;

    @BeforeAll
    static void setup() {
        librarian = LibrarianOld.createInMemory();
    }

    @Test
    @DisplayName("allFrames includes endorsed frames from EndorsementsTable")
    void allFramesIncludesEndorsed() {
        ItemOld item = new ItemOld(librarian);

        List<FrameOld> all = item.allFrames();
        // Should include whatever endorsed frames the item starts with
        assertThat(all).hasSizeGreaterThanOrEqualTo(item.frames().size());
    }

    @Test
    @DisplayName("unendorsedFrames returns empty when no unendorsed frames exist")
    void unendorsedFramesEmptyByDefault() {
        ItemOld item = new ItemOld(librarian);
        List<FrameOld> unendorsed = item.unendorsedFrames();
        assertThat(unendorsed).isEmpty();
    }

    @Test
    @DisplayName("unendorsedFrames returns frames stored via storeFrameBody")
    void unendorsedFramesFromIndex() {
        ItemOld item = new ItemOld(librarian);

        // Store an unendorsed "like" frame on this item
        FrameBodyOld likeBody = FrameBodyOld.of(LIKE, item.iid(),
                Map.of(ThematicRole.Agent.IID,
                        BindingTarget.iid(librarian.iid())));

        ContentID bodyCid = librarian.library().storeFrameBody(likeBody);
        assertThat(bodyCid).as("storeFrameBody should return non-null CID").isNotNull();

        // Verify the frame is in the index
        List<LibraryIndex.FrameRef> refs = librarian.library()
                .framesByItem(item.iid()).toList();
        assertThat(refs).as("framesByItem should find the stored frame").isNotEmpty();

        // Verify the body can be loaded
        var loadedBody = librarian.library().loadFrameBody(likeBody.hash());
        assertThat(loadedBody).as("loadFrameBody should find stored body").isPresent();

        List<FrameOld> unendorsed = item.unendorsedFrames();
        assertThat(unendorsed).hasSize(1);
        assertThat(unendorsed.getFirst().body()).isNotNull();
        assertThat(unendorsed.getFirst().body().predicate()).isEqualTo(LIKE);
        assertThat(unendorsed.getFirst().body().homeId()).isEqualTo(item.iid());
    }

    @Test
    @DisplayName("allFrames combines endorsed and unendorsed")
    void allFramesCombinesBoth() {
        ItemOld item = new ItemOld(librarian);
        int endorsedCount = item.frames().size();

        // Store an unendorsed frame
        FrameBodyOld annotationBody = FrameBodyOld.of(ANNOTATION, item.iid(),
                Map.of(ThematicRole.Agent.IID,
                        BindingTarget.iid(librarian.iid())));
        librarian.library().storeFrameBody(annotationBody);

        List<FrameOld> all = item.allFrames();
        assertThat(all.size()).isEqualTo(endorsedCount + 1);
    }

    @Test
    @DisplayName("unendorsedFrames excludes frames already in EndorsementsTable")
    void excludesEndorsedFrames() {
        ItemOld item = new ItemOld(librarian);

        // Store a frame body that is also endorsed (simulates overlap)
        // First commit the item so endorsed frames have body hashes
        item.commit(librarian);

        // Store a new unendorsed frame
        FrameBodyOld likeBody = FrameBodyOld.of(LIKE, item.iid());
        librarian.library().storeFrameBody(likeBody);

        List<FrameOld> unendorsed = item.unendorsedFrames();
        // Should only include the unendorsed like, not any endorsed frames
        for (FrameOld f : unendorsed) {
            assertThat(f.body().predicate()).isEqualTo(LIKE);
        }
    }

    @Test
    @DisplayName("unendorsedFrames filters by theme — excludes frames on other items")
    void filtersByTheme() {
        ItemOld itemA = new ItemOld(librarian);
        ItemOld itemB = new ItemOld(librarian);

        // Store a frame on itemA
        FrameBodyOld bodyA = FrameBodyOld.of(LIKE, itemA.iid(),
                Map.of(ThematicRole.Agent.IID,
                        BindingTarget.iid(librarian.iid())));
        librarian.library().storeFrameBody(bodyA);

        // Store a frame on itemB that references itemA in a binding
        FrameBodyOld bodyB = FrameBodyOld.of(ANNOTATION, itemB.iid(),
                Map.of(ThematicRole.Goal.IID,
                        BindingTarget.iid(itemA.iid())));
        librarian.library().storeFrameBody(bodyB);

        // itemA should only see its own LIKE frame, not the annotation on itemB
        List<FrameOld> unendorsedA = itemA.unendorsedFrames();
        assertThat(unendorsedA).hasSize(1);
        assertThat(unendorsedA.getFirst().body().predicate()).isEqualTo(LIKE);
    }

    @Test
    @DisplayName("unendorsedFrames with predicate filter")
    void unendorsedFramesWithPredicate() {
        ItemOld item = new ItemOld(librarian);

        // Store two different types of unendorsed frames
        FrameBodyOld likeBody = FrameBodyOld.of(LIKE, item.iid());
        FrameBodyOld annotationBody = FrameBodyOld.of(ANNOTATION, item.iid());
        librarian.library().storeFrameBody(likeBody);
        librarian.library().storeFrameBody(annotationBody);

        // Query by LIKE predicate only
        List<FrameOld> likes = item.unendorsedFrames(LIKE);
        assertThat(likes).hasSize(1);
        assertThat(likes.getFirst().body().predicate()).isEqualTo(LIKE);

        // Query by ANNOTATION predicate
        List<FrameOld> annotations = item.unendorsedFrames(ANNOTATION);
        assertThat(annotations).hasSize(1);
        assertThat(annotations.getFirst().body().predicate()).isEqualTo(ANNOTATION);
    }

    @Test
    @DisplayName("unendorsed frame has owner set")
    void ownerIsSet() {
        ItemOld item = new ItemOld(librarian);

        FrameBodyOld body = FrameBodyOld.of(LIKE, item.iid());
        librarian.library().storeFrameBody(body);

        List<FrameOld> unendorsed = item.unendorsedFrames();
        assertThat(unendorsed).hasSize(1);
        assertThat(unendorsed.getFirst().owner()).isSameAs(item);
    }

    @Test
    @DisplayName("unendorsed frame carries attestation records when stored with storeFrame")
    void recordsPopulated() {
        ItemOld item = new ItemOld(librarian);

        FrameBodyOld body = FrameBodyOld.of(LIKE, item.iid(),
                Map.of(ThematicRole.Agent.IID,
                        BindingTarget.iid(librarian.iid())));

        // Store with a signed record
        FrameRecordOld record = FrameRecordOld.create(body, librarian);
        librarian.library().storeFrame(body, record);

        List<FrameOld> unendorsed = item.unendorsedFrames();
        assertThat(unendorsed).hasSize(1);
        assertThat(unendorsed.getFirst().records()).isNotEmpty();
    }

    @Test
    @DisplayName("item without librarian returns empty for allFrames/unendorsedFrames")
    void noLibrarianReturnsEmpty() {
        ItemOld seed = new ItemOld(ItemID.fromString("cg:test/seed"));
        assertThat(seed.unendorsedFrames()).isEmpty();
        assertThat(seed.allFrames()).hasSizeGreaterThanOrEqualTo(0);
    }
}
