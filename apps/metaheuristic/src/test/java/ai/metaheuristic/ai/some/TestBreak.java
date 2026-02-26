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

package ai.metaheuristic.ai.some;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

import static java.text.NumberFormat.*;
import static java.util.Calendar.LONG;
import static java.util.Calendar.SHORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Execution(ExecutionMode.CONCURRENT)
public class TestBreak {

    @Test
    public void test_() {
        double d = 12324.134;
        System.out.println(getCurrencyInstance(Locale.US).format(d));
        final String formatted = getCurrencyInstance(Locale.of("hi", "IN")).format(d);
        System.out.println(formatted);
        assertEquals("₹12,324.13", formatted);
        System.out.println(getCurrencyInstance(Locale.CHINA).format(d));
        System.out.println(getCurrencyInstance(Locale.FRANCE).format(d));
    }

    @Test
    public void test_week() {
        Calendar c = Calendar.getInstance();
        System.out.println(c.getDisplayName(Calendar.DAY_OF_WEEK, LONG, Locale.US));
    }


    @Test
    public void testBreak() {

        int[] ints = new int[] {1,2,3,4,5};
        int i=0;
        while (i++<10) {
            for (int anInt : ints) {
                if (anInt == 3) {
                    break;
                }
            }
        }
        assertNotEquals(1, i);
    }
}
