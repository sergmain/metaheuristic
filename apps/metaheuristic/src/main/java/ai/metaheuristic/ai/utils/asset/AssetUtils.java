/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.File;

@Slf4j
public class AssetUtils {

    /**
     *
     * @param rootDir File
     * @param dataId -  this is the code of resource
     * @param variableFilename String
     * @return AssetFile
     */
    public static AssetFile prepareFileForVariable(File rootDir, String dataId, @Nullable String variableFilename, EnumsApi.DataType binaryType) {
        return prepareAssetFile(rootDir, dataId, variableFilename, binaryType.toString());
    }

    public static AssetFile prepareOutputAssetFile(File rootDir, String dataId) {
        return prepareAssetFile(rootDir, dataId, null, ConstsApi.ARTIFACTS_DIR);
    }

    // dataId must be String because for Function it's a code of function
    private static AssetFile prepareAssetFile(File rootDir, String dataId, @Nullable String filename, String assetDirname ) {
        final File assetDir = new File(rootDir, assetDirname);
        return prepareAssetFile(assetDir, dataId, filename);
    }

    public static AssetFile prepareAssetFile(File assetDir, @Nullable String dataId, @Nullable String filename) {
        final AssetFile assetFile = new AssetFile();
        if (!assetDir.exists() && !assetDir.mkdirs()) {
            assetFile.isError = true;
            log.error("#025.42 Can't create resource dir for task: {}", assetDir.getAbsolutePath());
            return assetFile;
        }
        if (StringUtils.isNotBlank(filename)) {
            assetFile.file = new File(assetDir, filename);
        }
        else if (!S.b(dataId)) {
            final String resId = dataId.replace(':', '_');
            assetFile.file = new File(assetDir, "" + resId);
        }
        else {
            throw new IllegalArgumentException("filename==null && S.b(dataId)");
        }
        assetFile.isExist = assetFile.file.exists();

        if (assetFile.isExist) {
            assetFile.fileLength = assetFile.file.length();
            if (assetFile.fileLength == 0) {
                assetFile.file.delete();
                assetFile.isExist = false;
            }
            else {
                assetFile.isContent = true;
            }
        }
        return assetFile;
    }

    public static AssetFile prepareFunctionFile(File baseDir, String functionCode, @Nullable String resourceFilename) {

        final AssetFile assetFile = new AssetFile();
        final File trgDir = new File(baseDir, EnumsApi.DataType.function.toString());
        if (!trgDir.exists() && !trgDir.mkdirs()) {
            assetFile.isError = true;
            log.error("#025.37 Can't create function dir: {}", trgDir.getAbsolutePath());
            return assetFile;
        }
        final String resId = functionCode.replace(':', '_');
        final File resDir = new File(trgDir, resId);
        if (!resDir.exists() && !resDir.mkdirs()) {
            assetFile.isError = true;
            log.error("#025.35 Can't create resource dir: {}", resDir.getAbsolutePath());
            return assetFile;
        }
        assetFile.file = !S.b(resourceFilename) ? new File(resDir, resourceFilename) : new File(resDir, resId);

        assetFile.isExist = assetFile.file.exists();

        if (assetFile.isExist) {
            if (assetFile.file.length() == 0) {
                assetFile.file.delete();
                assetFile.isExist = false;
            }
            else {
                assetFile.isContent = true;
            }
        }
        return assetFile;
    }
}
