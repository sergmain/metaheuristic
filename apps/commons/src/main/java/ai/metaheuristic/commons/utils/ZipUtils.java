/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;

import static java.nio.file.StandardOpenOption.*;

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
        try {
            final Path zipPath = zipFile.toPath();
            final Path directoryPath = directory.toPath();
            createZip(directoryPath, zipPath, renameTo);
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
     * @param directoryPath The path of the directory where the archive will be created. eg. c:/temp
     * @param zipPath File for output stream for writing result
     * @throws ZipArchiveException If anything goes wrong
     */
    public static void createZip(Path directoryPath, Path zipPath, Map<String, String> renameTo)  {
        try {
//            Path created = Files.createFile(zipPath);
            try (SeekableByteChannel seekableByteChannel = Files.newByteChannel(zipPath, EnumSet.of(CREATE, WRITE, READ, TRUNCATE_EXISTING));
                 ZipArchiveOutputStream tOut = new ZipArchiveOutputStream(seekableByteChannel) ) {
                addFileToZip(tOut, directoryPath, "", renameTo);
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

    @SneakyThrows
    private static void addFileToZip(ZipArchiveOutputStream zOut, Path path, String base, Map<String, String> renameMapping) {
        final String fileName = path.getFileName().toString();
        String entryName = base + fileName;
        if (renameMapping.containsKey(entryName)) {
            entryName = renameMapping.get(entryName);
        }
        ZipArchiveEntry zipEntry = new ZipArchiveEntry(path, entryName);

        zOut.putArchiveEntry(zipEntry);

        if (!Files.isDirectory(path)) {
            try (SeekableByteChannel inChannel = Files.newByteChannel(path, Collections.emptySet())) {

                final ByteBuffer buffer = ByteBuffer.wrap(new byte[8192]);
                int n;
                while (-1 != (n = inChannel.read(buffer))) {
                    zOut.write(buffer.array(), 0, n);
                }
                zOut.closeArchiveEntry();
            }
        } else {
            zOut.closeArchiveEntry();
            final String newEntryName = entryName;
            Files.list(path).forEach(child-> addFileToZip(zOut, child, newEntryName + "/", renameMapping));
        }
    }

    @SneakyThrows
    public static Path createTargetFile(Path zipDestinationFolder, String name) {
        Path destinationPath = zipDestinationFolder.resolve(name);
        if (name.endsWith(File.separator)) {
            if (!Files.isDirectory(destinationPath)) {
                Files.createDirectories(destinationPath);
            }
            return destinationPath;
        }
        // TODO 2019-06-27 what is that?
        else if (name.indexOf(File.separatorChar) != -1) {
            // Create the the parent directory if it doesn't exist
//            if (true) throw new IllegalStateException("need investigate this case");

            Path parentFolder = destinationPath.getParent();
            if (!Files.isDirectory(parentFolder)) {
                Files.createDirectories(parentFolder);
            }
        }
        return destinationPath;
    }

    public static class MyZipFile extends ZipFile implements AutoCloseable {

        public MyZipFile(SeekableByteChannel inChannel) throws IOException {
            super(inChannel);
        }
    }

    public static List<String> validate(File archiveFile, Function<ZipEntry, ValidationResult> validateZip) {
        return validate(archiveFile.toPath(), validateZip);
    }

    @SneakyThrows
    public static List<String> validate(Path archivePath, Function<ZipEntry, ValidationResult> validateZip) {

        log.debug("Start validating archive file");
        log.debug("'\tzip archive file: {}", archivePath.normalize());
        log.debug("'\t\texists: {}", Files.exists(archivePath));
        log.debug("'\t\tis writable: {}", Files.isWritable(archivePath));
        log.debug("'\t\tis readable: {}", Files.isReadable(archivePath));
        List<String> errors = new ArrayList<>();

        try (SeekableByteChannel inChannel = Files.newByteChannel(archivePath, Collections.emptySet()); MyZipFile zipFile = new MyZipFile(inChannel)) {
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
            throw new UnzipArchiveException("Unzip failed, error: " + th.getMessage(), th);
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
        final Path archivePath = archiveFile.toPath();
        final Path zipDestinationFolderPath = zipDestinationFolder.toPath();

        return unzipFolder(archivePath, zipDestinationFolderPath, useMapping, excludeFromMapping, true);
    }

    // TODO P3 2022-03-31 add a support of virtual FileSystem after fixing https://issues.apache.org/jira/browse/COMPRESS-365
    //  see also
    //  https://github.com/google/jimfs
    //  https://stackoverflow.com/a/30395017/2672202

    public static Map<String, String> unzipFolder(Path archivePath, Path zipDestinationFolderPath, boolean useMapping, List<String> excludeFromMapping, boolean debug) {

        if (debug) {
            log.debug("Start unzipping archive file");
            log.debug("'\tzip archive file: {}", archivePath.normalize());
            log.debug("'\t\texists: {}", Files.exists(archivePath));
            log.debug("'\t\tis writable: {}", Files.isWritable(archivePath));
            log.debug("'\t\tis readable: {}", Files.isReadable(archivePath));
            log.debug("'\ttarget dir: {}", zipDestinationFolderPath.normalize());
            log.debug("'\t\texists: {}", Files.exists(zipDestinationFolderPath));
            log.debug("'\t\tis writable: {}", Files.isWritable(zipDestinationFolderPath));
        }

        try (SeekableByteChannel inChannel = Files.newByteChannel(archivePath, Collections.emptySet()); MyZipFile zipFile = new MyZipFile(inChannel)) {

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

                    Path newDir = zipDestinationFolderPath.resolve(name);
                    if (debug) {
                        log.debug("'\t\t\tcreate dirs in {}", newDir.toFile().getAbsolutePath());
                    }
                    Files.createDirectories(newDir);
                }
                else {
                    String resultName;
                    File f = new File(name);
                    if (useMapping && !excludeFromMapping.contains(f.getName())) {
                        final File parentFile = f.getParentFile();
                        if (parentFile !=null) {
                            Path trgDir = zipDestinationFolderPath.resolve(parentFile.getPath());

                            Files.createDirectories(trgDir);
                            Path d = Files.createTempFile(trgDir, "doc-", ".bin");
                            resultName = d.getName(d.getNameCount()-1).toFile().getPath();
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
                    Path destinationPath = createTargetFile(zipDestinationFolderPath, resultName);
                    Files.createDirectories(destinationPath.getParent());
                    if (debug) {
                        log.debug("'\t\t\tcopy content of zip entry to file {}", destinationPath);
                    }
                    try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                        try (SeekableByteChannel outChannel = Files.newByteChannel(destinationPath, EnumSet.of(CREATE, WRITE, READ, TRUNCATE_EXISTING, SYNC))) {
                            int n=0;
                            int count = 0;
                            byte[] bytes = new byte[8192];
                            while ((n=inputStream.read(bytes))!=-1) {
                                final ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, n);
                                outChannel.write(buffer);
                                count += n;
                            }
                            int total = count;
                        }
                    }
                }
            }
            return mapping;
        }
        catch (Throwable th) {
            log.error("Unzipping error", th);
            throw new UnzipArchiveException("Unzip failed, error: " + th.getMessage(), th);
        }
    }
}
