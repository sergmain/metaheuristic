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
 * Admin-only endpoints to query vault status and unlock it for the JVM lifetime.
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
}
