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

import aiai.ai.launchpad.snippet.SnippetVersion;
import aiai.ai.utils.DirUtils;

import java.io.File;

public class StationSnippetUtils {

    public static class SnippetFile {
        public File file;
        public boolean isError;
        public boolean isContent;
    }

    public static SnippetFile getSnippetFile(File snippetDir, String snippetCode, String filename) {

        SnippetFile snippetFile = new SnippetFile();
        SnippetVersion snippetVersion = SnippetVersion.from(snippetCode);
        File currDir = DirUtils.createDir(snippetDir, snippetVersion.name);
        if (currDir == null) {
            snippetFile.isError = true;
            return snippetFile;
        }

        File versionDir = DirUtils.createDir(currDir, snippetVersion.version);
        if (versionDir == null) {
            snippetFile.isError = true;
            return snippetFile;
        }

        snippetFile.file = new File(versionDir, filename );
        if (snippetFile.file.exists()) {
            if (snippetFile.file.length() == 0) {
                snippetFile.file.delete();
            }
            else {
                snippetFile.isContent = true;
            }
        }
        return snippetFile;
    }

    public static File checkEvironment(File stationDir) {
        File dsDir = new File(stationDir, "snippet");
        if (!dsDir.exists()) {
            boolean isOk = dsDir.mkdirs();
            if (!isOk) {
                System.out.println("Can't make all directories for path: " + dsDir.getAbsolutePath());
                return null;
            }
        }
        return dsDir;
    }
}
