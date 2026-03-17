package dev.everydaythings.graph.crypt;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.InspectEntry;
import dev.everydaythings.graph.frame.Inspectable;
import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Append-only log for certificates issued by this signer.
 *
 * <p>CertLog tracks certificates a Signer has issued.
 * It supports two types of certificates:
 * <ul>
 *   <li><b>KeyCert</b> - CG-native certificates for trust attestations</li>
 *   <li><b>TlsCert</b> - X.509 certificates for TLS authentication</li>
 * </ul>
 *
 * <p>TLS certificates are self-signed X.509 certs generated from the Signer's
 * keypair. They're used to establish identity during the TLS handshake, and
 * peers can verify them by looking up the Signer's CertLog.
 *
 * <p>Unlike private keys (stored in Vault), CertLog content is syncable
 * and represents the public record of certificates.
 *
 * <p>Operations are applied in-memory via {@link #apply(Op)}. When a
 * Library is available, operations can be persisted as frames via FrameChain.
 */
@Implements(CertLog.TypeSeed.KEY)
@Type(glyph = "📜")
public class CertLog implements Canonical, Inspectable {

    // === TYPE DEFINITION ===
    public static final String KEY = TypeSeed.KEY;

    public static class TypeSeed {
        public static final String KEY = "cg.sememe:certlog";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "append-only certificate history")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "certlog");
    }

    /**
     * Create a new empty CertLog.
     */
    @Factory(label = "Empty", glyph = "📋", primary = true,
            doc = "New empty certificate log")
    public static CertLog create() {
        return new CertLog();
    }

    /* materialized state (transient) */
    private final Map<String, KeyCert> keyCerts = new LinkedHashMap<>();      // cid -> KeyCert
    private final Map<String, TlsCert> tlsCerts = new LinkedHashMap<>();      // cid -> TlsCert
    private final Set<String> revoked = new HashSet<>();                       // cid of revoked certs
    private String currentTlsCertCid = null;                                   // cid of current TLS cert

    // ==================================================================================
    // Operations
    // ==================================================================================

    public sealed interface Op permits AddCert, AddTlsCert, SetCurrentTls, RevokeCert {}

    /**
     * Add a CG-native KeyCert to the log.
     */
    public static final class AddCert implements Op {
        public final KeyCert cert;

        public AddCert(KeyCert c) {
            this.cert = c;
        }
    }

    /**
     * Add an X.509 TLS certificate to the log.
     */
    public static final class AddTlsCert implements Op {
        public final TlsCert cert;

        public AddTlsCert(TlsCert c) {
            this.cert = c;
        }
    }

    /**
     * Set the current TLS certificate.
     */
    public static final class SetCurrentTls implements Op {
        public final byte[] certCid;
        public final boolean current;

        public SetCurrentTls(byte[] certCid, boolean current) {
            this.certCid = certCid;
            this.current = current;
        }
    }

    /**
     * Revoke a certificate (KeyCert or TlsCert).
     *
     * <p>Reason codes:
     * <ul>
     *   <li>0 - Unspecified</li>
     *   <li>1 - Key/cert compromised</li>
     *   <li>2 - Superseded (replaced by newer cert)</li>
     *   <li>3 - Affiliation changed</li>
     *   <li>4 - Cessation of operation</li>
     * </ul>
     */
    public static final class RevokeCert implements Op {
        public final byte[] targetCid;
        public final int reason;

        public RevokeCert(byte[] targetCid, int reason) {
            this.targetCid = targetCid;
            this.reason = reason;
        }
    }

    // ==================================================================================
    // TLS Certificate Record
    // ==================================================================================

    /**
     * An X.509 TLS certificate.
     */
    public record TlsCert(
            byte[] keyCid,         // CID of the key this cert is for (from KeyLog)
            byte[] certBytes,      // DER-encoded X.509 certificate
            long notBefore,        // validity start (epoch millis)
            long notAfter          // validity end (epoch millis)
    ) {
        public static TlsCert fromX509(byte[] keyCid, X509Certificate x509) {
            try {
                return new TlsCert(
                        keyCid,
                        x509.getEncoded(),
                        x509.getNotBefore().getTime(),
                        x509.getNotAfter().getTime()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode X509Certificate", e);
            }
        }

        public X509Certificate toX509() {
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse X509Certificate", e);
            }
        }

        public boolean isValid() {
            long now = System.currentTimeMillis();
            return now >= notBefore && now <= notAfter;
        }

        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("keyCid", CBORObject.FromByteArray(keyCid));
            obj.set("cert", CBORObject.FromByteArray(certBytes));
            obj.set("notBefore", CBORObject.FromInt64(notBefore));
            obj.set("notAfter", CBORObject.FromInt64(notAfter));
            return obj;
        }

        public static TlsCert fromCbor(CBORObject obj) {
            return new TlsCert(
                    obj.get("keyCid").GetByteString(),
                    obj.get("cert").GetByteString(),
                    obj.get("notBefore").AsInt64Value(),
                    obj.get("notAfter").AsInt64Value()
            );
        }
    }

    // ==================================================================================
    // Apply (fold operations into state)
    // ==================================================================================

    /**
     * Apply an operation to this CertLog, updating materialized state.
     *
     * @param op the operation to apply
     */
    public void apply(Op op) {
        switch (op) {
            case AddCert a -> {
                byte[] bodyBytes = a.cert.encodeBinary(Scope.BODY);
                String cid = hex(hash(bodyBytes));
                keyCerts.put(cid, a.cert);
            }
            case AddTlsCert a -> {
                String cid = hex(hash(a.cert.certBytes()));
                tlsCerts.put(cid, a.cert);
            }
            case SetCurrentTls s -> {
                String cid = hex(s.certCid);
                if (s.current && tlsCerts.containsKey(cid) && !revoked.contains(cid)) {
                    currentTlsCertCid = cid;
                } else if (!s.current && cid.equals(currentTlsCertCid)) {
                    currentTlsCertCid = null;
                }
            }
            case RevokeCert r -> {
                String cid = hex(r.targetCid);
                revoked.add(cid);
                if (cid.equals(currentTlsCertCid)) {
                    currentTlsCertCid = null;
                }
            }
        }
    }

    /**
     * Check if this CertLog is empty (no operations applied).
     */
    public boolean isEmpty() {
        return keyCerts.isEmpty() && tlsCerts.isEmpty();
    }

    public boolean isExpandable() {
        return !isEmpty();
    }

    // ==================================================================================
    // Inspect Entries
    // ==================================================================================

    @Override
    public java.util.List<InspectEntry> inspectEntries() {
        java.util.List<InspectEntry> entries = new ArrayList<>();
        for (var entry : keyCerts.entrySet()) {
            String shortId = entry.getKey().length() > 8
                    ? entry.getKey().substring(0, 8) + "\u2026" : entry.getKey();
            boolean isRevoked = revoked.contains(entry.getKey());
            entries.add(new InspectEntry(
                    entry.getKey(),
                    shortId + (isRevoked ? " (revoked)" : ""),
                    isRevoked ? "\uD83D\uDEAB" : "\uD83D\uDCCB",
                    entry.getValue()));
        }
        for (var entry : tlsCerts.entrySet()) {
            String shortId = entry.getKey().length() > 8
                    ? entry.getKey().substring(0, 8) + "\u2026" : entry.getKey();
            entries.add(new InspectEntry(
                    entry.getKey(),
                    "TLS " + shortId,
                    "\uD83D\uDD12",
                    entry.getValue()));
        }
        return entries;
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    public String displayToken() {
        int total = keyCerts.size() + tlsCerts.size();
        int rev = revoked.size();
        if (total == 0) return "certs (empty)";
        return "certs (" + total + (rev > 0 ? ", " + rev + " revoked" : "") + ")";
    }

    @Override
    public String toString() {
        if (keyCerts.isEmpty() && tlsCerts.isEmpty()) {
            return "No certificates issued.";
        }
        StringBuilder sb = new StringBuilder();
        if (!keyCerts.isEmpty()) {
            sb.append(keyCerts.size()).append(keyCerts.size() == 1 ? " key cert" : " key certs");
            long activeCount = keyCerts.keySet().stream().filter(c -> !revoked.contains(c)).count();
            if (activeCount < keyCerts.size()) {
                sb.append(" (").append(activeCount).append(" active)");
            }
            sb.append("\n");
            for (var entry : keyCerts.entrySet()) {
                String shortId = entry.getKey().length() > 12
                        ? entry.getKey().substring(0, 12) + "\u2026" : entry.getKey();
                boolean isRevoked = revoked.contains(entry.getKey());
                sb.append(isRevoked ? "  \uD83D\uDEAB " : "  \uD83D\uDCCB ");
                sb.append(shortId);
                if (isRevoked) sb.append("  (revoked)");
                sb.append("\n");
            }
        }
        if (!tlsCerts.isEmpty()) {
            if (!keyCerts.isEmpty()) sb.append("\n");
            sb.append(tlsCerts.size()).append(tlsCerts.size() == 1 ? " TLS cert" : " TLS certs");
            if (currentTlsCertCid != null) sb.append(" (1 current)");
            sb.append("\n");
            for (var entry : tlsCerts.entrySet()) {
                String shortId = entry.getKey().length() > 12
                        ? entry.getKey().substring(0, 12) + "\u2026" : entry.getKey();
                boolean isCurrent = entry.getKey().equals(currentTlsCertCid);
                boolean isRevoked = revoked.contains(entry.getKey());
                sb.append(isRevoked ? "  \uD83D\uDEAB " : isCurrent ? "  \uD83D\uDD12 " : "  \uD83D\uDD13 ");
                sb.append(shortId);
                if (isCurrent) sb.append("  (current)");
                if (isRevoked) sb.append("  (revoked)");
                sb.append("\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    // ==================================================================================
    // Queries - KeyCerts
    // ==================================================================================

    public Map<String, KeyCert> certs() {
        return Collections.unmodifiableMap(keyCerts);
    }

    public Optional<KeyCert> getCert(String certCidHex) {
        return Optional.ofNullable(keyCerts.get(certCidHex));
    }

    public Map<String, KeyCert> activeCerts() {
        Map<String, KeyCert> active = new LinkedHashMap<>();
        for (var entry : keyCerts.entrySet()) {
            if (!revoked.contains(entry.getKey())) {
                active.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(active);
    }

    // ==================================================================================
    // Queries - TLS Certs
    // ==================================================================================

    public Map<String, TlsCert> tlsCerts() {
        return Collections.unmodifiableMap(tlsCerts);
    }

    public Optional<TlsCert> getTlsCert(String certCidHex) {
        return Optional.ofNullable(tlsCerts.get(certCidHex));
    }

    public Optional<TlsCert> currentTlsCert() {
        if (currentTlsCertCid == null) return Optional.empty();
        return Optional.ofNullable(tlsCerts.get(currentTlsCertCid));
    }

    public Optional<X509Certificate> currentTlsX509() {
        return currentTlsCert().map(TlsCert::toX509);
    }

    public Map<String, TlsCert> activeTlsCerts() {
        Map<String, TlsCert> active = new LinkedHashMap<>();
        for (var entry : tlsCerts.entrySet()) {
            if (!revoked.contains(entry.getKey()) && entry.getValue().isValid()) {
                active.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(active);
    }

    // ==================================================================================
    // Queries - Revocation
    // ==================================================================================

    public boolean isRevoked(byte[] targetCid) {
        return revoked.contains(hex(targetCid));
    }

    public boolean isRevoked(String targetCidHex) {
        return revoked.contains(targetCidHex);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    public static String tlsCertCid(TlsCert cert) {
        return hex(hash(cert.certBytes()));
    }

    public static byte[] tlsCertCidBytes(TlsCert cert) {
        return hash(cert.certBytes());
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] hash(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
