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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.commons.utils.ZipUtils;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.FileCopyUtils;
import org.thymeleaf.util.StringUtils;

import java.io.*;
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

import static ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService.VALIDATE_ZIP_FUNCTION;
import static java.nio.file.StandardOpenOption.READ;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 6/5/2019
 * Time: 12:24 PM
 */
public class TestZipUtils {

    @Test
    public void testBuffer() throws IOException {
        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
        Path temp = fs.getPath("/temp");
        Path actualTemp = Files.createDirectory(temp);

        Path text = actualTemp.resolve("test.txt");
        Files.writeString(text, StringUtils.randomAlphanumeric(10000), StandardCharsets.UTF_8);

        assertTimeoutPreemptively(Duration.ofSeconds(1), ()-> {
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

    // test how validator is working
    @Test
    public void validateZip(@TempDir File dir) throws IOException {
        final File tempZipFile = File.createTempFile("temp-zip-file-", ".zip", dir);
        try (FileOutputStream fos = new FileOutputStream(tempZipFile);
             InputStream is = TestZipUtils.class.getResourceAsStream("/bin/test-zip.zip")) {
            assertNotNull(is);
            FileCopyUtils.copy(is, fos);
        }
        List<String> errors = ZipUtils.validate(tempZipFile.toPath(), VALIDATE_ZIP_FUNCTION);
        System.out.println(errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    public void testCreateZip(@TempDir File tempDir) throws IOException {
        Path zip = testCreateZipInternal(tempDir.toPath());
        Files.copy(zip, File.createTempFile("copy-", ".zip", new File("D://2")).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void testCreateZipInIimfs() throws IOException {
        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
        Path temp = fs.getPath("/temp");
        Path actualTemp = Files.createDirectory(temp);

        Path zip = testCreateZipInternal(actualTemp);
        Files.copy(zip, File.createTempFile("copy-", ".zip", new File("D://2")).toPath(), StandardCopyOption.REPLACE_EXISTING);
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

        System.out.println("renamedTo:");
        Path zip1 = actualTemp.resolve("zip1");
        Files.createDirectories(zip1);
        Map<String, String> renamedTo = ZipUtils.unzipFolder(zipFile, zip1, true, List.of(), true);
        renamedTo.forEach((k,v)->System.out.printf("%-10s %s\n", k, v));
        assertEquals(6, renamedTo.size());

//        zip\日本語\doc-8313437611003610974.bin zip/日本語/日本語.txt
//        zip\ИИИ\doc-5007186664585605577.bin zip/ИИИ/ИИИ.txt
//        zip\doc-18166647090924731452.bin zip/ИИИ.txt
//        zip\natürlich\doc-5835525648914091765.bin zip/natürlich/natürlich.txt
//        zip\doc-4407121262801093426.bin zip/natürlich.txt
//        zip\doc-1651581947851693815.bin zip/日本語.txt

        assertTrue(renamedTo.containsValue("zip/日本語.txt"));
        String key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/日本語.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ File.separatorChar));

        assertTrue(renamedTo.containsValue("zip/日本語/日本語.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/日本語/日本語.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ File.separatorChar));

        assertTrue(renamedTo.containsValue("zip/natürlich.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/natürlich.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ File.separatorChar));

        assertTrue(renamedTo.containsValue("zip/natürlich/natürlich.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/natürlich/natürlich.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ File.separatorChar));

        assertTrue(renamedTo.containsValue("zip/ИИИ.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/ИИИ.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ File.separatorChar));

        assertTrue(renamedTo.containsValue("zip/ИИИ/ИИИ.txt"));
        key = renamedTo.entrySet().stream().filter(o->o.getValue().equals("zip/ИИИ/ИИИ.txt")).findFirst().map(Map.Entry::getKey).orElseThrow();
        assertTrue(key.startsWith("zip"+ File.separatorChar));

        List<String> errors = ZipUtils.validate(zipFile, VALIDATE_ZIP_FUNCTION);
//        assertTrue(errors.isEmpty(), String.join("\n", errors));
        return zipFile;
    }

    private static void fillFile(Path zip, String other, boolean createSubPath) throws IOException {
        Path text = zip.resolve(other+".txt");
        Files.writeString(text, "hello world " + other + "\n" + StringUtils.randomAlphanumeric(ZipUtils.BUFFER_SIZE + 256), StandardCharsets.UTF_8);
        if (createSubPath) {
            Path subPath = zip.resolve(other);
            Files.createDirectories(subPath);
            fillFile(subPath, other, false);
        }
    }


}
