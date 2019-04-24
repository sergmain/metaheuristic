/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.utils;

import aiai.apps.commons.utils.StrUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestStrUtils {

    @Test
    public void testGetExtension() {
        assertNull(StrUtils.getExtension(null));
        assertEquals("", StrUtils.getExtension(""));
        assertEquals("", StrUtils.getExtension("abc"));
        assertEquals(".txt", StrUtils.getExtension("abc.txt"));
        assertEquals(".txt", StrUtils.getExtension("abc.def.txt"));
    }
}
