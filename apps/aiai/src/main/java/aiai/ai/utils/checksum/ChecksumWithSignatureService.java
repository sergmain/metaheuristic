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
import aiai.apps.commons.utils.SecUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;

import java.security.*;
import java.security.spec.InvalidKeySpecException;

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
            throw new IllegalStateException("Wrong format of checksum with signature");
        }
        //noinspection UnnecessaryLocalVariable
        ChecksumWithSignature checksumWithSignature = new ChecksumWithSignature(data.substring(0, idx), data.substring(idx + SIGN_DELIMITER.length()));
        return checksumWithSignature;
    }

    public boolean isValid(ChecksumWithSignature checksumWithSignature, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(checksumWithSignature.checksum.getBytes());
            //noinspection UnnecessaryLocalVariable
            final byte[] bytes = Base64.decodeBase64(checksumWithSignature.signature);
            boolean status = signature.verify(bytes);
            return status;
        }
        catch (SignatureException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error checking signature", e);
            throw new RuntimeException("Error", e);
        }
    }
}
