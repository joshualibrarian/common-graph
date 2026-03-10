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
 * <p>Supported field types: boolean (toggle), enum (option list), String (editable text),
 * numeric (editable text with type-aware parsing), nested Canonical (expandable sub-editor).
 * Collections are display-only (item count) pending list editing support.
 *
 * @see dev.everydaythings.graph.CanonicalSchema
 */
package dev.everydaythings.graph.editing;
