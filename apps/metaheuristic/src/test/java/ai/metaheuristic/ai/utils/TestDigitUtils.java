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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.commons.utils.DigitUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Execution(ExecutionMode.CONCURRENT)
public class TestDigitUtils {

    @Test
    public void testPower() {

        assertEquals(10_000, DigitUtils.DIV, "Value of DigitUtils.DIV was changed, must be always 10_000");
        assertEquals(0, DigitUtils.getPower(42).power7);
        assertEquals(42, DigitUtils.getPower(42).power4);
        assertEquals(1, DigitUtils.getPower(10042).power7);
        assertEquals(42, DigitUtils.getPower(10042).power4);
        assertEquals(100, DigitUtils.getPower(1009999).power7);
        assertEquals(9999, DigitUtils.getPower(1009999).power4);
    }
}
