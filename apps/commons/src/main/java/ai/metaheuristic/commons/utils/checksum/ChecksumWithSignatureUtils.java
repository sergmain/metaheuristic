/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
package ai.metaheuristic.commons.utils.checksum;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.SecUtils;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Map;

@Slf4j
public class ChecksumWithSignatureUtils {

    @AllArgsConstructor
    public static class ChecksumWithSignature {
        public String checksum;
        public String signature;
    }

    public static CheckSumAndSignatureStatus verifyChecksumAndSignature(@NonNull Checksum checksum, String infoPrefix, InputStream fis, boolean isVerifySignature, PublicKey publicKey ) throws IOException {
        CheckSumAndSignatureStatus status = new CheckSumAndSignatureStatus();
        for (Map.Entry<EnumsApi.HashAlgo, String> entry : checksum.checksums.entrySet()) {
            String sum, entrySum;
            if (entry.getKey()== EnumsApi.HashAlgo.SHA256WithSignature) {
                ChecksumWithSignature checksumWithSignature = parse(entry.getValue());
                entrySum = checksumWithSignature.checksum;

                if (isVerifySignature) {
                    status.signature = isValid(checksumWithSignature.checksum.getBytes(), checksumWithSignature.signature, publicKey);
                    if (status.signature== CheckSumAndSignatureStatus.Status.wrong) {
                        log.warn("{}, validation was failed, checksum: {}, signature: {}, publicKey: {}", infoPrefix, checksumWithSignature.checksum, checksumWithSignature.signature, publicKey);
                        break;
                    }
                    log.info("{}, signature is Ok", infoPrefix);
                }
                sum = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, fis);
            }
            else {
                if (isVerifySignature) {
                    log.warn("{}, can't validate signature with checksum type {}", infoPrefix, entry.getKey());
                    break;
                }
                sum = Checksum.getChecksum(entry.getKey(), fis);
                entrySum = entry.getValue();
            }

            if (sum.equals(entrySum)) {
                status.checksum = CheckSumAndSignatureStatus.Status.correct;
                log.info("{}, checksum is Ok", infoPrefix);
            } else {
                log.error("S{}, checksum is wrong, expected: {}, actual: {}", infoPrefix, entrySum, sum);
                status.checksum = CheckSumAndSignatureStatus.Status.wrong;
                break;
            }
        }
        if (status.signature== CheckSumAndSignatureStatus.Status.wrong) {
            log.error("{}, Signature is wrong", infoPrefix);
        }
        return status;
    }

    public static ChecksumWithSignature parse(String data) {
        final int idx = data.indexOf(SecUtils.SIGNATURE_DELIMITER);
        if (idx == -1) {
            throw new IllegalStateException("Wrong format of checksum with signature");
        }
        //noinspection UnnecessaryLocalVariable
        ChecksumWithSignature checksumWithSignature = new ChecksumWithSignature(data.substring(0, idx), data.substring(idx + SecUtils.SIGNATURE_DELIMITER.length()));
        return checksumWithSignature;
    }

    public static CheckSumAndSignatureStatus.Status isValid(byte[] data, String signatureAsBase64, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data);
            //noinspection
            final byte[] bytes = Base64.decodeBase64(signatureAsBase64);
            boolean status = signature.verify(bytes);
            return status ? CheckSumAndSignatureStatus.Status.correct : CheckSumAndSignatureStatus.Status.wrong;
        }
        catch (GeneralSecurityException e) {
            log.error("Error checking signature", e);
            throw new RuntimeException("Error", e);
        }
    }
}
