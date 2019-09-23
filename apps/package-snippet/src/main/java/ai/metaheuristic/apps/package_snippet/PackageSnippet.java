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
package ai.metaheuristic.apps.package_snippet;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.SecUtils;
import ai.metaheuristic.commons.utils.SnippetCoreUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigList;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigListUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
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

        SnippetConfigList snippetConfigList = SnippetConfigListUtils.to(snippetYamlFile);

        // Verify
        boolean isError = false;
        Set<String> set = new HashSet<>();
        for (SnippetApiData.SnippetConfig snippet : snippetConfigList.getSnippets()) {
            final SnippetApiData.SnippetConfigStatus verify = SnippetCoreUtils.validate(snippet);
            if (!verify.isOk) {
                System.out.println(verify.error);
                isError=true;
            }
            if (snippet.sourcing==EnumsApi.SnippetSourcing.launchpad) {
                File sn = new File(snippetYamlFile.getParent(), snippet.file);
                if (!sn.exists()) {
                    System.out.println("File " + sn.getPath() + " wasn't found");
                    isError = true;
                }

                if (set.contains(snippet.code)) {
                    System.out.println("Found duplicate snippet: " + snippet.code);
                    isError = true;
                }
                set.add(snippet.code);
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
        for (SnippetApiData.SnippetConfig snippetConfig : snippetConfigList.getSnippets()) {
            String sum;
            if (snippetConfig.sourcing==EnumsApi.SnippetSourcing.station ||
                    snippetConfig.sourcing==EnumsApi.SnippetSourcing.git) {
                String s = SnippetCoreUtils.getDataForChecksumWhenGitSourcing(snippetConfig);
                sum = Checksum.getChecksum(EnumsApi.Type.SHA256, new ByteArrayInputStream(s.getBytes()));
            }
            else if (snippetConfig.sourcing==EnumsApi.SnippetSourcing.launchpad) {
                final File snippetFile = new File(targetDir, snippetConfig.file);
                FileUtils.copyFile(new File(snippetConfig.file), snippetFile);
                try (FileInputStream fis = new FileInputStream(snippetFile)) {
                    sum = Checksum.getChecksum(EnumsApi.Type.SHA256, fis);
                }
                snippetConfig.info.length = snippetFile.length();
            }
            else {
                throw new IllegalArgumentException();
            }

            snippetConfig.checksumMap = new HashMap<>();
            if (privateKey!=null) {
                String signature = SecUtils.getSignature(sum, privateKey);
                snippetConfig.checksumMap.put(EnumsApi.Type.SHA256WithSignature, sum + SecUtils.SIGNATURE_DELIMITER + signature);
                snippetConfig.info.signed = true;
            }
            else {
                snippetConfig.checksumMap.put(EnumsApi.Type.SHA256, sum);
                snippetConfig.info.signed = false;
            }
        }

        String yaml = SnippetConfigListUtils.toString(snippetConfigList);
        final File file = new File(targetDir, SNIPPETS_YAML);
        FileUtils.writeStringToFile(file, yaml, StandardCharsets.UTF_8);

        ZipUtils.createZip(targetDir, targetZip);
        FileUtils.deleteDirectory(targetDir);

        System.out.println("All done.");
    }
}
