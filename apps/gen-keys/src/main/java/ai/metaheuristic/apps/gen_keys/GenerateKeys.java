/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.commons.security.CreateKeys;
import org.apache.commons.codec.binary.Base64;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.security.*;

@SpringBootApplication
public class GenerateKeys implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(GenerateKeys.class, args);
    }

    @Override
    public void run(String... args) throws GeneralSecurityException {
        CreateKeys myKeys = new CreateKeys(2048);

        String privateKey64 = myKeys.encodeBase64String(myKeys.getPrivateKey().getEncoded());
        String publicKey64 = Base64.encodeBase64String(myKeys.getPublicKey().getEncoded());
        System.out.println("Private key in base64 format:\n" + privateKey64 +"\n\n");
        System.out.println("Public key in base64 format:\n" + publicKey64);

        System.out.println("""


            !!! Phrases 'Private key in base64 format:' and 'Public key in base64 format' aren't parts of keys and must not be used or stored in file.
            """);

   }
}