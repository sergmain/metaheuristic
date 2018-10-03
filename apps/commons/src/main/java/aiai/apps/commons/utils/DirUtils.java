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
package aiai.apps.commons.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class DirUtils {

    public static File createTargetFile(File zipDestinationFolder, String name) {
        File destinationFile = new File(zipDestinationFolder, name);
        if (name.endsWith(File.separator)) {
            if (!destinationFile.isDirectory() && !destinationFile.mkdirs()) {
                throw new RuntimeException("Error creating temp directory:" + destinationFile.getPath());
            }
            return null;
        }
        else if (name.indexOf(File.separatorChar) != -1) {
            // Create the the parent directory if it doesn't exist
            File parentFolder = destinationFile.getParentFile();
            if (!parentFolder.isDirectory()) {
                if (!parentFolder.mkdirs()) {
                    throw new RuntimeException("Error creating temp directory:" + parentFolder.getPath());
                }
            }
        }
        return destinationFile;
    }

    public static File createDir(File baseDir, String subDir) {
        File currDir = new File(baseDir, subDir);
        if (!currDir.exists()) {
            boolean isOk = currDir.mkdirs();
            if (!isOk) {
                log.error("Can't make all directories for path: {}", currDir.getAbsolutePath());
                return null;
            }
        }
        return currDir;
    }
}
