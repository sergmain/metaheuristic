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

import lombok.AllArgsConstructor;

public class DigitUtils {

    public static final int DIV = 10_000;

    // the divisor of the middle level of getPower3(): its power7 holds the 3 digits above power4
    // 2026-11-06: the middle level is 4 digits, the same divisor as DIV - 0-n / 0-9999 / 0-9999, 123456789 -> 1/2345/6789
    public static final int DIV_MIDDLE = DIV;

    @AllArgsConstructor
    public static class Power {
        public long power7;
        public long power4;
    }

    /**
     * Three levels of a number:
     * power4 - num % 10_000, the same as power4 of {@link #getPower(long)};
     * power7 - num / 10_000 % 1_000, the next 3 digits;
     * power10 - num / 10_000_000, everything above them.
     * 112222 -> 0, 11, 2222; 123456789 -> 12, 345, 6789.
     * 2026-11-06: power7 - num / 10_000 % 10_000, the next 4 digits; power10 - num / 100_000_000;
     * 112222 -> 0, 11, 2222; 123456789 -> 1, 2345, 6789.
     */
    public record Power3(long power10, long power7, long power4) {}

    public static Power getPower(long num) {
        return new Power(num/DIV, num % DIV );
    }

    // the split of getPower() with its upper part divided once more
    public static Power3 getPower3(long num) {
        final Power power = getPower(num);
        return new Power3(power.power7 / DIV_MIDDLE, power.power7 % DIV_MIDDLE, power.power4);
    }
}
