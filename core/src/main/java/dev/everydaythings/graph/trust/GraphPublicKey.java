package dev.everydaythings.graph.trust;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;

import java.security.KeyFactory;
import java.security.Provider;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;

@Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
@Getter
public abstract class GraphPublicKey implements Canonical {

    @Canon(order = 1)
    protected Algorithm.Asymmetric algorithm;   // Sign or KeyMgmt

    @Canon(order = 2)
    protected byte[] spki;                // X.509 SubjectPublicKeyInfo (DER)

    @Canon(order = 3)
    protected ItemID owner;               // owner item

    @Canon(order = 100)
    protected Instant createdAt;          // keep last

    // derived (not canonical)
    private transient volatile byte[] kidCache;

    protected GraphPublicKey(Algorithm.Asymmetric algorithm, byte[] spki, ItemID owner, Clock clock) {
        this.algorithm = algorithm;
        this.spki = spki;
        this.owner = owner;

        if (clock == null) clock = Clock.systemUTC();
        this.createdAt = clock.instant();
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    protected GraphPublicKey() {
        this.algorithm = null;
        this.spki = null;
        this.owner = null;
        this.createdAt = null;
    }

    /** sha256(SPKI) – derived; cached. */
    public final byte[] keyId() {
        byte[] k = kidCache;
        if (k == null) {
            k = Hash.DEFAULT.digest(spki);
            kidCache = k;
        }
        return k;
    }

    /** JCA PublicKey view of this SPKI. Pass a Provider (e.g., BC) when needed. */
    public final PublicKey toPublicKey() {
        return toPublicKey(null);
    }

    public final PublicKey toPublicKey(Provider provider) {
        try {
            KeyFactory kf = (provider == null)
                    ? KeyFactory.getInstance(algorithm.keyFactoryName())
                    : KeyFactory.getInstance(algorithm.keyFactoryName(), provider);
            return kf.generatePublic(new X509EncodedKeySpec(spki));
        } catch (Exception e) {
            throw new RuntimeException("toPublicKey failed for " + algorithm + ": " + e, e);
        }
    }
}
