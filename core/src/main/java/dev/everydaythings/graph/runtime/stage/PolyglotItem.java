package dev.everydaythings.graph.runtime.stage;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * An {@link Item} backed by a GraalVM-hosted guest-language implementation.
 *
 * <p>Whereas a native Java item subclass overrides {@link Item#receive(Frame)}
 * directly, a {@code PolyglotItem} holds a polyglot {@link Value} (the
 * {@code receive} function loaded from the guest source) plus its owning
 * {@link Context}.  Its {@code receive(Frame)} delegates to the Value,
 * marshaling the result back to a Java value.
 *
 * <p>The construction path is normally:
 * <ol>
 *   <li>{@link ItemStage} reads a code item's {@code @PYTHON:[SourceCode]}
 *       (or analogous) binding off the manifest.</li>
 *   <li>Stage creates a {@code Context} for the language, evaluates the
 *       source, and fetches the {@code receive} function from the bindings.</li>
 *   <li>Stage constructs a {@code PolyglotItem} wrapping the function +
 *       context, hands it back to the librarian's item cache.</li>
 *   <li>Subsequent {@code Stage.deliver(polyglotItem, frame)} calls route
 *       straight through the cached Value — zero further lookups.</li>
 * </ol>
 *
 * <p>For the BETWEEN proof case, the Python source looks roughly like:
 * <pre>
 * def receive(frame):
 *     body = frame.body()
 *     source = body.bindingsByRole(SOURCE_ROLE)[0].target()
 *     goal   = body.bindingsByRole(GOAL_ROLE)[0].target()
 *     theme  = body.bindingsByRole(THEME_ROLE)[0].target()
 *     return source &lt;= theme &lt;= goal
 * </pre>
 *
 * <p>{@code frame} arrives as a Java host object; Python calls Java methods
 * on it via GraalVM's polyglot host-access.  The role IIDs are pre-bound
 * into the Python context at materialization time.
 */
public class PolyglotItem extends Item {

    private final ItemRef archetype;
    private final Context context;
    private final Value receiveFunction;

    /**
     * Construct from a pre-evaluated polyglot {@link Value} for the guest's
     * {@code receive} function.  The {@link Context} is held so it stays
     * alive for the item's lifetime — closing the item closes the context.
     */
    public PolyglotItem(ItemRef iid, Librarian librarian, ItemRef archetype,
                        Context context, Value receiveFunction) {
        super(iid, librarian);
        this.archetype = archetype;
        this.context = context;
        this.receiveFunction = receiveFunction;
    }

    @Override
    public ItemRef archetype() {
        return archetype;
    }

    /**
     * Deliver the frame to the guest's {@code receive} function and marshal
     * the result back to a Java value.  Booleans, integers, strings and
     * null come through as their natural Java types; anything else returns
     * a {@link Value}.
     */
    @Override
    public Object receive(Frame frame) {
        Value result = receiveFunction.execute(frame);
        if (result == null || result.isNull()) return null;
        if (result.isBoolean()) return result.asBoolean();
        if (result.isNumber()) {
            return result.fitsInLong() ? result.asLong() : result.asDouble();
        }
        if (result.isString()) return result.asString();
        return result;
    }

    /** Release the polyglot {@link Context}.  Call when evicting the item. */
    public void close() {
        if (context != null) {
            context.close();
        }
    }
}
