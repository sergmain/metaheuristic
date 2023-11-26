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
package ai.metaheuristic.apps.package_bundle;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.GitData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.ExitApplicationException;
import ai.metaheuristic.commons.system.SystemProcessLauncher;
import ai.metaheuristic.commons.utils.*;
import ai.metaheuristic.commons.yaml.auth.ApiAuthUtils;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYaml;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.scheme.ApiSchemeUtils;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import lombok.SneakyThrows;
import org.apache.commons.cli.*;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.BundleItemType.*;
import static ai.metaheuristic.commons.CommonConsts.GIT_REPO;

@SpringBootApplication
public class PackageBundle implements CommandLineRunner {

    private final ApplicationContext appCtx;

    public PackageBundle(ApplicationContext appCtx) {
        this.appCtx = appCtx;
    }

    public static void main(String[] args) {
            SpringApplication.run(PackageBundle.class, args);
        }

    public static final class Cfg {
        public final int version;
        @Nullable
        public final PrivateKey privateKey;
        public Path workingDir;
        public Path baseDir;
        public Path currDir;
        public final BundleCfgYaml bundleCfg;
        @Nullable
        public final GitInfo gitInfo;

        public Cfg(int version, @Nullable PrivateKey privateKey, Path baseDir,
                   BundleCfgYaml bundleCfg, @Nullable GitInfo gitInfo) {
            this.version = version;
            this.privateKey = privateKey;
            this.baseDir = baseDir;
            this.bundleCfg = bundleCfg;
            this.gitInfo = gitInfo;
        }

        @SneakyThrows
        public void initOtherPaths() {
            Path tempDir = baseDir.resolve("bundles");
            Files.createDirectories(tempDir);
            Path workingDir = DirUtils.createTempPath(tempDir, "bundle-");
            if (workingDir==null) {
                throw new ExitApplicationException("Can't create temp directory in path " + tempDir);
            }
            this.workingDir = workingDir;

            if (gitInfo != null && !S.b(gitInfo.path)) {
                currDir = baseDir.resolve(gitInfo.path);
            }
            else {
                currDir = baseDir;
            }
        }
    }

    public record FunctionConfigAndFile(FunctionConfigYaml config, Path file) {}

    @Override
    public void run(String... args) throws IOException, GeneralSecurityException, ParseException {
        try {
            runInternal(args);
            System.out.println("All done.");
        } catch (ExitApplicationException e) {
            System.exit(SpringApplication.exit(appCtx, () -> -2));
        }
    }

    public static void runInternal(String... args) throws IOException, GeneralSecurityException, ParseException {
        Cfg cfg = initPackaging(args);

        if (cfg.gitInfo != null) {
            initRepo(cfg);
        }

        cfg.initOtherPaths();
        System.out.println("\tworking dir: " + cfg.workingDir);

        processFunctions(cfg);
        processCommonType(cfg, sourceCode, SourceCodeParamsYamlUtils.BASE_YAML_UTILS::to);
        processCommonType(cfg, auth, ApiAuthUtils.UTILS::to);
        processCommonType(cfg, api, ApiSchemeUtils.UTILS::to);

        createFinalZip(cfg);
    }

    @SneakyThrows
    private static void initRepo(Cfg cfg) {
        if (cfg.gitInfo==null) {
            throw new ExitApplicationException("cfg.gitInfo is null");
        }
        Path temp = cfg.baseDir.resolve("git-repo");
        if (Files.notExists(temp)) {
            Files.createDirectories(temp);
        }
        String gitCode = StrUtils.asCode(cfg.gitInfo.repo);
        Path gitDir = temp.resolve(gitCode);
        if (Files.notExists(gitDir)) {
            Files.createDirectories(gitDir);
        }

        GitData.GitContext gitContext = new GitData.GitContext(60L, 1000);

        SystemProcessLauncher.ExecResult result = GtiUtils.initGitRepository(cfg.gitInfo, gitDir, cfg.gitInfo.repo, gitContext, true);
        if (result!=null && !result.ok) {
            throw new ExitApplicationException("Error while cloning repo "+cfg.gitInfo.repo+", error: "+result.error);
        }

        cfg.baseDir = gitDir.resolve(GIT_REPO);
        System.out.println("\tnew currDir dir: " + cfg.baseDir);
    }

    private static void processCommonType(Cfg cfg, EnumsApi.BundleItemType type, Consumer<String> yamlCheckerFunc) throws IOException {
        for (BundleCfgYaml.BundleConfig bundleConfig : cfg.bundleCfg.bundleConfig) {
            if (bundleConfig.type!=type) {
                continue;
            }
            Path p = cfg.baseDir.resolve(bundleConfig.path);
            if (Files.notExists(p) || !Files.isDirectory(p)) {
                System.out.printf("Path %s is broken\n", p.toAbsolutePath());
                throw new ExitApplicationException();
            }
            Path tempPath = cfg.workingDir.resolve(bundleConfig.path);
            System.out.println("\t\tprocess path " + bundleConfig.path);
            Files.createDirectories(tempPath);


//            final FunctionConfigAndFile fcy = getFunctionConfigYaml(p);
//            if (verifySourceCode(fcy, p)) {
//                throw new ExitApplicationException();
//            }

            processFilesForCommonType(tempPath, p, yamlCheckerFunc);
        }
    }

    private static void processFilesForCommonType(Path tempFuncPath, Path srcPath, Consumer<String> yamlCheckerFunc) throws IOException {
        Files.walkFileTree(srcPath, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
                if (Files.isDirectory(p) || CommonConsts.FUNCTION_YAML.equals(p.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                String yaml = Files.readString(p);

                // let's check that this yaml is actually one of required type
                yamlCheckerFunc.accept(yaml);

                Path file = tempFuncPath.resolve(p.getFileName().toString());
                Files.writeString(file, yaml);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void createFinalZip(Cfg cfg) throws IOException {
        String bundleCfgYaml = BundleCfgYamlUtils.UTILS.toString(cfg.bundleCfg);
        Path bundleCfgPath = cfg.workingDir.resolve(CommonConsts.BUNDLE_CFG_YAML);
        Files.writeString(bundleCfgPath, bundleCfgYaml);

        final List<Path> paths = new ArrayList<>();
        Files.walkFileTree(cfg.workingDir, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                if (CommonConsts.FUNCTION_YAML.equals(p.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                paths.add(p);
                return FileVisitResult.CONTINUE;
            }
        });

        Path zip = cfg.workingDir.resolve("bundle-" + LocalDate.now() + CommonConsts.ZIP_EXTENSION);
        ZipUtils.createZip(paths, zip);
    }

    public static boolean verify(FunctionConfigAndFile fcy, Path tempFuncPath) {
        // Verify
        boolean isError = false;
        FunctionConfigYaml.FunctionConfig function = fcy.config.function;

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
            else {
                Path sn = tempFuncPath.resolve(function.file);
                if (Files.notExists(sn)) {
                    System.out.printf("Function %s has missing file %s\n", function.code, function.file);
                    isError = true;
                }

                Path f = Path.of(function.file);
                if (!f.toString().equals(function.file)) {
                    System.out.println("Relative path for function file isn't supported, file: " + function.file);
                    isError = true;
                }
            }
        }
        else if (function.sourcing==EnumsApi.FunctionSourcing.git) {
            throw new IllegalStateException("git sourcing isn't implemented");
        }
        return isError;
    }

    private static BundleCfgYaml initBundleCfg(CommandLine cmd, Path currDir, @Nullable GitInfo gitInfo) throws IOException {
        String bundleFilename = cmd.getOptionValue("b");
        if (S.b(bundleFilename)) {
            bundleFilename = CommonConsts.BUNDLE_CFG_YAML;
        }
        System.out.println("Effective bundle filename is " + bundleFilename);

        Path bundleCfgPath;
        if (gitInfo!=null && !S.b(gitInfo.path)) {
            bundleCfgPath = currDir.resolve(Path.of(gitInfo.path, bundleFilename));
        }
        else {
            bundleCfgPath = currDir.resolve(bundleFilename);
        }

        if (Files.notExists(bundleCfgPath)) {
            System.out.printf("File %s wasn't found in path %s\n", bundleFilename, currDir.toAbsolutePath());
            throw new ExitApplicationException();
        }

        String yaml = Files.readString(bundleCfgPath);
        BundleCfgYaml bundleCfgYaml = BundleCfgYamlUtils.UTILS.to(yaml);
        return bundleCfgYaml;
    }

    private static void processFunctions(Cfg cfg) throws IOException, GeneralSecurityException {
        List<Path> paths = collectPathsToFunctions(cfg);
        for (Path pathFunctionYaml : paths) {
            Path path = cfg.currDir.relativize(pathFunctionYaml).getParent();
            Path p = cfg.currDir.resolve(path);
            if (Files.notExists(p) || !Files.isDirectory(p)) {
                System.out.printf("Path %s is broken\n", p.toAbsolutePath());
                throw new ExitApplicationException();
            }
            Path tempFuncPath = cfg.workingDir.resolve(path);
            System.out.println("\t\tprocess path " + path);
            Files.createDirectories(tempFuncPath);

            final FunctionConfigAndFile fcy = getFunctionConfigYaml(p);
            if (verify(fcy, p)) {
                System.out.println("Error while verification was encountered.");
                throw new ExitApplicationException();
            }
            if (cfg.gitInfo==null) {
                Path zippedFunction = createZipWithFunction(tempFuncPath, fcy.config, p);
                calcChecksum(zippedFunction, fcy.config, cfg);
            }
            else {
                fcy.config.function.sourcing = EnumsApi.FunctionSourcing.git;
                Path pathToFunc = Path.of(cfg.gitInfo.path).resolve(path);
                if (pathToFunc.isAbsolute()) {
                    throw new ExitApplicationException("Can't create relative path, actual is absolute: " + pathToFunc);
                }
                fcy.config.function.git = new GitInfo(cfg.gitInfo.repo, cfg.gitInfo.branch, cfg.gitInfo.commit, pathToFunc.toString());

                int i=0;
            }

            storeFunctionConfigYaml(tempFuncPath, fcy.config);
        }
    }

    static IOFileFilter FUNCTION_YAML_FILTER = FileFileFilter.INSTANCE.and(new NameFileFilter(CommonConsts.FUNCTION_YAML));

    @SneakyThrows
    private static List<Path> collectPathsToFunctions(Cfg cfg) {
        List<Path> paths = new ArrayList<>();
        for (BundleCfgYaml.BundleConfig bundleConfig : cfg.bundleCfg.bundleConfig) {
            if (bundleConfig.type!= function) {
                continue;
            }
            Path p;
            if (cfg.gitInfo != null && !S.b(cfg.gitInfo.path)) {
                p = cfg.baseDir.resolve(cfg.gitInfo.path).resolve(bundleConfig.path);
            }
            else {
                p = cfg.baseDir.resolve(bundleConfig.path);
            }
            if (Files.notExists(p) || !Files.isDirectory(p)) {
                System.out.printf("Path %s is broken\n", p.toAbsolutePath());
                throw new ExitApplicationException();
            }

            Set<Path> collected = new HashSet<>();
            Collection<Path> files = PathUtils.walk(p, FUNCTION_YAML_FILTER, Integer.MAX_VALUE, false, FileVisitOption.FOLLOW_LINKS).collect(Collectors.toList());
            for (Path file : files) {
                checkForDoubles(collected, file);
                paths.add(file);
            }
        }
        return paths;
    }

    private static void checkForDoubles(Set<Path> collected, Path file) {
        Path p=file;
        while ((p=p.getParent())!=null) {
            if (collected.contains(p)) {
                System.out.printf("Path %s was already registered as function dir\n", p.toAbsolutePath());
                throw new ExitApplicationException();
            }
        }
    }

    private static void storeFunctionConfigYaml(Path tempFuncPath, FunctionConfigYaml config) throws IOException {
        String yaml = FunctionConfigYamlUtils.UTILS.toString(config);
        Path f = tempFuncPath.resolve(CommonConsts.FUNCTION_YAML);
        Files.writeString(f, yaml);
    }

    private static void calcChecksum(Path zippedFunction, FunctionConfigYaml config, Cfg cfg) throws IOException, GeneralSecurityException {
        FunctionConfigYaml.FunctionConfig functionConfig = config.function;
        String sum;
        if (functionConfig.sourcing== EnumsApi.FunctionSourcing.git) {
            String s = FunctionCoreUtils.getDataForChecksumForConfigOnly(functionConfig);
            sum = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, new ByteArrayInputStream(s.getBytes()));
        }
        else if (functionConfig.sourcing==EnumsApi.FunctionSourcing.dispatcher) {
            try (InputStream fis = Files.newInputStream(zippedFunction)) {
                sum = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, fis);
            }
        }
        else {
            throw new IllegalArgumentException("unknown functionConfig.sourcing: " + functionConfig.sourcing);
        }

        if (cfg.privateKey!=null) {
            String signature = SecUtils.getSignature(sum, cfg.privateKey, false, Objects.requireNonNull(EnumsApi.HashAlgo.SHA256WithSignature.signatureAlgo));
            config.system.checksumMap.put(EnumsApi.HashAlgo.SHA256WithSignature, sum + SecUtils.SIGNATURE_DELIMITER + signature);
        }
        else {
            config.system.checksumMap.put(EnumsApi.HashAlgo.SHA256, sum);
        }
    }

    private static Path createZipWithFunction(Path tempFuncPath, FunctionConfigYaml fcy, Path funcPath) throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.walkFileTree(funcPath, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                if (CommonConsts.FUNCTION_YAML.equals(p.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                paths.add(p);
                return FileVisitResult.CONTINUE;
            }
        });

        final String zipName = ArtifactCommonUtils.normalizeCode(fcy.function.code) + CommonConsts.ZIP_EXTENSION;
        Path zip = tempFuncPath.resolve(zipName);
        ZipUtils.createZip(paths, zip);
        fcy.system.archive = zipName;
        return zip;
    }

    private static FunctionConfigAndFile getFunctionConfigYaml(Path p) throws IOException {
        Path functionYaml = p.resolve(CommonConsts.FUNCTION_YAML);
        if (Files.notExists(p)) {
            System.out.printf("File %s wasn't found in path %s\n", CommonConsts.FUNCTION_YAML, p.toAbsolutePath());
            throw new ExitApplicationException();
        }
        String yaml = Files.readString(functionYaml);
        FunctionConfigYaml fcy = FunctionConfigYamlUtils.UTILS.to(yaml);
        return new FunctionConfigAndFile(fcy, p);
    }

    private static Cfg initPackaging(String[] args) throws ParseException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        CommandLine cmd = parseArgs(args);
        String ver = cmd.getOptionValue("v");
        if (args.length==0 || S.b(ver)) {
            System.out.println("PackageBundle -v 2 [-key <private key file>] [-b <bundle-filename>]");
            throw new ExitApplicationException();
        }
        int version = Integer.parseInt(ver);
        PrivateKey privateKey = getPrivateKey(cmd);

        Path currDir = Path.of(SystemUtils.USER_DIR);

        GitInfo gitInfo = null;
        if (cmd.hasOption("git-repo")) {
            if (!cmd.hasOption("git-branch")) {
                System.out.println("Option --git-branch was missed");
                throw new ExitApplicationException();
            }
            if (!cmd.hasOption("git-commit")) {
                throw new ExitApplicationException("Option --git-commit was missed");
            }
            gitInfo = new GitInfo(
                cmd.getOptionValue("git-repo"),
                cmd.getOptionValue("git-branch"),
                cmd.getOptionValue("git-commit"),
                cmd.getOptionValue("git-path"));

            if (gitInfo.branch.indexOf('/')!=-1) {
                throw new ExitApplicationException("Option --git-branch can't contain '/', i.e. origin/main or remote/master are wrong, must be main only.");
            }
            if (privateKey!=null) {
                privateKey = null;
                System.out.println("Warning. Even PrivateKey was specified, signature won't be calculated because source is git.");
            }
        }

        BundleCfgYaml bundleCfgYaml = initBundleCfg(cmd, currDir, gitInfo);

        Cfg cfg = new Cfg(version, privateKey, currDir, bundleCfgYaml, gitInfo);

        System.out.println("\tcurrDir dir: " + currDir);

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
        options.addOption(versionOption);

        Option keyOption = new Option("key", "private-key", true, "Private key for signing content");
        keyOption.setRequired(false);
        options.addOption(keyOption);

        Option bundleOption = new Option("b", "bundle", true, "path to bundle file");
        bundleOption.setRequired(false);
        options.addOption(bundleOption);

        Option gitRepoOption = new Option("repo", "git-repo", true, "URL of git repository");
        gitRepoOption.setRequired(false);
        options.addOption(gitRepoOption);

        Option gitBranchOption = new Option("branch", "git-branch", true, "Branch of git repository");
        gitBranchOption.setRequired(false);
        options.addOption(gitBranchOption);

        Option gitCommitOption = new Option("commit", "git-commit", true, "Commit in git repository");
        gitCommitOption.setRequired(false);
        options.addOption(gitCommitOption);

        Option gitPathOption = new Option("path", "git-path", true, "Path in git repository to dir with bundle.yaml");
        gitPathOption.setRequired(false);
        options.addOption(gitPathOption);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        return cmd;
    }
}
