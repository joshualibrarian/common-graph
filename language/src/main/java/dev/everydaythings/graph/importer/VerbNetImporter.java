package dev.everydaythings.graph.importer;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.user.SignerOld;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.LibrarianOld;
import lombok.extern.log4j.Log4j2;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * Imports VerbNet verb class data, adding EXPECTS (thematic role declarations)
 * to existing sememes.
 *
 * <p>VerbNet declares which thematic roles each verb class expects (Agent, Theme,
 * Recipient, etc.). These map directly to Common Graph's EXPECTS frames. Since
 * EXPECTS are on sememes (language-neutral), this import runs ONCE and benefits
 * ALL languages.
 *
 * <p>Must run AFTER WordNet import (sememes must exist and have WN_SENSE frames
 * for cross-referencing).
 *
 * <p>For each verb class:
 * <ol>
 *   <li>Parse THEMROLES → list of thematic role names</li>
 *   <li>For each MEMBER, resolve WordNet sense keys → find existing sememes</li>
 *   <li>Add EXPECTS frames to each sememe for the declared roles</li>
 *   <li>Store VerbNet class ID as a source frame for cross-referencing</li>
 * </ol>
 */
@Log4j2
public class VerbNetImporter {

    private final LibrarianOld librarian;
    private final String resourceDir;

    /** Maps VerbNet role names to ThematicRole IIDs. */
    private static final Map<String, ItemID> ROLE_MAP = buildRoleMap();

    public VerbNetImporter(LibrarianOld librarian, String resourceDir) {
        this.librarian = librarian;
        this.resourceDir = resourceDir;
    }

    /**
     * Import all VerbNet classes from resource directory.
     *
     * @param signer the asserting identity
     * @return import statistics
     */
    public ImportStats importAll(SignerOld signer) {
        long startTime = System.currentTimeMillis();
        int classCount = 0;
        int memberCount = 0;
        int expectsCount = 0;
        int unmappedMembers = 0;
        Set<String> unmappedRoles = new LinkedHashSet<>();

        // Find all VerbNet XML files in the resource directory
        List<URL> xmlFiles = findVerbNetFiles();
        logger.info("VerbNet import: found {} class files", xmlFiles.size());

        for (URL xmlFile : xmlFiles) {
            try {
                VerbClass vc = parseVerbClass(xmlFile);
                if (vc == null) continue;
                classCount++;

                // Process this class and all its subclasses
                int[] counts = processClass(vc, List.of(), signer, unmappedRoles);
                memberCount += counts[0];
                expectsCount += counts[1];
                unmappedMembers += counts[2];
            } catch (Exception e) {
                logger.warn("Failed to parse VerbNet file {}: {}", xmlFile, e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("VerbNet import complete: {} classes, {} members linked, {} EXPECTS created, " +
                        "{} unmapped members, {} unmapped roles in {}ms",
                classCount, memberCount, expectsCount, unmappedMembers, unmappedRoles.size(), duration);
        if (!unmappedRoles.isEmpty()) {
            logger.info("Unmapped VerbNet roles: {}", unmappedRoles);
        }

        return new ImportStats(classCount, memberCount, expectsCount, duration);
    }

    /**
     * Process a verb class: add EXPECTS to all members, then recurse into subclasses.
     *
     * @param vc             the verb class
     * @param inheritedRoles roles inherited from parent class
     * @param signer         asserting identity
     * @param unmappedRoles  collector for role names we can't map
     * @return [memberCount, expectsCount, unmappedMembers]
     */
    int[] processClass(VerbClass vc, List<String> inheritedRoles,
                       SignerOld signer, Set<String> unmappedRoles) {
        int memberCount = 0;
        int expectsCount = 0;
        int unmappedMembers = 0;

        // Combine inherited roles with this class's own roles
        List<String> allRoles = new ArrayList<>(inheritedRoles);
        for (String role : vc.roles) {
            if (!allRoles.contains(role)) {
                allRoles.add(role);
            }
        }

        // Map role names to ThematicRole IIDs
        List<ItemID> roleIids = new ArrayList<>();
        for (String roleName : allRoles) {
            ItemID roleIid = ROLE_MAP.get(roleName);
            if (roleIid != null) {
                roleIids.add(roleIid);
            } else {
                unmappedRoles.add(roleName);
            }
        }

        // For each member, find the sememe and add EXPECTS
        for (Member member : vc.members) {
            List<Sememe> sememes = resolveMember(member);
            if (sememes.isEmpty()) {
                unmappedMembers++;
                continue;
            }

            for (Sememe sememe : sememes) {
                // Add EXPECTS frames for each role
                int added = addExpectsFrames(sememe, roleIids, signer);
                expectsCount += added;

                // Store VerbNet class ID as source frame
                storeSourceIdFrame(sememe, CoreVocabulary.VerbNetClass.IID, vc.id, signer);
                memberCount++;
            }
        }

        // Recurse into subclasses
        for (VerbClass sub : vc.subclasses) {
            int[] subCounts = processClass(sub, allRoles, signer, unmappedRoles);
            memberCount += subCounts[0];
            expectsCount += subCounts[1];
            unmappedMembers += subCounts[2];
        }

        return new int[]{memberCount, expectsCount, unmappedMembers};
    }

    /**
     * Resolve a VerbNet member to sememes via WordNet sense keys.
     */
    private List<Sememe> resolveMember(Member member) {
        if (member.wnSenseKeys == null || member.wnSenseKeys.isEmpty()) {
            return List.of();
        }

        List<Sememe> result = new ArrayList<>();
        for (String senseKey : member.wnSenseKeys) {
            // Look up directly by WN-format sense key (stored during WordNet import)
            Sememe sememe = findSememeBySenseKey(senseKey);
            if (sememe != null && !result.contains(sememe)) {
                result.add(sememe);
            }
        }
        return result;
    }

    /**
     * Convert a VerbNet/WordNet sense key to OEWN sense ID format.
     *
     * <p>VerbNet: {@code give%2:40:00} → OEWN: {@code oewn-give__2.40.00..}
     */
    static String convertToOewnSenseId(String wnSenseKey) {
        if (wnSenseKey == null || wnSenseKey.isBlank()) return null;

        // Format: lemma%ss_type:lex_filenum:lex_id[:head_word:head_id]
        // OEWN format: oewn-lemma__ss_type.lex_filenum.lex_id[.head_word.head_id]
        // Trailing empty fields become ".." in OEWN
        int pct = wnSenseKey.indexOf('%');
        if (pct < 0) return null;

        String lemma = wnSenseKey.substring(0, pct);
        String rest = wnSenseKey.substring(pct + 1);

        // Split on colons, pad to 5 fields
        String[] parts = rest.split(":", -1);
        StringBuilder sb = new StringBuilder("oewn-");
        sb.append(lemma).append("__");
        for (int i = 0; i < 5; i++) {
            if (i > 0) sb.append('.');
            if (i < parts.length) sb.append(parts[i]);
        }

        return sb.toString();
    }

    /**
     * Find a sememe that has a WN_SENSE frame with the given sense ID.
     *
     * <p>Uses the TokenDictionary — sense keys are indexed as VALUE bindings
     * on WN_SENSE frames. The posting's target (homeId) is the sememe IID.
     */
    private Sememe findSememeBySenseKey(String senseId) {
        if (librarian == null || senseId == null) return null;

        // Look up via TokenDictionary — sense keys are indexed there
        var tokenDict = librarian.tokenIndex();
        if (tokenDict == null) return null;

        var bodyResolver = (java.util.function.Function<dev.everydaythings.graph.item.id.ContentID,
                Optional<FrameBodyOld>>)
                (hash -> librarian.library().loadFrameBody(hash));

        var postings = tokenDict.lookup(senseId, bodyResolver).toList();
        for (var posting : postings) {
            // The posting's target is homeId of the WN_SENSE frame = sememe IID
            ItemID sememeId = posting.target();
            if (sememeId != null) {
                var sememe = librarian.get(sememeId, Sememe.class);
                if (sememe.isPresent()) return sememe.get();
            }
        }
        return null;
    }

    /**
     * Add EXPECTS frames to a sememe for the given thematic roles.
     * Skips roles the sememe already expects (from seed annotations or earlier import).
     */
    private int addExpectsFrames(Sememe sememe, List<ItemID> roleIids, SignerOld signer) {
        if (sememe.frames() == null) return 0;

        // Check which roles are already declared
        List<ItemID> existingRoles = sememe.slotRoles();
        int added = 0;

        for (ItemID roleIid : roleIids) {
            if (existingRoles.contains(roleIid)) continue;

            List<CompoundKey.FrameToken> qualifiers = List.of(
                    new CompoundKey.Sememe(ItemID.fromString(ThematicRole.KEY)),
                    new CompoundKey.Sememe(roleIid));
            Binding topicBinding = Binding.qualified(
                    ThematicRole.Topic.IID, qualifiers,
                    BindingTarget.iid(roleIid));
            FrameBodyOld body = new FrameBodyOld(CoreVocabulary.Expects.IID,
                    List.of(topicBinding));

            librarian.storeFrame(body);
            added++;
        }

        return added;
    }

    /**
     * Store a source ID frame on a sememe.
     */
    private void storeSourceIdFrame(Sememe sememe, ItemID predicate, String sourceId, SignerOld signer) {
        if (sourceId == null || sourceId.isBlank()) return;

        FrameBodyOld body = FrameBodyOld.builder(predicate)
                .bind(ThematicRole.Theme.IID, sememe.iid())
                .bind(ThematicRole.Value.IID, sourceId)
                .build();

        librarian.storeFrame(body);
    }

    // ==================================================================================
    // XML Parsing
    // ==================================================================================

    private List<URL> findVerbNetFiles() {
        List<URL> files = new ArrayList<>();
        try {
            // Scan the resource directory for XML files
            var loader = getClass().getClassLoader();
            var resources = loader.getResources(resourceDir);
            while (resources.hasMoreElements()) {
                var dirUrl = resources.nextElement();
                if ("file".equals(dirUrl.getProtocol())) {
                    var dir = new java.io.File(dirUrl.toURI());
                    if (dir.isDirectory()) {
                        for (java.io.File f : Objects.requireNonNull(dir.listFiles((d, name) -> name.endsWith(".xml")))) {
                            files.add(f.toURI().toURL());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scan VerbNet directory '{}': {}", resourceDir, e.getMessage());
        }
        Collections.sort(files, Comparator.comparing(URL::toString));
        return files;
    }

    /**
     * Parse a single VerbNet XML file into a VerbClass.
     */
    VerbClass parseVerbClass(URL xmlFile) throws IOException, XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);

        try (InputStream is = xmlFile.openStream()) {
            XMLStreamReader reader = factory.createXMLStreamReader(is);
            VerbClass result = null;

            while (reader.hasNext()) {
                reader.next();
                if (reader.isStartElement() && "VNCLASS".equals(reader.getLocalName())) {
                    result = parseClassElement(reader, "VNCLASS");
                }
            }

            reader.close();
            return result;
        }
    }

    private VerbClass parseClassElement(XMLStreamReader reader, String endTag) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "ID");
        List<Member> members = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        List<VerbClass> subclasses = new ArrayList<>();

        while (reader.hasNext()) {
            reader.next();

            if (reader.isStartElement()) {
                switch (reader.getLocalName()) {
                    case "MEMBER" -> {
                        String name = reader.getAttributeValue(null, "name");
                        String wn = reader.getAttributeValue(null, "wn");
                        List<String> senseKeys = wn != null && !wn.isBlank()
                                ? Arrays.asList(wn.trim().split("\\s+"))
                                : List.of();
                        members.add(new Member(name, senseKeys));
                    }
                    case "THEMROLE" -> {
                        String type = reader.getAttributeValue(null, "type");
                        if (type != null && !type.isBlank()) {
                            roles.add(type);
                        }
                    }
                    case "VNSUBCLASS" -> {
                        subclasses.add(parseClassElement(reader, "VNSUBCLASS"));
                    }
                }
            }

            if (reader.isEndElement() && endTag.equals(reader.getLocalName())) {
                break;
            }
        }

        return new VerbClass(id, members, roles, subclasses);
    }

    // ==================================================================================
    // Data Records
    // ==================================================================================

    record VerbClass(String id, List<Member> members, List<String> roles, List<VerbClass> subclasses) {}
    record Member(String name, List<String> wnSenseKeys) {}
    public record ImportStats(int classCount, int memberCount, int expectsCount, long durationMs) {}

    // ==================================================================================
    // Role Mapping
    // ==================================================================================

    /**
     * Build the mapping from VerbNet role names to ThematicRole IIDs.
     */
    private static Map<String, ItemID> buildRoleMap() {
        Map<String, ItemID> map = new LinkedHashMap<>();

        // Direct matches
        map.put("Agent", ThematicRole.Agent.IID);
        map.put("Patient", ThematicRole.Patient.IID);
        map.put("Theme", ThematicRole.Theme.IID);
        map.put("Experiencer", ThematicRole.Experiencer.IID);
        map.put("Stimulus", ThematicRole.Stimulus.IID);
        map.put("Pivot", ThematicRole.Pivot.IID);
        map.put("Goal", ThematicRole.Goal.IID);
        map.put("Destination", ThematicRole.Destination.IID);
        map.put("Source", ThematicRole.Source.IID);
        map.put("Path", ThematicRole.Path.IID);
        map.put("Result", ThematicRole.Result.IID);
        map.put("Recipient", ThematicRole.Recipient.IID);
        map.put("Beneficiary", ThematicRole.Beneficiary.IID);
        map.put("Instrument", ThematicRole.Instrument.IID);
        map.put("Manner", ThematicRole.Manner.IID);
        map.put("Extent", ThematicRole.Extent.IID);
        map.put("Attribute", ThematicRole.Attribute.IID);
        map.put("Purpose", ThematicRole.Purpose.IID);
        map.put("Location", ThematicRole.Location.IID);
        map.put("Time", ThematicRole.Time.IID);
        map.put("Topic", ThematicRole.Topic.IID);
        map.put("Value", ThematicRole.Value.IID);

        // VerbNet names that map to our roles with different names
        map.put("Causer", ThematicRole.Cause.IID);
        map.put("Co-Agent", ThematicRole.Partner.IID);
        map.put("Trajectory", ThematicRole.Path.IID);
        map.put("Product", ThematicRole.Result.IID);
        map.put("Initial_Location", ThematicRole.Source.IID);

        // Roles we don't have yet — these will show up as unmapped
        // Asset, Co-Patient, Co-Theme, Duration, Eventuality, Initial_State,
        // Material, Maleficiary, Reflexive, Subeventuality, Affector,
        // Axis, Circumstance, Precondition

        return Collections.unmodifiableMap(map);
    }
}
