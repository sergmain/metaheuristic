/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
package ai.metaheuristic.apps.gen_keys;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.security.*;

@SpringBootApplication
public class GenerateKeys implements CommandLineRunner {

    public static class Keys {
        private KeyPairGenerator keyGen;
        private KeyPair pair;
        private PrivateKey privateKey;
        private PublicKey publicKey;

        Keys(int keylength) throws NoSuchAlgorithmException {
            this.keyGen = KeyPairGenerator.getInstance("RSA");
            this.keyGen.initialize(keylength);
        }

        void createKeys() {
            this.pair = this.keyGen.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
        }

        PrivateKey getPrivateKey() {
            return this.privateKey;
        }

        PublicKey getPublicKey() {
            return this.publicKey;
        }

        String encodeBase64String(final byte[] binaryData) {
            return StringUtils.newStringUsAscii(Base64.encodeBase64(binaryData, true));
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(GenerateKeys.class, args);
    }

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException {
        Keys myKeys = new Keys(2048);
        myKeys.createKeys();

        String privateKey64 = myKeys.encodeBase64String(myKeys.getPrivateKey().getEncoded());
        String publicKey64 = Base64.encodeBase64String(myKeys.getPublicKey().getEncoded());
        System.out.println("Private key in base64 format:\n" + privateKey64 +"\n\n");
        System.out.println("Public key in base64 format:\n" + publicKey64);

        System.out.println("\n\n\n\n!!! Phrases 'Private key in base64 format:' and 'Public key in base64 format' " +
                "aren't parts of keys and must not be used or stored in file.");

   }
}