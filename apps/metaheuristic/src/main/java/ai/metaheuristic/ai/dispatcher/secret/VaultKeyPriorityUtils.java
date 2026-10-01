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

import java.util.function.BiPredicate;
import java.util.function.LongPredicate;

/**
 * Which company's Vault answers for a key a task needs.
 *
 * <p>A key in the management company's Vault has priority over the same key in the Vault of the company
 * that owns the task, even when both Vaults hold it. The task company's own Vault answers only when the
 * management company has no such key.
 *
 * <p>❗ A management Vault that exists but is LOCKED resolves to the management company, so the caller
 * answers "locked" (423, a free retry) instead of falling through. Its contents are unknown while locked,
 * and falling through could seal the task company's key while a management key exists — the Processor
 * would then cache the wrong value until its TTL expires. A management company with no Vault at all is
 * skipped, which keeps every deployment without one exactly as it was.
 *
 * <p>Pure: every collaborator is a parameter, so production passes the real {@code VaultService} and
 * a test passes an in-memory Vault.
 *
 * @author Sergio Lissner
 * Date: 10/1/2026
 */
public final class VaultKeyPriorityUtils {

    private VaultKeyPriorityUtils() {
        // utility class
    }

    /**
     * @param taskCompanyId       company that owns the task's ExecContext
     * @param keyCode             Vault entry code the task's Function declares
     * @param managementCompanyId uniqueId of the management company
     * @param isOpened            is this company's Vault unlocked in dispatcher memory
     * @param hasVault            does this company have a Vault at all, locked or not
     * @param hasEntry            does this company's UNLOCKED Vault hold this keyCode
     * @return uniqueId of the company whose Vault must answer
     */
    public static long vaultCompanyIdFor(
            long taskCompanyId, String keyCode, long managementCompanyId,
            LongPredicate isOpened, LongPredicate hasVault, BiPredicate<Long, String> hasEntry) {

        if (taskCompanyId == managementCompanyId) {
            return managementCompanyId;
        }
        if (isOpened.test(managementCompanyId)) {
            return hasEntry.test(managementCompanyId, keyCode) ? managementCompanyId : taskCompanyId;
        }
        // locked or absent; only an absent management Vault lets the task company's Vault answer
        return hasVault.test(managementCompanyId) ? managementCompanyId : taskCompanyId;
    }
}
