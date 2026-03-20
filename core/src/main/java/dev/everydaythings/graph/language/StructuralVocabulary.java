package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.eval.ParseContext;
import dev.everydaythings.graph.frame.eval.ParseContribution;
import dev.everydaythings.graph.frame.eval.ParseContribution.StructuralRole;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Structural syntax symbols — sememes that shape expression structure.
 *
 * <p>Each structural symbol is a proper {@link Sememe} subclass that carries
 * its parsing behavior via {@link #contribute}. This follows the same
 * pattern as {@link dev.everydaythings.graph.value.Operator}: the class IS
 * the behavior.
 *
 * <p>These symbols are scoped to the expression/math language — when the
 * lattice resolves {@code (}, its posting carries a math-language scope,
 * which tells the language inference system "we're in expression mode."
 *
 * <p>Many of these symbols are polysemous across languages:
 * <ul>
 *   <li>{@code (} — grouping in math, function call, parenthetical in English</li>
 *   <li>{@code .} — property access, decimal point, sentence end</li>
 *   <li>{@code ,} — argument separator, list separator, clause boundary</li>
 * </ul>
 *
 * <p>Different languages may register additional postings for these symbols
 * with their own scopes. The lattice and context scoring disambiguate.
 */
public class StructuralVocabulary extends Sememe {

    public static final String EXPRESSION_LANGUAGE_KEY = "cg:language/expr";
    public static final ItemID EXPRESSION_LANGUAGE = ItemID.fromString(EXPRESSION_LANGUAGE_KEY);

    /** The structural role this symbol plays in parsing. */
    @Getter
    private final StructuralRole structuralRole;

    protected StructuralVocabulary(String canonicalKey, String symbol,
                                   String gloss, StructuralRole role) {
        super(canonicalKey,
                Map.of("en", gloss),
                Map.of(),
                List.of(symbol),
                List.of());
        this.structuralRole = role;
    }

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected StructuralVocabulary(ItemID typeId) {
        super(typeId);
        this.structuralRole = null;
    }

    @Override
    public ParseContribution contribute(ParseContext context) {
        if (structuralRole != null) {
            return ParseContribution.structural(structuralRole);
        }
        return ParseContribution.NONE;
    }

    // ==================================================================================
    // GROUPING
    // ==================================================================================

    @Implements(OpenGroup.KEY)
    @ItemSeed(key = OpenGroup.KEY)
    public static class OpenGroup extends StructuralVocabulary {
        public static final String KEY = "cg.syntax:open-group";
        public static final ItemID IID = ItemID.fromString(KEY);

        OpenGroup() {
            super(KEY, "(", "opens a group or parenthesized expression",
                    StructuralRole.OPEN_GROUP);
        }
    }

    @Implements(CloseGroup.KEY)
    @ItemSeed(key = CloseGroup.KEY)
    public static class CloseGroup extends StructuralVocabulary {
        public static final String KEY = "cg.syntax:close-group";
        public static final ItemID IID = ItemID.fromString(KEY);

        CloseGroup() {
            super(KEY, ")", "closes a group or parenthesized expression",
                    StructuralRole.CLOSE_GROUP);
        }
    }

    // ==================================================================================
    // SEPARATORS
    // ==================================================================================

    @Implements(Separator.KEY)
    @ItemSeed(key = Separator.KEY)
    public static class Separator extends StructuralVocabulary {
        public static final String KEY = "cg.syntax:separator";
        public static final ItemID IID = ItemID.fromString(KEY);

        Separator() {
            super(KEY, ",", "separates arguments, list elements, or clause boundaries",
                    StructuralRole.SEPARATOR);
        }
    }

    @Implements(Sequence.KEY)
    @ItemSeed(key = Sequence.KEY)
    public static class Sequence extends StructuralVocabulary {
        public static final String KEY = "cg.syntax:sequence";
        public static final ItemID IID = ItemID.fromString(KEY);

        Sequence() {
            super(KEY, ";", "separates sequential expressions or statements",
                    StructuralRole.SEQUENCE);
        }
    }

    // ==================================================================================
    // CHAINING
    // ==================================================================================

    @Implements(Pipe.KEY)
    @ItemSeed(key = Pipe.KEY)
    public static class Pipe extends StructuralVocabulary {
        public static final String KEY = "cg.syntax:pipe";
        public static final ItemID IID = ItemID.fromString(KEY);

        Pipe() {
            super(KEY, "|", "explicit chain break; pipe output to next expression",
                    StructuralRole.PIPE);
        }
    }

    // ==================================================================================
    // ACCESS
    // ==================================================================================

    @Implements(Access.KEY)
    @ItemSeed(key = Access.KEY)
    public static class Access extends StructuralVocabulary {
        public static final String KEY = "cg.syntax:access";
        public static final ItemID IID = ItemID.fromString(KEY);

        Access() {
            super(KEY, ".", "property access; navigating into an item",
                    StructuralRole.ACCESS);
        }
    }
}
