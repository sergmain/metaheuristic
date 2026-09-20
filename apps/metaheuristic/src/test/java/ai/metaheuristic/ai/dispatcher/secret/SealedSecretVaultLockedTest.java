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

import ai.metaheuristic.ai.dispatcher.processor.security.ProcessorKeyResolver;
import ai.metaheuristic.ai.dispatcher.vault.VaultService;
import ai.metaheuristic.commons.security.CreateKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Whether the seal path can tell a LOCKED Vault from one that simply has no such entry.
 *
 * <p>Today it cannot. {@code VaultService.getKeyBytes} answers {@code Optional.empty()} in both
 * cases, and {@link SealedSecretService#sealFor} turns that single empty into a single reason,
 * VAULT_ENTRY_MISSING. The Processor then prints {@code 01.812.04x Vault has no entry}, and the
 * dispatcher-level analyzer matches that one line — which is why the gate reason has to be spelled
 * "vault locked or key missing".
 *
 * <p>The two cases have opposite character and want opposite handling:
 * <ul>
 * <li>LOCKED is transient. Every Vault locks on a Dispatcher restart and clears when an operator
 *     unlocks, so a FREE retry ({@code incrementTries=false}) and a short block are right — the Task
 *     did nothing wrong and the condition really does resolve on its own schedule.</li>
 * <li>NO ENTRY is permanent. Nobody is coming. A free retry there is an unbounded livelock: the Task
 *     never spends a try, so it never exhausts them, never lands in ERROR, and never tells a human.
 *     Observed on ExecContext #294 — 1062 rejections across all 15 buckets of the window, with an
 *     UNLOCKED Vault, because company 2 has no ANTHROPIC_API_KEY entry at all.</li>
 * </ul>
 *
 * <p>This is the same distinction the call-cc Function's own analyzer block already draws for
 * credentials: "A rejected credential is NOT transient: waiting does not fix a wrong or revoked key
 * ... incrementTries is TRUE here - the Task should exhaust its retries and land in ERROR so a human
 * is told, rather than retrying free of charge forever."
 *
 * <p>No Mockito here, per RULE-NO-MOCKITO. {@code InMemoryVaultService} is a real if simple
 * implementation of the two methods that matter: it is seeded through its own API, it behaves the
 * same way in every test, and it can disagree with what a test hoped for. The assertions are on what
 * {@code sealFor} returned, never on the double. ⚠️ The sibling {@code SealedSecretServiceTest} is
 * built on {@code mock(VaultService.class)} and predates that rule; nothing here extends it.
 *
 * @author Sergio Lissner
 * Date: 9/20/2026
 */
@Execution(CONCURRENT)
public class SealedSecretVaultLockedTest {

    private static final long PROCESSOR_ID = 42L;
    private static final long COMPANY_ID = 7L;
    private static final String KEY_CODE = "ANTHROPIC_API_KEY";

    /**
     * A locked Vault and a Vault without the entry are two different facts about the world.
     */
    @Test
    public void test_lockedVaultIsDistinguishableFromAMissingEntry() throws Exception {
        final InMemoryVaultService vault = new InMemoryVaultService();
        // the entry EXISTS - only the lock stands between the caller and it
        vault.put(COMPANY_ID, KEY_CODE, "sk-test-1234");
        // ...and the vault is left locked

        final SealedSecretService svc = service(vault);
        final SealedSecretService.Outcome outcome = svc.sealFor(PROCESSOR_ID, COMPANY_ID, KEY_CODE);

        assertNull(outcome.payload());
        assertEquals(SealedSecretService.Outcome.Reason.VAULT_LOCKED, outcome.reason(),
                "a locked Vault is a transient condition with a human coming, and must be reported apart from "
                        + "a missing entry, which is permanent and must be allowed to exhaust the Task's tries");
    }

    /**
     * The genuinely-missing case, which must keep answering VAULT_ENTRY_MISSING after the fix.
     */
    @Test
    public void test_unlockedVaultWithoutTheEntryIsAMissingEntry() throws Exception {
        final InMemoryVaultService vault = new InMemoryVaultService();
        vault.unlockFake(COMPANY_ID);
        vault.put(COMPANY_ID, "SOME_OTHER_KEY", "irrelevant");

        final SealedSecretService.Outcome outcome = service(vault).sealFor(PROCESSOR_ID, COMPANY_ID, KEY_CODE);

        assertNull(outcome.payload());
        assertEquals(SealedSecretService.Outcome.Reason.VAULT_ENTRY_MISSING, outcome.reason(),
                "an unlocked Vault that has no such entry is a genuinely missing entry");
    }

    /**
     * Positive control. Without it the two assertions above would also pass against a fake that
     * simply never returns anything.
     */
    @Test
    public void test_unlockedVaultWithTheEntrySeals() throws Exception {
        final InMemoryVaultService vault = new InMemoryVaultService();
        vault.unlockFake(COMPANY_ID);
        vault.put(COMPANY_ID, KEY_CODE, "sk-test-1234");

        final SealedSecretService.Outcome outcome = service(vault).sealFor(PROCESSOR_ID, COMPANY_ID, KEY_CODE);

        assertEquals(SealedSecretService.Outcome.Reason.OK, outcome.reason());
        assertNotNull(outcome.payload());
    }

    private static SealedSecretService service(VaultService vault) throws Exception {
        return new SealedSecretService(vault, new EnrolledProcessorKeyResolver(new CreateKeys(2048).getPublicKey()));
    }

    /**
     * A real in-memory Vault rather than a script: seeded through its own API, one behaviour for every
     * test. It reproduces the property this whole test is about — a locked Vault yields nothing, which
     * is exactly what the production one does and exactly what hides the distinction.
     */
    static class InMemoryVaultService extends VaultService {
        private final Set<Long> opened = new HashSet<>();
        private final Map<Long, Map<String, String>> entries = new HashMap<>();

        InMemoryVaultService() {
            super(null);
        }

        void unlockFake(long companyUniqueId) {
            opened.add(companyUniqueId);
        }

        void put(long companyUniqueId, String code, String value) {
            entries.computeIfAbsent(companyUniqueId, k -> new HashMap<>()).put(code, value);
        }

        @Override
        public boolean isOpened(long companyUniqueId) {
            return opened.contains(companyUniqueId);
        }

        @Override
        public Optional<byte[]> getKeyBytes(long companyUniqueId, String code) {
            if (!opened.contains(companyUniqueId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(entries.getOrDefault(companyUniqueId, Map.of()).get(code))
                    .map(s -> s.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** An enrolled Processor, carrying a really generated key rather than an answer built for a branch. */
    static class EnrolledProcessorKeyResolver extends ProcessorKeyResolver {
        private final PublicKey pub;

        EnrolledProcessorKeyResolver(PublicKey pub) {
            super(null);
            this.pub = pub;
        }

        @Override
        public Optional<PublicKey> publicKeyFor(long processorId) {
            return Optional.of(pub);
        }
    }
}
