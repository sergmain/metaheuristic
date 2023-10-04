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
package ai.metaheuristic.apps.package_function;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.SecUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.bundle.BundleParamsYaml;
import ai.metaheuristic.commons.yaml.bundle.BundleParamsYamlUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.PathUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@SpringBootApplication
public class PackageFunction implements CommandLineRunner {

    private static final String FUNCTIONS_YAML = "functions.yaml";
    private static final String ZIP_EXTENSION = ".zip";

    public static void main(String[] args) {
            SpringApplication.run(PackageFunction.class, args);
        }

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException {

        if (args.length==0) {
            System.out.println("PackageFunction <target zip file> [private key file]");
            return;
        }
        if (!args[0].endsWith(ZIP_EXTENSION)) {
            System.out.println("Zip file have to have .zip extension, actual: " + args[0]);
            return;
        }

        PrivateKey privateKey = null;
        if (args.length>1) {
            Path privateKeyFile = Path.of(args[1]);
            if (Files.notExists(privateKeyFile)) {
                System.out.println("Private key file wasn't found. File: " + privateKeyFile);
                return;
            }
            String privateKeyStr = Files.readString(privateKeyFile, StandardCharsets.UTF_8);
            privateKey = SecUtils.getPrivateKey(privateKeyStr);
        }

        File functionYamlFile = new File(FUNCTIONS_YAML);
        if (!functionYamlFile.exists()) {
            System.out.println("File "+functionYamlFile.getPath()+" wasn't found");
            return;
        }

        File targetZip = new File(args[0]);
        if (targetZip.exists()) {
            System.out.println("File "+targetZip.getPath()+" already exists");
            return;
        }

        String tempDirName = args[0].substring(0, args[0].length() - ZIP_EXTENSION.length());

        Path targetDir = Path.of(tempDirName);
        if (Files.exists(targetDir)) {
            System.out.println("Directory "+targetDir+" already exists");
            return;
        }
        Files.createDirectories(targetDir);

        String yamlContent = FileUtils.readFileToString(functionYamlFile, StandardCharsets.UTF_8);
        BundleParamsYaml functionConfigList = BundleParamsYamlUtils.BASE_YAML_UTILS.to(yamlContent);

        // Verify
        boolean isError = false;
        Set<String> set = new HashSet<>();
        for (BundleParamsYaml.FunctionConfig function : functionConfigList.getFunctions()) {
            final FunctionApiData.FunctionConfigStatus verify = FunctionCoreUtils.validate(function);
            if (!verify.isOk) {
                System.out.println(verify.error);
                isError=true;
            }
            if (function.sourcing== EnumsApi.FunctionSourcing.dispatcher) {
                if (S.b(function.file)) {
                    System.out.println("function " + function.code + " has an empty 'file' field.");
                    isError = true;
                }
                File sn = new File(functionYamlFile.getParent(), function.file);
                if (!sn.exists()) {
                    System.out.println("File " + sn.getPath() + " wasn't found");
                    isError = true;
                }

                if (set.contains(function.code)) {
                    System.out.println("Found duplicate function: " + function.code);
                    isError = true;
                }
                set.add(function.code);
                File f = new File(function.file);
                if (!f.getPath().equals(function.file)) {
                    System.out.println("Relative path for function file isn't supported, file: " + function.file);
                    isError = true;
                }
            }
            else if (function.sourcing== EnumsApi.FunctionSourcing.processor) {
                // we don't need any checks here because all checks
                // have been made in ai.metaheuristic.commons.yaml.function_list.BundleParamsYaml.checkIntegrity
            }
        }
        if (isError) {
            return;
        }

        // Process
        for (BundleParamsYaml.FunctionConfig functionConfig : functionConfigList.getFunctions()) {
            String sum;
            if (functionConfig.sourcing== EnumsApi.FunctionSourcing.processor ||
                    functionConfig.sourcing== EnumsApi.FunctionSourcing.git) {
                String s = FunctionCoreUtils.getDataForChecksumForConfigOnly(functionConfig);
                sum = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, new ByteArrayInputStream(s.getBytes()));
            }
            else if (functionConfig.sourcing== EnumsApi.FunctionSourcing.dispatcher) {
                final Path functionFile = targetDir.resolve(functionConfig.file);
                Files.copy(Path.of(functionConfig.file), functionFile, StandardCopyOption.REPLACE_EXISTING);
                try (InputStream fis = Files.newInputStream(functionFile)) {
                    sum = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, fis);
                }
            }
            else {
                throw new IllegalArgumentException("unknown functionConfig.sourcing: " + functionConfig.sourcing);
            }

            functionConfig.checksumMap = new HashMap<>();
            if (privateKey!=null) {
                String signature = SecUtils.getSignature(sum, privateKey);
                functionConfig.checksumMap.put(EnumsApi.HashAlgo.SHA256WithSignature, sum + SecUtils.SIGNATURE_DELIMITER + signature);
            }
            else {
                functionConfig.checksumMap.put(EnumsApi.HashAlgo.SHA256, sum);
            }
        }

        String yaml = BundleParamsYamlUtils.BASE_YAML_UTILS.toString(functionConfigList);
        final Path file = targetDir.resolve(FUNCTIONS_YAML);
        Files.writeString(file, yaml, StandardCharsets.UTF_8);

        ZipUtils.createZip(targetDir, targetZip.toPath());
        PathUtils.deleteDirectory(targetDir);

        System.out.println("All done.");
    }
}
