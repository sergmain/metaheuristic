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
import aiai.apps.commons.utils.ZipUtils;
import aiai.apps.commons.yaml.snippet.SnippetsConfig;
import aiai.apps.commons.yaml.snippet.SnippetsConfigUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@SpringBootApplication
public class PackageSnippet implements CommandLineRunner {

    private static final String SNIPPETS_YAML = "snippets.yaml";
    private static final String ZIP_EXTENSION = ".zip";

    public static void main(String[] args) {
            SpringApplication.run(PackageSnippet.class, args);
        }

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException {

        if (args.length==0) {
            System.out.println("PackageSnippet <target zip file> [private key file]");
            return;
        }
        if (!args[0].endsWith(ZIP_EXTENSION)) {
            System.out.println("Zip file have to have .zip extension, actual: " + args[0]);
            return;
        }

        PrivateKey privateKey = null;
        if (args.length>1) {
            File privateKeyFile = new File(args[1]);
            if (!privateKeyFile.exists()) {
                System.out.println("Private key file wasn't found. File: " + args[1]);
                return;
            }
            String privateKeyStr = FileUtils.readFileToString(privateKeyFile, StandardCharsets.UTF_8);
            privateKey = SecUtils.getPrivateKey(privateKeyStr);
        }

        File snippetYamlFile = new File(SNIPPETS_YAML);
        if (!snippetYamlFile.exists()) {
            System.out.println("File "+snippetYamlFile.getPath()+" wasn't found");
            return;
        }

        File targetZip = new File(args[0]);
        if (targetZip.exists()) {
            System.out.println("File "+targetZip.getPath()+" already exists");
            return;
        }

        String tempDirName = args[0].substring(0, args[0].length() - ZIP_EXTENSION.length());

        File targetDir = new File(tempDirName);
        if (targetDir.exists()) {
            System.out.println("Directory "+targetDir.getPath()+" already exists");
            return;
        }

        SnippetsConfig snippetsConfig = SnippetsConfigUtils.to(snippetYamlFile);

        // Verify
        boolean isError = false;
        Set<String> set = new HashSet<>();
        for (SnippetsConfig.SnippetConfig snippet : snippetsConfig.getSnippets()) {
            final SnippetsConfig.SnippetConfigStatus verify = snippet.verify();
            if (!verify.isOk) {
                System.out.println(verify.error);
                isError=true;
            }
            if (!snippet.fileProvided) {
                File sn = new File(snippetYamlFile.getParent(), snippet.file);
                if (!sn.exists()) {
                    System.out.println("File " + sn.getPath() + " wasn't found");
                    isError = true;
                }

                final String o = snippet.name + ':' + snippet.version;
                if (set.contains(o)) {
                    System.out.println("Found duplicate snippet: " + o);
                    isError = true;
                }
                set.add(o);
                File f = new File(snippet.file);
                if (!f.getPath().equals(snippet.file)) {
                    System.out.println("Relative path for snippet file isn't supported, file: " + snippet.file);
                    isError = true;
                }
            }
        }
        if (isError) {
            return;
        }

        // Process
        for (SnippetsConfig.SnippetConfig snippet : snippetsConfig.getSnippets()) {
            String sum;
            if (snippet.fileProvided) {
                sum = Checksum.Type.SHA256.getChecksum(new ByteArrayInputStream(snippet.env.getBytes()));
            }
            else {
                final File snippetFile = new File(targetDir, snippet.file);
                FileUtils.copyFile(new File(snippet.file), snippetFile);
                try (FileInputStream fis = new FileInputStream(snippetFile)) {
                    sum = Checksum.Type.SHA256.getChecksum(fis);
                }
            }
            snippet.checksums = new HashMap<>();
            if (privateKey!=null) {
                String signature = SecUtils.getSignature(sum, privateKey);
                snippet.checksums.put(Checksum.Type.SHA256WithSignature, sum + SecUtils.SIGNATURE_DELIMITER + signature);
            }
            else {
                snippet.checksums.put(Checksum.Type.SHA256, sum);
            }
        }

        String yaml = SnippetsConfigUtils.toString(snippetsConfig);
        final File file = new File(targetDir, SNIPPETS_YAML);
        FileUtils.writeStringToFile(file, yaml, StandardCharsets.UTF_8);

        ZipUtils.createZip(targetDir, targetZip);
        FileUtils.deleteDirectory(targetDir);

        System.out.println("All done.");
    }
}
