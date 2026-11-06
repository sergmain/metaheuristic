/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DigitUtils.getPower3: power10 = num / 10_000_000, power7 = num / 10_000 % 1_000, power4 = num % 10_000.
 * The layout v2 of the Dispatcher's variable cache depends on this split - a change here moves every cached file.
 */
@Execution(ExecutionMode.CONCURRENT)
public class DigitUtilsTest {

    private static final List<Long> NUMS = List.of(
        0L, 1L, 42L, 9_999L, 10_000L, 10_001L, 112_222L, 999_999L, 1_000_000L, 9_999_999L, 10_000_000L,
        10_000_001L, 123_456_789L, 1_009_999L, 9_876_543_210L, Long.MAX_VALUE);

    private static void assertPower3(long num, long power10, long power7, long power4) {
        final DigitUtils.Power3 p = DigitUtils.getPower3(num);
        assertEquals(power10, p.power10(), num + ": power10");
        assertEquals(power7, p.power7(), num + ": power7");
        assertEquals(power4, p.power4(), num + ": power4");
    }

    @Test
    public void test_constants() {
        assertEquals(10_000, DigitUtils.DIV, "Value of DigitUtils.DIV was changed, must be always 10_000");
        assertEquals(1_000, DigitUtils.DIV_MIDDLE, "Value of DigitUtils.DIV_MIDDLE was changed, must be always 1_000");
    }

    @Test
    public void test_getPower3_exampleOfTheTask() {
        // variableId 112222 -> dispatcher/temp/variable/v2/0/11/2222
        assertPower3(112_222L, 0, 11, 2222);
    }

    @Test
    public void test_getPower3_allThreeLevelsNonZero() {
        assertPower3(123_456_789L, 12, 345, 6789);
        assertPower3(9_876_543_210L, 987, 654, 3210);
    }

    @Test
    public void test_getPower3_belowTenThousand() {
        assertPower3(0L, 0, 0, 0);
        assertPower3(1L, 0, 0, 1);
        assertPower3(42L, 0, 0, 42);
        assertPower3(9_999L, 0, 0, 9999);
    }

    @Test
    public void test_getPower3_boundaryOfPower4() {
        assertPower3(10_000L, 0, 1, 0);
        assertPower3(10_001L, 0, 1, 1);
    }

    @Test
    public void test_getPower3_boundaryOfPower7() {
        assertPower3(9_999_999L, 0, 999, 9999);
        assertPower3(10_000_000L, 1, 0, 0);
        assertPower3(10_000_001L, 1, 0, 1);
    }

    @Test
    public void test_getPower3_maxLong() {
        // 9223372036854775807 = 922337203685 * 10^7 + 477 * 10^4 + 5807
        assertPower3(Long.MAX_VALUE, 922_337_203_685L, 477, 5807);
    }

    @Test
    public void test_getPower3_levelsReconstructTheNumber() {
        for (long num : NUMS) {
            final DigitUtils.Power3 p = DigitUtils.getPower3(num);
            assertEquals(num, p.power10() * 10_000_000L + p.power7() * 10_000L + p.power4(), num + ": reconstructed");
        }
    }

    @Test
    public void test_getPower3_levelsAreInRange() {
        for (long num : NUMS) {
            final DigitUtils.Power3 p = DigitUtils.getPower3(num);
            assertTrue(p.power7() >= 0 && p.power7() < 1_000, num + ": power7 in [0, 1000), was " + p.power7());
            assertTrue(p.power4() >= 0 && p.power4() < 10_000, num + ": power4 in [0, 10000), was " + p.power4());
        }
    }

    @Test
    public void test_getPower3_agreesWithGetPower() {
        for (long num : NUMS) {
            final DigitUtils.Power p2 = DigitUtils.getPower(num);
            final DigitUtils.Power3 p3 = DigitUtils.getPower3(num);
            assertEquals(p2.power4, p3.power4(), num + ": power4 of both splits");
            assertEquals(p2.power7, p3.power10() * 1_000 + p3.power7(), num + ": upper part of getPower = power10, power7 of getPower3");
        }
    }
}
