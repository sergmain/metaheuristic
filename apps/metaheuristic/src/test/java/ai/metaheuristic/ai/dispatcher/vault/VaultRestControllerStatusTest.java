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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.VaultData;
import ai.metaheuristic.ai.dispatcher.rest.v1.VaultRestController;
import ai.metaheuristic.commons.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The status endpoint of {@link VaultRestController}, called the way the REST layer calls it - a real
 * {@link UserContextService}, a real {@link VaultService}, a real Spring Security principal - without a Spring
 * context. Lives beside {@link VaultServiceTest} to run on its in-memory blob store.
 *
 * <p>Pins the two statuses: the vault of the management company and the vault of the caller's own company,
 * each reported with its companyId.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class VaultRestControllerStatusTest {

    /** never 1L - reserved for the MH management company */
    private static final long COMPANY_ID = 42L;
    private static final String PASSPHRASE = "master-pass";

    private record Caller(VaultRestController controller, Authentication authentication, VaultService vaultService) {
        VaultData.VaultStatus status() {
            return controller.status(authentication);
        }
    }

    /** The caller is an account of company {@code callerCompanyId}; no vault is unlocked yet. */
    private static Caller caller(long callerCompanyId) {
        VaultService vaultService = new VaultService(new VaultServiceTest.FakeVaultTxService());

        CompanyCache companyCache = new VaultRestControllerRevealTest.InMemoryCompanyCache();
        Company company = new Company();
        company.uniqueId = callerCompanyId;
        companyCache.save(company);

        Account account = new Account();
        account.id = 7101L;
        account.companyId = callerCompanyId;
        account.username = "status-user";

        // boot-unlock is not reached by status
        VaultRestController controller = new VaultRestController(vaultService, new UserContextService(companyCache), null);
        return new Caller(controller, new UsernamePasswordAuthenticationToken(account, null, List.of()), vaultService);
    }

    private static void assertStatus(VaultData.VaultStatus s, boolean managementOpened, long currentCompanyId, boolean currentOpened) {
        assertNotNull(s.management, "management status must be reported");
        assertNotNull(s.current, "current status must be reported");
        assertEquals(new VaultData.CompanyVaultStatus(Consts.MANAGEMENT_COMPANY_ID, managementOpened), s.management);
        assertEquals(new VaultData.CompanyVaultStatus(currentCompanyId, currentOpened), s.current);
        assertTrue(s.errorMessages == null || s.errorMessages.isEmpty(), "errors: " + s.errorMessages);
    }

    @Test
    void status_bothVaultsLocked() {
        Caller c = caller(COMPANY_ID);

        assertStatus(c.status(), false, COMPANY_ID, false);
    }

    @Test
    void status_onlyManagementVaultUnlocked() {
        Caller c = caller(COMPANY_ID);
        assertTrue(c.vaultService().unlock(Consts.MANAGEMENT_COMPANY_ID, PASSPHRASE).opened);

        assertStatus(c.status(), true, COMPANY_ID, false);
    }

    @Test
    void status_onlyOwnVaultUnlocked() {
        Caller c = caller(COMPANY_ID);
        assertTrue(c.vaultService().unlock(COMPANY_ID, PASSPHRASE).opened);

        assertStatus(c.status(), false, COMPANY_ID, true);
    }

    @Test
    void status_bothVaultsUnlocked() {
        Caller c = caller(COMPANY_ID);
        assertTrue(c.vaultService().unlock(Consts.MANAGEMENT_COMPANY_ID, PASSPHRASE).opened);
        assertTrue(c.vaultService().unlock(COMPANY_ID, PASSPHRASE).opened);

        assertStatus(c.status(), true, COMPANY_ID, true);
    }

    @Test
    void status_followsUnlockBetweenCalls() {
        Caller c = caller(COMPANY_ID);
        assertStatus(c.status(), false, COMPANY_ID, false);

        assertTrue(c.vaultService().unlock(COMPANY_ID, PASSPHRASE).opened);

        assertStatus(c.status(), false, COMPANY_ID, true);
    }

    @Test
    void status_callerOfManagementCompany_bothReportTheManagementVault() {
        Caller c = caller(Consts.MANAGEMENT_COMPANY_ID);
        assertTrue(c.vaultService().unlock(Consts.MANAGEMENT_COMPANY_ID, PASSPHRASE).opened);

        assertStatus(c.status(), true, Consts.MANAGEMENT_COMPANY_ID, true);
    }

    @Test
    void status_wireJson_carriesCompanyIdAndOpenedOfBoth() {
        Caller c = caller(COMPANY_ID);
        assertTrue(c.vaultService().unlock(COMPANY_ID, PASSPHRASE).opened);

        final String json = JsonUtils.getMapper().writeValueAsString(c.status());
        final JsonNode node = JsonUtils.getMapper().readTree(json);

        assertEquals(1L, node.path("management").path("companyId").asLong(), "json: " + json);
        assertFalse(node.path("management").path("opened").asBoolean(), "json: " + json);
        assertEquals(COMPANY_ID, node.path("current").path("companyId").asLong(), "json: " + json);
        assertTrue(node.path("current").path("opened").asBoolean(), "json: " + json);
    }

    @Test
    void status_wireJson_roundTrip() {
        Caller c = caller(COMPANY_ID);
        assertTrue(c.vaultService().unlock(Consts.MANAGEMENT_COMPANY_ID, PASSPHRASE).opened);
        final VaultData.VaultStatus sent = c.status();

        final String json = JsonUtils.getMapper().writeValueAsString(sent);
        final VaultData.VaultStatus received = JsonUtils.getMapper().readValue(json, VaultData.VaultStatus.class);

        assertStatus(received, true, COMPANY_ID, false);
        assertEquals(sent, received, "json: " + json);
    }
}
