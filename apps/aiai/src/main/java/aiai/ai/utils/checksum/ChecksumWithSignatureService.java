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
import aiai.ai.station.actors.DownloadSnippetActor;
import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.utils.SecUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;

@Component
@Slf4j
public class ChecksumWithSignatureService {

    public final Globals globals;

    @AllArgsConstructor
    public static class ChecksumWithSignature {
        public String checksum;
        public String signature;
    }

    public ChecksumWithSignatureService(Globals globals) {
        this.globals = globals;
    }

    public void verifyChecksumAndSignature(Checksum checksum, String snippetCode, CheckSumAndSignatureStatus status, InputStream fis ) throws IOException {
        for (Map.Entry<Checksum.Type, String> entry : checksum.checksums.entrySet()) {
            String sum;
            if (entry.getKey()==Checksum.Type.SHA256WithSign) {
                ChecksumWithSignature checksumWithSignature = parse(entry.getValue());
                if (!(status.isSignatureOk= isValid(checksumWithSignature, globals.publicKey)) ) {
                    break;
                }
                sum = Checksum.Type.SHA256.getChecksum(fis);
            }
            else {
                sum = entry.getKey().getChecksum(fis);
            }
            if (sum.equals(entry.getValue())) {
                log.info("Snippet {}, checksum is Ok", snippetCode);
            } else {
                log.error("Snippet {}, checksum is wrong, expected: {}, actual: {}", snippetCode, entry.getValue(), sum);
                status.isOk = false;
                break;
            }
        }
    }

    public static ChecksumWithSignature parse(String data) {
        final int idx = data.indexOf(SecUtils.SIGN_DELIMITER);
        if (idx == -1) {
            throw new IllegalStateException("Wrong format of checksum with signature");
        }
        //noinspection UnnecessaryLocalVariable
        ChecksumWithSignature checksumWithSignature = new ChecksumWithSignature(data.substring(0, idx), data.substring(idx + SecUtils.SIGN_DELIMITER.length()));
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
