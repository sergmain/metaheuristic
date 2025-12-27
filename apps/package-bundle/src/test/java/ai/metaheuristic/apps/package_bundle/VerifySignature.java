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

package ai.metaheuristic.apps.package_bundle;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.SecUtils;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;

/**
 * @author Serge
 * Date: 10/2/2019
 * Time: 9:07 PM
 */
// TODO 2020-04-19 do we need this test still?
public class VerifySignature {

    public static final String checksum = "a46856f74d261889d93fbd0af0377bb7fcc6058ebf057a89d2a7f84352afb760";
    public static final String signature = "bGDyVZQG6M5LSC55RNHTiH5QPf2yqRa6S0xj5XXPv5S5KNDeo2l8iTjX+Hr6F7AsHPAzssmtfgCdMRx61wWT3ODrFCUa5a7JPDrT1+GRvM6LSq/fYXT3kk+yPjIE5iJjFOFa/ynTYph3XagPrG4O1tVUv/f4nOszhysM2n5QAwsbNBIQuxVI/nwlbxCcEpi8VC/43hGuda0lKC1w4lZIaXtPVQZJs/s/WDLZyzpvzDfdW4AmcH93nulBQRYj4i47ThY4zEVmcHyQrsSGvaZjLKbaDj4Wpvl6orxQStWH0NKDLOeiwsWj9qblQZzNh2/7YdiPPYyL0ETOM2JejodApA==";
    // for jar file
//    public static final String checksumAndSignatureStr = checksumStr + SecUtils.SIGNATURE_DELIMITER + signatureStr;


    public static final String pubKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiEOUe8E8p9Xin0ikGzwFYc7NJK25n8K6LcLivIjs+CPBkgcOMsyncIDKmBqw8GYyhUl4I6KhE7TQSdedgul9a0B9/rfgX49b+nS0U7ObIK2sdJDzHh32ne7moX34L3zCPPciJ8M7GYQuCjrTKaRB8RUaG5nKYk9wHnzSm53Pq5nsmIvGsHBsx901OXpdDpkB2VB2Hsgu8vMxNvtprAD4x9z+QR01jG94z7JIN1zROZ0xS6uFF9IjxzsXNudRaELobRMfhqfyYMB8c3VNqsJ/vjlG6m7uMrinHnqvSjBPffueAay19J06P6IpEJ1LqeQdF8fygL5SnspjusnY60QzZwIDAQAB";

    // for xml file
    public static final String checksumAndSignature = "a46856f74d261889d93fbd0af0377bb7fcc6058ebf057a89d2a7f84352afb760###bGDyVZQG6M5LSC55RNHTiH5QPf2yqRa6S0xj5XXPv5S5KNDeo2l8iTjX+Hr6F7AsHPAzssmtfgCdMRx61wWT3ODrFCUa5a7JPDrT1+GRvM6LSq/fYXT3kk+yPjIE5iJjFOFa/ynTYph3XagPrG4O1tVUv/f4nOszhysM2n5QAwsbNBIQuxVI/nwlbxCcEpi8VC/43hGuda0lKC1w4lZIaXtPVQZJs/s/WDLZyzpvzDfdW4AmcH93nulBQRYj4i47ThY4zEVmcHyQrsSGvaZjLKbaDj4Wpvl6orxQStWH0NKDLOeiwsWj9qblQZzNh2/7YdiPPYyL0ETOM2JejodApA==";


    public static void main(String[] args) throws IOException {
        if (args.length==0) {
            System.out.println("Need path to file");
            return;
        }
//        if (!pubKey.equals(pubKey06)) {
//            System.out.println("Public key is wrong");
//        }
//        if (!currPubKey.equals(pubKey06)) {
//            System.out.println("Public key is wrong");
//        }
//
//        if (!pubKey.equals(originPubKeyOld)) {
//            System.out.println("Public key is wrong");
//        }


        ChecksumAndSignatureData.ChecksumWithSignature cws = ChecksumWithSignatureUtils.parse(checksumAndSignature);
        System.out.println(checksum.equals(cws.checksum));
        System.out.println(signature.equals(cws.signature));

        PublicKey publicKey = SecUtils.getPublicKey(pubKey);
        Checksum cs = new Checksum();
        cs.checksums.put(EnumsApi.HashAlgo.SHA256WithSignature, checksumAndSignature);

        EnumsApi.SignatureState status = ChecksumWithSignatureUtils.isValid(EnumsApi.HashAlgo.SHA256WithSignature.signatureAlgo, checksum.getBytes(), signature, publicKey);
        System.out.println(status);

        try (InputStream fis = Files.newInputStream(Path.of(args[0]))) {
            CheckSumAndSignatureStatus s = ChecksumWithSignatureUtils.verifyChecksumAndSignature(cs, "info:", fis, publicKey);
            System.out.println(s);
        }
    }
}
