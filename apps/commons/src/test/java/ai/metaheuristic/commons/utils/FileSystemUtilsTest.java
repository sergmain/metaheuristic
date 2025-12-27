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

package ai.metaheuristic.commons.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author Sergio Lissner
 * Date: 6/26/2023
 * Time: 8:37 PM
 */
public class FileSystemUtilsTest {

    @Test
    public void test_writeStringToFileWithSync(@TempDir Path temp) {
        final Path file = temp.resolve("test-file.txt");
        final String data = "test string";

        assertDoesNotThrow(()->FileSystemUtils.writeStringToFileWithSync(file, data, StandardCharsets.UTF_8));
    }

}
