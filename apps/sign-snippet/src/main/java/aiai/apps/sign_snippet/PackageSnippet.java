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
package aiai.apps.sign_snippet;

import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.utils.SecUtils;
import aiai.apps.commons.yaml.snippet.SnippetType;
import aiai.apps.commons.yaml.snippet.SnippetsConfig;
import aiai.apps.commons.yaml.snippet.SnippetsConfigUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.util.ArrayList;
import java.util.HashMap;

@SpringBootApplication
public class PackageSnippet implements CommandLineRunner {
    public static void main(String[] args) {
            SpringApplication.run(PackageSnippet.class, args);
        }

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException {

        String privateKeyStr = FileUtils.readFileToString( new File(args[0]) );
        PrivateKey privateKey = SecUtils.getPrivateKey(privateKeyStr);

        final File file = new File(args[1]);
        String sum;
        try(FileInputStream fis = new FileInputStream(file)) {
            sum = Checksum.Type.SHA256.getChecksum(fis);
        }

        String signature = SecUtils.getSignature(sum, privateKey);

        SnippetsConfig snippets = new SnippetsConfig();
        SnippetsConfig.SnippetConfig config = new SnippetsConfig.SnippetConfig();
        config.checksums = new HashMap<>();
        config.checksums.put(Checksum.Type.SHA256WithSign, sum + '=' + signature);
        config.file = file.getName();
        config.name = args[2];
        config.version = args[3];
        config.type = SnippetType.valueOf(args[4]);
        config.env = args[5];
        snippets.snippets = new ArrayList<>();
        snippets.snippets.add(config);

        String yaml = SnippetsConfigUtils.toString(snippets);

        System.out.println("File: " + file.getPath());
        System.out.println("Yaml config: "+ "\n" + yaml);
        System.out.println();

        SnippetsConfig cs = SnippetsConfigUtils.to(yaml);
        System.out.println(cs);

    }

}
