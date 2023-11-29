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

package ai.metaheuristic.ai.southbridge;

import ai.metaheuristic.commons.CommonConsts;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 8/29/2020
 * Time: 1:47 AM
 */
public class TestLimitedInputStream {

    private static final long SKIP_BYTES = 15L;
    private Path testFile = null;

    @BeforeEach
    public void before() throws IOException {
        testFile = Files.createTempFile("test-limited-input-stream", CommonConsts.BIN_EXT);
    }

    @AfterEach
    public void after() throws IOException {
        if (testFile!=null) {
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    public void test() throws IOException {
        Files.writeString(testFile, "123456789012345678901234567890", StandardCharsets.UTF_8);

        try (InputStream fis = Files.newInputStream( testFile);
            BoundedInputStream bis = new BoundedInputStream(fis, 5)) {
            long skipped = fis.skip(SKIP_BYTES);
            assertEquals(15L, skipped);
            String s = IOUtils.toString(bis, StandardCharsets.UTF_8);
            assertEquals("67890", s);
        }
    }

}
