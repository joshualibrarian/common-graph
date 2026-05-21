package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.scene.VariableResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tests for Session's runtime Variable binding — the contract the
 * presenter consults during scene resolution.  Variables that bind here
 * include CurrentTime, Viewport, FocusedNode, and any session-local
 * deferred value the scene declaration references.
 */
@DisplayName("Session Variable binding")
class SessionVariableTest {

    private final ItemRef sessionIid = ItemRef.iid("cg.test:session-instance");
    private final ItemRef currentTimeRef = ItemRef.iid(CoreVocabulary.CurrentTime.KEY);

    private Session newSession() {
        return new Session(sessionIid, Librarian.anonymous());
    }

    @Nested
    @DisplayName("Bind / unbind / read")
    class BindUnbindRead {

        @Test
        @DisplayName("Bound supplier is reachable via the resolver")
        void boundIsReachable() {
            Session session = newSession();
            session.bindVariable(currentTimeRef, () -> "12:34:56");

            VariableResolver resolver = session.variableResolver();
            Optional<Object> value = resolver.resolve(currentTimeRef);

            assertThat(value).contains("12:34:56");
        }

        @Test
        @DisplayName("isVariableBound reflects current state")
        void boundReflectsState() {
            Session session = newSession();
            assertThat(session.isVariableBound(currentTimeRef)).isFalse();

            session.bindVariable(currentTimeRef, () -> "x");
            assertThat(session.isVariableBound(currentTimeRef)).isTrue();

            session.unbindVariable(currentTimeRef);
            assertThat(session.isVariableBound(currentTimeRef)).isFalse();
        }

        @Test
        @DisplayName("Unknown Variable resolves to empty")
        void unknownIsEmpty() {
            Session session = newSession();
            VariableResolver resolver = session.variableResolver();

            assertThat(resolver.resolve(currentTimeRef)).isEmpty();
        }

        @Test
        @DisplayName("Re-binding replaces the prior supplier")
        void rebindReplaces() {
            Session session = newSession();
            session.bindVariable(currentTimeRef, () -> "first");
            session.bindVariable(currentTimeRef, () -> "second");

            assertThat(session.variableResolver().resolve(currentTimeRef)).contains("second");
        }

        @Test
        @DisplayName("Unbinding removes the supplier; resolver then returns empty")
        void unbindMakesEmpty() {
            Session session = newSession();
            session.bindVariable(currentTimeRef, () -> "x");
            session.unbindVariable(currentTimeRef);

            assertThat(session.variableResolver().resolve(currentTimeRef)).isEmpty();
        }

        @Test
        @DisplayName("Unbind with null is a no-op")
        void unbindNullIsNoop() {
            Session session = newSession();
            session.bindVariable(currentTimeRef, () -> "x");
            session.unbindVariable(null);
            assertThat(session.isVariableBound(currentTimeRef)).isTrue();
        }
    }

    @Nested
    @DisplayName("Supplier semantics")
    class SupplierSemantics {

        @Test
        @DisplayName("Supplier is invoked on each resolve, not snapshotted by the resolver")
        void supplierCalledEachTime() {
            Session session = newSession();
            AtomicInteger ticks = new AtomicInteger(0);
            session.bindVariable(currentTimeRef, () -> "tick " + ticks.incrementAndGet());

            VariableResolver resolver = session.variableResolver();
            assertThat(resolver.resolve(currentTimeRef)).contains("tick 1");
            assertThat(resolver.resolve(currentTimeRef)).contains("tick 2");
            assertThat(resolver.resolve(currentTimeRef)).contains("tick 3");
        }

        @Test
        @DisplayName("Supplier returning null surfaces as empty Optional")
        void supplierNullIsEmpty() {
            Session session = newSession();
            session.bindVariable(currentTimeRef, () -> null);
            assertThat(session.variableResolver().resolve(currentTimeRef)).isEmpty();
        }

        @Test
        @DisplayName("Resolver from one snapshot keeps working after another bind happens")
        void resolverSeesLiveBindings() {
            Session session = newSession();
            session.bindVariable(currentTimeRef, () -> "alpha");
            VariableResolver resolver = session.variableResolver();
            assertThat(resolver.resolve(currentTimeRef)).contains("alpha");

            session.bindVariable(currentTimeRef, () -> "beta");
            assertThat(resolver.resolve(currentTimeRef)).contains("beta");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("bindVariable rejects null variable IID")
        void nullVariableRejected() {
            Session session = newSession();
            assertThatNullPointerException()
                    .isThrownBy(() -> session.bindVariable(null, () -> "x"));
        }

        @Test
        @DisplayName("bindVariable rejects null supplier")
        void nullSupplierRejected() {
            Session session = newSession();
            assertThatNullPointerException()
                    .isThrownBy(() -> session.bindVariable(currentTimeRef, null));
        }
    }
}
