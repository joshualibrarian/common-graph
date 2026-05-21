package dev.everydaythings.graph.runtime.stage;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.operator.compare.Between;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Polyglot end-to-end proof: a Python implementation of BETWEEN, dispatched
 * through the same {@link ItemStage#deliver} primitive as the Java
 * implementation.
 *
 * <p>The Python source receives a Java {@link Frame} via GraalVM host-access,
 * reads its operands using the Frame's normal API, and returns a Python
 * Boolean.  {@link PolyglotItem#receive} marshals the result back to a
 * Java {@link Boolean}.  Same outer contract as Java-BETWEEN; entirely
 * different runtime path.
 *
 * <p>Skips gracefully if GraalPython isn't available in the test JVM.
 */
class PolyglotBetweenTest {

    private static final String PYTHON_BETWEEN =
            "def receive(frame):\n"
            + "    body = frame.body()\n"
            + "    source = body.bindingsByRole(SOURCE_ROLE)[0].target()\n"
            + "    goal   = body.bindingsByRole(GOAL_ROLE)[0].target()\n"
            + "    theme  = body.bindingsByRole(THEME_ROLE)[0].target()\n"
            + "    return source <= theme <= goal\n";

    @Test
    @DisplayName("Python BETWEEN through PolyglotItem returns true when THEME lies in [SOURCE, GOAL]")
    void pythonBetweenInRange() {
        assumeTrue(PolyglotEnvironment.isAvailable()
                && PolyglotEnvironment.availableLanguages().contains("python"),
                "GraalPython not available in this JVM");

        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        PolyglotItem pythonBetween = buildPythonBetween(librarian);

        Frame frame = Frame.compose(ItemRef.iid(Between.KEY))
                .with(ItemRef.iid(ThematicRole.Source.KEY), 0L)
                .with(ItemRef.iid(ThematicRole.Goal.KEY), 255L)
                .with(ItemRef.iid(ThematicRole.Theme.KEY), 128L)
                .build();

        Object result = librarian.stage().deliver(pythonBetween, frame);
        assertThat(result).isEqualTo(true);

        pythonBetween.close();
    }

    @Test
    @DisplayName("Python BETWEEN returns false when THEME is outside [SOURCE, GOAL]")
    void pythonBetweenOutOfRange() {
        assumeTrue(PolyglotEnvironment.isAvailable()
                && PolyglotEnvironment.availableLanguages().contains("python"),
                "GraalPython not available in this JVM");

        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        PolyglotItem pythonBetween = buildPythonBetween(librarian);

        Frame frame = Frame.compose(ItemRef.iid(Between.KEY))
                .with(ItemRef.iid(ThematicRole.Source.KEY), 0L)
                .with(ItemRef.iid(ThematicRole.Goal.KEY), 100L)
                .with(ItemRef.iid(ThematicRole.Theme.KEY), 200L)
                .build();

        Object result = librarian.stage().deliver(pythonBetween, frame);
        assertThat(result).isEqualTo(false);

        pythonBetween.close();
    }

    /**
     * Wire up a Python {@link Context} with the BETWEEN source, pre-binding
     * the ThematicRole IIDs as Python globals so the Python code can use them
     * without constructing Java identity values itself.
     */
    private static PolyglotItem buildPythonBetween(Librarian librarian) {
        Context context = Context.newBuilder("python")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(name -> true)
                .build();

        Value pythonGlobals = context.getBindings("python");
        pythonGlobals.putMember("SOURCE_ROLE", ItemRef.iid(ThematicRole.Source.KEY));
        pythonGlobals.putMember("GOAL_ROLE",   ItemRef.iid(ThematicRole.Goal.KEY));
        pythonGlobals.putMember("THEME_ROLE",  ItemRef.iid(ThematicRole.Theme.KEY));

        context.eval("python", PYTHON_BETWEEN);
        Value receive = pythonGlobals.getMember("receive");

        ItemRef polyglotIid = ItemRef.fromString("test.code:between-python");
        return new PolyglotItem(polyglotIid, librarian,
                ItemRef.iid(Between.KEY), context, receive);
    }
}
