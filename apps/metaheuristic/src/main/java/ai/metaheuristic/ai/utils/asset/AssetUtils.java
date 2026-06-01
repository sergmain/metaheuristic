/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
package ai.metaheuristic.ai.utils.asset;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.ArtifactCommonUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.HashSet;
import java.nio.file.attribute.PosixFilePermission;
import java.io.IOException;

@Slf4j
public class AssetUtils {

    /**
     *
     * @param rootDir File
     * @param dataId -  this is the code of resource
     * @param variableFilename String
     * @return AssetFile
     */
    public static AssetFile prepareFileForVariable(Path rootDir, String dataId, @Nullable String variableFilename, EnumsApi.DataType binaryType) {
        return prepareAssetFile(rootDir, dataId, variableFilename, binaryType.toString());
    }

    public static AssetFile prepareOutputAssetFile(Path rootDir, String dataId) {
        return prepareAssetFile(rootDir, dataId, null, ConstsApi.ARTIFACTS_DIR);
    }

    @SneakyThrows
    public static AssetFile fromFile(Path file) {
        final AssetFile assetFile = new AssetFile();
        assetFile.file = file;
        if (Files.isDirectory(file)) {
            String es = S.f("025.020 path {} is dir", file.toAbsolutePath());
            log.error(es);
            assetFile.error = es;
            assetFile.isError = true;
        }
        assetFile.isExist = Files.exists(assetFile.file);

        if (assetFile.isExist) {
            assetFile.fileLength = Files.size(assetFile.file);
            if (assetFile.fileLength == 0) {
                Files.deleteIfExists(assetFile.file);
                assetFile.isExist = false;
            }
            else {
                assetFile.isContent = true;
            }
        }
        return assetFile;
    }

    // dataId must be String because for Function it's a code of function
    private static AssetFile prepareAssetFile(Path rootDir, String dataId, @Nullable String filename, String assetDirname ) {
        final Path assetDir = rootDir.resolve(assetDirname);
        return prepareAssetFile(assetDir, dataId, filename);
    }

    @SneakyThrows
    public static AssetFile prepareAssetFile(Path assetDir, @Nullable String dataId, @Nullable String filename) {
        final AssetFile assetFile = new AssetFile();
        Files.createDirectories(assetDir);
        if (Files.notExists(assetDir)) {
            assetFile.isError = true;
            log.error("025.040 Can't create a variable dir for task: {}", assetDir.toAbsolutePath());
            return assetFile;
        }
        if (StringUtils.isNotBlank(filename)) {
            assetFile.file = assetDir.resolve(filename);
        }
        else if (!S.b(dataId)) {
            final String resId = ArtifactCommonUtils.normalizeCode(dataId);
            assetFile.file = assetDir.resolve(resId);
        }
        else {
            throw new IllegalArgumentException("025.050 filename==null && S.b(dataId)");
        }
        assetFile.isExist = Files.exists(assetFile.file);

        if (assetFile.isExist) {
            assetFile.fileLength = Files.size(assetFile.file);
            if (assetFile.fileLength == 0) {
                Files.deleteIfExists(assetFile.file);
                assetFile.isExist = false;
            }
            else {
                assetFile.isContent = true;
            }
        }
        return assetFile;
    }

    @SneakyThrows
    public static AssetFile prepareFunctionAssetFile(Path baseDir, String functionCode, @Nullable String resourceFilename) {

        Path baseFunctionDir = ArtifactCommonUtils.prepareFunctionPath(baseDir);

        final String resId = ArtifactCommonUtils.normalizeCode(functionCode);
        final Path resDir = baseFunctionDir.resolve(resId);
        Files.createDirectories(resDir);

        final AssetFile assetFile = new AssetFile();
        if (Files.notExists(resDir)) {
            assetFile.isError = true;
            log.error("025.080 Can't create a concrete function dir: {}", resDir.toAbsolutePath());
            return assetFile;
        }
        assetFile.file = (!S.b(resourceFilename) ? resDir.resolve(resourceFilename) : resDir.resolve(resId));

        assetFile.isExist = Files.exists(assetFile.file);

        if (assetFile.isExist) {
            if (Files.size(assetFile.file) == 0) {
                Files.deleteIfExists(assetFile.file);
                assetFile.isExist = false;
            }
            else {
                assetFile.isContent = true;
            }
        }
        return assetFile;
    }

    @Nullable
    public static String getActualFunctionFile(TaskParamsYaml.FunctionConfig functionConfig) {
        TaskParamsYaml.Target target = selectTarget(functionConfig, EnumsApi.OsArch.detect());
        if (target==null || S.b(target.file)) {
            return null;
        }
        // src may be a multi-segment, '/'-style path (e.g. bin/linux-amd64); Path normalises per-OS
        return S.b(target.src)
            ? target.file
            : Path.of(target.src).resolve(target.file).toString();
    }

    public static TaskParamsYaml.@Nullable Target selectTarget(TaskParamsYaml.FunctionConfig functionConfig, EnumsApi.OsArch osArch) {
        TaskParamsYaml.Target t = functionConfig.targets.get(osArch.key());
        if (t==null) {
            t = functionConfig.targets.get(CommonConsts.MH_DEFAULT_OS_KEY);
        }
        return t;
    }

    /**
     * ZIP entries don't preserve the POSIX executable bit, so a native Function binary
     * unpacked from a function package isn't runnable until +x is restored. Sets
     * owner/group/others execute on POSIX file systems; a no-op on Windows.
     */
    public static void makeExecutableIfPosix(Path file) {
        try {
            if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(file));
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(file, perms);
            }
        }
        catch (IOException e) {
            log.warn("Can't set the executable bit on {}: {}", file, e.getMessage());
        }
    }
}
