package dev.everydaythings.graph.imports.keyboard;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.InputVocabulary;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * KeyImporter — reads {@code keys.tsv} from the classpath and seeds one
 * {@link InputVocabulary.Key Key} item per row into the given librarian.
 *
 * <p>Each row's IID is derived deterministically from its W3C UI Events
 * code (e.g., {@code cg.key:KeyA}, {@code cg.key:ControlLeft}), so the
 * same key seeded on two different installations produces structurally
 * identical bodies and the same IID — they merge idempotently.
 *
 * <p>Per-key seeds are <b>unsigned</b>.  Once the codebase is released,
 * the developers' real keys sign the canonical set and distribute them
 * on the live graph; future installs receive the signed bodies and the
 * local-bootstrap entries merge cleanly with them (same IID, same body
 * bytes, the signed record just adds attestation).
 *
 * <p>The importer is invoked explicitly by callers that want keyboard
 * vocabulary populated.  {@code :core} doesn't know about it — the
 * dependency is one-way ({@code :imports:keyboard} → {@code :core}).
 *
 * <p>Per-row body shape:
 *
 * <pre>
 * Body[head = {category-subarchetype}]
 *   ITEM_ID    → @cg.key:&lt;code&gt;
 *   ENDORSES   → &lt;english-name-lexeme-frame DatumRef&gt;
 *   ENDORSES   → &lt;alias-lexeme-frame DatumRef&gt; ...  (one per alias)
 * </pre>
 *
 * <p>USB HID codes and layout hints are intentionally not yet seeded as
 * bindings — adding them as predicate frames is straightforward once
 * concrete consumers emerge that want to query by them.
 */
@Log4j2
public final class KeyImporter {

    /** Default classpath resource name. */
    public static final String DEFAULT_RESOURCE = "/keys.tsv";

    private KeyImporter() {}

    /**
     * Read {@code /keys.tsv} from the classpath and seed all entries.
     * Returns the number of key items seeded.
     */
    public static int bootstrap(Librarian librarian) {
        return bootstrap(librarian, DEFAULT_RESOURCE);
    }

    /**
     * Read the given classpath resource and seed all entries.  Useful
     * for tests that supply alternate TSV inputs.
     */
    public static int bootstrap(Librarian librarian, String resourcePath) {
        InputStream in = KeyImporter.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Keyboard TSV resource not found: " + resourcePath);
        }
        try (in) {
            return bootstrap(librarian, in);
        } catch (IOException e) {
            throw new RuntimeException("Failed reading keyboard TSV: " + resourcePath, e);
        }
    }

    /** Read the given TSV stream and seed all entries.  Returns the number seeded. */
    public static int bootstrap(Librarian librarian, InputStream in) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean headerSeen = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (!headerSeen) {
                    headerSeen = true;
                    continue;  // skip the column-name header row
                }
                Row row = parseRow(line);
                if (row == null) continue;
                seedKey(librarian, row);
                count++;
            }
        }
        logger.debug("KeyImporter seeded {} keys", count);
        return count;
    }

    // ==================================================================================
    // Per-key seeding.
    // ==================================================================================

    private static void seedKey(Librarian librarian, Row row) {
        ItemRef keyIid = ItemRef.fromString("cg.key:" + row.code);
        ItemRef categoryHead = categoryArchetype(row.category);

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(Manifest.ITEM_ID, keyIid));

        // English-name lexeme (canonical Lemma).
        if (!row.englishName.isEmpty()) {
            DatumRef lemmaFrame = persistLexeme(librarian, row.englishName, false);
            bindings.add(new Binding(Manifest.ENDORSES, lemmaFrame));
        }

        // Each alias becomes a Lexeme frame with the Alias qualifier in
        // place of Lemma.
        for (String alias : row.aliases) {
            if (alias.isEmpty()) continue;
            DatumRef aliasFrame = persistLexeme(librarian, alias, true);
            bindings.add(new Binding(Manifest.ENDORSES, aliasFrame));
        }

        librarian.persist(Body.of(ItemRef.of(categoryHead), bindings));
    }

    /**
     * Build and persist a Lexeme frame body.  Returns its DatumRef so it
     * can be cited by an ENDORSES binding on a manifest.
     */
    private static DatumRef persistLexeme(Librarian librarian, String text, boolean alias) {
        ItemRef qualifier = alias
                ? ItemRef.iid(LexicalVocabulary.Alias.KEY)
                : ItemRef.iid(GrammaticalFeature.Lemma.KEY);

        Body lexemeBody = (Body) Body.compose(ItemRef.iid(LexicalVocabulary.Lexeme.KEY))
                .binding(ItemRef.iid(ThematicRole.Value.KEY))
                    .qualifier(ItemRef.iid(Language.English.KEY))
                    .qualifier(ItemRef.iid(PartOfSpeech.Noun.KEY))
                    .qualifier(qualifier)
                    .target(text)
                .build();
        return librarian.persist(lexemeBody);
    }

    /** Map a TSV `category` value to its corresponding Key subarchetype IID. */
    private static ItemRef categoryArchetype(String category) {
        return switch (category) {
            case "letter"     -> ItemRef.iid(InputVocabulary.Letter.KEY);
            case "digit"      -> ItemRef.iid(InputVocabulary.Digit.KEY);
            case "modifier"   -> ItemRef.iid(InputVocabulary.Modifier.KEY);
            case "function"   -> ItemRef.iid(InputVocabulary.Function.KEY);
            case "navigation" -> ItemRef.iid(InputVocabulary.Navigation.KEY);
            case "whitespace" -> ItemRef.iid(InputVocabulary.Whitespace.KEY);
            case "symbol"     -> ItemRef.iid(InputVocabulary.SymbolKey.KEY);
            case "lock"       -> ItemRef.iid(InputVocabulary.Lock.KEY);
            case "media"      -> ItemRef.iid(InputVocabulary.MediaKey.KEY);
            default -> throw new IllegalArgumentException("Unknown key category: " + category);
        };
    }

    // ==================================================================================
    // TSV parsing.
    // ==================================================================================

    /** Parsed TSV row.  Fields after the last present column default to empty. */
    private record Row(
            String code,
            String category,
            String usbHidCode,
            String englishName,
            String languageLayoutHint,
            List<String> aliases) {}

    private static Row parseRow(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "TSV row needs at least 4 columns (code, category, usb_hid_code, english_name); got: "
                            + line);
        }
        String code = parts[0].trim();
        String category = parts[1].trim();
        String usbHidCode = parts[2].trim();
        String englishName = parts[3].trim();
        String layoutHint = parts.length > 4 ? parts[4].trim() : "";
        List<String> aliases = parts.length > 5 ? splitAliases(parts[5]) : List.of();
        if (code.isEmpty() || category.isEmpty()) return null;
        return new Row(code, category, usbHidCode, englishName, layoutHint, aliases);
    }

    private static List<String> splitAliases(String raw) {
        if (raw == null) return List.of();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return List.of();
        String[] parts = trimmed.split(",");
        List<String> result = new ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }
}
