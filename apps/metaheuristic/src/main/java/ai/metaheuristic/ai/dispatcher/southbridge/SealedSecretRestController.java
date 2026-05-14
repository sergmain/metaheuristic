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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.dispatcher.data.SealedSecretData;
import ai.metaheuristic.ai.dispatcher.secret.SealedSecretService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Per-task sealed-secret endpoint for the Processor.
 *
 * <p>{@code POST /rest/v1/processor/sealed-secret} with body
 * {@code { processorId, companyId, keyCode }}. Returns one sealed
 * {@code SealedSecret} payload Base64-encoded, RSA-OAEP-wrapped under the
 * Processor's registered public key.
 *
 * <p>Status codes:
 * <ul>
 *   <li>200 — sealed payload returned</li>
 *   <li>404 — Processor has no {@code publicKeySpki} on its
 *       {@code MH_PROCESSOR.STATUS} yet (Stage 4 prerequisite not satisfied).
 *       Treated as transient by the caller; retry next cycle.</li>
 *   <li>410 — Vault has no entry for {@code (companyId, keyCode)}. Permanent;
 *       caller routes to {@code markAsFinishedWithError}.</li>
 *   <li>500 — internal sealing/encoding failure.</li>
 * </ul>
 *
 * <p>Role gate: {@code ASSET_REST_ACCESS} (the established Processor role).
 *
 * @author Sergio Lissner
 */
@RestController
@RequestMapping("/rest/v1/processor")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@PreAuthorize("hasAnyRole('ASSET_REST_ACCESS')")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SealedSecretRestController {

    private final SealedSecretService sealedSecretService;

    @PostMapping("/sealed-secret")
    public ResponseEntity<SealedSecretData.FetchResponse> fetch(
            @RequestBody SealedSecretData.FetchRequest request) {

        if (request.keyCode() == null || request.keyCode().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        SealedSecretService.Outcome outcome = sealedSecretService.sealFor(
            request.processorId(), request.companyId(), request.keyCode());

        return switch (outcome.reason()) {
            case OK -> {
                SealedSecretService.SealedPayload p = outcome.payload();
                // payload is non-null on OK by construction; the local assignment plus
                // the switch arm structure satisfies static analyzers.
                yield ResponseEntity.ok(new SealedSecretData.FetchResponse(
                    p.sealed(), p.fingerprint(), p.issuedOn(), p.notAfter()));
            }
            case PROCESSOR_NOT_ENROLLED -> ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            case VAULT_ENTRY_MISSING   -> ResponseEntity.status(HttpStatus.GONE).build();
            case INTERNAL_ERROR        -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        };
    }
}
