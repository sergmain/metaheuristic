/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.data.VaultData;
import ai.metaheuristic.ai.dispatcher.vault.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the dispatcher Key Vault.
 * Admin-only endpoints to query vault status, unlock, list/add/delete entries.
 *
 * <p>All write/delete operations require the master passphrase as a
 * proof-of-knowledge gate even after the vault has been unlocked.
 *
 * @author Sergio Lissner
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/vault")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@PreAuthorize("hasAnyRole('ADMIN')")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VaultRestController {

    private final VaultService vaultService;

    @GetMapping("/status")
    public VaultData.VaultStatus status() {
        return new VaultData.VaultStatus(vaultService.isOpened());
    }

    @PostMapping("/unlock")
    public VaultData.UnlockResult unlock(@RequestBody VaultData.UnlockRequest request) {
        return vaultService.unlock(request.passphrase());
    }

    @GetMapping("/entries")
    public VaultData.EntriesList entries() {
        if (!vaultService.isOpened()) {
            return new VaultData.EntriesList("Vault is locked");
        }
        return new VaultData.EntriesList(vaultService.listEntries(), true);
    }

    @PostMapping("/entries")
    public VaultData.OpResult putEntry(@RequestBody VaultData.PutEntryRequest request) {
        if (!vaultService.isOpened()) {
            return new VaultData.OpResult("Vault is locked");
        }
        if (request.code() == null || request.code().isBlank()) {
            return new VaultData.OpResult("Code must not be blank");
        }
        if (request.secret() == null || request.secret().isEmpty()) {
            return new VaultData.OpResult("Secret must not be empty");
        }
        if (!vaultService.verifyPassphrase(request.passphrase())) {
            return new VaultData.OpResult("Passphrase verification failed");
        }
        boolean ok = vaultService.putApiKey(request.accountId(), request.code(), request.secret());
        return ok ? new VaultData.OpResult(true) : new VaultData.OpResult("Failed to persist entry");
    }

    @DeleteMapping("/entries/{accountId}/{code}")
    public VaultData.OpResult deleteEntry(
            @PathVariable long accountId,
            @PathVariable String code,
            @RequestBody VaultData.DeleteEntryRequest request) {
        if (!vaultService.isOpened()) {
            return new VaultData.OpResult("Vault is locked");
        }
        if (!vaultService.verifyPassphrase(request.passphrase())) {
            return new VaultData.OpResult("Passphrase verification failed");
        }
        boolean ok = vaultService.deleteApiKey(accountId, code);
        return ok ? new VaultData.OpResult(true) : new VaultData.OpResult("Entry not found or persistence failed");
    }
}
