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

package ai.metaheuristic.ai.some;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

public class TestBreak {

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
