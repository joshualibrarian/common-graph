package dev.everydaythings.graph.policy;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Authority policy - defines who can modify an item.
 *
 * <p>Ownership and maintainer relationships.
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Canonical.Canonization
public class AuthorityPolicy implements Canonical {

    public enum Operation {
        PROMOTE_CHANNEL,
        TRANSFER_OWNERSHIP,
        SET_MAINTAINERS
    }

    @Canon(order = 0)
    private String version;

    @Canon(order = 1)
    private ItemID ownerId;

    @Canon(order = 2)
    @Builder.Default
    private List<ItemID> maintainers = new ArrayList<>();
}
