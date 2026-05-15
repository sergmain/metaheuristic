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
 * Each company manages its own entries — companyUniqueId is taken from the
 * authenticated principal, never from the request body or path. Cross-company
 * read or delete is structurally impossible: list is filtered by principal,
 * delete refuses if the path companyId does not match the principal.
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
    private final UserContextService userContextService;

    @GetMapping("/status")
    public VaultData.VaultStatus status(Authentication authentication) {
        UserContext ctx = userContextService.getContext(authentication);
        return new VaultData.VaultStatus(vaultService.isOpened(ctx.getCompanyId()));
    }

    @PostMapping("/unlock")
    public VaultData.UnlockResult unlock(@RequestBody VaultData.UnlockRequest request, Authentication authentication) {
        UserContext ctx = userContextService.getContext(authentication);
        return vaultService.unlock(ctx.getCompanyId(), request.passphrase());
    }

    @GetMapping("/entries")
    public VaultData.EntriesList entries(Authentication authentication) {
        UserContext ctx = userContextService.getContext(authentication);
        if (!vaultService.isOpened(ctx.getCompanyId())) {
            return new VaultData.EntriesList("Vault is locked");
        }
        return new VaultData.EntriesList(vaultService.listEntries(ctx.getCompanyId()), true);
    }

    @PostMapping("/entries")
    public VaultData.OpResult putEntry(@RequestBody VaultData.PutEntryRequest request, Authentication authentication) {
        UserContext ctx = userContextService.getContext(authentication);
        if (!vaultService.isOpened(ctx.getCompanyId())) {
            return new VaultData.OpResult("Vault is locked");
        }
        if (request.code() == null || request.code().isBlank()) {
            return new VaultData.OpResult("Code must not be blank");
        }
        if (request.secret() == null || request.secret().isEmpty()) {
            return new VaultData.OpResult("Secret must not be empty");
        }
        if (!vaultService.verifyPassphrase(ctx.getCompanyId(), request.passphrase())) {
            return new VaultData.OpResult("Passphrase verification failed");
        }
        boolean ok = vaultService.putApiKey(ctx.getCompanyId(), request.code(), request.secret());
        return ok ? new VaultData.OpResult(true) : new VaultData.OpResult("Failed to persist entry");
    }

    @DeleteMapping("/entries/{companyId}/{code}")
    public VaultData.OpResult deleteEntry(
            @PathVariable long companyId,
            @PathVariable String code,
            @RequestBody VaultData.DeleteEntryRequest request,
            Authentication authentication) {
        UserContext ctx = userContextService.getContext(authentication);
        if (ctx.getCompanyId() == null || companyId != ctx.getCompanyId()) {
            // Refuse cross-account deletes; do not leak whether the entry exists.
            return new VaultData.OpResult("Entry not found or persistence failed");
        }
        if (!vaultService.isOpened(companyId)) {
            return new VaultData.OpResult("Vault is locked");
        }
        if (!vaultService.verifyPassphrase(companyId, request.passphrase())) {
            return new VaultData.OpResult("Passphrase verification failed");
        }
        boolean ok = vaultService.deleteApiKey(companyId, code);
        return ok ? new VaultData.OpResult(true) : new VaultData.OpResult("Entry not found or persistence failed");
    }
}
