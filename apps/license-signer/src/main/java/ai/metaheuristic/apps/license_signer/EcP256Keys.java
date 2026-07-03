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

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * EC P-256 (secp256r1) keypair generation and base64 codec.
 *
 * Keys are encoded as base64 of their standard DER form (PKCS#8 for private, X.509 for public),
 * mirroring the base64 key convention already used by the RSA gen-keys app. The X.509 public-key
 * string is exactly what later becomes the hard-coded verification constant on the MH runtime side.
 *
 * @author Serge
 */
public class EcP256Keys {

    private EcP256Keys() {
    }

    public static KeyPair generate() throws GeneralSecurityException {
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        return gen.generateKeyPair();
    }

    public static String encodeBase64(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    public static String encodeBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public static ECPrivateKey readPrivateKey(String base64Pkcs8) throws GeneralSecurityException {
        final byte[] der = Base64.getDecoder().decode(base64Pkcs8.replaceAll("\\s", ""));
        final KeyFactory kf = KeyFactory.getInstance("EC");
        return (ECPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
