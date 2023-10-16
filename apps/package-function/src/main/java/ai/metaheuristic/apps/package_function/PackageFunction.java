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

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.ExitApplicationException;
import ai.metaheuristic.commons.utils.*;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYaml;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.SneakyThrows;
import org.apache.commons.cli.*;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class PackageFunction implements CommandLineRunner {

    private static final String FUNCTION_YAML = "function.yaml";
    private static final String ZIP_EXTENSION = ".zip";
    private static final String BUNDLE_CFG_YAML = "bundle-cfg.yaml";

    private final ApplicationContext appCtx;

    public PackageFunction(ApplicationContext appCtx) {
        this.appCtx = appCtx;
    }

    public static void main(String[] args) {
            SpringApplication.run(PackageFunction.class, args);
        }

    public record Cfg(int version, @Nullable PrivateKey privateKey, Path workingDir, Path currDir,
                      BundleCfgYaml bundleCfg) {}

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException, ParseException {
        try {
            runInternal(args);
        } catch (ExitApplicationException e) {
            System.exit(SpringApplication.exit(appCtx, () -> -2));
        }
    }

    public void runInternal(String... args) throws IOException, GeneralSecurityException, ParseException {

        Cfg cfg = initPackaging(args);

        prepareFunctions(cfg);



/*
        File functionYamlFile = new File(FUNCTION_YAML);
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
*/

/*
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
*/

        System.out.println("All done.");
    }

    @SneakyThrows
    private static BundleCfgYaml initBundleCfg(Path currDir) {
        Path bundleCfgPath = currDir.resolve(BUNDLE_CFG_YAML);

        if (Files.notExists(bundleCfgPath)) {
            System.out.printf("File %s wasn't found in path %s\n", BUNDLE_CFG_YAML, currDir.toAbsolutePath());
            throw new ExitApplicationException();
        }

        String yaml = Files.readString(bundleCfgPath);
        BundleCfgYaml bundleCfgYaml = BundleCfgYamlUtils.UTILS.to(yaml);
        return bundleCfgYaml;
    }

    @SneakyThrows
    private static void prepareFunctions(Cfg cfg) {

        for (BundleCfgYaml.BundleConfig bundleConfig : cfg.bundleCfg.bundleConfig) {
            if (bundleConfig.type!= EnumsApi.BundleItemType.function) {
                continue;
            }
            Path p = cfg.currDir.resolve(bundleConfig.path);
            if (Files.notExists(p) || !Files.isDirectory(p)) {
                System.out.printf("Path %s is broken\n", p.toAbsolutePath());
                throw new ExitApplicationException();
            }
            Path tempFuncPath = cfg.workingDir.resolve(bundleConfig.path);
            System.out.println("\t\tprocess path " + tempFuncPath);
            Files.createDirectories(tempFuncPath);


            final FunctionConfigYaml fcy = getFunctionConfigYaml(p);
            createZip(cfg, tempFuncPath, fcy);


        }

    }

    @SneakyThrows
    private static void createZip(Cfg cfg, Path tempFuncPath, FunctionConfigYaml fcy) {
        Path zip = tempFuncPath.resolve(ArtifactCommonUtils.normalizeCode(fcy.function.code) + ZIP_EXTENSION);

        final List<Path> paths = new ArrayList<>();
        Files.walkFileTree(tempFuncPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
                if (Files.isDirectory(p)) {
                    paths.add(p);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        ZipUtils.createZip(paths, zip);
    }

    private static FunctionConfigYaml getFunctionConfigYaml(Path p) throws IOException {
        Path functionYaml = p.resolve(FUNCTION_YAML);
        if (Files.notExists(p)) {
            System.out.printf("File %s wasn't found in path %s\n", FUNCTION_YAML, p.toAbsolutePath());
            throw new ExitApplicationException();
        }
        String yaml = Files.readString(functionYaml);
        FunctionConfigYaml fcy = FunctionConfigYamlUtils.UTILS.to(yaml);
        return fcy;
    }

    private static Cfg initPackaging(String[] args) throws ParseException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        CommandLine cmd = parseArgs(args);
        String ver = cmd.getOptionValue("v");
        if (args.length==0 || S.b(ver)) {
            System.out.println("PackageFunction -v 2 [-key <private key file>]");
            throw new ExitApplicationException();
        }
        int version = Integer.parseInt(ver);
        PrivateKey privateKey = getPrivateKey(cmd);

        Path currDir = Path.of(SystemUtils.USER_DIR);
        Path tempDir = currDir.resolve("temp");
//        if (Files.exists(tempDir)) {
//            PathUtils.deleteDirectory(tempDir);
//        }
        Files.createDirectories(tempDir);
        Path workingDir = DirUtils.createTempPath(tempDir, "bundle-");
        if (workingDir==null) {
            System.out.println("Can't create temp directory in path " + tempDir);
            throw new ExitApplicationException();
        }

        BundleCfgYaml bundleCfgYaml = initBundleCfg(currDir);
        System.out.println("\tcurrDir dir: " + currDir);
        System.out.println("\tworking dir: " + workingDir);
        Cfg cfg = new Cfg(version, privateKey, workingDir, currDir, bundleCfgYaml);
        return cfg;
    }

    @Nullable
    private static PrivateKey getPrivateKey(CommandLine cmd) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        PrivateKey privateKey = null;
        if (cmd.hasOption("key")) {
            Path privateKeyFile = Path.of(cmd.getOptionValue("key"));
            if (Files.notExists(privateKeyFile)) {
                System.out.println("Private key file wasn't found. File: " + privateKeyFile);
                throw new ExitApplicationException();
            }
            String privateKeyStr = Files.readString(privateKeyFile, StandardCharsets.UTF_8);
            privateKey = SecUtils.getPrivateKey(privateKeyStr);
        }
        return privateKey;
    }

    public static CommandLine parseArgs(String... args) throws ParseException {
        Options options = new Options();
        Option versionOption = new Option("v", "version", true, "version of cli parameters");
        versionOption.setRequired(false);
        Option keyOption = new Option("key", "private-key", true, "Private key for signing content");
        keyOption.setRequired(false);
        options.addOption(versionOption);
        options.addOption(keyOption);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        return cmd;
    }
}
