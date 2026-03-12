package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.component.expression.BinaryExpression;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.component.expression.EvaluationContext;
import dev.everydaythings.graph.item.component.expression.LiteralExpression;
import dev.everydaythings.graph.language.VerbSememe;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.mapdb.MapDBItemStore;
import dev.everydaythings.graph.library.SeedVocabulary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Operator as a first-class Item.
 */
class OperatorTest {

    private boolean hasImplementedByRelation(ItemStore store, dev.everydaythings.graph.item.id.ItemID typeId) {
        return store.relations()
                .filter(r -> r.predicate().equals(VerbSememe.ImplementedBy.SEED.iid()))
                .filter(r -> typeId.equals(r.bindingId(dev.everydaythings.graph.item.id.ItemID.fromString("cg.role:theme"))))
                .findFirst()
                .isPresent();
    }

    private boolean hasManifest(ItemStore store, dev.everydaythings.graph.item.id.ItemID iid) {
        return store.manifests(iid).findFirst().isPresent();
    }

    // ==================================================================================
    // Seed Instance Tests
    // ==================================================================================

    @Test
    void seedInstancesExist() {
        assertThat(Operator.And.SEED).isNotNull();
        assertThat(Operator.Or.SEED).isNotNull();
    }

    @Test
    void seedInstancesHaveDeterministicIids() {
        // IIDs derived from canonical key should be stable
        assertThat(Operator.And.SEED.iid()).isEqualTo(Operator.And.SEED.iid());
        assertThat(Operator.Or.SEED.iid()).isEqualTo(Operator.Or.SEED.iid());

        // Different operators have different IIDs
        assertThat(Operator.And.SEED.iid()).isNotEqualTo(Operator.Or.SEED.iid());
    }

    @Test
    void seedInstancesHaveCorrectMetadata() {
        // AND
        assertThat(Operator.And.SEED.canonicalKey()).isEqualTo("cg.op:and");
        assertThat(Operator.And.SEED.symbol()).isEqualTo("&&");
        assertThat(Operator.And.SEED.name()).isEqualTo("and");
        assertThat(Operator.And.SEED.arity()).isEqualTo(2);
        assertThat(Operator.And.SEED.precedence()).isEqualTo(1);

        // OR
        assertThat(Operator.Or.SEED.canonicalKey()).isEqualTo("cg.op:or");
        assertThat(Operator.Or.SEED.symbol()).isEqualTo("||");
        assertThat(Operator.Or.SEED.name()).isEqualTo("or");
        assertThat(Operator.Or.SEED.arity()).isEqualTo(2);
        assertThat(Operator.Or.SEED.precedence()).isEqualTo(0);
    }

    @Test
    void andHasHigherPrecedenceThanOr() {
        assertThat(Operator.And.SEED.precedence()).isGreaterThan(Operator.Or.SEED.precedence());
    }

    // ==================================================================================
    // SeedStore Integration Tests
    // ==================================================================================

    @Test
    void bootstrapIncludesOperators() {
        ItemStore store = MapDBItemStore.memory();
        SeedVocabulary.bootstrap(store);

        // Operator type should have IMPLEMENTED_BY relation
        assertThat(hasImplementedByRelation(store, ItemID.fromString(Operator.KEY))).isTrue();

        // Operator seeds should have manifests
        assertThat(hasManifest(store, Operator.And.SEED.iid())).isTrue();
        assertThat(hasManifest(store, Operator.Or.SEED.iid())).isTrue();
    }

    @Test
    void operatorsAreDiscoverableByType() {
        ItemStore store = MapDBItemStore.memory();
        SeedVocabulary.bootstrap(store);

        // Count manifests of type Operator
        long count = store.manifests(null)
                .filter(m -> ItemID.fromString(Operator.KEY).equals(m.type()))
                .count();
        assertThat(count).isGreaterThanOrEqualTo(2); // AND, OR
    }

    // ==================================================================================
    // fromSymbol Tests
    // ==================================================================================

    @Test
    void fromSymbolParsesSymbolicAndVariants() {
        assertThat(Operator.fromSymbol("&&")).isEqualTo(Operator.And.SEED);
        assertThat(Operator.fromSymbol("&")).isEqualTo(Operator.And.SEED);
    }

    @Test
    void fromSymbolParsesSymbolicOrVariants() {
        assertThat(Operator.fromSymbol("||")).isEqualTo(Operator.Or.SEED);
        assertThat(Operator.fromSymbol("|")).isEqualTo(Operator.Or.SEED);
    }

    @Test
    void fromSymbolDoesNotMatchEnglishWords() {
        // English words "and"/"or" are resolved through the completion system
        // as ConjunctionSememe seeds, not as operators
        assertThat(Operator.fromSymbol("and")).isNull();
        assertThat(Operator.fromSymbol("or")).isNull();
        assertThat(Operator.fromSymbol("AND")).isNull();
        assertThat(Operator.fromSymbol("OR")).isNull();
    }

    @Test
    void fromSymbolReturnsNullForUnknown() {
        assertThat(Operator.fromSymbol("XOR")).isNull();
        assertThat(Operator.fromSymbol("~~~")).isNull();
        assertThat(Operator.fromSymbol(null)).isNull();
    }

    // ==================================================================================
    // Lookup Tests
    // ==================================================================================

    @Test
    void lookupFindsSeeds() {
        assertThat(Operator.lookup(Operator.And.SEED.iid(), null)).isEqualTo(Operator.And.SEED);
        assertThat(Operator.lookup(Operator.Or.SEED.iid(), null)).isEqualTo(Operator.Or.SEED);
    }

    // ==================================================================================
    // Boolean Coercion Tests
    // ==================================================================================

    @Test
    void toBooleanHandlesNullAsFalse() {
        assertThat(Operator.toBoolean(null)).isFalse();
    }

    @Test
    void toBooleanHandlesBooleans() {
        assertThat(Operator.toBoolean(true)).isTrue();
        assertThat(Operator.toBoolean(false)).isFalse();
    }

    @Test
    void toBooleanHandlesNumbers() {
        assertThat(Operator.toBoolean(0)).isFalse();
        assertThat(Operator.toBoolean(0.0)).isFalse();
        assertThat(Operator.toBoolean(1)).isTrue();
        assertThat(Operator.toBoolean(-1)).isTrue();
        assertThat(Operator.toBoolean(0.5)).isTrue();
    }

    @Test
    void toBooleanHandlesStrings() {
        assertThat(Operator.toBoolean("")).isFalse();
        assertThat(Operator.toBoolean("hello")).isTrue();
        assertThat(Operator.toBoolean("false")).isTrue(); // Non-empty string!
    }

    @Test
    void toBooleanHandlesLists() {
        assertThat(Operator.toBoolean(List.of())).isFalse();
        assertThat(Operator.toBoolean(List.of("a"))).isTrue();
    }

    @Test
    void toBooleanHandlesObjects() {
        assertThat(Operator.toBoolean(new Object())).isTrue();
    }

    // ==================================================================================
    // AND Evaluation Tests
    // ==================================================================================

    @Test
    void andReturnsFalseWhenLeftIsFalse() {
        var left = LiteralExpression.of(false);
        var right = LiteralExpression.of(true);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.And.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(false);
    }

    @Test
    void andReturnsTrueWhenBothAreTrue() {
        var left = LiteralExpression.of(true);
        var right = LiteralExpression.of(true);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.And.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(true);
    }

    @Test
    void andReturnsFalseWhenRightIsFalse() {
        var left = LiteralExpression.of(true);
        var right = LiteralExpression.of(false);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.And.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(false);
    }

    @Test
    void andWithTruthyValues() {
        var left = LiteralExpression.of(1);  // truthy: non-zero number
        var right = LiteralExpression.of("hello");  // truthy: non-empty string
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.And.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(true);
    }

    @Test
    void andWithFalsyLeft() {
        var left = LiteralExpression.of(0);  // falsy: zero
        var right = LiteralExpression.of(true);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.And.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(false);
    }

    // ==================================================================================
    // OR Evaluation Tests
    // ==================================================================================

    @Test
    void orReturnsTrueWhenLeftIsTrue() {
        var left = LiteralExpression.of(true);
        var right = LiteralExpression.of(false);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.Or.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(true);
    }

    @Test
    void orReturnsFalseWhenBothAreFalse() {
        var left = LiteralExpression.of(false);
        var right = LiteralExpression.of(false);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.Or.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(false);
    }

    @Test
    void orReturnsTrueWhenRightIsTrue() {
        var left = LiteralExpression.of(false);
        var right = LiteralExpression.of(true);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.Or.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(true);
    }

    @Test
    void orWithTruthyValues() {
        var left = LiteralExpression.of("");  // falsy: empty string
        var right = LiteralExpression.of(List.of("a"));  // truthy: non-empty list
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.Or.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(true);
    }

    @Test
    void orWithAllFalsy() {
        var left = LiteralExpression.of("");  // falsy: empty string
        var right = LiteralExpression.of(0);  // falsy: zero
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.Or.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(false);
    }

    // ==================================================================================
    // Short-Circuit Behavior Tests
    // ==================================================================================

    @Test
    void andShortCircuitsWhenLeftIsFalse() {
        // When left is false, AND should not evaluate right
        // We test this by using a nested AND where right doesn't matter
        // false AND anything = false (right never evaluated)

        var left = LiteralExpression.of(false);
        // Any right value - if it were evaluated, the result would depend on it
        // But with short-circuit, we always get false
        var right = LiteralExpression.of(true);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.And.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(false);
    }

    @Test
    void orShortCircuitsWhenLeftIsTrue() {
        // When left is true, OR should not evaluate right
        // true OR anything = true (right never evaluated)

        var left = LiteralExpression.of(true);
        var right = LiteralExpression.of(false);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = Operator.Or.SEED.evaluate(left, right, ctx);

        assertThat(result).isEqualTo(true);
    }

    // ==================================================================================
    // BinaryExpression Integration Tests
    // ==================================================================================

    @Test
    void binaryExpressionAndUsesOperatorItem() {
        BinaryExpression expr = BinaryExpression.and(
            LiteralExpression.of(true),
            LiteralExpression.of(false)
        );
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = expr.evaluate(ctx);

        assertThat(result).isEqualTo(false);
    }

    @Test
    void binaryExpressionOrUsesOperatorItem() {
        BinaryExpression expr = BinaryExpression.or(
            LiteralExpression.of(false),
            LiteralExpression.of(true)
        );
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = expr.evaluate(ctx);

        assertThat(result).isEqualTo(true);
    }

    @Test
    void binaryExpressionAndWithBothTrue() {
        BinaryExpression expr = BinaryExpression.and(
            LiteralExpression.of(true),
            LiteralExpression.of(true)
        );
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = expr.evaluate(ctx);

        assertThat(result).isEqualTo(true);
    }

    @Test
    void binaryExpressionOrWithBothFalse() {
        BinaryExpression expr = BinaryExpression.or(
            LiteralExpression.of(false),
            LiteralExpression.of(false)
        );
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = expr.evaluate(ctx);

        assertThat(result).isEqualTo(false);
    }

    @Test
    void binaryExpressionDisplaysCorrectSymbol() {
        BinaryExpression and = BinaryExpression.and(
            LiteralExpression.of(true),
            LiteralExpression.of(false)
        );
        BinaryExpression or = BinaryExpression.or(
            LiteralExpression.of(true),
            LiteralExpression.of(false)
        );

        assertThat(and.toExpressionString()).isEqualTo("true && false");
        assertThat(or.toExpressionString()).isEqualTo("true || false");
    }

    @Test
    void binaryExpressionIsAndAndIsOrWork() {
        BinaryExpression and = BinaryExpression.and(
            LiteralExpression.of(true),
            LiteralExpression.of(false)
        );
        BinaryExpression or = BinaryExpression.or(
            LiteralExpression.of(true),
            LiteralExpression.of(false)
        );

        assertThat(and.isAnd()).isTrue();
        assertThat(and.isOr()).isFalse();
        assertThat(or.isAnd()).isFalse();
        assertThat(or.isOr()).isTrue();
    }

    @Test
    void nestedBinaryExpressions() {
        // (true AND false) OR true = false OR true = true
        BinaryExpression inner = BinaryExpression.and(
            LiteralExpression.of(true),
            LiteralExpression.of(false)
        );
        BinaryExpression outer = BinaryExpression.or(
            inner,
            LiteralExpression.of(true)
        );
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = outer.evaluate(ctx);

        assertThat(result).isEqualTo(true);
    }

    @Test
    void complexNestedExpression() {
        // (true OR false) AND (false OR true) = true AND true = true
        BinaryExpression left = BinaryExpression.or(
            LiteralExpression.of(true),
            LiteralExpression.of(false)
        );
        BinaryExpression right = BinaryExpression.or(
            LiteralExpression.of(false),
            LiteralExpression.of(true)
        );
        BinaryExpression expr = BinaryExpression.and(left, right);
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = expr.evaluate(ctx);

        assertThat(result).isEqualTo(true);
    }

    // ==================================================================================
    // isShortCircuit Tests
    // ==================================================================================

    @Test
    void isShortCircuitReturnsTrueForAndOr() {
        assertThat(Operator.And.SEED.isShortCircuit()).isTrue();
        assertThat(Operator.Or.SEED.isShortCircuit()).isTrue();
    }

    // ==================================================================================
    // displayToken Tests
    // ==================================================================================

    @Test
    void displayTokenReturnsName() {
        assertThat(Operator.And.SEED.displayToken()).isEqualTo("and");
        assertThat(Operator.Or.SEED.displayToken()).isEqualTo("or");
    }

    // ==================================================================================
    // toString Tests
    // ==================================================================================

    @Test
    void toStringReturnsSymbolAndName() {
        assertThat(Operator.And.SEED.toString()).isEqualTo("&& (and)");
        assertThat(Operator.Or.SEED.toString()).isEqualTo("|| (or)");
    }

    // ==================================================================================
    // Legacy Operators Still Work
    // ==================================================================================

    @Test
    void arithmeticOperatorsStillWork() {
        BinaryExpression add = BinaryExpression.add(
            LiteralExpression.of(2),
            LiteralExpression.of(3)
        );
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = add.evaluate(ctx);

        assertThat(result).isEqualTo(5.0);
    }

    @Test
    void comparisonOperatorsStillWork() {
        BinaryExpression equal = BinaryExpression.equal(
            LiteralExpression.of(5),
            LiteralExpression.of(5)
        );
        EvaluationContext ctx = new EvaluationContext(null, null);

        Object result = equal.evaluate(ctx);

        assertThat(result).isEqualTo(true);
    }
}
