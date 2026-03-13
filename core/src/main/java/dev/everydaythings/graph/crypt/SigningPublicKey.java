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
public final class SigningPublicKey extends GraphPublicKey {

    @Builder
    private static SigningPublicKey build(PublicKey jcaPublicKey,
                                         Algorithm.Sign algorithm, byte[] spki,
                                         ItemID owner, Clock clock) {

        Objects.requireNonNull(jcaPublicKey, "jcaPublicKey");

        String algName = jcaPublicKey.getAlgorithm();
        if (algorithm != null || spki != null)
            throw new IllegalArgumentException("cannot provide both jcaPublicKey and raw key data");

        algorithm = Algorithm.Sign.byJcaAlgorithmName(algName);
        spki = jcaPublicKey.getEncoded();

        return new SigningPublicKey(algorithm, spki, owner, clock);

    }

    @Override
    public Algorithm.Sign algorithm() {
        return (Algorithm.Sign) super.algorithm();
    }

    private SigningPublicKey(Algorithm.Sign algorithm, byte[] spki, ItemID owner, Clock clock) {
        super(algorithm, spki, owner, clock);
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private SigningPublicKey() {
        super();
    }
}