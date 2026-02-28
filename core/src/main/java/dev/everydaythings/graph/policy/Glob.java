package dev.everydaythings.graph.policy;

import java.util.regex.Pattern;

public final class Glob {
    /** Convert a shell-like glob to a safe regex. */
    public static Pattern toRegex(String glob) {
        StringBuilder re = new StringBuilder("^");
        boolean inGroup = false;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '\\', '.', '(', ')', '+', '^', '$', '|' -> re.append('\\').append(c);
                case '*' -> {
                    boolean dbl = (i + 1 < glob.length() && glob.charAt(i + 1) == '*');
                    if (dbl) { re.append(".*"); i++; } else re.append("[^/]*");
                }
                case '?' -> re.append('.');
                case '[' -> re.append('[');
                case ']' -> re.append(']');
                case '{' -> { inGroup = true; re.append("(?:"); }
                case '}' -> { inGroup = false; re.append(')'); }
                case ',' -> re.append(inGroup ? '|' : ',');
                default -> re.append(c);
            }
        }
        re.append('$');
        return Pattern.compile(re.toString());
    }
}
