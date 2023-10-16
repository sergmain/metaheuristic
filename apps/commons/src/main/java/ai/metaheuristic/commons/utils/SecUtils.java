/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
package ai.metaheuristic.commons.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
public class SecUtils {

    public static final String SIGNATURE_DELIMITER = "###";

    public static String getSignature(String data, PrivateKey privateKey) throws GeneralSecurityException {
        return getSignature(data, privateKey, false);
    }

    public static String getSignature(String data, PrivateKey privateKey, boolean isChunked) throws GeneralSecurityException {
        return getSignature(data, privateKey, isChunked, "SHA256withRSA");
    }

    public static String getSignature(String data, PrivateKey privateKey, boolean isChunked, String algorithm) throws GeneralSecurityException {
        Signature signer= Signature.getInstance(algorithm);
        signer.initSign(privateKey);
        signer.update(data.getBytes(StandardCharsets.UTF_8));
        return StringUtils.newStringUsAscii(Base64.encodeBase64(signer.sign(), isChunked));
    }

    public static PublicKey getPublicKey(String keyBase64) {
        try {
            byte[] keyBytes = Base64.decodeBase64(keyBase64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (GeneralSecurityException e) {
            String es = "Error of creating public key from string";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    public static PrivateKey getPrivateKey(String keyBase64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = Base64.decodeBase64(keyBase64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }


}
