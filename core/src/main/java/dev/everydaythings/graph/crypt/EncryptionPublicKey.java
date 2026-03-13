package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Builder;
import lombok.Getter;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Objects;

@Getter
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
public final class EncryptionPublicKey extends GraphPublicKey {

    @Builder
    private static EncryptionPublicKey build(PublicKey jcaPublicKey,
                                             Algorithm.KeyMgmt algorithm, byte[] spki,
                                             ItemID owner, Clock clock) {

        Objects.requireNonNull(jcaPublicKey, "jcaPublicKey");

        String algName = jcaPublicKey.getAlgorithm();
        if (algorithm != null || spki != null)
            throw new IllegalArgumentException("cannot provide both jcaPublicKey and raw key data");

        algorithm = Algorithm.KeyMgmt.byJcaAlgorithmName(algName);
        spki = jcaPublicKey.getEncoded();

        return new EncryptionPublicKey(algorithm, spki, owner, clock);
    }

    @Override
    public Algorithm.KeyMgmt algorithm() {
        return (Algorithm.KeyMgmt) super.algorithm();
    }

    private EncryptionPublicKey(Algorithm.KeyMgmt algorithm, byte[] spki, ItemID owner, Clock clock) {
        super(algorithm, spki, owner, clock);
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private EncryptionPublicKey() {
        super();
    }
}
