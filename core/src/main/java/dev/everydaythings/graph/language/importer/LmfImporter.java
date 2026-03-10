package dev.everydaythings.graph.language.importer;

import javax.xml.stream.*;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

/**
 * Imports WordNet data from Global WordNet LMF (Lexical Markup Framework) XML format.
 *
 * <p>This parser uses StAX (Streaming API for XML) for memory-efficient
 * parsing of large LMF files (typically 80-100MB).
 *
 * <p>LMF is the standard interchange format for Global WordNet Association
 * wordnets, including Open English WordNet (OEWN).
 *
 * <p>Usage:
 * <pre>{@code
 * try (var importer = LmfImporter.fromResource("/english-wordnet-2025.xml")) {
 *     importer.synsets().forEach(System.out::println);
 * }
 * }</pre>
 *
 * @see <a href="https://globalwordnet.github.io/schemas/">GWN-LMF Schema</a>
 */
public class LmfImporter extends WordNetImporter {

    private final InputStream inputStream;
    private final boolean ownsStream;
    private SourceMetadata metadata;
    private boolean metadataParsed = false;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create an importer from an input stream.
     *
     * @param inputStream The XML input stream
     * @param ownsStream  If true, the stream will be closed when the importer is closed
     */
    public LmfImporter(InputStream inputStream, boolean ownsStream) {
        this.inputStream = inputStream;
        this.ownsStream = ownsStream;
    }

    /**
     * Create an importer from a file path.
     *
     * <p>Automatically handles .gz compressed files.
     */
    public static LmfImporter fromPath(Path path) throws IOException {
        InputStream is = Files.newInputStream(path);
        if (path.toString().endsWith(".gz")) {
            is = new GZIPInputStream(is);
        }
        return new LmfImporter(is, true);
    }

    /**
     * Create an importer from a classpath resource.
     *
     * <p>Automatically handles .gz compressed files.
     */
    public static LmfImporter fromResource(String resourcePath) throws IOException {
        URL url = LmfImporter.class.getResource(resourcePath);
        if (url == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        InputStream is = url.openStream();
        if (resourcePath.endsWith(".gz")) {
            is = new GZIPInputStream(is);
        }
        return new LmfImporter(is, true);
    }

    // ==================================================================================
    // WORDNET IMPORTER IMPLEMENTATION
    // ==================================================================================

    @Override
    public SourceMetadata metadata() {
        if (!metadataParsed) {
            parseMetadataOnly();
        }
        return metadata;
    }

    @Override
    public Stream<SynsetRecord> synsets() {
        try {
            XMLStreamReader reader = createReader();
            Iterator<SynsetRecord> iterator = new SynsetIterator(reader);
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                    false
            ).onClose(() -> closeReader(reader));
        } catch (XMLStreamException e) {
            throw new RuntimeException("Failed to create XML reader", e);
        }
    }

    @Override
    public Stream<LexicalEntryRecord> lexicalEntries() {
        try {
            XMLStreamReader reader = createReader();
            Iterator<LexicalEntryRecord> iterator = new LexicalEntryIterator(reader);
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                    false
            ).onClose(() -> closeReader(reader));
        } catch (XMLStreamException e) {
            throw new RuntimeException("Failed to create XML reader", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (ownsStream && inputStream != null) {
            inputStream.close();
        }
    }

    // ==================================================================================
    // PARSING HELPERS
    // ==================================================================================

    private XMLStreamReader createReader() throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Security: disable external entities
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory.createXMLStreamReader(inputStream);
    }

    private void closeReader(XMLStreamReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException e) {
            // Ignore
        }
    }

    private void parseMetadataOnly() {
        try {
            XMLStreamReader reader = createReader();
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("Lexicon".equals(reader.getLocalName())) {
                        metadata = new SourceMetadata(
                                reader.getAttributeValue(null, "id"),
                                reader.getAttributeValue(null, "label"),
                                reader.getAttributeValue(null, "language"),
                                reader.getAttributeValue(null, "version"),
                                reader.getAttributeValue(null, "license"),
                                reader.getAttributeValue(null, "url")
                        );
                        metadataParsed = true;
                        reader.close();
                        return;
                    }
                }
            }
            reader.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException("Failed to parse metadata", e);
        }
    }

    // ==================================================================================
    // SYNSET ITERATOR
    // ==================================================================================

    private class SynsetIterator implements Iterator<SynsetRecord> {
        private final XMLStreamReader reader;
        private SynsetRecord next;
        private boolean finished = false;

        SynsetIterator(XMLStreamReader reader) {
            this.reader = reader;
            advance();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public SynsetRecord next() {
            SynsetRecord result = next;
            advance();
            return result;
        }

        private void advance() {
            if (finished) {
                next = null;
                return;
            }

            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        if ("Synset".equals(reader.getLocalName())) {
                            next = parseSynset();
                            return;
                        }
                    }
                }
                finished = true;
                next = null;
            } catch (XMLStreamException e) {
                throw new RuntimeException("Failed to parse synset", e);
            }
        }

        private SynsetRecord parseSynset() throws XMLStreamException {
            String id = reader.getAttributeValue(null, "id");
            String ili = reader.getAttributeValue(null, "ili");
            String pos = reader.getAttributeValue(null, "partOfSpeech");
            String members = reader.getAttributeValue(null, "members");

            String definition = null;
            List<String> examples = new ArrayList<>();
            List<RelationRecord> relations = new ArrayList<>();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    switch (name) {
                        case "Definition" -> definition = reader.getElementText();
                        case "Example" -> examples.add(reader.getElementText());
                        case "SynsetRelation" -> {
                            String relType = reader.getAttributeValue(null, "relType");
                            String target = reader.getAttributeValue(null, "target");
                            relations.add(new RelationRecord(relType, target));
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("Synset".equals(reader.getLocalName())) {
                        break;
                    }
                }
            }

            return new SynsetRecord(id, ili, pos, members, definition, examples, relations);
        }
    }

    // ==================================================================================
    // LEXICAL ENTRY ITERATOR
    // ==================================================================================

    private class LexicalEntryIterator implements Iterator<LexicalEntryRecord> {
        private final XMLStreamReader reader;
        private LexicalEntryRecord next;
        private boolean finished = false;

        LexicalEntryIterator(XMLStreamReader reader) {
            this.reader = reader;
            advance();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public LexicalEntryRecord next() {
            LexicalEntryRecord result = next;
            advance();
            return result;
        }

        private void advance() {
            if (finished) {
                next = null;
                return;
            }

            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        if ("LexicalEntry".equals(reader.getLocalName())) {
                            next = parseLexicalEntry();
                            return;
                        }
                    }
                }
                finished = true;
                next = null;
            } catch (XMLStreamException e) {
                throw new RuntimeException("Failed to parse lexical entry", e);
            }
        }

        private LexicalEntryRecord parseLexicalEntry() throws XMLStreamException {
            String id = reader.getAttributeValue(null, "id");
            String lemma = null;
            String pos = null;
            List<SenseRecord> senses = new ArrayList<>();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    switch (name) {
                        case "Lemma" -> {
                            lemma = reader.getAttributeValue(null, "writtenForm");
                            pos = reader.getAttributeValue(null, "partOfSpeech");
                        }
                        case "Sense" -> {
                            senses.add(parseSense());
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("LexicalEntry".equals(reader.getLocalName())) {
                        break;
                    }
                }
            }

            return new LexicalEntryRecord(id, lemma, pos, senses);
        }

        private SenseRecord parseSense() throws XMLStreamException {
            String id = reader.getAttributeValue(null, "id");
            String synsetId = reader.getAttributeValue(null, "synset");
            List<RelationRecord> senseRelations = new ArrayList<>();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("SenseRelation".equals(reader.getLocalName())) {
                        String relType = reader.getAttributeValue(null, "relType");
                        String target = reader.getAttributeValue(null, "target");
                        senseRelations.add(new RelationRecord(relType, target));
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("Sense".equals(reader.getLocalName())) {
                        break;
                    }
                }
            }

            return new SenseRecord(id, synsetId, senseRelations);
        }
    }
}
