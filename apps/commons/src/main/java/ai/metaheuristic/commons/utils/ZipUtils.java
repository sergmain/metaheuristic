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

import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.exceptions.ZipArchiveException;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;

/**
 * Utility to Zip and Unzip nested directories recursively.
 * Author of 1st version is:
 * @author Robin Spark
 * circa 13.07.12
 * @author Serge
 * circa 01.01.15
 */
@SuppressWarnings("WeakerAccess")
@Slf4j
public class ZipUtils {

    public enum State {OK, ERROR}

    public static final ValidationResult VALIDATION_RESULT_OK = new ValidationResult();

    @NoArgsConstructor
    public static class ValidationResult {
        public State state = State.OK;
        private final List<String> errors = new ArrayList<>();
        public void addError(String error) {
            state = State.ERROR;
            errors.add(error);
        }
        public List<String> getErrors() {
            return errors;
        }

        public ValidationResult(String error) {
            addError(error);
        }
    }

    public static void createZip(File directory, File zipFile)  {
        createZip(directory, zipFile, Collections.emptyMap());
    }

    public static void createZip(File directory, File zipFile, Map<String, String> renameTo)  {
        try (FileOutputStream fOut = new FileOutputStream(zipFile)) {
            createZip(directory,fOut, renameTo);
        }
        catch (ZipArchiveException e) {
            log.error("Zipping error", e);
            throw e;
        }
        catch (Throwable th) {
            log.error("Zipping error", th);
            throw new ZipArchiveException("Zip failed", th);
        }
    }

    /**
     * Creates a zip file at the specified path with the contents of the specified directory.
     * NB:
     *
     * @param directory The path of the directory where the archive will be created. eg. c:/temp
     * @param os OutputStream stream for writing result
     * @throws ZipArchiveException If anything goes wrong
     */
    public static void createZip(File directory, OutputStream os, Map<String, String> renameTo)  {

        try {
            try (BufferedOutputStream bOut = new BufferedOutputStream(os);
                 ZipArchiveOutputStream tOut = new ZipArchiveOutputStream(bOut) ) {
                addFileToZip(tOut, directory, "", renameTo);
            }
        }
        catch (ZipArchiveException e) {
            log.error("Zipping error", e);
            throw e;
        }
        catch (Throwable th) {
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
    private static void addFileToZip(ZipArchiveOutputStream zOut, File f, String base, Map<String, String> renameMapping) throws IOException {
        String entryName = base + f.getName();
        if (renameMapping.containsKey(entryName)) {
            entryName = renameMapping.get(entryName);
        }
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
                    addFileToZip(zOut, child.getAbsoluteFile(), entryName + "/", renameMapping);
                }
            }
        }
    }

    public static File createTargetFile(File zipDestinationFolder, String name) {
        File destinationFile = new File(zipDestinationFolder, name);
        if (name.endsWith(File.separator)) {
            if (!destinationFile.isDirectory() && !destinationFile.mkdirs()) {
                throw new RuntimeException("Error creating temp directory:" + destinationFile.getPath());
            }
            return destinationFile;
        }
        // TODO 2019-06-27 what is that?
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

    public static class MyZipFile extends ZipFile implements Cloneable, AutoCloseable {

        public MyZipFile(File f) throws IOException {
            super(f);
        }
    }

    public static List<String> validate(File archiveFile, Function<ZipEntry, ValidationResult> validateZip) {

        log.debug("Start validating archive file");
        log.debug("'\tzip archive file: {}", archiveFile.getAbsolutePath());
        log.debug("'\t\texists: {}", archiveFile.exists());
        log.debug("'\t\tis writable: {}", archiveFile.canWrite());
        log.debug("'\t\tis readable: {}", archiveFile.canRead());
        List<String> errors = new ArrayList<>();
        try (MyZipFile zipFile = new MyZipFile(archiveFile)) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry zipEntry = entries.nextElement();
                if (zipEntry.isDirectory()) {
                    log.debug("'\t\tzip entry: {} is directory", zipEntry.getName());
                }
                else {
                    log.debug("'\t\tzip entry: {} is file, size: {}", zipEntry.getName(), zipEntry.getSize());
                }

                ValidationResult validationResult = validateZip.apply(zipEntry);
                if (validationResult.state==State.ERROR) {
                    errors.addAll(validationResult.getErrors());
                }
            }
        }
        catch (Throwable th) {
            log.error("Unzipping error", th);
            throw new UnzipArchiveException("Unzip failed, error: " + th.toString(), th);
        }
        return errors;
    }

    public static Map<String, String> unzipFolder(File archiveFile, File zipDestinationFolder) {
        return unzipFolder(archiveFile, zipDestinationFolder, false, Collections.emptyList());
    }

    /**
     * Unzips a zip file into the given destination directory.
     *
     * The archive file MUST have a unique "root" folder. This root folder is
     * skipped when unarchiving.
     *
     */
    public static Map<String, String> unzipFolder(File archiveFile, File zipDestinationFolder, boolean useMapping, List<String> excludeFromMapping) {
        return unzipFolder(archiveFile, zipDestinationFolder, useMapping, excludeFromMapping, true);
    }

    public static Map<String, String> unzipFolder(File archiveFile, File zipDestinationFolder, boolean useMapping, List<String> excludeFromMapping, boolean debug) {

        if (debug) {
            log.debug("Start unzipping archive file");
            log.debug("'\tzip archive file: {}", archiveFile.getAbsolutePath());
            log.debug("'\t\texists: {}", archiveFile.exists());
            log.debug("'\t\tis writable: {}", archiveFile.canWrite());
            log.debug("'\t\tis readable: {}", archiveFile.canRead());
            log.debug("'\ttarget dir: {}", zipDestinationFolder.getAbsolutePath());
            log.debug("'\t\texists: {}", zipDestinationFolder.exists());
            log.debug("'\t\tis writable: {}", zipDestinationFolder.canWrite());
        }

        try (MyZipFile zipFile = new MyZipFile(archiveFile)) {

            Map<String, String> mapping = new HashMap<>();

            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry zipEntry = entries.nextElement();
                if (debug) {
                    if (zipEntry.isDirectory()) {
                        log.debug("'\t\tzip entry: {} is directory", zipEntry.getName());
                    }
                    else {
                        log.debug("'\t\tzip entry: {} is file, size: {}", zipEntry.getName(), zipEntry.getSize());
                    }
                }

                String name = zipEntry.getName();
                if (zipEntry.isDirectory()) {
                    if (name.endsWith("/")|| name.endsWith("\\")) {
                        name = name.substring(0, name.length()-1);
                    }

                    File newDir = new File(zipDestinationFolder, name);
                    if (debug) {
                        log.debug("'\t\t\tcreate dirs in {}", newDir.getAbsolutePath());
                    }
                    Files.createDirectories(newDir.toPath());
                }
                else {
                    String resultName;
                    File f = new File(name);
                    if (useMapping && !excludeFromMapping.contains(f.getName())) {
                        final File parentFile = f.getParentFile();
                        if (parentFile !=null) {
                            File trgDir = new File(zipDestinationFolder, parentFile.getPath());

                            Files.createDirectories(trgDir.toPath());
                            File d = File.createTempFile("doc-", ".bin", trgDir);
                            resultName = new File(parentFile, d.getName()).getPath();
                        }
                        else {
                            File d = File.createTempFile("doc-", ".bin");
                            resultName = d.getName();
                        }
                        mapping.put(resultName, name);
                    }
                    else {
                        resultName = name;
                    }
                    File destinationFile = createTargetFile(zipDestinationFolder, resultName);
                    if (!destinationFile.getParentFile().exists()) {
                        Files.createDirectories(destinationFile.getParentFile().toPath());
                    }
                    if (debug) {
                        log.debug("'\t\t\tcopy content of zip entry to file {}", destinationFile.getAbsolutePath());
                    }
                    try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                        FileUtils.copyInputStreamToFile(inputStream, destinationFile);
                    }
                }
            }
            return mapping;
        }
        catch (Throwable th) {
            log.error("Unzipping error", th);
            throw new UnzipArchiveException("Unzip failed, error: " + th.toString(), th);
        }
    }
}
