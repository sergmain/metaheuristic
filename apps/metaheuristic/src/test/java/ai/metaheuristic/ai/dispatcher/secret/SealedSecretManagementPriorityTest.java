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

package ai.metaheuristic.ai.dispatcher.secret;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.commons.security.CreateKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * A key in the management company's Vault has priority over the same key in the Vault of the company
 * that owns the task.
 *
 * <p>No Mockito, per RULE-NO-MOCKITO. Reuses the in-memory Vault and the enrolled-Processor resolver of
 * {@link SealedSecretVaultLockedTest}: seeded through their own API, one behaviour for every test. Which
 * Vault answered is read from what {@code sealFor} computed - the SHA-256 fingerprint of the plaintext it
 * sealed - never from the double.
 *
 * @author Sergio Lissner
 * Date: 10/1/2026
 */
@Execution(CONCURRENT)
public class SealedSecretManagementPriorityTest {

    private static final long PROCESSOR_ID = 42L;
    private static final long COMPANY_ID = 7L;
    private static final long MGMT_ID = Consts.MANAGEMENT_COMPANY_ID;
    private static final String KEY_CODE = "ANTHROPIC_API_KEY";

    private static final String MGMT_SECRET = "sk-management-1111";
    private static final String OWN_SECRET = "sk-company-7-2222";

    @Test
    public void test_managementKeyWinsOverTheSameKeyOfTheTaskCompany() throws Exception {
        final SealedSecretVaultLockedTest.InMemoryVaultService vault = new SealedSecretVaultLockedTest.InMemoryVaultService();
        vault.unlockFake(MGMT_ID);
        vault.put(MGMT_ID, KEY_CODE, MGMT_SECRET);
        vault.unlockFake(COMPANY_ID);
        vault.put(COMPANY_ID, KEY_CODE, OWN_SECRET);

        final SealedSecretService.Outcome outcome = service(vault).sealFor(PROCESSOR_ID, COMPANY_ID, KEY_CODE);

        assertEquals(SealedSecretService.Outcome.Reason.OK, outcome.reason());
        assertNotNull(outcome.payload());
        assertEquals(sha256Hex(MGMT_SECRET), outcome.payload().fingerprint(),
                "the management company's key must win over the same key of the task company");
    }

    @Test
    public void test_taskCompanyKeyAnswersWhenManagementVaultLacksTheKey() throws Exception {
        final SealedSecretVaultLockedTest.InMemoryVaultService vault = new SealedSecretVaultLockedTest.InMemoryVaultService();
        vault.unlockFake(MGMT_ID);
        vault.put(MGMT_ID, "SOME_OTHER_KEY", "irrelevant");
        vault.unlockFake(COMPANY_ID);
        vault.put(COMPANY_ID, KEY_CODE, OWN_SECRET);

        final SealedSecretService.Outcome outcome = service(vault).sealFor(PROCESSOR_ID, COMPANY_ID, KEY_CODE);

        assertEquals(SealedSecretService.Outcome.Reason.OK, outcome.reason());
        assertNotNull(outcome.payload());
        assertEquals(sha256Hex(OWN_SECRET), outcome.payload().fingerprint(),
                "a management Vault without this keyCode must let the task company's own key answer");
    }

    @Test
    public void test_noManagementVaultLeavesTheTaskCompanyVaultInCharge() throws Exception {
        final SealedSecretVaultLockedTest.InMemoryVaultService vault = new SealedSecretVaultLockedTest.InMemoryVaultService();
        vault.unlockFake(COMPANY_ID);
        vault.put(COMPANY_ID, KEY_CODE, OWN_SECRET);

        final SealedSecretService.Outcome outcome = service(vault).sealFor(PROCESSOR_ID, COMPANY_ID, KEY_CODE);

        assertEquals(SealedSecretService.Outcome.Reason.OK, outcome.reason());
        assertNotNull(outcome.payload());
        assertEquals(sha256Hex(OWN_SECRET), outcome.payload().fingerprint(),
                "a deployment whose management company has no Vault must behave exactly as before");
    }

    @Test
    public void test_lockedManagementVaultAnswersLockedEvenWhenTheTaskCompanyHasTheKey() throws Exception {
        final SealedSecretVaultLockedTest.InMemoryVaultService vault = new SealedSecretVaultLockedTest.InMemoryVaultService();
        // the management Vault exists (it holds an entry) but is left LOCKED
        vault.put(MGMT_ID, KEY_CODE, MGMT_SECRET);
        vault.unlockFake(COMPANY_ID);
        vault.put(COMPANY_ID, KEY_CODE, OWN_SECRET);

        final SealedSecretService.Outcome outcome = service(vault).sealFor(PROCESSOR_ID, COMPANY_ID, KEY_CODE);

        assertNull(outcome.payload());
        assertEquals(SealedSecretService.Outcome.Reason.VAULT_LOCKED, outcome.reason(),
                "while the management Vault is locked its contents are unknown; falling through could seal the "
                        + "task company's key while a management key exists, and the Processor would cache it");
    }

    @Test
    public void test_taskOfTheManagementCompanyReadsTheManagementVault() throws Exception {
        final SealedSecretVaultLockedTest.InMemoryVaultService vault = new SealedSecretVaultLockedTest.InMemoryVaultService();
        vault.unlockFake(MGMT_ID);
        vault.put(MGMT_ID, KEY_CODE, MGMT_SECRET);

        final SealedSecretService.Outcome outcome = service(vault).sealFor(PROCESSOR_ID, MGMT_ID, KEY_CODE);

        assertEquals(SealedSecretService.Outcome.Reason.OK, outcome.reason());
        assertNotNull(outcome.payload());
        assertEquals(sha256Hex(MGMT_SECRET), outcome.payload().fingerprint());
    }

    private static SealedSecretService service(SealedSecretVaultLockedTest.InMemoryVaultService vault) throws Exception {
        return new SealedSecretService(vault,
                new SealedSecretVaultLockedTest.EnrolledProcessorKeyResolver(new CreateKeys(2048).getPublicKey()));
    }

    private static String sha256Hex(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
