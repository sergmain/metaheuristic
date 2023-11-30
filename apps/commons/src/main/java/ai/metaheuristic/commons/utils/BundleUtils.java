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
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BundleProcessingException;
import ai.metaheuristic.commons.system.SystemProcessLauncher;
import ai.metaheuristic.commons.yaml.auth.ApiAuthUtils;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYaml;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.scheme.ApiSchemeUtils;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;

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
import static ai.metaheuristic.commons.CommonConsts.*;

/**
 * @author Sergio Lissner
 * Date: 11/26/2023
 * Time: 1:56 AM
 */
@SuppressWarnings({"unused", "SimplifyStreamApiCallChains"})
@Slf4j
public class BundleUtils {
    public static final IOFileFilter YAML_SUFFIX_FILTER = FileFileFilter.INSTANCE.and(new SuffixFileFilter(YAML_EXT, YML_EXT));
    public static IOFileFilter FUNCTION_YAML_FILTER = FileFileFilter.INSTANCE.and(new NameFileFilter(MH_FUNCTION_YAML));

    public static Path createBundle(BundleData.Cfg cfg, BundleCfgYaml bundleCfgYaml) throws IOException, GeneralSecurityException {
        log.info("\tworking dir: " + cfg.workingDir);
        processFunctions(cfg,bundleCfgYaml);
        processCommonType(cfg, bundleCfgYaml, sourceCode, SourceCodeParamsYamlUtils.BASE_YAML_UTILS::to);
        processCommonType(cfg, bundleCfgYaml, auth, ApiAuthUtils.UTILS::to);
        processCommonType(cfg, bundleCfgYaml, api, ApiSchemeUtils.UTILS::to);

        return createFinalZip(cfg, bundleCfgYaml);
    }

    @SneakyThrows
    public static void initRepo(BundleData.Cfg cfg) {
        if (cfg.gitInfo==null) {
            throw new BundleProcessingException("cfg.gitInfo is null");
        }
        String gitCode = StrUtils.asCode(cfg.gitInfo.repo);
        Path gitDir = cfg.baseDir.resolve(gitCode);
        if (Files.notExists(gitDir)) {
            Files.createDirectories(gitDir);
        }

        GitData.GitContext gitContext = new GitData.GitContext(60L, 1000);

        SystemProcessLauncher.ExecResult result = GtiUtils.initGitRepository(cfg.gitInfo, gitDir, cfg.gitInfo.repo, gitContext, true);
        if (result!=null && !result.ok) {
            throw new BundleProcessingException("Error while cloning repo "+cfg.gitInfo.repo+", error: "+result.error);
        }

        cfg.repoDir = gitDir.resolve(GIT_REPO);
        System.out.println("\trepoDir dir: " + cfg.repoDir);
    }

    private static void processCommonType(BundleData.Cfg cfg, BundleCfgYaml bundleCfg, EnumsApi.BundleItemType type, Consumer<String> yamlCheckerFunc) throws IOException {
        for (BundleCfgYaml.BundleConfig bundleConfig : bundleCfg.bundleConfig) {
            if (bundleConfig.type!=type) {
                continue;
            }
            Path p = cfg.currDir.resolve(bundleConfig.path);
            if (Files.notExists(p) || !Files.isDirectory(p)) {
                throw new BundleProcessingException(S.f("Path %s is broken", p.toAbsolutePath()));
            }

            PathUtils.walk(p, BundleUtils.YAML_SUFFIX_FILTER, Integer.MAX_VALUE, false, FileVisitOption.FOLLOW_LINKS)
                .forEach(f-> {
                    try {
                        if (Files.isDirectory(f) || MH_FUNCTION_YAML.equals(p.getFileName().toString())) {
                            return;
                        }

                        String yaml = Files.readString(f);

                        System.out.println("\t\tprocess path " + f);
                        Path file = cfg.workingDir.resolve(bundleConfig.path).resolve(p.relativize(f));
                        Path tempPath = file.getParent();
                        if (Files.notExists(tempPath)) {
                            Files.createDirectories(tempPath);
                        }

                        // let's check that this yaml is actually one of required type
                        try {
                            yamlCheckerFunc.accept(yaml);
                        } catch (Throwable th) {
                            throw new RuntimeException("Validation of content was failed. type: " + bundleConfig.type, th);
                        }

//                        Path file = tempPath.resolve(p.getFileName().toString());
                        Files.writeString(file, yaml);

                    } catch (IOException e) {
                        log.error("Error", e);
                        throw new RuntimeException("Error", e);
                    }
                });
        }
    }

    private static void processFilesForCommonType(Path tempFuncPath, Path srcPath, Consumer<String> yamlCheckerFunc) throws IOException {
        Files.walkFileTree(srcPath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
                if (Files.isDirectory(p) || MH_FUNCTION_YAML.equals(p.getFileName().toString())) {
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

    private static Path createFinalZip(BundleData.Cfg cfg, BundleCfgYaml bundleCfg) throws IOException {
        String bundleCfgYaml = BundleCfgYamlUtils.UTILS.toString(bundleCfg);
        Path bundleCfgPath = cfg.workingDir.resolve(MH_BUNDLE_YAML);
        Files.writeString(bundleCfgPath, bundleCfgYaml);

        final List<Path> paths = new ArrayList<>();
        Files.walkFileTree(cfg.workingDir, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                if (MH_FUNCTION_YAML.equals(p.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                paths.add(p);
                return FileVisitResult.CONTINUE;
            }
        });

        Path zip = cfg.workingDir.resolve("bundle-" + LocalDate.now() + ZIP_EXTENSION);
        ZipUtils.createZip(paths, zip);
        return zip;
    }

    public static boolean verify(BundleData.FunctionConfigAndFile fcy, Path tempFuncPath) {
        // Verify
        boolean isError = false;
        FunctionConfigYaml.FunctionConfig function = fcy.config().function;

        final FunctionApiData.FunctionConfigStatus verify = FunctionCoreUtils.validate(function);
        if (!verify.isOk) {
            log.error(verify.error);
            isError=true;
        }
        if (function.sourcing== EnumsApi.FunctionSourcing.dispatcher) {
            if (S.b(function.file)) {
                log.error("function " + function.code + " has an empty 'file' field.");
                isError = true;
            }
            else {
                Path sn = tempFuncPath.resolve(function.src).resolve(function.file);
                if (Files.notExists(sn)) {
                    log.error("Function {} has missing file {}", function.code, function.file);
                    isError = true;
                }

                Path f = Path.of(function.file);
                if (!f.toString().equals(function.file)) {
                    log.error("Relative path for function file isn't supported, file: " + function.file);
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
                throw new BundleProcessingException(S.f("Path %s is broken", p.toAbsolutePath()));
            }
            Path tempFuncPath = cfg.workingDir.resolve(path);
            log.info("'\tprocess path " + path);
            Files.createDirectories(tempFuncPath);

            final BundleData.FunctionConfigAndFile fcy = getFunctionConfigYaml(p);
            if (verify(fcy, p)) {
                throw new BundleProcessingException("Error while verification was encountered.");
            }
            if (cfg.gitInfo==null) {
                Path zippedFunction = createZipWithFunction(tempFuncPath, fcy.config(), p);
                calcChecksum(zippedFunction, fcy.config(), cfg);
            }
            else {
                fcy.config().function.sourcing = EnumsApi.FunctionSourcing.git;
                Path pathToFunc = Path.of(cfg.gitInfo.path).resolve(path);
                if (pathToFunc.isAbsolute()) {
                    throw new BundleProcessingException("Can't create relative path, actual is absolute: " + pathToFunc);
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
                p = cfg.repoDir.resolve(cfg.gitInfo.path).resolve(bundleConfig.path);
            }
            else {
                p = cfg.baseDir.resolve(bundleConfig.path);
            }
            if (Files.notExists(p) || !Files.isDirectory(p)) {
                throw new BundleProcessingException(S.f("Path %s is broken", p.toAbsolutePath()));
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
                throw new BundleProcessingException(S.f("Path %s was already registered as function dir", p.toAbsolutePath()));
            }
        }
    }

    private static void storeFunctionConfigYaml(Path tempFuncPath, FunctionConfigYaml config) throws IOException {
        String yaml = FunctionConfigYamlUtils.UTILS.toString(config);
        Path f = tempFuncPath.resolve(MH_FUNCTION_YAML);
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
                if (MH_FUNCTION_YAML.equals(p.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                paths.add(p);
                return FileVisitResult.CONTINUE;
            }
        });

        final String zipName = ArtifactCommonUtils.normalizeCode(fcy.function.code) + ZIP_EXTENSION;
        Path zip = tempFuncPath.resolve(zipName);
        ZipUtils.createZip(paths, zip);
        fcy.system.archive = zipName;
        return zip;
    }

    private static BundleData.FunctionConfigAndFile getFunctionConfigYaml(Path p) throws IOException {
        Path functionYaml = p.resolve(MH_FUNCTION_YAML);
        if (Files.notExists(p)) {
            throw new BundleProcessingException(S.f("File %s wasn't found in path %s", MH_FUNCTION_YAML, p.toAbsolutePath()));
        }
        String yaml = Files.readString(functionYaml);
        FunctionConfigYaml fcy = FunctionConfigYamlUtils.UTILS.to(yaml);
        return new BundleData.FunctionConfigAndFile(fcy, p);
    }

    public static BundleCfgYaml readBundleCfgYaml(BundleData.Cfg cfg, String bundleFilename) throws IOException {
        Path bundleCfgPath;
        if (cfg.gitInfo !=null && !S.b(cfg.gitInfo.path)) {
            bundleCfgPath = cfg.repoDir.resolve(Path.of(cfg.gitInfo.path, bundleFilename));
        }
        else {
            bundleCfgPath = cfg.baseDir.resolve(bundleFilename);
        }

        if (Files.notExists(bundleCfgPath)) {
            throw new BundleProcessingException(S.f("File %s wasn't found", bundleCfgPath));
        }

        String yaml = Files.readString(bundleCfgPath);
        BundleCfgYaml bundleCfgYaml = BundleCfgYamlUtils.UTILS.to(yaml);
        return bundleCfgYaml;
    }
}
