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
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;

/**
 * Reveal of one Key Vault entry's secret - the only path by which a secret leaves the Dispatcher for the UI.
 *
 * <p>The listing carries codes only. A secret is returned one entry at a time, only to a caller of the
 * entry's own company, and only after the master passphrase is re-entered - even though the Vault is
 * already unlocked.
 *
 * <p>Every Vault access is a parameter: production passes the real {@code VaultService} methods.
 *
 * <p>Error code prefix: {@code 01.673.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 10/5/2026
 */
public final class VaultRevealUtils {

    private VaultRevealUtils() {
        // utility class
    }

    /**
     * @param principalCompanyId company of the authenticated caller, taken from the principal - never from the request
     * @param companyId          company of the entry, from the request path
     * @param code               code of the entry
     * @param passphrase         master passphrase, re-entered as proof of knowledge
     * @param isOpened           is the company's Vault unlocked
     * @param verifyPassphrase   does the passphrase match the one that unlocked the company's Vault
     * @param getSecret          secret of an entry, empty if there is no such entry
     */
    public static VaultData.SecretResult revealSecret(
            @Nullable Long principalCompanyId, long companyId, String code, String passphrase,
            LongPredicate isOpened, BiPredicate<Long, String> verifyPassphrase,
            BiFunction<Long, String, Optional<String>> getSecret) {

        if (principalCompanyId == null || principalCompanyId != companyId) {
            // Refuse cross-company reads; do not leak whether the entry exists.
            return VaultData.SecretResult.ofError("01.673.010 Entry not found");
        }
        if (!isOpened.test(companyId)) {
            return VaultData.SecretResult.ofError("01.673.020 Vault is locked");
        }
        if (!verifyPassphrase.test(companyId, passphrase)) {
            return VaultData.SecretResult.ofError("01.673.030 Passphrase verification failed");
        }
        return getSecret.apply(companyId, code)
                .map(VaultData.SecretResult::ofSecret)
                .orElseGet(() -> VaultData.SecretResult.ofError("01.673.040 Entry not found"));
    }
}
