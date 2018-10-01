/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.utils.checksum;

import aiai.ai.Globals;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Component
@Slf4j
public class ChecksumWithSignatureService {

    public static final String SIGN_DELIMITER = "###";

    public final Globals globals;

    @AllArgsConstructor
    public static class ChecksumWithSignature {
        public String checksum;
        public String signature;
    }

    public ChecksumWithSignatureService(Globals globals) {
        this.globals = globals;
    }

    public static ChecksumWithSignature parse(String data) {
        final int idx = data.indexOf(SIGN_DELIMITER);
        if (idx == -1) {
            throw new IllegalStateException("Wroong format of checksum with signature");
        }
        //noinspection UnnecessaryLocalVariable
        ChecksumWithSignature checksumWithSignature = new ChecksumWithSignature(data.substring(0, idx), data.substring(idx + SIGN_DELIMITER.length()));
        return checksumWithSignature;
    }

    public static String getSignature(String data, PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signer= Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeBase64String(signer.sign());
    }

    public static PublicKey getPublicKey(String keyBase64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = Base64.decodeBase64(keyBase64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    public static PrivateKey getPrivateKey(String keyBase64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = Base64.decodeBase64(keyBase64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    public boolean isValid(ChecksumWithSignature checksumWithSignature) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(getPublicKey(globals.publicKey));
            signature.update(checksumWithSignature.checksum.getBytes());
            //noinspection UnnecessaryLocalVariable
            final byte[] bytes = Base64.decodeBase64(checksumWithSignature.signature);
            boolean status = signature.verify(bytes);
            return status;
        }
        catch (SignatureException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            log.error("Error checking signature", e);
            throw new RuntimeException("Error", e);
        }
    }
}
