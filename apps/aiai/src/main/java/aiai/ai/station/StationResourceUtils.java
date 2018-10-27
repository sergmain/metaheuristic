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

import aiai.ai.Consts;
import aiai.ai.launchpad.beans.BinaryData;
import aiai.ai.utils.DigitUtils;
import aiai.apps.commons.utils.DirUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class StationResourceUtils {

    public static AssetFile prepareResourceFile(File stationResourceDir, BinaryData.Type type, String id) {

        final AssetFile assetFile = new AssetFile();

        File typeDir = new File(stationResourceDir, type.toString());
        String subDir;
        if (type.idAsString) {
            subDir = id.replace(':', '_');
        }
        else {
            DigitUtils.Power power = DigitUtils.getPower(Long.parseLong(id));
            subDir = "" + power.power7 + File.separatorChar + power.power4 + File.separatorChar;
        }
        File trgDir;
        trgDir = new File(typeDir, subDir);
        if (!trgDir.mkdirs()) {
            assetFile.isError = true;
            log.error("Can't create resource dir for task: {}", trgDir.getAbsolutePath());
            return assetFile;
        }
        assetFile.file = new File(trgDir, ""+id+".bin");
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
