/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public class DirUtils {

    public static void deleteAsync(@Nullable final File fileOrDir) {
        if (fileOrDir != null) {
            Thread t = new Thread(() -> {
                try {
                    if (fileOrDir.isDirectory()) {
                        FileUtils.deleteDirectory(fileOrDir);
                    }
                    else {
                        fileOrDir.delete();
                    }
                } catch(IOException e){
                    // it's cleaning so don't report any error
                }
            });
            t.start();
        }
    }

    public static @Nullable File createDir(File baseDir, String subDir) {
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

    public static @Nullable File createTempDir(String prefix) {
        String tempDir = System.getProperty("java.io.tmpdir");

        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String prefixDate = format.format(date);
        for (int i = 0; i < 5; i++) {
            File newTempDir = new File(tempDir, prefix + prefixDate + "-" + System.nanoTime());
            if (newTempDir.exists()) {
                continue;
            }
            newTempDir.mkdirs();
            return newTempDir;
        }
        return null;
    }
}
