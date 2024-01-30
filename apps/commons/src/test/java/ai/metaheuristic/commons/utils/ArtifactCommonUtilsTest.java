/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Sergio Lissner
 * Date: 10/20/2023
 * Time: 12:32 AM
 */
public class ArtifactCommonUtilsTest {

    @Test
    public void test_normalizeCode() {
        assertEquals("aaa", ArtifactCommonUtils.normalizeCode("aaa"));
        assertEquals("aaa", ArtifactCommonUtils.normalizeCode("aaa."));
        assertEquals("aaa", ArtifactCommonUtils.normalizeCode("aaa.."));
        assertEquals("a_a_a", ArtifactCommonUtils.normalizeCode("a:a:a.."));
        assertEquals("a_a_a..b", ArtifactCommonUtils.normalizeCode("a:a:a..b"));


        assertThrows(IllegalStateException.class, ()->ArtifactCommonUtils.normalizeCode(".."));
    }
}
