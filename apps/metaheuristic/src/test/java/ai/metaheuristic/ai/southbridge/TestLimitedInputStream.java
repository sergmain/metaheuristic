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

package ai.metaheuristic.ai.southbridge;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Serge
 * Date: 8/29/2020
 * Time: 1:47 AM
 */
@Execution(CONCURRENT)
class TestLimitedInputStream {

    private static final long SKIP_BYTES = 15L;

    @Test
    public void test(@TempDir Path path) throws IOException {
        Path testFile = path.resolve("test-limited-input-stream.bin");
        Files.writeString(testFile, "123456789012345678901234567890", StandardCharsets.UTF_8);

        try (InputStream fis = Files.newInputStream( testFile);
            BoundedInputStream bis = BoundedInputStream.builder().setInputStream(fis).setMaxCount(5).get()) {

            // act
            long skipped = fis.skip(SKIP_BYTES);
            assertEquals(15L, skipped);
            String s = IOUtils.toString(bis, StandardCharsets.UTF_8);
            assertEquals("67890", s);
        }
    }

}
