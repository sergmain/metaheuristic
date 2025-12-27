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
package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.security.CreateKeys;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Map;
import java.util.Random;

import static ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData.ChecksumWithSignature;
import static org.junit.jupiter.api.Assertions.*;

public class TestChecksumWithSignature {

    private static final String CONTENT_1 = Long.toString(System.currentTimeMillis());
    private static final String CONTENT_2 = CONTENT_1 + "1234";

    public static final Random r = new Random();

    @SuppressWarnings("SameParameterValue")
    private static byte[] createBytes(int size) {
        byte[] bytes = new byte[size];
        r.nextBytes(bytes);
        return bytes;
    }

    @Test
    public void testEncode() throws GeneralSecurityException {
        CreateKeys keys = new CreateKeys(2048);

        byte[] bytes = createBytes(30_000_000);
        String sum = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256WithSignature, new ByteArrayInputStream(bytes));
        String signature = SecUtils.getSignature(sum, keys.getPrivateKey());

        // ###idea### why?
        EnumsApi.SignatureState status = ChecksumWithSignatureUtils.isValid(EnumsApi.HashAlgo.SHA256WithSignature.signatureAlgo, sum.getBytes(), signature, keys.getPublicKey());
        System.out.println(status);

        assertEquals(EnumsApi.SignatureState.correct, status);


        Signature signer= Signature.getInstance("SHA256withRSA");
        signer.initSign(keys.getPrivateKey());
        signer.update(CONTENT_1.getBytes(StandardCharsets.UTF_8));
        byte[] sign = signer.sign();
        byte[] byte64 = Base64.encodeBase64(sign, false);
        String base1 = StringUtils.newStringUsAscii(byte64);
        final byte[] decoded1 = Base64.decodeBase64(StringUtils.getBytesUsAscii(base1));

        assertArrayEquals(sign, decoded1);

    }

    @Test
    public void test() throws GeneralSecurityException, IOException {
        CreateKeys keys = new CreateKeys(2048);

        String checksum1 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256WithSignature, CONTENT_1);
        String signature1 = SecUtils.getSignature(checksum1, keys.getPrivateKey());
        String forVerifying1 = checksum1 + SecUtils.SIGNATURE_DELIMITER + signature1;

        ChecksumWithSignature checksumWithSignature1 = ChecksumWithSignatureUtils.parse(forVerifying1);
        assertNotNull(checksumWithSignature1.checksum);
        assertEquals(checksum1, checksumWithSignature1.checksum);
        assertNotNull(checksumWithSignature1.signature);
        assertEquals(signature1, checksumWithSignature1.signature);

        // ###idea### why?
        assertEquals(EnumsApi.SignatureState.correct,
                ChecksumWithSignatureUtils.isValid(
                        EnumsApi.HashAlgo.SHA256WithSignature.signatureAlgo, checksumWithSignature1.checksum.getBytes(), checksumWithSignature1.signature, keys.getPublicKey()));


        String checksum2 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, CONTENT_2);
        String signature2 = SecUtils.getSignature(checksum2, keys.getPrivateKey());
        String forVerifying2 = checksum2 + SecUtils.SIGNATURE_DELIMITER + signature2;

        ChecksumWithSignature checksumWithSignature2 = ChecksumWithSignatureUtils.parse(forVerifying2);
        assertNotNull(checksumWithSignature2.checksum);
        assertEquals(checksum2, checksumWithSignature2.checksum);
        assertNotNull(checksumWithSignature2.signature);
        assertEquals(signature2, checksumWithSignature2.signature);

        assertEquals(EnumsApi.SignatureState.correct,
                ChecksumWithSignatureUtils.isValid(
                        EnumsApi.HashAlgo.SHA256WithSignature.signatureAlgo, checksumWithSignature2.checksum.getBytes(), checksumWithSignature2.signature, keys.getPublicKey()));

        assertEquals(EnumsApi.SignatureState.wrong,
                ChecksumWithSignatureUtils.isValid(
                        EnumsApi.HashAlgo.SHA256WithSignature.signatureAlgo, checksumWithSignature1.checksum.getBytes(), checksumWithSignature2.signature, keys.getPublicKey()));

        assertEquals(EnumsApi.SignatureState.wrong,
                ChecksumWithSignatureUtils.isValid(
                        EnumsApi.HashAlgo.SHA256WithSignature.signatureAlgo, checksumWithSignature2.checksum.getBytes(), checksumWithSignature1.signature, keys.getPublicKey()));

        String signature = SecUtils.getSignature(checksum1, keys.getPrivateKey());

        String checksumWithSignature = checksum1 + SecUtils.SIGNATURE_DELIMITER + signature;

        InputStream is = new ByteArrayInputStream(CONTENT_1.getBytes());
        CheckSumAndSignatureStatus status = ChecksumWithSignatureUtils.verifyChecksumAndSignature("info:", is, keys.getPublicKey(), checksumWithSignature, EnumsApi.HashAlgo.SHA256WithSignature);
        System.out.println(status);
        assertEquals(EnumsApi.ChecksumState.correct, status.checksum);
        assertEquals(EnumsApi.SignatureState.correct, status.signature);


        is = new ByteArrayInputStream(CONTENT_1.getBytes());
        Checksum checksum = new Checksum();
        checksum.checksums = Map.of(EnumsApi.HashAlgo.SHA256WithSignature, checksumWithSignature);
        status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(checksum, "info:", is, keys.getPublicKey());
        assertEquals(EnumsApi.ChecksumState.correct, status.checksum);
        assertEquals(EnumsApi.SignatureState.correct, status.signature);
    }


}
