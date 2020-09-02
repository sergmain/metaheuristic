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

package ai.metaheuristic.apps.package_function;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.SecUtils;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.PublicKey;

/**
 * @author Serge
 * Date: 10/2/2019
 * Time: 9:07 PM
 */
// TODO 2020-04-19 do we need this test still?
public class VerifySignature {

    public static final String checksumStr = "cc4c932c51885be27c052001eb0456d302d3baa11704510674484c0fe34d91d9";
    public static final String signatureStr = "ULKcyJaZCmn9Lt8Vwtu1YxMP3JKudIuN2mdFGxVnWodmXxTqfAy9tkOeZ8oYrRdmVDEstJKY+XeiWVaxdR+XyxOBHufWsMnFz16z47IAOPWT6EBc24gY65hhw0XJ6ScPD6y9BpOdouYB6p/dl+h04VpkVkolItQtiBwh6yxeTe7JR3Bv5adh6kwq6vFKXzdQKC6GGxh5t5p7DQJum2xRZ4zefz8RfU9MNbV1cO+FKkx56aqCBqOLgNPVK8P7LU6i75woygVqTgdSC7uCZsC3tiH2uYEQlqJx+yyK+K8gCG6O7yAeqyW2C8SLpA4pJ6M2WlI+c5i8pJbM4LG/xAuirA==";
    public static final String originPubKey =    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqIgGAaJ6NhVI4JlSZDt6l/FKfu/PNu34pU7AvfL9n7tmLF/gd2GPMxinMfKsnfH6yb4neUFfIeEJR5Q9Fqf4l3uqjoWNiuAuaHgVPPgIAnXjltblCugDVj+0Bf2zUHpIMdYOvEWZO1l76R+l0iFtSX+cQyfYIzD44oTHRX1lTNkgU+qFJaz26t9aucVrGI+wyqcalpmTObxQjwnlAv8mZURGmnQj/QVzxuFimSluOOnm8OD5tDJU13Af+QWgoHmxILEhbjgqrUyoRHaN/nP4dxWxuL5PVNPBo+X1Z3TpTf4hZ1zE5x3OwXRfpqLf762dxKE4LjqPnoodRVitg3+PzwIDAQAB";
    public static final String currPubKey =    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqIgGAaJ6NhVI4JlSZDt6l/FKfu/PNu34pU7AvfL9n7tmLF/gd2GPMxinMfKsnfH6yb4neUFfIeEJR5Q9Fqf4l3uqjoWNiuAuaHgVPPgIAnXjltblCugDVj+0Bf2zUHpIMdYOvEWZO1l76R+l0iFtSX+cQyfYIzD44oTHRX1lTNkgU+qFJaz26t9aucVrGI+wyqcalpmTObxQjwnlAv8mZURGmnQj/QVzxuFimSluOOnm8OD5tDJU13Af+QWgoHmxILEhbjgqrUyoRHaN/nP4dxWxuL5PVNPBo+X1Z3TpTf4hZ1zE5x3OwXRfpqLf762dxKE4LjqPnoodRVitg3+PzwIDAQAB";

    public static final String pubKey =          "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtS3jRjE1wlHcxiqn6fCRvTahRt6LBvhrqxzgo1FcpJ9uZvRUmf3KwszwQoL+Ypw7aM9oxmg15Q+pssKcrulS/ofDfbuusiYdny7wMlil1H11svQM3yGwMl9gjZ2FupaRwpyZkIMj1ILaDhylTudQCBoJgJ/BWyMCDn2kzh5EpV7hkhhfjZ/2/NRIcayQVmMKOikCXR8q1bb3QNQ2HiMyUsBUGzeO2DuvX4n375+SaFIDrse4eGNVbR/ImWw7TeD4wk0h5kJ2VTdgl2J7gVS7gCCMwBN9TVxPErRDxg/OtXreS8VRUd0hOZiadX12KjwI4mjhC4q+geXAq2sC1DOV8wIDAQAB";

    // for jar file
//    public static final String checksumAndSignatureStr = checksumStr + SecUtils.SIGNATURE_DELIMITER + signatureStr;

    // for xml file
    public static final String checksumAndSignatureStr = "ecbc2bddd1db7853db3eabb0912cf4cf36e5f8129ef3cac80458b8600cadf215###PT7VW4jUYnjVJ2GKd8slyhJSp6oeEignEVqoYHAuLG86IDzwOg0VDjsPRcyWZUDCZA3a8FLlUkZLSeGKcq2vB3pyEmM7NL9Ucfs1/XW+ZgkYs6WMCTMfbOCZloijWxGEr2eCQAVEO5E6gxNoL8eEKt6krby0w3xpgnpC5tvdZ49H9NooFOQFfN6s5fD2pVb/nnky8IwyCGBHHZpjfiuHtredMGIZWLhiFDas63JFKr36/vvGVtUmTIXqA+XLAxdLe8tmGT/LPlmnmV0AZcb3shpoUHd0bCjbWI1SEB4cuAWpKOjZqZc1+Pw83MtN6rdvXT3YczSQhXIXWSIrEr9h5Q==";

    public static final String checksum = "a46856f74d261889d93fbd0af0377bb7fcc6058ebf057a89d2a7f84352afb760";
    public static final String signature = "b4i9XsRtVbvKvtSkfJv7Eaf5ALVVt5wzl72pc25H+P5dQr2pfNpc1HItY12G6akJrIzX6o+oeXWlZiPIA5mqHl1V4D69gHqieTtb+U/3o5gUQdhqutZguPmD7uoLt21ootyYjm91RTZPULVSjiYctaSbYELGDYc3d86VLBDX9A+PfEsIB+6gxUWyaDEhuqzEKD0r1SXhcIYZc+it5q8E/mQqS7BdvzEsFYJcaON47row9bPWFHkoWf5/vdO61BIhwOZpWFTNaRJSnZ5JySJk4nHJtbvM2px+yQWJYKVIxvdetX5xqOHokRUavFkGVRFg2uCAfonUiXOk8EjIRN6zsg==";


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

        PublicKey publicKey = SecUtils.getPublicKey(currPubKey);
        Checksum checksum = new Checksum();
        checksum.checksums.put(EnumsApi.HashAlgo.SHA256WithSignature, checksumAndSignatureStr);
//        checksum.checksums.put(EnumsApi.HashAlgo.SHA256WithSignature, checksum + SecUtils.SIGNATURE_DELIMITER + signature);
        CheckSumAndSignatureStatus status;
        try (FileInputStream fis = new FileInputStream(new File(args[0]))) {
            status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(checksum, "info:", fis, true, publicKey);
        }
        System.out.println(status);
    }
}
