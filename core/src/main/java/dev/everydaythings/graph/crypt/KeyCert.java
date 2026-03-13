package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.Canonical;
import lombok.NoArgsConstructor;

/** Certificate BODY lives in BODY; signature lives in RECORD (detached over BODY). */
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
@NoArgsConstructor
public class KeyCert implements Canonical {
    // BODY (immutable statement)
    @Canon(order = 1)
    public byte[] issuerKeyRef;  // e.g., IID\CID of issuer key (opaque bytes)

    @Canon(order = 2)
    public byte[] subjectKeyCid; // content handle of subject key (~iid\cid) as bytes

    @Canon(order = 3)
    public int purposes;         // bitmask: subset of subject.use

    @Canon(order = 4)
    public Long nbf;             // ms

    @Canon(order = 5)
    public Long naf;             // ms

    @Canon(order = 6)
    public String scope;         // e.g., "atDomain:example.org" (keep simple; refine later)

    @Canon(order = 7)
    public String strength;      // "full" | "marginal" | null

    // RECORD (detached sig over BODY)
    @Canon(order = 8, isBody = false)
    public long   alg;           // COSE alg id

    @Canon(order = 9, isBody = false)
    public byte[] sig;           // signature over BODY bytes

}
