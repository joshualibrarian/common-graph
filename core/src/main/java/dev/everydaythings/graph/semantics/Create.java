package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Optional;

/**
 * The abstract concept of creation, embodied as a runtime item.
 *
 * <p>Sememes are units of meaning, language- and POS-agnostic. "Create" (English
 * verb), "creation" (English noun), "crear" (Spanish), and so on are <i>lexemes
 * pointing at</i> this sememe — they are about it, not what it is.
 *
 * <p>{@code @Seed(Create.KEY)} declares this class as a seed sememe; bootstrap
 * persists a manifest body for it. {@code @Embodies(Create.KEY)} declares
 * "this Java class IS the Create seed item" — the bootstrap adds an
 * IMPLEMENTATION binding to the seed manifest, so future {@code fetchItem(Create.IID)}
 * hydrates as this class and {@code onFrameAssembled} fires when CREATE frames
 * are routed.
 *
 * <p>This Java class is one possible <i>implementation</i> of the create concept:
 * "a CREATE frame finds an IMPLEMENTS frame for the THEME's concept and instantiates
 * the runnable class." Other implementations could exist (different nodes, different
 * apps); each item's manifest declares its own IMPLEMENTATION binding.
 */
@Seed.Item(key = Create.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = Create.KEY)
public class Create extends Item {

    /** Canonical key for the create sememe. POS-agnostic — this is a unit of meaning. */
    public static final String KEY = "cg.sememe:create";

    /** The deterministic IID for the create sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    public Create(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * When a CREATE frame is assembled, find an IMPLEMENTS frame for the THEME's
     * concept and instantiate its runnable Java class.
     *
     * <p>Walks: read THEME from the frame → query IMPLEMENTS frames where THEME
     * points at the named concept → for each, check it's an IMPLEMENTS frame
     * (head matches) and the AGENT binding's qualifier is Java → try to load
     * the class → instantiate via {@code (ItemID, Librarian)} constructor and
     * commit. First runnable wins.
     *
     * <p>Silently no-ops on missing prerequisites (no THEME, theme target isn't
     * a reference, no IMPLEMENTS frame, all candidates fail to load). Future:
     * trust-ordered selection when multiple implementations exist; explicit
     * filter for runnable runtimes when polyglot lands.
     */
    @Override
    public void onFrameAssembled(Frame frame) {
        Optional<Binding> themeBinding =
                frame.body().binding(CompoundKey.of(ThematicRole.Theme.IID));
        if (themeBinding.isEmpty()) return;

        ItemID conceptIid = extractIid(themeBinding.get().target());
        if (conceptIid == null) return;

        List<ContentID> candidateBodyCids = librarian.library()
                .bodyCidsForReferenceBinding(ThematicRole.Theme.IID, conceptIid);

        for (ContentID bodyCid : candidateBodyCids) {
            Frame candidate = librarian.fetchFrame(bodyCid).orElse(null);
            if (candidate == null) continue;
            if (!isImplementsFrame(candidate)) continue;

            Class<? extends Item> instanceClass = readJavaImplementation(candidate);
            if (instanceClass == null) continue;

            try {
                Item instance = instanceClass
                        .getConstructor(ItemID.class, Librarian.class)
                        .newInstance(ItemID.random(), librarian);
                instance.commit(List.of());
                return;  // first runnable wins
            } catch (ReflectiveOperationException e) {
                // Try the next candidate.
            }
        }
    }

    private static boolean isImplementsFrame(Frame frame) {
        if (!(frame.body().head() instanceof ItemRef itemRef)) return false;
        return Implements.IID.equals(itemRef.iid());
    }

    /**
     * Read the Java instance class from an IMPLEMENTS frame's AGENT binding,
     * which should carry a Java-class Literal qualified by the Java runtime.
     * Returns null if the binding shape doesn't match — caller skips and tries
     * the next candidate.
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends Item> readJavaImplementation(Frame frame) {
        for (Binding b : frame.body().bindings()) {
            if (!ThematicRole.Agent.IID.equals(b.role())) continue;
            // Must be qualified by the Java runtime.
            boolean javaQualified = b.qualifiers().stream()
                    .anyMatch(q -> q instanceof CompoundKey.Sememe s
                            && Runtimes.Java.IID.equals(s.id()));
            if (!javaQualified) continue;
            if (!(b.target() instanceof Literal lit)) continue;
            if (!Literal.TYPE_JAVA_CLASS.equals(lit.valueType())) continue;
            try {
                Class<?> clazz = lit.asJavaClass();
                if (Item.class.isAssignableFrom(clazz)) {
                    return (Class<? extends Item>) clazz;
                }
            } catch (RuntimeException ignored) {
                // class not on classpath; not runnable here
            }
        }
        return null;
    }

    private static ItemID extractIid(BindingTarget target) {
        if (target instanceof BindingTarget.IidTarget iid) return iid.iid();
        if (target instanceof BindingTarget.RefTarget ref && !ref.isCompound()) {
            return ref.asItemId();
        }
        return null;
    }
}
