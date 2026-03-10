package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * A resolved sememe reference — carries both the ItemID and the display token.
 *
 * <p>In general expression evaluation, evaluates to the ItemID (same as a literal).
 * In assignment context ({@code author = Tolkien} or {@code author is Tolkien}),
 * the token is used as the component handle name on the focused item.
 *
 * <p>Created by ExpressionParser when a RefToken (resolved dictionary match)
 * appears in an expression. The token is transient — not serialized. On
 * deserialization, the expression degrades to a plain ItemID literal.
 */
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
public class SememeExpression implements Expression, Canonical {

    @Canon(order = 0)
    private final ItemID target;

    /** The display text from the original token (e.g., "author"). Transient — not serialized. */
    private final String token;

    public SememeExpression(ItemID target, String token) {
        this.target = target;
        this.token = token;
    }

    /** Canonical decoder constructor. */
    @SuppressWarnings("unused")
    private SememeExpression() {
        this.target = null;
        this.token = null;
    }

    public ItemID target() { return target; }

    public String token() { return token; }

    @Override
    public Object evaluate(EvaluationContext context) {
        return target;
    }

    @Override
    public String toExpressionString() {
        return token != null ? token : (target != null ? target.encodeText() : "nil");
    }

    @Override
    public boolean hasDependencies() {
        return false;
    }
}
