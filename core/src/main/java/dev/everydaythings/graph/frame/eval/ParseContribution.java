package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.SemanticFrame;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * What a predicate tells the parser about how it participates in parsing.
 *
 * <p>ParseContribution carries both declarative metadata (precedence, fixity,
 * expected roles) and active behavior (delegate to sub-language, chain frames).
 * Most predicates return only metadata; the language parser interprets it
 * according to its own grammar rules.
 *
 * <p>The {@link #NONE} singleton is the common case — the predicate has no
 * parsing opinion.
 *
 * <p>Examples:
 * <ul>
 *   <li>Operator (+): {@code infix(5, LEFT)} — expression language handles precedence</li>
 *   <li>Function (sqrt): {@code prefix().grouped(true)} — expects parenthesized args</li>
 *   <li>Preposition (on): {@code assignRole(GOAL)} — next token fills this role</li>
 *   <li>Auxiliary (named): {@code chain(TITLE frame)} — chains a new frame</li>
 *   <li>Chess notation: {@code delegateTo(ChessNotation)} — switches parser</li>
 * </ul>
 */
@Getter
@Builder(toBuilder = true)
public class ParseContribution {

    /** No parsing opinion — the predicate does not influence parsing. */
    public static final ParseContribution NONE = ParseContribution.builder().build();

    // ==================================================================================
    // Declarative metadata (read by any language parser)
    // ==================================================================================

    /** Expression fixity: how this operator/function appears relative to its arguments. */
    private final Fixity fixity;

    /** Operator precedence for expression parsing (higher binds tighter). */
    private final int precedence;

    /** Operator associativity for expression parsing. */
    private final Associativity associativity;

    /** Role assigned by a preposition to its object (e.g., "on" assigns GOAL). */
    private final ItemID assignedRole;

    /** Expected argument roles for this predicate (e.g., CREATE expects THEME). */
    private final List<ItemID> expectedRoles;

    /** Whether this predicate expects grouped (parenthesized) arguments. */
    private final boolean grouped;

    // ==================================================================================
    // Active behavior
    // ==================================================================================

    /** Delegate parsing to this sub-language (e.g., chess notation, expression syntax). */
    private final Language subLanguage;

    /** Number of tokens consumed directly by this predicate during active parsing. */
    private final int tokensConsumed;

    /** Frames produced by active token consumption. */
    private final List<SemanticFrame> producedFrames;

    /** Additional frames to chain after the current frame. */
    private final List<SemanticFrame> chainedFrames;

    // ==================================================================================
    // Enums
    // ==================================================================================

    /** How an operator/function appears relative to its arguments. */
    public enum Fixity {
        /** Before arguments: {@code -x}, {@code sqrt(x)} */
        PREFIX,
        /** Between arguments: {@code x + y} */
        INFIX,
        /** After arguments: {@code x!} */
        POSTFIX
    }

    /** Operator associativity for same-precedence grouping. */
    public enum Associativity {
        /** Groups left: {@code a - b - c} = {@code (a - b) - c} */
        LEFT,
        /** Groups right: {@code a ^ b ^ c} = {@code a ^ (b ^ c)} */
        RIGHT,
        /** Cannot chain: {@code a < b < c} is an error */
        NONE
    }

    // ==================================================================================
    // Static factory methods
    // ==================================================================================

    /** Infix operator with precedence and associativity. */
    public static ParseContribution infix(int precedence, Associativity associativity) {
        return builder()
                .fixity(Fixity.INFIX)
                .precedence(precedence)
                .associativity(associativity)
                .build();
    }

    /** Prefix operator or function with precedence. */
    public static ParseContribution prefix(int precedence) {
        return builder()
                .fixity(Fixity.PREFIX)
                .precedence(precedence)
                .build();
    }

    /** Preposition that assigns a thematic role to its object. */
    public static ParseContribution assignRole(ItemID role) {
        return builder()
                .assignedRole(role)
                .build();
    }

    /** Delegate parsing to a sub-language. */
    public static ParseContribution delegateTo(Language language) {
        return builder()
                .subLanguage(language)
                .build();
    }

    /** Predicate consumed tokens directly and produced frames. */
    public static ParseContribution consumed(int tokenCount, List<SemanticFrame> frames) {
        return builder()
                .tokensConsumed(tokenCount)
                .producedFrames(frames)
                .build();
    }

    /** Chain additional frames after the current frame. */
    public static ParseContribution chain(List<SemanticFrame> frames) {
        return builder()
                .chainedFrames(frames)
                .build();
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    /** Whether this contribution has any content (is not NONE). */
    public boolean isPresent() {
        return this != NONE;
    }
}
