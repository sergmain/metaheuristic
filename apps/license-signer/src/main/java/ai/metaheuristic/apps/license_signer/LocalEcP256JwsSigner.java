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

package ai.metaheuristic.apps.license_signer;

import ai.metaheuristic.commons.spi.license.JwsSigner;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;

import java.security.interfaces.ECPrivateKey;

/**
 * Local-key {@link JwsSigner} backend: Nimbus JWS assembly over an in-process EC P-256 key.
 * The external-key-manager backends (KMS/HSM/PKCS#11) would replace only this one class - the
 * SPI, the claim schema and the app orchestration are untouched (section 7.3).
 *
 * @author Serge
 */
public class LocalEcP256JwsSigner implements JwsSigner {

    private final ECPrivateKey privateKey;

    public LocalEcP256JwsSigner(ECPrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    @Override
    public String sign(String protectedHeaderJson, String payloadJson) {
        try {
            final JWSHeader header = JWSHeader.parse(protectedHeaderJson);
            final JWSObject jwsObject = new JWSObject(header, new Payload(payloadJson));
            jwsObject.sign(new ECDSASigner(privateKey));
            return jwsObject.serialize();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to sign license token: " + e.getMessage(), e);
        }
    }
}
