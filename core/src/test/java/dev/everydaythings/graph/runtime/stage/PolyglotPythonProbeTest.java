package dev.everydaythings.graph.runtime.stage;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Step 1 of the polyglot spike: prove GraalPython is actually loadable in this JVM.
 *
 * <p>If Python is not in {@link PolyglotEnvironment#availableLanguages()}, the
 * test is skipped with a hint about the missing dependency. If Python IS
 * available, it evaluates a trivial expression to confirm the runtime works end
 * to end.
 */
class PolyglotPythonProbeTest {

    @Test
    @DisplayName("Python language is on the classpath (probe)")
    void pythonLanguageAvailable() {
        assumeThat(PolyglotEnvironment.isAvailable())
                .as("Polyglot engine itself must be available")
                .isTrue();

        boolean hasPython = PolyglotEnvironment.availableLanguages().contains("python");
        assumeThat(hasPython)
                .as("Python not in available languages — add org.graalvm.polyglot:python "
                        + "(and org.graalvm.python:python-community) to core/build.gradle. "
                        + "Currently available: " + PolyglotEnvironment.availableLanguages())
                .isTrue();
    }

    @Test
    @DisplayName("Python evaluates 1+1 = 2 via Polyglot Context")
    void pythonEvaluatesOnePlusOne() {
        assumeThat(PolyglotEnvironment.availableLanguages().contains("python"))
                .as("Python not available; skipping")
                .isTrue();

        try (Context ctx = Context.newBuilder("python").allowAllAccess(true).build()) {
            Value result = ctx.eval("python", "1 + 1");
            assertThat(result.asInt()).isEqualTo(2);
        }
    }
}
