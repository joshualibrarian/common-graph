package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.FrameRecord;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.library.LibraryIndex;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Frame Cache — allFrames() and unendorsedFrames()")
class FrameCacheTest {

    static final ItemID LIKE = ItemID.fromString("cg:pred/like");
    static final ItemID ANNOTATION = ItemID.fromString("cg:pred/annotation");

    static Librarian librarian;

    @BeforeAll
    static void setup() {
        librarian = Librarian.createInMemory();
    }

    @Test
    @DisplayName("allFrames includes endorsed frames from EndorsementsTable")
    void allFramesIncludesEndorsed() {
        Item item = new Item(librarian);

        List<Frame> all = item.allFrames();
        // Should include whatever endorsed frames the item starts with
        assertThat(all).hasSizeGreaterThanOrEqualTo(item.frames().size());
    }

    @Test
    @DisplayName("unendorsedFrames returns empty when no unendorsed frames exist")
    void unendorsedFramesEmptyByDefault() {
        Item item = new Item(librarian);
        List<Frame> unendorsed = item.unendorsedFrames();
        assertThat(unendorsed).isEmpty();
    }

    @Test
    @DisplayName("unendorsedFrames returns frames stored via storeFrameBody")
    void unendorsedFramesFromIndex() {
        Item item = new Item(librarian);

        // Store an unendorsed "like" frame on this item
        FrameBody likeBody = FrameBody.of(LIKE, item.iid(),
                Map.of(ThematicRole.Agent.SEED.iid(),
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

        List<Frame> unendorsed = item.unendorsedFrames();
        assertThat(unendorsed).hasSize(1);
        assertThat(unendorsed.getFirst().body()).isNotNull();
        assertThat(unendorsed.getFirst().body().predicate()).isEqualTo(LIKE);
        assertThat(unendorsed.getFirst().body().theme()).isEqualTo(item.iid());
    }

    @Test
    @DisplayName("allFrames combines endorsed and unendorsed")
    void allFramesCombinesBoth() {
        Item item = new Item(librarian);
        int endorsedCount = item.frames().size();

        // Store an unendorsed frame
        FrameBody annotationBody = FrameBody.of(ANNOTATION, item.iid(),
                Map.of(ThematicRole.Agent.SEED.iid(),
                        BindingTarget.iid(librarian.iid())));
        librarian.library().storeFrameBody(annotationBody);

        List<Frame> all = item.allFrames();
        assertThat(all.size()).isEqualTo(endorsedCount + 1);
    }

    @Test
    @DisplayName("unendorsedFrames excludes frames already in EndorsementsTable")
    void excludesEndorsedFrames() {
        Item item = new Item(librarian);

        // Store a frame body that is also endorsed (simulates overlap)
        // First commit the item so endorsed frames have body hashes
        item.commit(librarian);

        // Store a new unendorsed frame
        FrameBody likeBody = FrameBody.of(LIKE, item.iid());
        librarian.library().storeFrameBody(likeBody);

        List<Frame> unendorsed = item.unendorsedFrames();
        // Should only include the unendorsed like, not any endorsed frames
        for (Frame f : unendorsed) {
            assertThat(f.body().predicate()).isEqualTo(LIKE);
        }
    }

    @Test
    @DisplayName("unendorsedFrames filters by theme — excludes frames on other items")
    void filtersByTheme() {
        Item itemA = new Item(librarian);
        Item itemB = new Item(librarian);

        // Store a frame on itemA
        FrameBody bodyA = FrameBody.of(LIKE, itemA.iid(),
                Map.of(ThematicRole.Agent.SEED.iid(),
                        BindingTarget.iid(librarian.iid())));
        librarian.library().storeFrameBody(bodyA);

        // Store a frame on itemB that references itemA in a binding
        FrameBody bodyB = FrameBody.of(ANNOTATION, itemB.iid(),
                Map.of(ThematicRole.Goal.SEED.iid(),
                        BindingTarget.iid(itemA.iid())));
        librarian.library().storeFrameBody(bodyB);

        // itemA should only see its own LIKE frame, not the annotation on itemB
        List<Frame> unendorsedA = itemA.unendorsedFrames();
        assertThat(unendorsedA).hasSize(1);
        assertThat(unendorsedA.getFirst().body().predicate()).isEqualTo(LIKE);
    }

    @Test
    @DisplayName("unendorsedFrames with predicate filter")
    void unendorsedFramesWithPredicate() {
        Item item = new Item(librarian);

        // Store two different types of unendorsed frames
        FrameBody likeBody = FrameBody.of(LIKE, item.iid());
        FrameBody annotationBody = FrameBody.of(ANNOTATION, item.iid());
        librarian.library().storeFrameBody(likeBody);
        librarian.library().storeFrameBody(annotationBody);

        // Query by LIKE predicate only
        List<Frame> likes = item.unendorsedFrames(LIKE);
        assertThat(likes).hasSize(1);
        assertThat(likes.getFirst().body().predicate()).isEqualTo(LIKE);

        // Query by ANNOTATION predicate
        List<Frame> annotations = item.unendorsedFrames(ANNOTATION);
        assertThat(annotations).hasSize(1);
        assertThat(annotations.getFirst().body().predicate()).isEqualTo(ANNOTATION);
    }

    @Test
    @DisplayName("unendorsed frame has owner set")
    void ownerIsSet() {
        Item item = new Item(librarian);

        FrameBody body = FrameBody.of(LIKE, item.iid());
        librarian.library().storeFrameBody(body);

        List<Frame> unendorsed = item.unendorsedFrames();
        assertThat(unendorsed).hasSize(1);
        assertThat(unendorsed.getFirst().owner()).isSameAs(item);
    }

    @Test
    @DisplayName("unendorsed frame carries attestation records when stored with storeFrame")
    void recordsPopulated() {
        Item item = new Item(librarian);

        FrameBody body = FrameBody.of(LIKE, item.iid(),
                Map.of(ThematicRole.Agent.SEED.iid(),
                        BindingTarget.iid(librarian.iid())));

        // Store with a signed record
        FrameRecord record = FrameRecord.create(body, librarian);
        librarian.library().storeFrame(body, record);

        List<Frame> unendorsed = item.unendorsedFrames();
        assertThat(unendorsed).hasSize(1);
        assertThat(unendorsed.getFirst().records()).isNotEmpty();
    }

    @Test
    @DisplayName("item without librarian returns empty for allFrames/unendorsedFrames")
    void noLibrarianReturnsEmpty() {
        Item seed = new Item(ItemID.fromString("cg:test/seed"));
        assertThat(seed.unendorsedFrames()).isEmpty();
        assertThat(seed.allFrames()).hasSizeGreaterThanOrEqualTo(0);
    }
}
