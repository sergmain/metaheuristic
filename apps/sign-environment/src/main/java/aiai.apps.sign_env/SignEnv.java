/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.apps.sign_env;

import aiai.apps.commons.utils.SecUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

public class SignEnv  implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(SignEnv.class, args);
    }

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException {

        if (args.length<2) {
            System.out.println("PackageSnippet <file with env string> <private key file>");
            return;
        }

        File srcFile = new File(args[0]);
        if (!srcFile.exists()) {
            System.out.println("Source file with evnironment doesn't exist");
            return;
        }

        File privateKeyFile = new File(args[1]);
        if (!privateKeyFile.exists()) {
            System.out.println("Private key file wasn't found. File: " + args[1]);
            return;
        }
        String privateKeyStr = FileUtils.readFileToString(privateKeyFile);
        PrivateKey privateKey = SecUtils.getPrivateKey(privateKeyStr);


        // Process
        String env = FileUtils.readFileToString(srcFile, StandardCharsets.UTF_8);
        String signature = SecUtils.getSignature(env, privateKey, true);

        System.out.println("Env:\n" + env);
        System.out.println("\nSignature:\n" + signature);
    }
}
