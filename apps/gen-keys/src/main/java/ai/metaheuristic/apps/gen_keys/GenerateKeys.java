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
package ai.metaheuristic.apps.gen_keys;

import ai.metaheuristic.commons.security.CreateKeys;
import org.apache.commons.codec.binary.Base64;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.security.*;

/**
 * No arguments: a 2048-bit RSA key pair.
 * {@code kek} (or {@code --kek}): a KEK for the Key Vault boot-unlock of the Dispatcher, see {@link KekUtils}.
 */
@SpringBootApplication
public class GenerateKeys implements CommandLineRunner {

    static void main(String[] args) {
        SpringApplication.run(GenerateKeys.class, args);
    }

    @Override
    public void run(String... args) throws GeneralSecurityException {
        if (KekUtils.isKekCommand(args)) {
            printKek();
            return;
        }
        CreateKeys myKeys = new CreateKeys(2048);

        String privateKey64 = myKeys.encodeBase64String(myKeys.getPrivateKey().getEncoded());
        String publicKey64 = Base64.encodeBase64String(myKeys.getPublicKey().getEncoded());
        System.out.println("Private key in base64 format:\n" + privateKey64 +"\n\n");
        System.out.println("Public key in base64 format:\n" + publicKey64);

        System.out.println("""


            !!! Phrases 'Private key in base64 format:' and 'Public key in base64 format' aren't parts of keys and must not be used or stored in file.
            """);

   }

    private static void printKek() {
        // new SecureRandom() is the platform CSPRNG; getInstanceStrong() can block on some Linux setups
        System.out.println("KEK in base64 format:\n" + KekUtils.generateKek(new SecureRandom()) + "\n\n");

        System.out.println("""
            How to make the management company's Key Vault unlock itself at Dispatcher start:

            1. Set an environment variable for the Dispatcher process, with the KEK above as its value.
               Variable name: MH_VAULT_KEK, unless application.properties sets
               mh.dispatcher.vault.boot-unlock.kek-env - then use the name given there.
            2. Restart the Dispatcher, so that the process sees the variable.
            3. Log in as the admin of the management company.
            4. Open Dispatcher -> Key Vault (left menu) and unlock the Vault with the master passphrase.
               A Vault without entries is fine: at start it is created empty and open with this passphrase.
            5. Press 'Auto-unlock at restart', enter the master passphrase, press 'Generate'.
               The Dispatcher saves the encrypted passphrase to vault-boot-unlock.txt in mh.home;
               nothing has to be added to application.properties.
            6. Restart the Dispatcher. From now on the Vault is unlocked at every start,
               and the log shows: 01.672.070 Key Vault of companyUniqueId=1 unlocked at Dispatcher start
               (or, while the Vault has no stored entry yet: 01.672.180 ... created an empty one and unlocked it)

            The same KEK must be in the environment at every start, and mh.home must survive redeploys -
            the file is the only copy. With a new KEK, repeat all steps: the old file no longer decrypts,
            so the Vault starts locked until step 6.

            !!! Phrase 'KEK in base64 format:' isn't a part of the key and must not be used or stored in file.
            """);
    }
}