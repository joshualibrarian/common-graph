/**
 * Expression language for the Common Graph.
 *
 * <p><b>"Expressions all the way down."</b>
 *
 * <p>This package defines the core abstraction for computation in the graph.
 * An {@link Expression} is a recipe for producing a value. Expressions are:
 * <ul>
 *   <li><b>Canonical</b> - stored as the "snapshot" of a Value component</li>
 *   <li><b>Composable</b> - expressions can reference other expressions</li>
 *   <li><b>Evaluable</b> - produce a result when given a context (Librarian)</li>
 * </ul>
 *
 * <h2>Expression Types</h2>
 *
 * <table>
 *   <tr><th>Type</th><th>Example</th><th>Description</th></tr>
 *   <tr>
 *     <td>{@link LiteralExpression}</td>
 *     <td>{@code 42}, {@code "hello"}</td>
 *     <td>Constant values</td>
 *   </tr>
 *   <tr>
 *     <td>{@link PatternExpression}</td>
 *     <td>{@code ? → implemented-by → *}</td>
 *     <td>Graph pattern queries</td>
 *   </tr>
 *   <tr>
 *     <td>{@link BinaryExpression}</td>
 *     <td>{@code x + y}, {@code a && b}</td>
 *     <td>Two-operand operations</td>
 *   </tr>
 *   <tr>
 *     <td>{@link UnaryExpression}</td>
 *     <td>{@code -x}, {@code !flag}</td>
 *     <td>Single-operand operations</td>
 *   </tr>
 *   <tr>
 *     <td>{@link ReferenceExpression}</td>
 *     <td>{@code total}, {@code other.amount}</td>
 *     <td>References to other values</td>
 *   </tr>
 *   <tr>
 *     <td>{@link SequenceExpression}</td>
 *     <td>{@code a; b; c}</td>
 *     <td>Sequential evaluation</td>
 *   </tr>
 *   <tr>
 *     <td>{@link ConditionalExpression}</td>
 *     <td>{@code if x then a else b}</td>
 *     <td>Conditional branching</td>
 *   </tr>
 *   <tr>
 *     <td>{@link BindingExpression}</td>
 *     <td>{@code let x = 10 in x + 1}</td>
 *     <td>Local variable binding</td>
 *   </tr>
 *   <tr>
 *     <td>{@link FunctionExpression}</td>
 *     <td>{@code format("Hello, %s!", name)}</td>
 *     <td>Function calls</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage</h2>
 *
 * <p>Expressions are used within {@link dev.everydaythings.graph.frame.Value}
 * components. The expression is the "snapshot" (what gets stored), and the evaluated
 * result is the "stream" (what gets computed).
 *
 * <pre>{@code
 * // Create a literal value
 * Value name = Value.literal("Alice");
 *
 * // Create a computed value
 * Value sum = Value.of(add(ref("a"), ref("b")));
 *
 * // Create a pattern query
 * Value types = Value.subjects(IMPLEMENTED_BY);
 *
 * // Evaluate
 * Object result = value.evaluate(librarian);
 * }</pre>
 *
 * <h2>Inter-Item References</h2>
 *
 * <p>Expressions can reference values in other items, creating a computation graph:
 *
 * <pre>{@code
 * // In Item A: x = 10
 * Value x = Value.literal(10);
 *
 * // In Item B: y = A.x + 5
 * Value y = Value.of(add(ref(itemA.iid(), "x"), literal(5)));
 * }</pre>
 *
 * @see Expression
 * @see dev.everydaythings.graph.frame.Value
 */
package dev.everydaythings.graph.frame.expression;
