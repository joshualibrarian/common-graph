package dev.everydaythings.graph.policy;

import dev.everydaythings.graph.item.id.ItemID;

import java.util.function.Function;
import java.util.regex.Matcher;

public final class Macros {
    /** Replace %SELF% in a glob or regex with the caller’s short id. */
    public static String injectSelf(String pattern, ItemID self, Function<ItemID,String> shortener) {
        if (pattern == null || self == null) return pattern;
        String s = shortener.apply(self); // e.g., replace ‘:’ with ‘_’
        return pattern.replace("%SELF%", Matcher.quoteReplacement(s));
    }
}
