/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.GitData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.ExitApplicationException;
import ai.metaheuristic.commons.system.SystemProcessLauncher;
import ai.metaheuristic.commons.yaml.auth.ApiAuthUtils;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYaml;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.scheme.ApiSchemeUtils;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.springframework.lang.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.BundleItemType.*;
import static ai.metaheuristic.commons.CommonConsts.GIT_REPO;

/**
 * @author Sergio Lissner
 * Date: 11/26/2023
 * Time: 1:56 AM
 */
public class BundleUtils {
    static IOFileFilter FUNCTION_YAML_FILTER = FileFileFilter.INSTANCE.and(new NameFileFilter(CommonConsts.FUNCTION_YAML));

    public static void createBundle(BundleData.Cfg cfg, BundleCfgYaml bundleCfgYaml) throws IOException, GeneralSecurityException {
        if (cfg.gitInfo != null) {
            initRepo(cfg);
        }

        cfg.initOtherPaths(null);
        System.out.println("\tworking dir: " + cfg.workingDir);
        processFunctions(cfg,bundleCfgYaml);
        processCommonType(cfg, bundleCfgYaml, sourceCode, SourceCodeParamsYamlUtils.BASE_YAML_UTILS::to);
        processCommonType(cfg, bundleCfgYaml, auth, ApiAuthUtils.UTILS::to);
        processCommonType(cfg, bundleCfgYaml, api, ApiSchemeUtils.UTILS::to);

        createFinalZip(cfg, bundleCfgYaml);
    }

    @SneakyThrows
    public static void initRepo(BundleData.Cfg cfg) {
        if (cfg.gitInfo==null) {
            throw new ExitApplicationException("cfg.gitInfo is null");
        }
        String gitCode = StrUtils.asCode(cfg.gitInfo.repo);
        Path gitDir = cfg.baseDir.resolve(gitCode);
        if (Files.notExists(gitDir)) {
            Files.createDirectories(gitDir);
        }

        GitData.GitContext gitContext = new GitData.GitContext(60L, 1000);

        SystemProcessLauncher.ExecResult result = GtiUtils.initGitRepository(cfg.gitInfo, gitDir, cfg.gitInfo.repo, gitContext, true);
        if (result!=null && !result.ok) {
            throw new ExitApplicationException("Error while cloning repo "+cfg.gitInfo.repo+", error: "+result.error);
        }

        cfg.repoDir = gitDir.resolve(GIT_REPO);
        System.out.println("\trepoDir dir: " + cfg.repoDir);
    }

    private static void processCommonType(BundleData.Cfg cfg, BundleCfgYaml bundleCfg, EnumsApi.BundleItemType type, Consumer<String> yamlCheckerFunc) throws IOException {
        for (BundleCfgYaml.BundleConfig bundleConfig : bundleCfg.bundleConfig) {
            if (bundleConfig.type!=type) {
                continue;
            }
            Path p = cfg.baseDir.resolve(bundleConfig.path);
            if (Files.notExists(p) || !Files.isDirectory(p)) {
                throw new ExitApplicationException(S.f("Path %s is broken", p.toAbsolutePath()));
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

    private static void createFinalZip(BundleData.Cfg cfg, BundleCfgYaml bundleCfg) throws IOException {
        String bundleCfgYaml = BundleCfgYamlUtils.UTILS.toString(bundleCfg);
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

    public static boolean verify(BundleData.FunctionConfigAndFile fcy, Path tempFuncPath) {
        // Verify
        boolean isError = false;
        FunctionConfigYaml.FunctionConfig function = fcy.config().function;

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

    private static void processFunctions(BundleData.Cfg cfg, BundleCfgYaml bundleCfg) throws IOException, GeneralSecurityException {
        List<Path> paths = collectPathsToFunctions(cfg, bundleCfg);
        for (Path pathFunctionYaml : paths) {
            Path path = cfg.currDir.relativize(pathFunctionYaml).getParent();
            Path p = cfg.currDir.resolve(path);
            if (Files.notExists(p) || !Files.isDirectory(p)) {
                throw new ExitApplicationException(S.f("Path %s is broken", p.toAbsolutePath()));
            }
            Path tempFuncPath = cfg.workingDir.resolve(path);
            System.out.println("\t\tprocess path " + path);
            Files.createDirectories(tempFuncPath);

            final BundleData.FunctionConfigAndFile fcy = getFunctionConfigYaml(p);
            if (verify(fcy, p)) {
                throw new ExitApplicationException("Error while verification was encountered.");
            }
            if (cfg.gitInfo==null) {
                Path zippedFunction = createZipWithFunction(tempFuncPath, fcy.config(), p);
                calcChecksum(zippedFunction, fcy.config(), cfg);
            }
            else {
                fcy.config().function.sourcing = EnumsApi.FunctionSourcing.git;
                Path pathToFunc = Path.of(cfg.gitInfo.path).resolve(path);
                if (pathToFunc.isAbsolute()) {
                    throw new ExitApplicationException("Can't create relative path, actual is absolute: " + pathToFunc);
                }
                fcy.config().function.git = new GitInfo(cfg.gitInfo.repo, cfg.gitInfo.branch, cfg.gitInfo.commit, pathToFunc.toString());

                int i=0;
            }

            storeFunctionConfigYaml(tempFuncPath, fcy.config());
        }
    }

    @SneakyThrows
    private static List<Path> collectPathsToFunctions(BundleData.Cfg cfg, BundleCfgYaml bundleCfg) {
        List<Path> paths = new ArrayList<>();
        for (BundleCfgYaml.BundleConfig bundleConfig : bundleCfg.bundleConfig) {
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
                throw new ExitApplicationException(S.f("Path %s is broken", p.toAbsolutePath()));
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
                throw new ExitApplicationException(S.f("Path %s was already registered as function dir", p.toAbsolutePath()));
            }
        }
    }

    private static void storeFunctionConfigYaml(Path tempFuncPath, FunctionConfigYaml config) throws IOException {
        String yaml = FunctionConfigYamlUtils.UTILS.toString(config);
        Path f = tempFuncPath.resolve(CommonConsts.FUNCTION_YAML);
        Files.writeString(f, yaml);
    }

    private static void calcChecksum(Path zippedFunction, FunctionConfigYaml config, BundleData.Cfg cfg) throws IOException, GeneralSecurityException {
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

    private static BundleData.FunctionConfigAndFile getFunctionConfigYaml(Path p) throws IOException {
        Path functionYaml = p.resolve(CommonConsts.FUNCTION_YAML);
        if (Files.notExists(p)) {
            throw new ExitApplicationException(S.f("File %s wasn't found in path %s", CommonConsts.FUNCTION_YAML, p.toAbsolutePath()));
        }
        String yaml = Files.readString(functionYaml);
        FunctionConfigYaml fcy = FunctionConfigYamlUtils.UTILS.to(yaml);
        return new BundleData.FunctionConfigAndFile(fcy, p);
    }

    public static BundleCfgYaml readBundleCfgYaml(Path currDir, @Nullable GitInfo gitInfo, String bundleFilename) throws IOException {
        Path bundleCfgPath;
        if (gitInfo !=null && !S.b(gitInfo.path)) {
            bundleCfgPath = currDir.resolve(Path.of(gitInfo.path, bundleFilename));
        }
        else {
            bundleCfgPath = currDir.resolve(bundleFilename);
        }

        if (Files.notExists(bundleCfgPath)) {
            throw new ExitApplicationException(S.f("File %s wasn't found in path %s", bundleFilename, currDir.toAbsolutePath()));
        }

        String yaml = Files.readString(bundleCfgPath);
        BundleCfgYaml bundleCfgYaml = BundleCfgYamlUtils.UTILS.to(yaml);
        return bundleCfgYaml;
    }
}
