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

import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.VaultData;
import ai.metaheuristic.ai.dispatcher.vault.VaultService;
import ai.metaheuristic.commons.account.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the dispatcher Key Vault.
 * Each authenticated user manages their own entries — accountId is taken from
 * the authenticated principal, never from the request body or path. Cross-account
 * read or delete is structurally impossible: list is filtered by principal,
 * delete refuses if the path accountId does not match the principal.
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
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VaultRestController {

    private final VaultService vaultService;
    private final UserContextService userContextService;

    @GetMapping("/status")
    public VaultData.VaultStatus status() {
        return new VaultData.VaultStatus(vaultService.isOpened());
    }

    @PostMapping("/unlock")
    public VaultData.UnlockResult unlock(@RequestBody VaultData.UnlockRequest request) {
        return vaultService.unlock(request.passphrase());
    }

    @GetMapping("/entries")
    public VaultData.EntriesList entries(Authentication authentication) {
        if (!vaultService.isOpened()) {
            return new VaultData.EntriesList("Vault is locked");
        }
        UserContext ctx = userContextService.getContext(authentication);
        return new VaultData.EntriesList(vaultService.listEntries(ctx.getAccountId()), true);
    }

    @PostMapping("/entries")
    public VaultData.OpResult putEntry(@RequestBody VaultData.PutEntryRequest request, Authentication authentication) {
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
        UserContext ctx = userContextService.getContext(authentication);
        boolean ok = vaultService.putApiKey(ctx.getAccountId(), request.code(), request.secret());
        return ok ? new VaultData.OpResult(true) : new VaultData.OpResult("Failed to persist entry");
    }

    @DeleteMapping("/entries/{accountId}/{code}")
    public VaultData.OpResult deleteEntry(
            @PathVariable long accountId,
            @PathVariable String code,
            @RequestBody VaultData.DeleteEntryRequest request,
            Authentication authentication) {
        if (!vaultService.isOpened()) {
            return new VaultData.OpResult("Vault is locked");
        }
        UserContext ctx = userContextService.getContext(authentication);
        if (ctx.getAccountId() == null || accountId != ctx.getAccountId()) {
            // Refuse cross-account deletes; do not leak whether the entry exists.
            return new VaultData.OpResult("Entry not found or persistence failed");
        }
        if (!vaultService.verifyPassphrase(request.passphrase())) {
            return new VaultData.OpResult("Passphrase verification failed");
        }
        boolean ok = vaultService.deleteApiKey(accountId, code);
        return ok ? new VaultData.OpResult(true) : new VaultData.OpResult("Entry not found or persistence failed");
    }
}
