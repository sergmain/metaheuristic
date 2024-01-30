/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.SecUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.springframework.lang.Nullable;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Map;

import static ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData.*;

@Slf4j
public class ChecksumWithSignatureUtils {

    private static final CheckSumAndSignatureStatus CHECK_SUM_AND_SIGNATURE_NOT_PRESENTED = new CheckSumAndSignatureStatus(EnumsApi.ChecksumState.not_presented, EnumsApi.SignatureState.not_presented);
    private static final CheckSumAndSignatureStatus CHECK_SUM_AND_SIGNATURE_WRONG = new CheckSumAndSignatureStatus(EnumsApi.ChecksumState.wrong, EnumsApi.SignatureState.wrong);

    public static CheckSumAndSignatureStatus verifyChecksumAndSignature(Checksum checksum, String infoPrefix, InputStream fis, PublicKey publicKey ) {
        CheckSumAndSignatureStatus status = new CheckSumAndSignatureStatus();
        for (Map.Entry<EnumsApi.HashAlgo, String> entry : checksum.checksums.entrySet()) {
            status = verifyChecksumAndSignature(infoPrefix, fis, publicKey, entry.getValue(), entry.getKey());
            if (status.checksum== EnumsApi.ChecksumState.wrong && status.signature== EnumsApi.SignatureState.wrong) {
                return status;
            }
        }
        return status;
    }

    public static CheckSumAndSignatureStatus verifyChecksumAndSignature(String infoPrefix, InputStream fis, @Nullable PublicKey publicKey, String value, EnumsApi.HashAlgo hashAlgo) {
        CheckSumAndSignatureStatus status = verifyChecksumAndSignatureInternal(infoPrefix, fis, publicKey, value, hashAlgo);
        log.info("{}, signature is {}", infoPrefix, status.signature);
        return status;
    }

    private static CheckSumAndSignatureStatus verifyChecksumAndSignatureInternal(String infoPrefix, InputStream fis, @Nullable PublicKey publicKey, String value, EnumsApi.HashAlgo hashAlgo) {
        ChecksumWithSignature checksumWithSignature = parse(value);
        // there isn't a signature without a checksum
        if (checksumWithSignature.checksum==null) {
            return CHECK_SUM_AND_SIGNATURE_NOT_PRESENTED;
        }

        String actualSum = Checksum.getChecksum(hashAlgo, fis);

        if (!actualSum.equals(checksumWithSignature.checksum)) {
            log.error("{}, checksum is wrong, expected: {}, actual: {}", infoPrefix, checksumWithSignature.checksum, actualSum);
            return CHECK_SUM_AND_SIGNATURE_WRONG;
        }

        EnumsApi.ChecksumState checksumState = EnumsApi.ChecksumState.correct;
        log.info("{}, checksum is correct", infoPrefix);

        if (!hashAlgo.isSigned || S.b(hashAlgo.signatureAlgo) || S.b(checksumWithSignature.signature)) {
            return new CheckSumAndSignatureStatus(checksumState, EnumsApi.SignatureState.not_presented);
        }

        EnumsApi.SignatureState signatureState = isValid(hashAlgo.signatureAlgo, checksumWithSignature.checksum.getBytes(), checksumWithSignature.signature, publicKey);
        return new CheckSumAndSignatureStatus(checksumState, signatureState);

    }

    public static ChecksumWithSignature parse(@Nullable String data) {
        if (S.b(data)) {
            return new ChecksumWithSignature();
        }
        final int idx = data.indexOf(SecUtils.SIGNATURE_DELIMITER);
        if (idx == -1) {
            return new ChecksumWithSignature(data, null);
        }
        ChecksumWithSignature checksumWithSignature = new ChecksumWithSignature(data.substring(0, idx), data.substring(idx + SecUtils.SIGNATURE_DELIMITER.length()));
        return checksumWithSignature;
    }

    public static EnumsApi.SignatureState isValid(String signatureAlgo, byte[] data, String signatureAsBase64, @Nullable PublicKey publicKey) {
        if (publicKey==null) {
            return EnumsApi.SignatureState.correct;
        }
        try {
            Signature signature = Signature.getInstance(signatureAlgo);
            signature.initVerify(publicKey);
            signature.update(data);
            final byte[] bytes = Base64.decodeBase64(StringUtils.getBytesUsAscii(signatureAsBase64));
            boolean status = signature.verify(bytes);
            return status ? EnumsApi.SignatureState.correct : EnumsApi.SignatureState.wrong;
        }
        catch (GeneralSecurityException e) {
            log.error("Error checking signature", e);
            throw new RuntimeException("Error", e);
        }
    }
}
