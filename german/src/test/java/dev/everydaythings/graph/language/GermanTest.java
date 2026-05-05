package dev.everydaythings.graph.language;

import dev.everydaythings.graph.importer.LanguageImporter;
import dev.everydaythings.graph.runtime.LibrarianOld;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("German")
class GermanTest {

    @Test
    @DisplayName("German import with limit creates sememes and lexemes")
    void importWithLimit() {
        LibrarianOld lib = LibrarianOld.createInMemory();
        German german = new German(lib);
        german.generate(lib, 100);

        LanguageImporter.ImportStats stats = german.stats();
        assertThat(stats).isNotNull();
        assertThat(stats.synsetCount()).isEqualTo(100);
        assertThat(stats.lexemeCount()).isGreaterThan(0);

        System.out.println("German import (100 synsets):");
        System.out.println("  Synsets: " + stats.synsetCount());
        System.out.println("  Lexemes: " + stats.lexemeCount());
        System.out.println("  Relations: " + stats.relationCount());
        System.out.println("  Duration: " + stats.durationMs() + "ms");
    }

    @Test
    @Disabled("Full import: 36K synsets, 144K lexemes, 112s, 1.1GB.")
    @DisplayName("Full German import")
    void fullImport() {
        LibrarianOld lib = LibrarianOld.createInMemory();
        German german = new German(lib);

        long start = System.currentTimeMillis();
        german.generate(lib, 0);
        long duration = System.currentTimeMillis() - start;

        LanguageImporter.ImportStats stats = german.stats();
        System.out.println("German full import:");
        System.out.println("  Synsets: " + stats.synsetCount());
        System.out.println("  Lexemes: " + stats.lexemeCount());
        System.out.println("  Relations: " + stats.relationCount());
        System.out.println("  Duration: " + duration + "ms");

        Runtime rt = Runtime.getRuntime();
        rt.gc();
        long memMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        System.out.println("  Memory: " + memMB + " MB");

        assertThat(stats.synsetCount()).isGreaterThan(30000);
        assertThat(stats.lexemeCount()).isGreaterThan(50000);
    }
}
