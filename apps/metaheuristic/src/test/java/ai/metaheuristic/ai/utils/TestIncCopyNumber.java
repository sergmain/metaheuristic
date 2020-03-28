/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.commons.utils.StrUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestIncCopyNumber {

    @Test
    public void testIncNumber() {
        assertEquals("Copy #2, aaa", StrUtils.incCopyNumber("aaa"));
        assertEquals("Copy #3, aaa", StrUtils.incCopyNumber("Copy #2, aaa"));
        assertEquals("Copy #2, aaa", StrUtils.incCopyNumber("Copy #aa2, aaa"));
    }
}
