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
package ai.metaheuristic.apps.sign_string;

import ai.metaheuristic.commons.utils.SecUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.List;

public class SignString implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(SignString.class, args);
    }

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException {

        if (args.length<2) {
            System.out.println("SignString <file with string> <private key file>");
            System.out.println("Only first line in the file will be used.");
            System.out.println("Leading spaces, LR and CR will be trimmed.");
            return;
        }

        Path srcFile = Path.of(args[0]);
        if (Files.notExists(srcFile)) {
            System.out.println(String.format("Source file %s doesn't exist ", srcFile.getFileSystem()));
            return;
        }

        Path privateKeyFile = Path.of(args[1]);
        if (Files.notExists(privateKeyFile)) {
            System.out.println("Private key file wasn't found. File: " + args[1]);
            return;
        }
        String privateKeyStr = Files.readString(privateKeyFile, StandardCharsets.UTF_8);
        PrivateKey privateKey = SecUtils.getPrivateKey(privateKeyStr);


        // Process
        List<String> strings = Files.readAllLines(srcFile, StandardCharsets.UTF_8);
        if (strings.isEmpty()) {
            System.out.println(String.format("File %s is empty", srcFile));
            return;
        }
        String strForSigning = StringUtils.trim(strings.get(0));
        if (StringUtils.isBlank(strForSigning)) {
            System.out.println(String.format("First string in file %s is empty", srcFile));
            return;
        }
        String signature = SecUtils.getSignature(strForSigning, privateKey, true);

        System.out.println("String for signing:\n" + strForSigning);
        System.out.println("\nSignature:\n" + signature);
    }
}
