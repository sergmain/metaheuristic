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

package ai.metaheuristic.commons.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStrUtils {

    @Test
    public void testMatching() {
        assertTrue(StrUtils.isCodeOk("1234567890-abc_xyz:1.0"));

        assertTrue(StrUtils.isCodeOk("aaa.txt"));
        assertTrue(StrUtils.isCodeOk("aaa."));

        assertFalse(StrUtils.isCodeOk("1234567890-?#$%abc_xyz:1.0"));
        assertFalse(StrUtils.isCodeOk("aaa bbb.txt"));
        assertFalse(StrUtils.isCodeOk("aaa,bbb.txt"));
        assertFalse(StrUtils.isCodeOk("aaaäöü.txt"));
    }
}
