/**
 * Schema-driven editing for Canonical types.
 *
 * <p>This package provides the foundation for editing any {@link dev.everydaythings.graph.Canonical}
 * object through its {@code @Canon} field schema. The key classes are:
 *
 * <ul>
 *   <li>{@link dev.everydaythings.graph.editing.EditModel} — stateful model wrapping a mutable Canonical,
 *       dispatches field-level events (toggle, select, set)</li>
 *   <li>{@link dev.everydaythings.graph.editing.CanonicalEditorSurface} — procedural surface that renders
 *       type-appropriate widgets per field (toggle for booleans, options for enums, text for strings)</li>
 * </ul>
 *
 * <p>Phase 1 supports boolean toggle and enum selection. String/numeric fields are display-only
 * until text input write-back is implemented.
 *
 * @see dev.everydaythings.graph.CanonicalSchema
 */
package dev.everydaythings.graph.editing;
