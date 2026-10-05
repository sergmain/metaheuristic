/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.dispatcher.vault;

import ai.metaheuristic.ai.dispatcher.data.VaultData;
import ai.metaheuristic.commons.utils.JsonUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Plain unit tests for {@link VaultRevealUtils} - no Spring context. Every check runs against a real
 * {@link VaultService} over the in-memory blob store of {@link VaultServiceTest.FakeVaultTxService},
 * wired the same way the REST controller wires it.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class VaultRevealUtilsTest {

    /** never 1L - reserved for the MH management company */
    private static final long COMPANY_ID = 42L;
    private static final long OTHER_COMPANY_ID = 43L;
    private static final String PASSPHRASE = "master-pass";

    /** A real Vault of {@link #COMPANY_ID}, unlocked with {@link #PASSPHRASE}, holding one entry. */
    private static VaultService unlockedVaultWith(String code, String secret) {
        VaultService service = new VaultService(new VaultServiceTest.FakeVaultTxService());
        assertTrue(service.unlock(COMPANY_ID, PASSPHRASE).opened);
        assertTrue(service.putApiKey(COMPANY_ID, code, secret));
        return service;
    }

    /** The same wiring as {@code VaultRestController.revealSecret}. */
    private static VaultData.SecretResult reveal(VaultService service, @Nullable Long principalCompanyId, long companyId, String code, String passphrase) {
        return VaultRevealUtils.revealSecret(principalCompanyId, companyId, code, passphrase,
                service::isOpened, service::verifyPassphrase, service::getApiKey);
    }

    private static void assertRefused(VaultData.SecretResult r, String expectedCodePrefix) {
        assertNull(r.secret, "no secret may be returned when refused");
        assertNotNull(r.errorMessages);
        assertEquals(1, r.errorMessages.size(), "errors: " + r.errorMessages);
        assertTrue(r.errorMessages.getFirst().startsWith(expectedCodePrefix), "error: " + r.errorMessages.getFirst());
    }

    @Test
    void revealSecret_correctPassphrase_returnsSecret() {
        VaultService service = unlockedVaultWith("openai", "sk-test-value-1");

        VaultData.SecretResult r = reveal(service, COMPANY_ID, COMPANY_ID, "openai", PASSPHRASE);

        assertEquals("sk-test-value-1", r.secret);
        assertTrue(r.errorMessages == null || r.errorMessages.isEmpty(), "errors: " + r.errorMessages);
    }

    @Test
    void revealSecret_512CharSecret_returnedWhole() {
        final String secret = "0123456789abcdef".repeat(32);
        assertEquals(512, secret.length());
        VaultService service = unlockedVaultWith("long-key", secret);

        VaultData.SecretResult r = reveal(service, COMPANY_ID, COMPANY_ID, "long-key", PASSPHRASE);

        assertEquals(secret, r.secret);
    }

    @Test
    void revealSecret_8kCharSecret_returnedWhole() {
        final String secret = "Zz-9_".repeat(1640);
        VaultService service = unlockedVaultWith("pem-key", secret);

        VaultData.SecretResult r = reveal(service, COMPANY_ID, COMPANY_ID, "pem-key", PASSPHRASE);

        assertEquals(secret, r.secret);
    }

    @Test
    void revealSecret_wrongPassphrase_refused() {
        VaultService service = unlockedVaultWith("openai", "sk-test-value-1");

        assertRefused(reveal(service, COMPANY_ID, COMPANY_ID, "openai", "wrong-pass"), "01.673.030");
    }

    @Test
    void revealSecret_emptyPassphrase_refused() {
        VaultService service = unlockedVaultWith("openai", "sk-test-value-1");

        assertRefused(reveal(service, COMPANY_ID, COMPANY_ID, "openai", ""), "01.673.030");
    }

    @Test
    void revealSecret_nullPassphrase_refused() {
        VaultService service = unlockedVaultWith("openai", "sk-test-value-1");

        assertRefused(reveal(service, COMPANY_ID, COMPANY_ID, "openai", null), "01.673.030");
    }

    @Test
    void revealSecret_entryOfAnotherCompany_refusedEvenWithItsPassphrase() {
        VaultService service = unlockedVaultWith("openai", "sk-test-value-1");
        assertTrue(service.unlock(OTHER_COMPANY_ID, PASSPHRASE).opened);

        // a caller of the other company, holding the very passphrase that opens COMPANY_ID's Vault
        assertRefused(reveal(service, OTHER_COMPANY_ID, COMPANY_ID, "openai", PASSPHRASE), "01.673.010");
    }

    @Test
    void revealSecret_principalWithoutCompany_refused() {
        VaultService service = unlockedVaultWith("openai", "sk-test-value-1");

        assertRefused(reveal(service, null, COMPANY_ID, "openai", PASSPHRASE), "01.673.010");
    }

    @Test
    void revealSecret_lockedVault_refused() {
        VaultServiceTest.FakeVaultTxService store = new VaultServiceTest.FakeVaultTxService();
        VaultService before = new VaultService(store);
        assertTrue(before.unlock(COMPANY_ID, PASSPHRASE).opened);
        assertTrue(before.putApiKey(COMPANY_ID, "openai", "sk-test-value-1"));
        // Dispatcher restart: the entry is persisted, the Vault is locked
        VaultService afterRestart = new VaultService(store);

        assertRefused(reveal(afterRestart, COMPANY_ID, COMPANY_ID, "openai", PASSPHRASE), "01.673.020");
    }

    @Test
    void revealSecret_unknownCode_refused() {
        VaultService service = unlockedVaultWith("openai", "sk-test-value-1");

        assertRefused(reveal(service, COMPANY_ID, COMPANY_ID, "anthropic", PASSPHRASE), "01.673.040");
    }

    @Test
    void revealSecret_deletedEntry_refused() {
        VaultService service = unlockedVaultWith("openai", "sk-test-value-1");
        assertTrue(service.deleteApiKey(COMPANY_ID, "openai"));

        assertRefused(reveal(service, COMPANY_ID, COMPANY_ID, "openai", PASSPHRASE), "01.673.040");
    }

    @Test
    void revealSecret_overwrittenEntry_returnsNewSecret() {
        VaultService service = unlockedVaultWith("openai", "old-value");
        assertTrue(service.putApiKey(COMPANY_ID, "openai", "new-value"));

        assertEquals("new-value", reveal(service, COMPANY_ID, COMPANY_ID, "openai", PASSPHRASE).secret);
    }

    @Test
    void secretResult_toString_neverContainsSecret() {
        VaultService service = unlockedVaultWith("openai", "sk-never-in-a-log-1");

        VaultData.SecretResult r = reveal(service, COMPANY_ID, COMPANY_ID, "openai", PASSPHRASE);

        assertEquals("sk-never-in-a-log-1", r.secret);
        assertFalse(r.toString().contains("sk-never-in-a-log-1"), "toString: " + r);
    }

    @Test
    void secretResult_wireJson_carriesSecret() {
        VaultService service = unlockedVaultWith("openai", "sk-on-the-wire-1");

        final String json = JsonUtils.getMapper().writeValueAsString(reveal(service, COMPANY_ID, COMPANY_ID, "openai", PASSPHRASE));

        assertTrue(json.contains("\"secret\":\"sk-on-the-wire-1\""), "json: " + json);
    }

    @Test
    void secretResult_wireJson_refusalCarriesNoSecret() {
        VaultService service = unlockedVaultWith("openai", "sk-on-the-wire-2");

        final String json = JsonUtils.getMapper().writeValueAsString(reveal(service, COMPANY_ID, COMPANY_ID, "openai", "wrong-pass"));

        assertFalse(json.contains("sk-on-the-wire-2"), "json: " + json);
        assertTrue(json.contains("01.673.030"), "json: " + json);
    }
}
