package dev.everydaythings.graph.importer;

import dev.everydaythings.graph.language.English;
import dev.everydaythings.graph.runtime.LibrarianOld;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerbNet Importer")
class VerbNetImporterTest {

    // ==================================================================================
    // XML Parser Tests — verify parsing of real VerbNet data
    // ==================================================================================

    @Nested
    @DisplayName("XML Parser")
    class Parser {

        @Test
        @DisplayName("parses give-13.1 class with members, roles, and subclass")
        void parseGiveClass() throws Exception {
            LibrarianOld lib = LibrarianOld.createInMemory();
            VerbNetImporter importer = new VerbNetImporter(lib, "verbnet");

            URL giveFile = getClass().getClassLoader().getResource("verbnet/give-13.1.xml");
            assertThat(giveFile).as("give-13.1.xml must be on classpath").isNotNull();

            VerbNetImporter.VerbClass vc = importer.parseVerbClass(giveFile);
            assertThat(vc).isNotNull();
            assertThat(vc.id()).isEqualTo("give-13.1");

            // Members
            assertThat(vc.members()).isNotEmpty();
            assertThat(vc.members().stream().map(VerbNetImporter.Member::name))
                    .contains("deal", "lend", "pass");

            // Thematic roles
            assertThat(vc.roles()).containsExactly("Agent", "Theme", "Recipient");

            // Subclass
            assertThat(vc.subclasses()).hasSize(1);
            VerbNetImporter.VerbClass sub = vc.subclasses().get(0);
            assertThat(sub.id()).isEqualTo("give-13.1-1");
            assertThat(sub.members().stream().map(VerbNetImporter.Member::name))
                    .contains("give", "sell", "rent");
            // Subclass adds Asset role
            assertThat(sub.roles()).contains("Asset");
        }

        @Test
        @DisplayName("parses member WordNet sense keys")
        void parseSenseKeys() throws Exception {
            LibrarianOld lib = LibrarianOld.createInMemory();
            VerbNetImporter importer = new VerbNetImporter(lib, "verbnet");

            URL giveFile = getClass().getClassLoader().getResource("verbnet/give-13.1.xml");
            VerbNetImporter.VerbClass vc = importer.parseVerbClass(giveFile);

            // "lend" has one sense key
            VerbNetImporter.Member lend = vc.members().stream()
                    .filter(m -> "lend".equals(m.name()))
                    .findFirst().orElseThrow();
            assertThat(lend.wnSenseKeys()).containsExactly("lend%2:40:00");

            // "deal" has multiple sense keys
            VerbNetImporter.Member deal = vc.members().stream()
                    .filter(m -> "deal".equals(m.name()))
                    .findFirst().orElseThrow();
            assertThat(deal.wnSenseKeys()).hasSizeGreaterThan(1);

            // "give-back" has no sense keys
            VerbNetImporter.Member giveBack = vc.members().stream()
                    .filter(m -> "give-back".equals(m.name()))
                    .findFirst().orElseThrow();
            assertThat(giveBack.wnSenseKeys()).isEmpty();
        }

        @Test
        @DisplayName("all 329 VerbNet files parse without errors")
        void parseAllFiles() throws Exception {
            LibrarianOld lib = LibrarianOld.createInMemory();
            VerbNetImporter importer = new VerbNetImporter(lib, "verbnet");

            // Parse all files — just verify no exceptions
            int classCount = 0;
            int totalMembers = 0;
            int totalRoles = 0;

            var dir = getClass().getClassLoader().getResource("verbnet");
            assertThat(dir).isNotNull();
            var dirFile = new java.io.File(dir.toURI());
            for (java.io.File f : dirFile.listFiles((d, n) -> n.endsWith(".xml"))) {
                VerbNetImporter.VerbClass vc = importer.parseVerbClass(f.toURI().toURL());
                assertThat(vc).as("Failed to parse: " + f.getName()).isNotNull();
                classCount++;
                totalMembers += vc.members().size();
                totalRoles += vc.roles().size();
            }

            assertThat(classCount).as("Should parse all VerbNet class files").isGreaterThanOrEqualTo(320);
            assertThat(totalMembers).as("Should have many members across all classes").isGreaterThan(2000);
            System.out.println("Parsed " + classCount + " classes, " + totalMembers + " members, " + totalRoles + " role declarations");
        }
    }

    // ==================================================================================
    // Sense Key Conversion Tests
    // ==================================================================================

    @Nested
    @DisplayName("Sense Key Conversion")
    class SenseKeyConversion {

        @Test
        @DisplayName("converts VerbNet sense key to OEWN format")
        void convertSenseKey() {
            assertThat(VerbNetImporter.convertToOewnSenseId("give%2:40:00"))
                    .isEqualTo("oewn-give__2.40.00..");
            assertThat(VerbNetImporter.convertToOewnSenseId("lend%2:40:00"))
                    .isEqualTo("oewn-lend__2.40.00..");
            assertThat(VerbNetImporter.convertToOewnSenseId("run%2:38:04"))
                    .isEqualTo("oewn-run__2.38.04..");
        }

        @Test
        @DisplayName("handles null and blank keys")
        void handlesBadKeys() {
            assertThat(VerbNetImporter.convertToOewnSenseId(null)).isNull();
            assertThat(VerbNetImporter.convertToOewnSenseId("")).isNull();
            assertThat(VerbNetImporter.convertToOewnSenseId("nopercent")).isNull();
        }
    }

    // ==================================================================================
    // Integration Test — small-scale with manual sememes
    // ==================================================================================

    @Nested
    @DisplayName("Integration")
    class Integration {

        @Test
        @DisplayName("sense key conversion produces correct OEWN format for lookup")
        void senseKeyRoundTrip() {
            // Verify the sense key format matches what OEWN import would store
            // VerbNet: lend%2:40:00 → OEWN sense ID: oewn-lend__2.40.00..
            String vnKey = "lend%2:40:00";
            String oewnId = VerbNetImporter.convertToOewnSenseId(vnKey);
            assertThat(oewnId).isEqualTo("oewn-lend__2.40.00..");

            // This is the ID that would be stored in WN_SENSE frames during OEWN import,
            // allowing VerbNet to find the sememe via findBySourceFrame()
        }

        @Test
        @DisplayName("role mapping covers all VerbNet roles in give-13.1")
        void roleMappingForGiveClass() throws Exception {
            LibrarianOld lib = LibrarianOld.createInMemory();
            VerbNetImporter importer = new VerbNetImporter(lib, "verbnet");

            URL giveFile = getClass().getClassLoader().getResource("verbnet/give-13.1.xml");
            VerbNetImporter.VerbClass vc = importer.parseVerbClass(giveFile);

            // Process without actual sememe lookup — just check role mapping works
            var unmapped = new java.util.LinkedHashSet<String>();
            importer.processClass(vc, List.of(), null, unmapped);

            // Agent, Theme, Recipient should all map. Asset from subclass may not.
            assertThat(unmapped).as("Only unmapped roles should be ones we haven't added yet")
                    .doesNotContain("Agent", "Theme", "Recipient");
        }
    }

    // ==================================================================================
    // Full Pipeline Test — WordNet + VerbNet (SLOW, run manually)
    // ==================================================================================

    @Nested
    @DisplayName("Full Pipeline")
    @Disabled("Full pipeline: WN=175s/2.7GB, VN=1s. 7,814 members, 23,991 EXPECTS.")
    class FullPipeline {

        @Test
        @DisplayName("WordNet import + VerbNet import end-to-end")
        void fullImportPipeline() {
            LibrarianOld lib = LibrarianOld.createInMemory();
            English english = new English(lib);

            // Step 1: Import WordNet (creates sememes + lexemes + sense key frames)
            System.out.println("=== Step 1: WordNet Import ===");
            long wnStart = System.currentTimeMillis();
            english.generate(lib, 0); // 0 = unlimited
            long wnDuration = System.currentTimeMillis() - wnStart;
            LanguageImporter.ImportStats wnStats = english.stats();

            System.out.println("  Sememes: " + wnStats.synsetCount());
            System.out.println("  Lexemes: " + wnStats.lexemeCount());
            System.out.println("  Relations: " + wnStats.relationCount());
            System.out.println("  Duration: " + wnDuration + "ms");
            System.out.println("  Memory: " + usedMemoryMB() + " MB");

            assertThat(wnStats.synsetCount()).isGreaterThan(100_000);

            // Step 2: Import VerbNet (adds EXPECTS to verb sememes)
            System.out.println("\n=== Step 2: VerbNet Import ===");
            long vnStart = System.currentTimeMillis();
            VerbNetImporter vnImporter = new VerbNetImporter(lib, "verbnet");
            VerbNetImporter.ImportStats vnStats = vnImporter.importAll(lib);
            long vnDuration = System.currentTimeMillis() - vnStart;

            System.out.println("  Classes: " + vnStats.classCount());
            System.out.println("  Members linked: " + vnStats.memberCount());
            System.out.println("  EXPECTS created: " + vnStats.expectsCount());
            System.out.println("  Duration: " + vnDuration + "ms");
            System.out.println("  Memory: " + usedMemoryMB() + " MB");

            assertThat(vnStats.classCount()).isGreaterThan(300);
            assertThat(vnStats.memberCount()).as("Should link many members to sememes").isGreaterThan(5000);
            assertThat(vnStats.expectsCount()).as("Should create EXPECTS frames").isGreaterThan(10000);

            System.out.println("\n=== Total Pipeline ===");
            System.out.println("  Duration: " + (wnDuration + vnDuration) + "ms");
            System.out.println("  Memory: " + usedMemoryMB() + " MB");
        }

        private long usedMemoryMB() {
            Runtime rt = Runtime.getRuntime();
            rt.gc();
            return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        }
    }
}
