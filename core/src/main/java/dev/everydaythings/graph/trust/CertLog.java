package dev.everydaythings.graph.trust;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.Dag;
import dev.everydaythings.graph.item.component.Factory;
import dev.everydaythings.graph.item.component.InspectEntry;
import dev.everydaythings.graph.item.component.Inspectable;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Append-only log for certificates issued by this signer.
 *
 * <p>CertLog is a stream component that tracks certificates a Signer has issued.
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
 */
@Type(value = CertLog.KEY, glyph = "📋")
public class CertLog extends Dag<CertLog.Op> implements Inspectable {

    // === TYPE DEFINITION ===
    public static final String KEY = "cg:type/certlog";

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
     *
     * <p>The certificate should be self-signed by this Signer's key.
     * The subject CN typically contains the Signer's IID for identification.
     */
    public static final class AddTlsCert implements Op {
        public final TlsCert cert;

        public AddTlsCert(TlsCert c) {
            this.cert = c;
        }
    }

    /**
     * Set the current TLS certificate.
     *
     * <p>Only one TLS cert can be "current" at a time. This is the cert
     * that will be presented during TLS handshakes.
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
     *
     * <p>Contains the DER-encoded certificate bytes and metadata.
     * The actual X509Certificate object can be parsed on demand.
     */
    public record TlsCert(
            byte[] keyCid,         // CID of the key this cert is for (from KeyLog)
            byte[] certBytes,      // DER-encoded X.509 certificate
            long notBefore,        // validity start (epoch millis)
            long notAfter          // validity end (epoch millis)
    ) {
        /**
         * Create a TlsCert from an X509Certificate.
         */
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

        /**
         * Parse the X509Certificate from the stored bytes.
         */
        public X509Certificate toX509() {
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse X509Certificate", e);
            }
        }

        /**
         * Check if the certificate is currently valid.
         */
        public boolean isValid() {
            long now = System.currentTimeMillis();
            return now >= notBefore && now <= notAfter;
        }

        /**
         * Encode to CBOR.
         */
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("keyCid", CBORObject.FromByteArray(keyCid));
            obj.set("cert", CBORObject.FromByteArray(certBytes));
            obj.set("notBefore", CBORObject.FromInt64(notBefore));
            obj.set("notAfter", CBORObject.FromInt64(notAfter));
            return obj;
        }

        /**
         * Decode from CBOR.
         */
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
    // Encode/Decode
    // ==================================================================================

    @Override
    protected CBORObject encodeOp(Op op) {
        CBORObject m = CBORObject.NewMap();
        switch (op) {
            case AddCert a -> {
                m.set(num(0), num(1)); // t=1
                m.set(num(1), Canonical.toCborTree(a.cert, Canonical.Scope.BODY));
                m.set(num(2), Canonical.toCborTree(a.cert, Canonical.Scope.RECORD));
            }
            case AddTlsCert a -> {
                m.set(num(0), num(10)); // t=10 for TLS certs
                m.set(num(1), a.cert.toCbor());
            }
            case SetCurrentTls s -> {
                m.set(num(0), num(11)); // t=11
                m.set(num(1), CBORObject.FromByteArray(s.certCid));
                m.set(num(2), s.current ? CBORObject.True : CBORObject.False);
            }
            case RevokeCert r -> {
                m.set(num(0), num(2)); // t=2
                m.set(num(1), CBORObject.FromByteArray(r.targetCid));
                m.set(num(2), num(r.reason));
            }
        }
        return m;
    }

    @Override
    protected Op decodeOp(CBORObject c) {
        int t = c.get(num(0)).AsInt32();
        return switch (t) {
            case 1 -> {
                KeyCert body = Canonical.fromCborTree(c.get(num(1)), KeyCert.class, Canonical.Scope.BODY);
                KeyCert rec = Canonical.fromCborTree(c.get(num(2)), KeyCert.class, Canonical.Scope.RECORD);
                body.alg = rec.alg;
                body.sig = rec.sig;
                yield new AddCert(body);
            }
            case 10 -> new AddTlsCert(TlsCert.fromCbor(c.get(num(1))));
            case 11 -> new SetCurrentTls(c.get(num(1)).GetByteString(), c.get(num(2)).AsBoolean());
            case 2 -> new RevokeCert(c.get(num(1)).GetByteString(), c.get(num(2)).AsInt32());
            default -> throw new IllegalArgumentException("bad t=" + t);
        };
    }

    private static CBORObject num(int i) {
        return CBORObject.FromInt32(i);
    }

    // ==================================================================================
    // Fold (apply operations to state)
    // ==================================================================================

    @Override
    protected void fold(Op op, Event ev) {
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
                // If revoking the current TLS cert, clear it
                if (cid.equals(currentTlsCertCid)) {
                    currentTlsCertCid = null;
                }
            }
        }
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
            if (!heads().isEmpty()) {
                return heads().size() + " event(s) recorded (state not materialized).";
            }
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

    /**
     * Get all KeyCerts in this log.
     */
    public Map<String, KeyCert> certs() {
        return Collections.unmodifiableMap(keyCerts);
    }

    /**
     * Get a KeyCert by its CID.
     */
    public Optional<KeyCert> getCert(String certCidHex) {
        return Optional.ofNullable(keyCerts.get(certCidHex));
    }

    /**
     * Get all non-revoked KeyCerts.
     */
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

    /**
     * Get all TLS certificates in this log.
     */
    public Map<String, TlsCert> tlsCerts() {
        return Collections.unmodifiableMap(tlsCerts);
    }

    /**
     * Get a TLS certificate by its CID.
     */
    public Optional<TlsCert> getTlsCert(String certCidHex) {
        return Optional.ofNullable(tlsCerts.get(certCidHex));
    }

    /**
     * Get the current TLS certificate, if one is set.
     */
    public Optional<TlsCert> currentTlsCert() {
        if (currentTlsCertCid == null) return Optional.empty();
        return Optional.ofNullable(tlsCerts.get(currentTlsCertCid));
    }

    /**
     * Get the current TLS certificate as an X509Certificate.
     */
    public Optional<X509Certificate> currentTlsX509() {
        return currentTlsCert().map(TlsCert::toX509);
    }

    /**
     * Get all non-revoked, valid TLS certificates.
     */
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

    /**
     * Check if a cert has been revoked.
     */
    public boolean isRevoked(byte[] targetCid) {
        return revoked.contains(hex(targetCid));
    }

    /**
     * Check if a cert has been revoked.
     */
    public boolean isRevoked(String targetCidHex) {
        return revoked.contains(targetCidHex);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /**
     * Compute the CID for a TLS certificate.
     */
    public static String tlsCertCid(TlsCert cert) {
        return hex(hash(cert.certBytes()));
    }

    /**
     * Compute the CID for a TLS certificate.
     */
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
