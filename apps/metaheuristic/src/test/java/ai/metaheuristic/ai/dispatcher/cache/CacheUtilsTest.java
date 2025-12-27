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

package ai.metaheuristic.ai.dispatcher.cache;

import ai.metaheuristic.ai.dispatcher.data.CacheData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 12/27/2025
 * Time: 12:56 PM
 */
@Execution(CONCURRENT)
class CacheUtilsTest {

    @Test
    @DisplayName("SHA256 and length calculated correctly for simple string")
    void testSimpleString() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        InputStream is = new ByteArrayInputStream(data);

        CacheData.Sha256PlusLength result = CacheUtils.getSha256PlusLength(is);

        assertEquals(13, result.getLength());
        // SHA256 of "Hello, World!"
        assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f",
            result.getSha256().toLowerCase());
    }

    @Test
    @DisplayName("Empty input returns zero length with empty hash")
    void testEmptyInput() {
        InputStream is = new ByteArrayInputStream(new byte[0]);

        CacheData.Sha256PlusLength result = CacheUtils.getSha256PlusLength(is);

        assertEquals(0, result.getLength());
        // SHA256 of empty string
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result.getSha256().toLowerCase());
    }

    @Test
    @DisplayName("Binary data processed correctly")
    void testBinaryData() {
        byte[] data = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
        InputStream is = new ByteArrayInputStream(data);

        CacheData.Sha256PlusLength result = CacheUtils.getSha256PlusLength(is);

        assertEquals(5, result.getLength());
        assertNotNull(result.getSha256());
        assertEquals(64, result.getSha256().length()); // SHA256 hex is 64 chars
    }

    @Test
    @DisplayName("Large input processed correctly")
    void testLargeInput() {
        byte[] data = new byte[1024 * 1024]; // 1 MB
        java.util.Arrays.fill(data, (byte) 'A');
        InputStream is = new ByteArrayInputStream(data);

        CacheData.Sha256PlusLength result = CacheUtils.getSha256PlusLength(is);

        assertEquals(1024 * 1024, result.getLength());
        assertNotNull(result.getSha256());
    }

    @Test
    @DisplayName("asString format is correct")
    void testAsStringFormat() {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        InputStream is = new ByteArrayInputStream(data);

        CacheData.Sha256PlusLength result = CacheUtils.getSha256PlusLength(is);

        String asString = result.asString();
        assertTrue(asString.contains("###"));
        assertTrue(asString.endsWith("###4"));
    }
}