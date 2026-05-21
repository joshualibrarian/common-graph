package dev.everydaythings.graph.datum;

/**
 * DatumNode — the unifying type for anything that can occupy a node in a
 * datum's canonical tree.
 *
 * <p>The data model of a body is a tree of nodes: the body's head, its
 * bindings list, each binding (with its compound key and target), each
 * qualifier inside a compound key.  At every node position, the value can
 * be either the structural type that "normally" lives there (a Binding, a
 * CompoundKey, a Qualifier, a Body, etc.) or an {@link Opaque} standing in
 * for it.  This interface is the common parent of every type that can
 * appear in such a node, so polymorphic slots ({@code List<DatumNode>}, a
 * widened key field, ...) can hold either flavor without losing type
 * safety.
 *
 * <p>Implementors today:
 *
 * <ul>
 *   <li>{@link Datum} (so {@link Body} and {@link Record} transitively)
 *       — a whole signed-or-unsigned datum, headed by a sememe.</li>
 *   <li>{@link Binding} — a single role→value entry in a body's bindings
 *       list.</li>
 *   <li>{@link dev.everydaythings.graph.ref.CompoundKey CompoundKey} — the
 *       semantic address of a binding (head sememe + qualifiers).</li>
 *   <li>{@link dev.everydaythings.graph.ref.CompoundKey.Qualifier Qualifier}
 *       — a single sememe or literal qualifier inside a CompoundKey.</li>
 *   <li>{@link BindingTarget} (so its implementing {@link
 *       BindingTarget.RefTarget RefTarget} and {@link BindingTarget.FrameTarget
 *       FrameTarget}) — structured forms a binding target can take.</li>
 *   <li>{@link Opaque} (so {@link Opaque.Redacted}, {@link Opaque.Compressed},
 *       {@link Opaque.Encrypted}) — merkle-preserving stand-ins.</li>
 *   <li>{@link dev.everydaythings.graph.ref.HashID HashID} (so {@link
 *       dev.everydaythings.graph.ref.ItemRef ItemRef}, {@link
 *       dev.everydaythings.graph.ref.ContentRef ContentRef}, {@link
 *       dev.everydaythings.graph.ref.DatumRef DatumRef}, …) — references
 *       that can stand at a leaf-ish node.</li>
 * </ul>
 *
 * <p>Leaf literals (String, Long, Boolean, byte[], Instant, BigInteger,
 * BigDecimal, Rational) are not DatumNode — they're standard Java types
 * carried as-is.  Slots that can hold a literal alongside a DatumNode use
 * {@code Object} (today's case for {@link Binding#target()}).
 *
 * <p>This interface is intentionally unsealed and methodless — a marker.
 * Code that processes nodes (CanonWalker, the CBOR codec, the matcher,
 * etc.) does its own dispatch by runtime type; the marker exists for
 * polymorphic slot typing, not for shared API.
 */
public interface DatumNode {
}
