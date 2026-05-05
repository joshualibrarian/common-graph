package dev.everydaythings.graph.item.user;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignerTest {

    @Test
    @DisplayName("Signer extends Item")
    void extendsItem() {
        Signer s = new Signer(ItemID.random());
        assertThat(s).isInstanceOf(Item.class);
    }

    @Test
    @DisplayName("Signer carries an iid")
    void carriesIid() {
        ItemID iid = ItemID.fromString("test-signer");
        Signer s = new Signer(iid);
        assertThat(s.iid()).isEqualTo(iid);
    }

    @Test
    @DisplayName("Signer KEY is the archetype canonical key")
    void keyMatches() {
        assertThat(Signer.KEY).isEqualTo("cg.archetype:signer");
    }
}
