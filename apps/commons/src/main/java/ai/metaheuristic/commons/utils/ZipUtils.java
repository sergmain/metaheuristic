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

import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.exceptions.ZipArchiveException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.Enumeration;

/**
 * Utility to Zip and Unzip nested directories recursively.
 * Author of 1st version is:
 * @author Robin Spark
 * circa 13.07.12
 * @author Serge
 * circa 01.01.15
 */
@Slf4j
public class ZipUtils {

    /**
     * Creates a zip file at the specified path with the contents of the specified directory.
     * NB:
     *
     * @param directory The path of the directory where the archive will be created. eg. c:/temp
     * @param zipFile zip file
     * @throws ZipArchiveException If anything goes wrong
     */
    public static void createZip(File directory, File zipFile)  {

        try {
            try (FileOutputStream fOut = new FileOutputStream(zipFile);
                 BufferedOutputStream bOut = new BufferedOutputStream(fOut);
                 ZipArchiveOutputStream tOut = new ZipArchiveOutputStream(bOut) ) {
                addFileToZip(tOut, directory, "");
            }
        } catch (Throwable th) {
            log.error("Zipping error", th);
            throw new ZipArchiveException("Zip failed", th);
        }
    }

    /**
     * Creates a zip entry for the path specified with a name built from the base passed in and the file/directory
     * name. If the path is a directory, a recursive call is made such that the full directory is added to the zip.
     *
     * @param zOut The zip file's output stream
     * @param f The filesystem path of the file/directory being added
     * @param base The base prefix to for the name of the zip file entry
     *
     */
    private static void addFileToZip(ZipArchiveOutputStream zOut, File f, String base) throws IOException {
        String entryName = base + f.getName();
        ZipArchiveEntry zipEntry = new ZipArchiveEntry(f, entryName);

        zOut.putArchiveEntry(zipEntry);

        if (f.isFile()) {
            try (FileInputStream fInputStream = new FileInputStream(f) ) {
                IOUtils.copy(fInputStream, zOut);
                zOut.closeArchiveEntry();
            }
        } else {
            zOut.closeArchiveEntry();
            File[] children = f.listFiles();

            if (children != null) {
                for (File child : children) {
                    addFileToZip(zOut, child.getAbsoluteFile(), entryName + "/");
                }
            }
        }
    }

    public static class MyZipFile extends ZipFile implements Cloneable, AutoCloseable {

        public MyZipFile(File f) throws IOException {
            super(f);
        }
    }

    /**
     * Unzips a zip file into the given destination directory.
     *
     * The archive file MUST have a unique "root" folder. This root folder is
     * skipped when unarchiving.
     *
     */
    public static void unzipFolder(File archiveFile, File zipDestinationFolder) {

        log.debug("Start unzipping archive file");
        log.debug("'\tzip archive file: {}", archiveFile.getAbsolutePath());
        log.debug("'\t\tis exist: {}", archiveFile.exists());
        log.debug("'\t\tis writable: {}", archiveFile.canWrite());
        log.debug("'\t\tis readable: {}", archiveFile.canRead());
        log.debug("'\ttarget dir: {}", zipDestinationFolder.getAbsolutePath());
        log.debug("'\t\tis exist: {}", zipDestinationFolder.exists());
        log.debug("'\t\tis writable: {}", zipDestinationFolder.canWrite());
        try (MyZipFile zipFile = new MyZipFile(archiveFile)) {

            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry zipEntry = entries.nextElement();
                log.debug("'\t\tzip entry: {}, is directory: {}", zipEntry.getName(), zipEntry.isDirectory());

                String name = zipEntry.getName();
                if (zipEntry.isDirectory()) {
                    if (name.endsWith("/")|| name.endsWith("\\")) {
                        name = name.substring(0, name.length()-1);
                    }

                    File newDir = new File(zipDestinationFolder, name);
                    log.debug("'\t\t\tcreate dirs in {}", newDir.getAbsolutePath());
                    if (!newDir.mkdirs()) {
                        throw new RuntimeException("Creation of target dir was failed, target dir: " + zipDestinationFolder+", entity: " + name);
                    }
                }
                else {
                    File destinationFile = DirUtils.createTargetFile(zipDestinationFolder, name);
                    if (destinationFile==null) {
                        throw new RuntimeException("Creation of target file was failed, target dir: " + zipDestinationFolder+", entity: " + name);
                    }
                    if (!destinationFile.getParentFile().exists()) {
                        destinationFile.getParentFile().mkdirs();
                    }
                    log.debug("'\t\t\tcopy content of zip entry to file {}", destinationFile.getAbsolutePath());
                    FileUtils.copyInputStreamToFile(zipFile.getInputStream(zipEntry), destinationFile);
                }
            }
        }
        catch (Throwable th) {
            log.error("Unzipping error", th);
            throw new UnzipArchiveException("Unzip failed", th);
        }
    }
}
