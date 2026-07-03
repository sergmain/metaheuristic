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

package ai.metaheuristic.commons.spi.license;

/**
 * Issuing-side signer SPI (section 7.3). Vendor-side only; never shipped to customers.
 *
 * Signs an already-assembled protected header + payload and returns a compact JWS
 * (header.payload.signature). The concrete signing-key backend - local key, AWS/GCP/Azure KMS,
 * on-prem HSM, PKCS#11/TPM - is hidden behind this interface so the keygen/signer tool stays
 * uniform and swapping the backend is a one-class change with no SPI impact.
 *
 * Both arguments are JSON. Any 'features' inside the payload are opaque strings.
 *
 * @author Serge
 */
public interface JwsSigner {

    String sign(String protectedHeaderJson, String payloadJson);
}
