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
package ai.metaheuristic.commons.utils;

import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.SystemUtils;
import javax.annotation.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Slf4j
public class DirUtils {

    @Nullable
    public static Path getParent(Path main, Path ending) {
        if (!main.endsWith(ending)) {
            return null;
        }
        Path curr = ending;
        Path r = main;
        do {
            r = r.getParent();
            curr = curr.getParent();
        } while (curr!=null);
        return r;
    }

    public static Path getPoweredPath(Path basePath, long taskId) {
        final String path = getPoweredPath(taskId);
        final Path poweredPath = basePath.resolve(path);
        return poweredPath;
    }

    public static String getPoweredPath(long taskId) {
        DigitUtils.Power power = DigitUtils.getPower(taskId);
        return Long.toString(power.power7) + File.separatorChar + power.power4 + File.separatorChar;
    }

    @SneakyThrows
    public static void copy(InputStream is, Path to) {
        final Path parent = to.getParent();
        if (Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        try (BufferedInputStream bis = new BufferedInputStream(is, 0x4000);
             OutputStream os = Files.newOutputStream(to); BufferedOutputStream bos = new BufferedOutputStream(os, 0x4000)) {
            IOUtils.copy(bis, bos);
        }
    }

    public static void deletePathAsync(@Nullable final Path fileOrDir) {
        if (fileOrDir != null) {
            Thread t = Thread.ofVirtual().start(() -> {
                try {
                    if (Files.isDirectory(fileOrDir)) {
                        PathUtils.deleteDirectory(fileOrDir);
                    }
                    else {
                        Files.deleteIfExists(fileOrDir);
                    }
                } catch(IOException e){
                    // it's cleaning so don't report any error
                }
            });
        }
    }

    @SneakyThrows
    @Nullable
    public static Path createMhTempPath(String prefix) {
        return createMhTempPath(SystemUtils.getJavaIoTmpDir().toPath(), prefix);
    }

    @SneakyThrows
    @Nullable
    public static Path createMhTempPath(Path base, String prefix) {
        Path trgDir = base.resolve(CommonConsts.METAHEURISTIC_TEMP);
        if (Files.notExists(trgDir)) {
            Files.createDirectories(trgDir);
        }
        return createTempPath(trgDir, prefix);
    }

    public static void deletePaths(@Nullable List<Path> toClean) {
        if (toClean!=null) {
            for (Path file : toClean) {
                try {
                    if (Files.notExists(file)) {
                        continue;
                    }
                    deletePathAsync(file);
                } catch (Throwable th) {
                    log.error("017.020 Error while cleaning resources", th);
                }
            }
        }
    }

    @Nullable
    public static Path createTempPath(Path trgDir, String prefix) {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String prefixDate = format.format(date);
        for (int i = 0; i < 5; i++) {
            Path newTempDir = trgDir.resolve( prefix + prefixDate + "-" + System.nanoTime());
            if (Files.exists(newTempDir)) {
                continue;
            }
            try {
                Files.createDirectories(newTempDir);
                if (Files.exists(newTempDir)) {
                    return newTempDir;
                }
            } catch (IOException e) {
                log.error(S.f("017.040 Can't create temporary dir %s, attempt #%d, error: %s", newTempDir.normalize(), i, e.toString()));
            }
        }
        return null;
    }

    @Deprecated(forRemoval = true)
    public static void deleteAsync(@Nullable final File fileOrDir) {
        if (fileOrDir != null) {
            Thread t = Thread.ofVirtual().start(() -> {
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
        }
    }

    @Deprecated(forRemoval = true)
    @Nullable
    public static File createDir(File baseDir, String subDir) {
        File currDir = new File(baseDir, subDir);
        if (!currDir.exists()) {
            boolean isOk = currDir.mkdirs();
            if (!isOk) {
                log.error("017.060 can't make all directories for path: {}", currDir.getAbsolutePath());
                return null;
            }
        }
        return currDir;
    }

    @Deprecated(forRemoval = true)
    @Nullable
    public static File createMhTempDir(String prefix) {
        final Path mhTempPath = createMhTempPath(prefix);
        return mhTempPath==null ? null : mhTempPath.toFile();
    }

    @Deprecated(forRemoval = true)
    @Nullable
    public static File createTempDir(String prefix) {
        final Path tempPath = createTempPath(SystemUtils.getJavaIoTmpDir().toPath(), prefix);
        return tempPath==null ? null : tempPath.toFile();
    }

    @Deprecated(forRemoval = true)
    @Nullable
    public static File createTempDir(File trgDir, String prefix) {
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
                log.error(S.f("017.080 Can't create temporary dir %s, attempt #%d, error: %s", newTempDir.getAbsolutePath(), i, e.toString()));
            }
        }
        return null;
    }

    @Deprecated(forRemoval = true)
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
                    log.error("017.100 Error while cleaning resources", th);
                }
            }
        }
    }
}
