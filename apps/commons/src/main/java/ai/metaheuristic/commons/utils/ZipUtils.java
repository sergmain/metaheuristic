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
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
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

    public static final int BUFFER_SIZE = 4096 * 8;

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

    @Deprecated(forRemoval = true)
    public static void createZip(File directory, File zipFile)  {
        createZip(directory, zipFile, Collections.emptyMap());
    }

    @Deprecated(forRemoval = true)
    public static void createZip(File directory, File zipFile, Map<String, String> renameTo)  {
        try {
            final Path zipPath = zipFile.toPath();
            final Path directoryPath = directory.toPath();
            createZip(List.of(directoryPath), zipPath, renameTo);
        }
        catch (ZipArchiveException e) {
            log.error("Zipping error, " + e.getMessage(), e);
            throw e;
        }
        catch (Throwable th) {
            log.error("Zipping error", th);
            throw new ZipArchiveException("Zip failed, " + th.getMessage(), th);
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
    @Deprecated(forRemoval = true)
    public static void createZip(File directory, OutputStream os, Map<String, String> renameTo)  {

        try {
            final File mhTempDir = DirUtils.createMhTempDir("zip-dir-");
            File tempFile = new File(mhTempDir, "zip.zip");
            createZip(List.of(tempFile.toPath()), directory.toPath(), renameTo);

            try (FileInputStream fis = new FileInputStream(tempFile);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 BufferedOutputStream bos = new BufferedOutputStream(os)) {
                IOUtils.copy(bis, bos);
            }
        }
        catch (ZipArchiveException e) {
            log.error("Zipping error, " + e.getMessage(), e);
            throw e;
        }
        catch (Throwable th) {
            log.error("Zipping error", th);
            throw new ZipArchiveException("Zip failed, " + th.getMessage(), th);
        }
    }

    public static void createZip(Path directoryPath, Path zipPath) {
        createZip(List.of(directoryPath), zipPath, Collections.emptyMap());
    }

    public static void createZip(List<Path> directoryPaths, Path zipPath) {
        createZip(directoryPaths, zipPath, Collections.emptyMap());
    }

    /**
     * Creates a zip file at the specified path with the contents of the specified files/directories
     * NB:
     *
     * @param directoryPaths The paths of the files/directories which will be added to zip
     * @param zipPath File for output stream for writing result
     * @throws ZipArchiveException If anything goes wrong
     */
    public static void createZip(List<Path> directoryPaths, Path zipPath, Map<String, String> renameTo)  {
        try {
            try (SeekableByteChannel seekableByteChannel = Files.newByteChannel(zipPath, EnumSet.of(CREATE, WRITE, READ, TRUNCATE_EXISTING));
                 ZipArchiveOutputStream tOut = new ZipArchiveOutputStream(seekableByteChannel) ) {
                for (Path directoryPath : directoryPaths) {
                    addFileToZip(tOut, directoryPath, "", renameTo);
                }
            }
        }
        catch (ZipArchiveException e) {
            log.error("Zipping error, " + e.getMessage(), e);
            throw e;
        }
        catch (Throwable th) {
            log.error("Zipping error", th);
            throw new ZipArchiveException("Zip failed, " + th.getMessage(), th);
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
            try (ReadableByteChannel rbc = Files.newByteChannel(path, EnumSet.of(READ))) {

                final ByteBuffer buffer = ByteBuffer.wrap(new byte[BUFFER_SIZE]);
                int n;
                while (-1 != (n = rbc.read(buffer))) {
                    zOut.write(buffer.array(), 0, n);
                    buffer.clear();
                }
                zOut.closeArchiveEntry();
            }
        } else {
            zOut.closeArchiveEntry();
            final String newEntryName = entryName;
            // do not remove try(Stream<Path>){}
            try (final Stream<Path> list = Files.list(path) ) {
                list.forEach(child -> addFileToZip(zOut, child, newEntryName + "/", renameMapping));
            }
        }
    }

    @Deprecated(forRemoval = true)
    public static File createTargetFile(File zipDestinationFolder, String name) {
        return createTargetFile(zipDestinationFolder.toPath(), name).toFile();
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

    @Deprecated(forRemoval = true)
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

        try (SeekableByteChannel inChannel = Files.newByteChannel(archivePath, EnumSet.of(READ)); MyZipFile zipFile = new MyZipFile(inChannel)) {
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

    @Deprecated(forRemoval = true)
    public static Map<String, String> unzipFolder(File archiveFile, File zipDestinationFolder) {
        return unzipFolder(archiveFile.toPath(), zipDestinationFolder.toPath(), false, Collections.emptyList(), true);
    }

    /**
     * Unzips a zip file into the given destination directory.
     *
     * The archive file MUST have a unique "root" folder. This root folder is
     * skipped when unarchiving.
     *
     */
    @SneakyThrows
    @Deprecated(forRemoval = true)
    public static Map<String, String> unzipFolder(File archiveFile, File zipDestinationFolder, boolean useMapping, List<String> excludeFromMapping) {
        final Path archivePath = archiveFile.toPath();
        final Path zipDestinationFolderPath = zipDestinationFolder.toPath();
        if (!zipDestinationFolder.exists()) {
            Files.createDirectories(zipDestinationFolderPath);
        }
        return unzipFolder(archivePath, zipDestinationFolderPath, useMapping, excludeFromMapping, true);
    }

    // TODO P3 2022-03-31 add a support of virtual FileSystem after fixing https://issues.apache.org/jira/browse/COMPRESS-365
    //  see also
    //  https://github.com/google/jimfs
    //  https://stackoverflow.com/a/30395017/2672202

    public static Map<String, String> unzipFolder(Path archivePath, Path zipDestinationFolderPath) {
        return unzipFolder(archivePath, zipDestinationFolderPath, false, Collections.emptyList(), true);
    }

    public static Map<String, String> unzipFolder(Path archivePath, Path zipDestinationFolderPath, boolean useMapping, List<String> excludeFromMapping, boolean debug) {

        if (debug && log.isDebugEnabled()) {
            log.debug("Start unzipping archive file");
            log.debug("'\tzip archive file: {}", archivePath.normalize());
            log.debug("'\t\texists: {}", Files.exists(archivePath));
            log.debug("'\t\tis writable: {}", Files.isWritable(archivePath));
            log.debug("'\t\tis readable: {}", Files.isReadable(archivePath));
            log.debug("'\ttarget dir: {}", zipDestinationFolderPath.normalize());
            log.debug("'\t\texists: {}", Files.exists(zipDestinationFolderPath));
            log.debug("'\t\tis writable: {}", Files.isWritable(zipDestinationFolderPath));
        }

        try (SeekableByteChannel inChannel = Files.newByteChannel(archivePath, EnumSet.of(READ)); MyZipFile zipFile = new MyZipFile(inChannel)) {

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
                        log.debug("'\t\t\tcreate dirs in {}", newDir.normalize());
                    }
                    Files.createDirectories(newDir);
                }
                else {
                    String resultName;
                    File f = new File(name);
                    if (useMapping && !excludeFromMapping.contains(f.getName())) {
                        final Path parentPath = zipDestinationFolderPath.getFileSystem().getPath(name).getParent();
                        if (parentPath !=null) {
                            Path trgDir = zipDestinationFolderPath.resolve(parentPath);
                            Files.createDirectories(trgDir);

                            Path d = Files.createTempFile(trgDir, "doc-", ".bin");
                            resultName = parentPath.resolve(d.getFileName()).toString();
                        }
                        else {
                            Path d = Files.createTempFile(zipDestinationFolderPath, "doc-", ".bin");
                            resultName = d.getFileName().toString();
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
                        try (SeekableByteChannel outChannel = Files.newByteChannel(destinationPath, EnumSet.of(CREATE, WRITE, READ, TRUNCATE_EXISTING))) {
                            int n;
                            int count = 0;
                            byte[] bytes = new byte[BUFFER_SIZE];
                            while ((n=inputStream.read(bytes))!=-1) {
                                final ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, n);
                                outChannel.write(buffer);
                                count += n;
                            }
                            //noinspection unused
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
