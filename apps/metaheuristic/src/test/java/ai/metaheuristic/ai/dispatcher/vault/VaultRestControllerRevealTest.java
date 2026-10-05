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

import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.VaultData;
import ai.metaheuristic.ai.dispatcher.rest.v1.VaultRestController;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The reveal endpoint of {@link VaultRestController}, called the way the REST layer calls it - a real
 * {@link UserContextService}, a real {@link VaultService}, a real Spring Security principal - without a Spring
 * context. Lives beside {@link VaultServiceTest} to run on its in-memory blob store.
 *
 * <p>Pins the audit trace: every read attempt, granted or refused, is logged at WARN with who asked for which
 * entry and how it ended - never the secret, never the passphrase.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class VaultRestControllerRevealTest {

    /** never 1L - reserved for the MH management company */
    private static final long COMPANY_ID = 42L;
    private static final long OTHER_COMPANY_ID = 43L;
    private static final String PASSPHRASE = "master-pass-never-logged";

    /** In-memory {@link CompanyCache}: what is saved through it is what it finds. */
    static final class InMemoryCompanyCache extends CompanyCache {
        private final Map<Long, Company> companies = new ConcurrentHashMap<>();

        InMemoryCompanyCache() {
            super(null);
        }

        @Override
        public Company save(Company company) {
            companies.put(company.uniqueId, company);
            return company;
        }

        @Override
        public @Nullable Company findByUniqueId(Long uniqueId) {
            return companies.get(uniqueId);
        }
    }

    private record Caller(VaultRestController controller, Authentication authentication) {}

    /**
     * The Vault of {@link #COMPANY_ID} unlocked with {@link #PASSPHRASE} and holding {@code code=secret};
     * the caller is account {@code username} of company {@code callerCompanyId}.
     */
    private static Caller caller(String username, long callerCompanyId, String code, String secret) {
        VaultService vaultService = new VaultService(new VaultServiceTest.FakeVaultTxService());
        assertTrue(vaultService.unlock(COMPANY_ID, PASSPHRASE).opened);
        assertTrue(vaultService.putApiKey(COMPANY_ID, code, secret));

        CompanyCache companyCache = new InMemoryCompanyCache();
        Company company = new Company();
        company.uniqueId = callerCompanyId;
        companyCache.save(company);

        Account account = new Account();
        account.id = 7001L;
        account.companyId = callerCompanyId;
        account.username = username;

        // boot-unlock is not reached by reveal
        VaultRestController controller = new VaultRestController(vaultService, new UserContextService(companyCache), null);
        return new Caller(controller, new UsernamePasswordAuthenticationToken(account, null, List.of()));
    }

    /** WARN lines the controller logged for {@code username} while {@code action} ran. */
    private static List<String> warningsFor(String username, Runnable action) {
        final Logger logger = (Logger) LoggerFactory.getLogger(VaultRestController.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        }
        finally {
            logger.detachAppender(appender);
        }
        // the other tests of this class log through the same logger at the same time - keep this caller's lines only
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("user: " + username + ","))
                .toList();
    }

    private static VaultData.SecretResult reveal(Caller c, long companyId, String code, String passphrase) {
        return c.controller().revealSecret(companyId, code, new VaultData.RevealSecretRequest(passphrase), c.authentication());
    }

    @Test
    void revealSecret_granted_isLoggedAtWarnWithoutSecretOrPassphrase() {
        Caller c = caller("audit-user-1", COMPANY_ID, "openai", "sk-audit-secret-1");
        AtomicReference<VaultData.SecretResult> result = new AtomicReference<>();

        List<String> warnings = warningsFor("audit-user-1", () -> result.set(reveal(c, COMPANY_ID, "openai", PASSPHRASE)));

        assertEquals("sk-audit-secret-1", result.get().secret);
        assertEquals(1, warnings.size(), "warnings: " + warnings);
        final String w = warnings.getFirst();
        assertTrue(w.startsWith("01.674.010 "), w);
        assertTrue(w.contains("accountId: 7001,"), w);
        assertTrue(w.contains("user's companyId: 42, entry companyId: 42,"), w);
        assertTrue(w.contains("code: openai,"), w);
        assertTrue(w.endsWith("outcome: revealed"), w);
        assertFalse(w.contains("sk-audit-secret-1"), w);
        assertFalse(w.contains(PASSPHRASE), w);
    }

    @Test
    void revealSecret_wrongPassphrase_isLoggedAtWarnWithTheRefusal() {
        Caller c = caller("audit-user-2", COMPANY_ID, "openai", "sk-audit-secret-2");
        AtomicReference<VaultData.SecretResult> result = new AtomicReference<>();

        List<String> warnings = warningsFor("audit-user-2", () -> result.set(reveal(c, COMPANY_ID, "openai", "wrong-pass-never-logged")));

        assertNull(result.get().secret);
        assertEquals(1, warnings.size(), "warnings: " + warnings);
        final String w = warnings.getFirst();
        assertTrue(w.startsWith("01.674.010 "), w);
        assertTrue(w.contains("outcome: refused [01.673.030 "), w);
        assertFalse(w.contains("sk-audit-secret-2"), w);
        assertFalse(w.contains("wrong-pass-never-logged"), w);
    }

    @Test
    void revealSecret_entryOfAnotherCompany_isLoggedAtWarnWithBothCompanies() {
        Caller c = caller("audit-user-3", OTHER_COMPANY_ID, "openai", "sk-audit-secret-3");
        AtomicReference<VaultData.SecretResult> result = new AtomicReference<>();

        List<String> warnings = warningsFor("audit-user-3", () -> result.set(reveal(c, COMPANY_ID, "openai", PASSPHRASE)));

        assertNull(result.get().secret);
        assertEquals(1, warnings.size(), "warnings: " + warnings);
        final String w = warnings.getFirst();
        assertTrue(w.contains("user's companyId: 43, entry companyId: 42,"), w);
        assertTrue(w.contains("outcome: refused [01.673.010 "), w);
        assertFalse(w.contains("sk-audit-secret-3"), w);
    }

    @Test
    void revealSecret_unknownCode_isLoggedAtWarnWithTheRefusal() {
        Caller c = caller("audit-user-4", COMPANY_ID, "openai", "sk-audit-secret-4");
        AtomicReference<VaultData.SecretResult> result = new AtomicReference<>();

        List<String> warnings = warningsFor("audit-user-4", () -> result.set(reveal(c, COMPANY_ID, "anthropic", PASSPHRASE)));

        assertNull(result.get().secret);
        assertEquals(1, warnings.size(), "warnings: " + warnings);
        final String w = warnings.getFirst();
        assertTrue(w.contains("code: anthropic,"), w);
        assertTrue(w.contains("outcome: refused [01.673.040 "), w);
    }
}
