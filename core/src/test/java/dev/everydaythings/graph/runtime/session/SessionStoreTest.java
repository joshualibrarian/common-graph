package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.cryptography.vault.Vault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SessionStore} — load-or-mint of session vaults.
 */
class SessionStoreTest {

    @TempDir Path workspace;

    private SessionResolver.Resolved location() {
        Path sessionRoot = workspace.resolve("my-session");
        return new SessionResolver.Resolved(sessionRoot, SessionResolver.Kind.EXPLICIT);
    }

    @Nested
    @DisplayName("Mint")
    class Mint {

        @Test
        void mints_creates_root_and_vault() {
            SessionResolver.Resolved loc = location();
            Vault v = SessionStore.mintSessionVault(loc);
            assertThat(v).isNotNull();
            assertThat(v.canSign()).isTrue();
            assertThat(Files.isDirectory(loc.path())).isTrue();
            assertThat(Files.isDirectory(loc.path().resolve(SessionStore.VAULT_DIR_NAME))).isTrue();
            assertThat(SessionStore.hasVault(loc)).isTrue();
        }

        @Test
        void mints_throws_if_vault_already_exists() {
            SessionResolver.Resolved loc = location();
            SessionStore.mintSessionVault(loc);
            assertThatThrownBy(() -> SessionStore.mintSessionVault(loc))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("Load")
    class Load {

        @Test
        void load_round_trips_minted_vault_identity() {
            SessionResolver.Resolved loc = location();
            Vault minted = SessionStore.mintSessionVault(loc);
            Vault loaded = SessionStore.loadSessionVault(loc);
            assertThat(loaded.identity()).isEqualTo(minted.identity());
        }

        @Test
        void load_throws_if_vault_missing() {
            SessionResolver.Resolved loc = location();
            assertThatThrownBy(() -> SessionStore.loadSessionVault(loc))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("loadOrMint")
    class LoadOrMint {

        @Test
        void mints_on_first_call_loads_on_second() {
            SessionResolver.Resolved loc = location();
            Vault first = SessionStore.loadOrMintSessionVault(loc);
            Vault second = SessionStore.loadOrMintSessionVault(loc);
            assertThat(second.identity()).isEqualTo(first.identity());
        }
    }

    @Nested
    @DisplayName("Probe")
    class Probe {

        @Test
        void probe_reports_no_vault_before_mint_and_present_after() {
            SessionResolver.Resolved loc = location();
            assertThat(SessionStore.probe(loc).hasVault()).isFalse();
            SessionStore.mintSessionVault(loc);
            assertThat(SessionStore.probe(loc).hasVault()).isTrue();
        }
    }
}
