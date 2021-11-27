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
package ai.metaheuristic.commons.utils;

import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

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
                log.error("#017.020 can't make all directories for path: {}", currDir.getAbsolutePath());
                return null;
            }
        }
        return currDir;
    }

    public static @Nullable File createMhTempDir(String prefix) {
        File trgDir = new File(SystemUtils.getJavaIoTmpDir(), CommonConsts.METAHEURISTIC_TEMP);
        if (!trgDir.exists()) {
            boolean isOk = trgDir.mkdirs();
            if (!isOk) {
                return null;
            }
        }
        return createTempDir(trgDir, prefix);
    }

    public static @Nullable File createTempDir(String prefix) {
        return createTempDir(SystemUtils.getJavaIoTmpDir(), prefix);
    }

    public static @Nullable File createTempDir(File trgDir, String prefix) {

        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String prefixDate = format.format(date);
        for (int i = 0; i < 5; i++) {
            File newTempDir = new File(trgDir, prefix + prefixDate + "-" + System.nanoTime());
            if (newTempDir.exists()) {
                continue;
            }
            try {
                Files.createDirectories(newTempDir.toPath());
                if (newTempDir.exists()) {
                    return newTempDir;
                }
            } catch (IOException e) {
                log.error(S.f("#017.040 Can't create temporary dir %s, attempt #%d, error: %s", newTempDir.getAbsolutePath(), i, e.getMessage()));
            }
        }
        return null;
    }

    public static void deleteFiles(@Nullable List<File> toClean) {
        if (toClean!=null) {
            for (File file : toClean) {
                try {
                    if (!file.exists()) {
                        continue;
                    }
                    if (file.isFile()) {
                        file.delete();
                    }
                    else {
                        deleteAsync(file);
                    }
                } catch (Throwable th) {
                    log.error("Error while cleaning resources", th);
                }
            }
        }
    }
}
