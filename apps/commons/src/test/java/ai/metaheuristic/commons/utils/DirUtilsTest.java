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

package ai.metaheuristic.commons.utils;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Sergio Lissner
 * Date: 11/26/2023
 * Time: 10:18 PM
 */
public class DirUtilsTest {

    @Test
    public void test_getParent_55() {
        assertEquals(Path.of("\\aaa"), DirUtils.getParent(Path.of("\\aaa\\bbbb\\ccc"), Path.of("bbbb\\ccc")));
        assertEquals(Path.of("/aaa"), DirUtils.getParent(Path.of("/aaa/bbbb/ccc"), Path.of("bbbb/ccc")));
        assertNull(DirUtils.getParent(Path.of("/aaa/bbbb/ccc/ddd"), Path.of("bbbb/ccc")));
    }
}
