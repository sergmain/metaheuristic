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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.company.CompanyTopLevelService;
import ai.metaheuristic.ai.dispatcher.data.VaultData;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Boot-unlock of a company's Key Vault against the real Spring context and the shared H2 (UT harness V3),
 * plus the role gate of the Vault REST endpoints.
 *
 * <p>❗ Every Vault here belongs to a FRESH company, never to the management company: the management
 * company's key has priority over every other company's, so a management Vault left in the shared DB
 * would change the sealed-secret answer of every later test in the batch. The service takes the company
 * as a parameter; production passes the management company.
 *
 * <p>⚠️ {@link VaultService#resetForTests()} drops the in-memory unlocked state of ALL companies in the
 * shared context. It is the only way to make a persisted Vault locked again without a restart.
 *
 * @author Sergio Lissner
 * Date: 10/2/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@Import({SpringSecurityWebAuxTestConfig.class})
@AutoConfigureCache
public class VaultBootUnlockServiceTest extends MhSharedItTest {

    private static final String PASSPHRASE = "boot unlock master passphrase";
    private static final String KEY_CODE = "BOOT_UNLOCK_TEST_KEY";
    private static final String SECRET = "sk-boot-unlock-3333";
    private static final String KEK_ENV = "TEST_MH_VAULT_KEK";

    @Autowired private VaultBootUnlockService vaultBootUnlockService;
    @Autowired private VaultService vaultService;
    @Autowired private CompanyTopLevelService companyTopLevelService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private long newCompany() {
        companyTopLevelService.addCompany(SharedItEnv.uniqueCode("vault-boot-unlock"));
        return Objects.requireNonNull(companyRepository.getMaxUniqueIdValue());
    }

    private static String newKek() {
        byte[] b = new byte[VaultBootUnlockUtils.KEK_LEN];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    /** A Vault persisted in Company.params and then locked, as after a Dispatcher restart. */
    private long companyWithLockedVault() {
        final long companyId = newCompany();
        assertTrue(vaultService.unlock(companyId, PASSPHRASE).opened);
        assertTrue(vaultService.putApiKey(companyId, KEY_CODE, SECRET), "the first entry persists the Vault");
        vaultService.resetForTests();
        assertFalse(vaultService.isOpened(companyId));
        return companyId;
    }

    private static String encrypted(String kek, String passphrase, long companyId) throws Exception {
        return VaultBootUnlockUtils.encryptPassphrase(kek, passphrase, companyId);
    }

    @Test
    public void test_bootUnlock_opensThePersistedVault() throws Exception {
        final long companyId = companyWithLockedVault();
        final String kek = newKek();

        final VaultBootUnlockUtils.Status status = vaultBootUnlockService.bootUnlock(
            companyId, encrypted(kek, PASSPHRASE, companyId), KEK_ENV, Map.of(KEK_ENV, kek)::get);

        assertEquals(VaultBootUnlockUtils.Status.UNLOCKED, status);
        assertTrue(vaultService.isOpened(companyId));
        assertEquals(SECRET, vaultService.getApiKey(companyId, KEY_CODE).orElseThrow(),
            "the entry persisted before the 'restart' must be readable after boot-unlock");
    }

    /** The startup path: the value written to {@code ${mh.home}/vault-boot-unlock.txt} is read back and opens the Vault. */
    @Test
    public void test_bootUnlock_opensThePersistedVault_fromTheFileInMhHome(@TempDir Path mhHome) throws Exception {
        final long companyId = companyWithLockedVault();
        final String kek = newKek();
        VaultBootUnlockFileUtils.writeEncryptedPassphrase(mhHome, encrypted(kek, PASSPHRASE, companyId));

        final VaultBootUnlockUtils.Status status = vaultBootUnlockService.bootUnlock(
            companyId, VaultBootUnlockFileUtils.readEncryptedPassphrase(mhHome, null), KEK_ENV, Map.of(KEK_ENV, kek)::get);

        assertEquals(VaultBootUnlockUtils.Status.UNLOCKED, status);
        assertEquals(SECRET, vaultService.getApiKey(companyId, KEY_CODE).orElseThrow());
    }

    @Test
    public void test_bootUnlock_neverCreatesAVault() throws Exception {
        final long companyId = newCompany();
        final String kek = newKek();

        final VaultBootUnlockUtils.Status status = vaultBootUnlockService.bootUnlock(
            companyId, encrypted(kek, PASSPHRASE, companyId), KEK_ENV, Map.of(KEK_ENV, kek)::get);

        assertEquals(VaultBootUnlockUtils.Status.NO_VAULT, status);
        assertFalse(vaultService.isOpened(companyId));
        assertFalse(vaultService.hasVault(companyId), "boot-unlock must not create a Vault with the machine-held passphrase");
    }

    @Test
    public void test_bootUnlock_wrongPassphraseLeavesTheVaultLocked() throws Exception {
        final long companyId = companyWithLockedVault();
        final String kek = newKek();

        final VaultBootUnlockUtils.Status status = vaultBootUnlockService.bootUnlock(
            companyId, encrypted(kek, "not the vault passphrase", companyId), KEK_ENV, Map.of(KEK_ENV, kek)::get);

        assertEquals(VaultBootUnlockUtils.Status.UNLOCK_FAILED, status);
        assertFalse(vaultService.isOpened(companyId));
    }

    @Test
    public void test_bootUnlock_kekAbsentFromEnvironmentLeavesTheVaultLocked() throws Exception {
        final long companyId = companyWithLockedVault();

        final VaultBootUnlockUtils.Status status = vaultBootUnlockService.bootUnlock(
            companyId, encrypted(newKek(), PASSPHRASE, companyId), KEK_ENV, Map.<String, String>of()::get);

        assertEquals(VaultBootUnlockUtils.Status.KEK_MISSING, status);
        assertFalse(vaultService.isOpened(companyId));
    }

    @Test
    public void test_bootUnlock_notConfiguredIsANoOp() {
        final long companyId = companyWithLockedVault();

        final VaultBootUnlockUtils.Status status = vaultBootUnlockService.bootUnlock(
            companyId, null, KEK_ENV, Map.of(KEK_ENV, newKek())::get);

        assertEquals(VaultBootUnlockUtils.Status.NOT_CONFIGURED, status);
        assertFalse(vaultService.isOpened(companyId));
    }

    @Test
    public void test_bootUnlockValue_isRefusedForANonManagementCompany() {
        final long companyId = companyWithLockedVault();
        assertTrue(vaultService.unlock(companyId, PASSPHRASE).opened);

        final VaultData.BootUnlockValue value = vaultBootUnlockService.bootUnlockValue(
            companyId, PASSPHRASE, Map.of(KEK_ENV, newKek())::get);

        assertNull(value.filePath, "a refused request must not have written the boot-unlock file");
        assertNotNull(value.errorMessages);
        assertTrue(value.errorMessages.getFirst().startsWith("01.672.080 "), value.errorMessages.getFirst());
    }

    // ---------- role gate of /rest/v1/dispatcher/vault ----------

    @Test
    @WithUserDetails("data")
    public void test_vaultStatus_isForbiddenForDataRole() throws Exception {
        mockMvc.perform(get("/rest/v1/dispatcher/vault/status"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("main_admin")
    public void test_vaultStatus_isAllowedForManagementCompanyMainAdmin() throws Exception {
        mockMvc.perform(get("/rest/v1/dispatcher/vault/status"))
            .andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("admin")
    public void test_bootUnlock_isForbiddenForCustomerCompanyAdmin() throws Exception {
        mockMvc.perform(post("/rest/v1/dispatcher/vault/boot-unlock").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passphrase\":\"x\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("main_admin")
    public void test_bootUnlock_isAllowedForManagementCompanyMainAdmin() throws Exception {
        mockMvc.perform(post("/rest/v1/dispatcher/vault/boot-unlock").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passphrase\":\"x\"}"))
            .andExpect(status().isOk());
    }
}
