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
package aiai.ai.station;

import aiai.ai.Enums;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

@Slf4j
public class StationResourceUtils {

    public static AssetFile prepareResourceFile(File rootDir, Enums.BinaryDataType binaryDataType, String id, String resourceFilename) {

        final AssetFile assetFile = new AssetFile();

        File typeDir = new File(rootDir, binaryDataType.toString());
        final String resId = id.replace(':', '_');

        File trgDir = typeDir;
        if (!trgDir.exists() && !trgDir.mkdirs()) {
            assetFile.isError = true;
            log.error("Can't create resource dir for task: {}", trgDir.getAbsolutePath());
            return assetFile;
        }
        assetFile.file = new File(trgDir, StringUtils.isNotBlank(resourceFilename) ? resourceFilename : "" + resId);
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
