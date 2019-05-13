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

import ai.metaheuristic.ai.utils.DigitUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestDigitUtils {

    @Test
    public void testPower() {
        assertEquals(0, DigitUtils.getPower(42).power7);
        assertEquals(42, DigitUtils.getPower(42).power4);
        assertEquals(1, DigitUtils.getPower(10042).power7);
        assertEquals(42, DigitUtils.getPower(10042).power4);
        assertEquals(100, DigitUtils.getPower(1009999).power7);
        assertEquals(9999, DigitUtils.getPower(1009999).power4);
    }
}
