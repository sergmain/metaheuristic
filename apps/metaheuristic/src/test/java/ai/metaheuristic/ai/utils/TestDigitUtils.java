/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.commons.utils.DirUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestDigitUtils {

    public static final int PATH_COUNT = 1000;
    public static final int FILE_SIZE = 1_000_000;

    @Test
    public void testPower() {

        assertEquals(10_000, DigitUtils.DIV, "Value of DigitUtils.DIV was changed, must be always 10_000");
        assertEquals(0, DigitUtils.getPower(42).power7);
        assertEquals(42, DigitUtils.getPower(42).power4);
        assertEquals(1, DigitUtils.getPower(10042).power7);
        assertEquals(42, DigitUtils.getPower(10042).power4);
        assertEquals(100, DigitUtils.getPower(1009999).power7);
        assertEquals(9999, DigitUtils.getPower(1009999).power4);
    }

    @Disabled
    @Test
    public void benchmark() throws IOException {

//        Path temp = DirUtils.createMhTempPath("benchmarking-");
        Path base = Path.of("D:\\Temp");

        Path temp = DirUtils.createMhTempPath(base, "benchmarking-");
        assertNotNull(temp);

        System.out.println("prepare src");
        Path[] srcFiles = new Path[PATH_COUNT];
        fillTrgFiles(temp,"temp-src-file-", srcFiles);

        System.out.println("prepare trg");
        Path[] files = new Path[PATH_COUNT];
        fillTrgFiles(temp,"temp-file-", files);

        System.out.println("Start benchmarking");;
        benchmark(srcFiles, files, TestDigitUtils::processAsCopyFunc);
        benchmark(srcFiles, files, TestDigitUtils::processAsStreamFunc);
    }

    @SneakyThrows
    public static void processAsCopyFunc(Path srcFile, Path file) {
        try (InputStream is = Files.newInputStream(srcFile)) {
            Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SneakyThrows
    public static void processAsStreamFunc(Path srcFile, Path file) {
        try (InputStream is = Files.newInputStream(srcFile); OutputStream os = Files.newOutputStream(file)) {
            IOUtils.copyLarge(is, os);
        }
    }

    private void benchmark(Path[] srcFiles, Path[] trgFiles, BiConsumer<Path, Path> processAsCopyFunc) {
        long mills = System.currentTimeMillis();
        for (int i = 0; i < srcFiles.length; i++) {
            final Path srcFile = srcFiles[i];
            final Path file = trgFiles[i];
            processAsCopyFunc.accept(srcFile, file);
        }
        System.out.println("Files.copy() was finished for " + (System.currentTimeMillis()-mills));
    }

    private void fillTrgFiles(Path temp, String prefix, Path[] files) throws IOException {
        for (int i = 0; i < files.length; i++) {
            files[i] = temp.resolve(prefix+i+".bin");
            Files.write(files[i], createRandomContent(FILE_SIZE));
        }
    }

    public static final Random r = new Random();
    private byte[] createRandomContent(int size) {
        byte[] bytes = new byte[size];
        r.nextBytes(bytes);
        return bytes;
    }
}
