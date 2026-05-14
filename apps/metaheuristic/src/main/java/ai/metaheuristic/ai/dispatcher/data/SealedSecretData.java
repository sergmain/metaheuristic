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

package ai.metaheuristic.ai.dispatcher.data;

/**
 * Wire payloads for the per-task sealed-secret endpoint.
 *
 * <p>Request: {@code { companyId, keyCode }}.
 * <p>Response: {@code { sealed, fingerprint, issuedOn, notAfter }} on 200.
 *
 * @author Sergio Lissner
 */
public final class SealedSecretData {

    private SealedSecretData() { /* utility */ }

    /**
     * Request body for POST /rest/v1/processor/sealed-secret.
     *
     * <p>{@code processorId} carries the Processor's own identity for
     * recipient-specific sealing (RSA-OAEP wraps the AES key under the
     * Processor's registered public key). The endpoint's role gate
     * ({@code ASSET_REST_ACCESS}) authenticates that the caller is a
     * Processor; this field identifies which one.
     */
    public record FetchRequest(long processorId, long companyId, String keyCode) {}

    /**
     * Response body on 200.
     *
     * @param sealed       Base64 of the binary {@code SealedSecret} bytes
     *                     produced by {@code SealedSecretCodec.toBytes}.
     * @param fingerprint  SHA-256 hex of the plaintext secret. Cache-keying
     *                     hint so Processors can skip re-decrypting unchanged
     *                     entries on refresh. Not load-bearing for security.
     * @param issuedOn     epoch millis when this sealed payload was assembled.
     * @param notAfter     epoch millis past which the Processor MUST refresh.
     *                     Backstop when signal-bus invalidation fails.
     */
    public record FetchResponse(
        String sealed,
        String fingerprint,
        long issuedOn,
        long notAfter
    ) {}
}
