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

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.ZipUtils;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;

import static ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService.VALIDATE_ZIP_FUNCTION;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 6/5/2019
 * Time: 12:24 PM
 */
public class TestZipUtils {

    // test how validator is working
    @Test
    public void validateZip(@TempDir File dir) throws IOException {
        final File tempZipFile = File.createTempFile("temp-zip-file-", ".zip", dir);
        try (FileOutputStream fos = new FileOutputStream(tempZipFile);
             InputStream is = TestZipUtils.class.getResourceAsStream("/bin/test-zip.zip")) {
            assertNotNull(is);
            FileCopyUtils.copy(is, fos);
        }
        List<String> errors = ZipUtils.validate(tempZipFile, VALIDATE_ZIP_FUNCTION);
        System.out.println(errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    public void testCreateZip(@TempDir File tempDir) throws IOException {
        Path zip = testCreateZipInternal(tempDir.toPath());
        Files.copy(zip, File.createTempFile("copy-", ".zip", new File("D://2")).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void testCreateZipInIimfs(@TempDir File tempDir) throws IOException {
        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
        Path temp = fs.getPath("/temp");
        Path actualTemp = Files.createDirectory(temp);

        Path zip = testCreateZipInternal(actualTemp);
        Files.copy(zip, File.createTempFile("copy-", ".zip", new File("D://2")).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path testCreateZipInternal(Path actualTemp) throws IOException {

        Path zip = actualTemp.resolve("zip");
        Files.createDirectory(zip);
        Path text = zip.resolve("aaa.txt");
        Files.writeString(text, "hello world", StandardCharsets.UTF_8);

        Path zipFile = actualTemp.resolve("zip.zip");
        ZipUtils.createZip(zip, zipFile, Collections.emptyMap());

        List<String> errors = ZipUtils.validate(zipFile, VALIDATE_ZIP_FUNCTION);
        assertTrue(errors.isEmpty());
        return zipFile;
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = new FileInputStream("docs-dev/error/params_1") ) {
            IOUtils.copy(is, baos);
        }
        final byte[] origin = baos.toByteArray();
        byte[] bytes = zip(origin, "params.yaml");
        System.out.println(S.f("Origin: %d\nZipped: %d", origin.length, bytes.length ));
    }

    private static byte[] zip(byte[] data, String filename) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(bos);

        ZipArchiveEntry entry = new ZipArchiveEntry(filename);
        entry.setSize(data.length);
        zos.putArchiveEntry(entry);
        zos.write(data);
        zos.closeArchiveEntry();

        zos.close();
        bos.close();

        return bos.toByteArray();
    }
}
