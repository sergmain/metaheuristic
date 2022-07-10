/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.commons.S;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import lombok.SneakyThrows;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.READ;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 6/5/2019
 * Time: 12:24 PM
 */
@SuppressWarnings("UnusedReturnValue")
class ZipUtilsTest {

    private static int currentBufferSize = ZipUtils.BUFFER_SIZE + 256;
    private static char separatorChar = File.separatorChar;

    @Test
    public void testBuffer() throws IOException {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path temp = fs.getPath("/temp");
            Path actualTemp = Files.createDirectory(temp);

            Path text = actualTemp.resolve("test.txt");
            Files.writeString(text, StrUtils.randomAlphanumeric(10000), StandardCharsets.UTF_8);

            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ReadableByteChannel rbc = Files.newByteChannel(text, EnumSet.of(READ))) {

                    final ByteBuffer buffer = ByteBuffer.wrap(new byte[256]);
                    int n;
                    while (-1 != (n = rbc.read(buffer))) {
                        baos.write(buffer.array(), 0, n);
                        buffer.clear();
                    }
                }
            });
        }
    }

    // test zip with hierarchy

    @Test
    public void testCreateZip(@TempDir File tempDir) {
        currentBufferSize = ZipUtils.BUFFER_SIZE * 2;
        separatorChar = File.separatorChar;
        testCreateZipInternal(tempDir.toPath());
    }

    @Test
    public void testCreateZip_1(@TempDir File tempDir) {
        currentBufferSize = ZipUtils.BUFFER_SIZE / 2;
        separatorChar = File.separatorChar;
        testCreateZipInternal(tempDir.toPath());
    }

    @Test
    public void testCreateZipInIimfs_unix() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE * 2;
        separatorChar = '/';
        createZipInIimfs_unix(ZipUtilsTest::testCreateZipInternal);
    }

    @Test
    public void testCreateZipInIimfs_unix_1() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE / 2;
        separatorChar = '/';
        createZipInIimfs_unix(ZipUtilsTest::testCreateZipInternal);
    }

    @Test
    public void testCreateZipInIimfs_win() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE * 2;
        separatorChar = '\\';
        createZipInIimfs_win(ZipUtilsTest::testCreateZipInternal);
    }

    @Test
    public void testCreateZipInIimfs_win_1() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE / 2;
        separatorChar = '\\';
        createZipInIimfs_win(ZipUtilsTest::testCreateZipInternal);
    }

    // test zip with plain structure

    @Test
    public void testCreateZip_plain(@TempDir File tempDir) {
        currentBufferSize = ZipUtils.BUFFER_SIZE * 2;
        separatorChar = File.separatorChar;
        testCreatePlainZipInternal(tempDir.toPath());
    }

    @Test
    public void testCreateZip_1_plain(@TempDir File tempDir) {
        currentBufferSize = ZipUtils.BUFFER_SIZE / 2;
        separatorChar = File.separatorChar;
        testCreatePlainZipInternal(tempDir.toPath());
    }

    @Test
    public void testCreateZipInIimfs_unix_plain() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE * 2;
        separatorChar = '/';
        createZipInIimfs_unix(ZipUtilsTest::testCreatePlainZipInternal);
    }

    @Test
    public void testCreateZipInIimfs_unix_1_plain() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE / 2;
        separatorChar = '/';
        createZipInIimfs_unix(ZipUtilsTest::testCreatePlainZipInternal);
    }

    @Test
    public void testCreateZipInIimfs_win_plain() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE * 2;
        separatorChar = '\\';
        createZipInIimfs_win(ZipUtilsTest::testCreatePlainZipInternal);
    }

    @Test
    public void testCreateZipInIimfs_win_1_plain() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE / 2;
        separatorChar = '\\';
        createZipInIimfs_win(ZipUtilsTest::testCreatePlainZipInternal);
    }

    // test listing of name of archive

    @Test
    public void testListNameZipInIimfs_unix() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE * 2;
        separatorChar = '/';
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            // https://github.com/google/jimfs/issues/69
            // with windows configuration an absolute path isn't working rn
            Path temp = fs.getPath("temp");
            Path actualTemp = Files.createDirectory(temp);
            Path zipFile = ZipUtilsTest.testCreateZipInternal(actualTemp);
            List<String> names = ZipUtils.listNameOfEntries(zipFile);
            names.forEach(System.out::println);
            checkNames(names);
        }
    }

    @Test
    public void testListNameZipInIimfs_win() throws IOException {
        currentBufferSize = ZipUtils.BUFFER_SIZE * 2;
        separatorChar = '\\';
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.windows())) {
            // https://github.com/google/jimfs/issues/69
            // with windows configuration an absolute path isn't working rn
            Path temp = fs.getPath("temp");
            Path actualTemp = Files.createDirectory(temp);
            Path zipFile = ZipUtilsTest.testCreateZipInternal(actualTemp);
            List<String> names = ZipUtils.listNameOfEntries(zipFile);
            names.forEach(System.out::println);
            checkNames(names);
        }
    }

    private void checkNames(List<String> names) {
        assertEquals(6, names.size());
        assertTrue(names.contains("zip/natürlich/natürlich.txt"));
        assertTrue(names.contains("zip/natürlich.txt"));
        assertTrue(names.contains("zip/ИИИ/ИИИ.txt"));
        assertTrue(names.contains("zip/ИИИ.txt"));
        assertTrue(names.contains("zip/日本語/日本語.txt"));
        assertTrue(names.contains("zip/日本語.txt"));
    }


    // ====================

    private static void createZipInIimfs_unix(Consumer<Path> createZipFunc) throws IOException {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path temp = fs.getPath("/temp");
            Path actualTemp = Files.createDirectory(temp);

            createZipFunc.accept(actualTemp);
        }
    }

    private static void createZipInIimfs_win(Consumer<Path> createZipFunc) throws IOException {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.windows())) {
            // https://github.com/google/jimfs/issues/69
            // with windows configuration an absolute path isn't working rn
            Path temp = fs.getPath("temp");
            Path actualTemp = Files.createDirectory(temp);

            createZipFunc.accept(actualTemp);
        }
    }

    @SneakyThrows
    private static Path testCreateZipInternal(Path actualTemp) {

        // ИИИ, 日本語, natürlich
        Path zip = actualTemp.resolve("zip");
        Files.createDirectory(zip);
        fillFile(zip, "ИИИ", true);
        fillFile(zip, "日本語", true);
        fillFile(zip, "natürlich", true);

        Path zipFile = actualTemp.resolve("zip.zip");
        ZipUtils.createZip(zip, zipFile);

        final Path target = Files.createTempFile("copy-", ".zip");
        System.out.println("zip storead at " + target.toFile().getAbsolutePath());
        Files.copy(zipFile, target, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("renamedTo:");
        Path zip1 = actualTemp.resolve("zip1");
        Files.createDirectories(zip1);
        Map<String, String> renamedTo = ZipUtils.unzipFolder(zipFile, zip1, true, List.of(), true);

        List<Path> paths = PathUtils.walk(zip1, FileFileFilter.INSTANCE, Integer.MAX_VALUE, false).toList();
        assertEquals(6, paths.size(), paths.stream().map(p-> S.f("%-50s %s", p.toString(), getSize(p))).collect(Collectors.joining("\n")));

        for (Path path : paths) {
            assertNotEquals(0, Files.size(path), path.toString());
        }

        renamedTo.forEach((k,v)->System.out.printf("%-40s %s\n", k, v));
        assertEquals(6, renamedTo.size());

//        zip\日本語\doc-8313437611003610974.bin zip/日本語/日本語.txt
//        zip\ИИИ\doc-5007186664585605577.bin zip/ИИИ/ИИИ.txt
//        zip\doc-18166647090924731452.bin zip/ИИИ.txt
//        zip\natürlich\doc-5835525648914091765.bin zip/natürlich/natürlich.txt
//        zip\doc-4407121262801093426.bin zip/natürlich.txt
//        zip\doc-1651581947851693815.bin zip/日本語.txt

        assertTrue(renamedTo.containsValue("zip/日本語.txt"));
        String key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/日本語.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ separatorChar), key);

        assertTrue(renamedTo.containsValue("zip/日本語/日本語.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/日本語/日本語.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ separatorChar));

        assertTrue(renamedTo.containsValue("zip/natürlich.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/natürlich.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ separatorChar));

        assertTrue(renamedTo.containsValue("zip/natürlich/natürlich.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/natürlich/natürlich.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ separatorChar));

        assertTrue(renamedTo.containsValue("zip/ИИИ.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/ИИИ.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ separatorChar));

        assertTrue(renamedTo.containsValue("zip/ИИИ/ИИИ.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/ИИИ/ИИИ.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ separatorChar));

//        List<String> errors = ZipUtils.validate(zipFile, VALIDATE_ZIP_FUNCTION);
//        assertTrue(errors.isEmpty(), String.join("\n", errors));
        return zipFile;
    }

    @SneakyThrows
    private static Path testCreatePlainZipInternal(Path actualTemp) {

        // ИИИ, 日本語, natürlich
        Path zip = actualTemp.resolve("zip");
        Files.createDirectory(zip);
        Path ru = fillFile(zip, "ИИИ", false);
        Path ja = fillFile(zip, "日本語", false);
        Path de = fillFile(zip, "natürlich", false);

        Path zipFile = actualTemp.resolve("zip.zip");
        ZipUtils.createZip(List.of(ru, ja, de), zipFile);

        final Path target = Files.createTempFile("copy-", ".zip");
        System.out.println("zip storead at " + target.toFile().getAbsolutePath());
        Files.copy(zipFile, target, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("renamedTo:");
        Path zip1 = actualTemp.resolve("zip1");
        Files.createDirectories(zip1);
        Map<String, String> renamedTo = ZipUtils.unzipFolder(zipFile, zip1, true, List.of(), true);

        List<Path> paths = PathUtils.walk(zip1, FileFileFilter.INSTANCE, Integer.MAX_VALUE, false).toList();
        assertEquals(3, paths.size(), paths.stream().map(p-> S.f("%-50s %s", p.toString(), getSize(p))).collect(Collectors.joining("\n")));

        for (Path path : paths) {
            assertNotEquals(0, Files.size(path), path.toString());
        }

        renamedTo.forEach((k,v)->System.out.printf("%-40s %s\n", k, v));
        assertEquals(3, renamedTo.size());

        assertTrue(renamedTo.containsValue("日本語.txt"));
        String key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("日本語.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("doc-") && key.endsWith(".bin"));

        assertTrue(renamedTo.containsValue("natürlich.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("natürlich.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("doc-") && key.endsWith(".bin"));

        assertTrue(renamedTo.containsValue("ИИИ.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("ИИИ.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("doc-") && key.endsWith(".bin"));

        return zipFile;
    }

    @SneakyThrows
    private static long getSize(Path p) {
        return Files.size(p);
    }

    private static Path fillFile(Path zip, String other, boolean createSubPath) throws IOException {
        Path text = zip.resolve(other+".txt");
        Files.writeString(text, "hello world " + other + "\n" + StrUtils.randomAlphanumeric(currentBufferSize), StandardCharsets.UTF_8);
        if (createSubPath) {
            Path subPath = zip.resolve(other);
            Files.createDirectories(subPath);
            fillFile(subPath, other, false);
        }
        return text;
    }


}
