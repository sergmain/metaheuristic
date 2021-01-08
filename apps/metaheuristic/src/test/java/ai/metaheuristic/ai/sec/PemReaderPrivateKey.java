/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.sec;

import org.apache.commons.io.FileUtils;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Serge
 * Date: 9/3/2020
 * Time: 10:59 PM
 */
public class PemReaderPrivateKey {

    public static void main(String[] args) throws Exception {
        if (args.length==0) {
            System.out.println("PemReaderPrivateKey <path-to-public-key>");
            return;
        }

        File privateKeyFile = new File(args[0]);
//        String privateKeyStr = FileUtils.readFileToString(privateKeyFile, StandardCharsets.UTF_8);
//        PrivateKey privateKey = getPrivateKeyFromString(privateKeyStr);
//        System.out.println(privateKey);
//
//        PublicKey publicKey = getPublicKey(args[0]);
//        System.out.println(publicKey);
    }

/*
    public static PublicKey getPublicKey(String fileName) throws Exception {
        FileReader reader = new FileReader(fileName);
        PemReader pemReader = new PemReader(reader);
        PemObject pemObj = pemReader.readPemObject();
        byte[] content = pemObj.getContent();
        pemReader.close();

        X509EncodedKeySpec spec = new X509EncodedKeySpec(content);
        KeyFactory kf = KeyFactory.getInstance("RSA", "BC");
        return kf.generatePublic(spec);
    }
*/

    /**
     * Generates Private Key from BASE64 encoded string
     */
/*
    @Nullable
    public static PrivateKey getPrivateKeyFromString(String keyString) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        try (PEMParser pemReader = new PEMParser(new StringReader(keyString))) {
            Object readObject = pemReader.readObject();
            if (readObject instanceof KeyPair) {
                KeyPair keyPair = (KeyPair)readObject;
                return keyPair.getPrivate();
            } else if (readObject instanceof PrivateKey) {
                return (PrivateKey)readObject;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new Exception("Cannot read private key", e);
        }
    }
*/
}
