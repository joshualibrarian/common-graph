package dev.everydaythings.graph.ui.text;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves emoji text into icon resources (e.g., OpenMoji SVG) with fallback.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Explicit resource path from scene/image node (if present)</li>
 *   <li>Emoji icon set mapping (when enabled)</li>
 *   <li>No resource (caller falls back to text shaping/rendering)</li>
 * </ol>
 *
 * <p>System properties:
 * <ul>
 *   <li>{@code graph.emoji.iconMode}: {@code auto} (default), {@code openmoji}, {@code font}</li>
 *   <li>{@code graph.emoji.openmoji.base}: classpath base path
 *       (default {@code /icons/openmoji/svg})</li>
 * </ul>
 */
public final class EmojiIconResolver {
    private static final String MODE_PROP = "graph.emoji.iconMode";
    private static final String OPENMOJI_BASE_PROP = "graph.emoji.openmoji.base";
    private static final String DEFAULT_MODE = "auto";
    private static final String DEFAULT_OPENMOJI_BASE = "/icons/openmoji/svg";

    private static final Map<String, Boolean> EXISTS_CACHE = new ConcurrentHashMap<>();

    private EmojiIconResolver() {}

    public static String resolveResource(String explicitResource, String altText) {
        if (explicitResource != null && !explicitResource.isBlank()) {
            return explicitResource;
        }
        String mode = System.getProperty(MODE_PROP, DEFAULT_MODE).trim().toLowerCase(Locale.ROOT);
        if ("font".equals(mode)) {
            return null;
        }
        if (!"auto".equals(mode) && !"openmoji".equals(mode)) {
            return null;
        }
        if (!isEmojiLike(altText)) {
            return null;
        }
        String openMoji = resolveOpenMojiResource(altText);
        if (openMoji != null) {
            return openMoji;
        }
        return null;
    }

    private static String resolveOpenMojiResource(String text) {
        String base = normalizeBase(System.getProperty(OPENMOJI_BASE_PROP, DEFAULT_OPENMOJI_BASE));
        for (String filename : filenameCandidates(text)) {
            String resource = base + "/" + filename + ".svg";
            if (resourceExists(resource)) {
                return resource;
            }
        }
        return null;
    }

    private static String normalizeBase(String value) {
        if (value == null || value.isBlank()) return DEFAULT_OPENMOJI_BASE;
        String base = value.trim();
        if (!base.startsWith("/")) base = "/" + base;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static boolean resourceExists(String resource) {
        return EXISTS_CACHE.computeIfAbsent(resource, EmojiIconResolver::checkResourceExists);
    }

    private static boolean checkResourceExists(String resource) {
        String path = resource.startsWith("/") ? resource.substring(1) : resource;
        try (InputStream in = EmojiIconResolver.class.getClassLoader().getResourceAsStream(path)) {
            return in != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static List<String> filenameCandidates(String text) {
        int[] cps = text.codePoints().toArray();
        List<String> out = new ArrayList<>(2);
        if (cps.length == 0) return out;
        out.add(toHexFilename(cps));

        int[] noVs = Arrays.stream(cps)
                .filter(cp -> cp != 0xFE0F && cp != 0xFE0E)
                .toArray();
        if (noVs.length > 0 && noVs.length != cps.length) {
            out.add(toHexFilename(noVs));
        }
        return out;
    }

    private static String toHexFilename(int[] cps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cps.length; i++) {
            if (i > 0) sb.append('-');
            sb.append(Integer.toHexString(cps[i]).toUpperCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static boolean isEmojiLike(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        if (t.indexOf(' ') >= 0 || t.indexOf('\n') >= 0 || t.indexOf('\t') >= 0) return false;

        int[] cps = t.codePoints().toArray();
        if (cps.length == 0 || cps.length > 10) return false;
        boolean hasEmoji = false;
        for (int cp : cps) {
            if (cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || cp == 0x20E3) continue;
            if (cp >= 0x1F3FB && cp <= 0x1F3FF) continue; // skin tone modifiers
            if (isEmojiBase(cp)) {
                hasEmoji = true;
                continue;
            }
            return false;
        }
        return hasEmoji;
    }

    private static boolean isEmojiBase(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF)
                || (cp >= 0x2600 && cp <= 0x27BF)
                || (cp >= 0x2300 && cp <= 0x23FF)
                || cp == 0x00A9 || cp == 0x00AE || cp == 0x2122;
    }
}
