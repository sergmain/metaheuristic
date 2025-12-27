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
package ai.metaheuristic.commons.permutation;

import ai.metaheuristic.commons.permutation.Permutation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPermutation {

    @Test
    public void testPermutation_String() {
        final String[] ss = new String[]{
                "[+1, +2, -3]",
                "[+1, +2, -4]",
                "[+1, +2, 5!]",
                "[+1, -3, -4]",
                "[+1, -3, 5!]",
                "[+1, -4, 5!]",
                "[+2, -3, -4]",
                "[+2, -3, 5!]",
                "[+2, -4, 5!]",
                "[-3, -4, 5!]"
        };

        final ArrayList<String> arr = new ArrayList<>(Arrays.asList("+1", "+2", "-3", "-4", "5!"));

        final int r = 3;
        Set<String> result = new HashSet<>();
        Permutation<String> permutation = new Permutation<>();
        int combination = permutation.printCombination(arr, r,
                data -> {
                    result.add(String.valueOf(data));
                    return true;
                }
        );

        assertEquals(10, combination);
        assertEquals(result.size(), combination);

        for (String s : ss) {
            assertTrue(result.contains(s));
        }

        // one more time for testing internal state

        combination = permutation.printCombination(arr, r,
                data -> {
                    result.add(String.valueOf(data));
                    return true;
                }
        );

        assertEquals(10, combination);
        assertEquals(result.size(), combination);

        for (String s : ss) {
            assertTrue(result.contains(s));
        }
        System.out.println("\nTotal number of combination: " + combination);
    }

    @Test
    public void testPermutation_Integer() {
        String[] ss = new String[]{
                "[1, 2, 3]",
                "[1, 2, 4]",
                "[1, 2, 5]",
                "[1, 3, 4]",
                "[1, 3, 5]",
                "[1, 4, 5]",
                "[2, 3, 4]",
                "[2, 3, 5]",
                "[2, 4, 5]",
                "[3, 4, 5]"
        };

        ArrayList<Integer> arr = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));

        int r = 3;
        Set<String> result = new HashSet<>();
        Permutation<Integer> permutation = new Permutation<>();
        final int combination = permutation.printCombination(arr, r,
                data -> {
                    final String e = String.valueOf(data);
                    System.out.println(e);
                    result.add(e);
                    return true;
                }
        );
        assertEquals(10, combination);
        assertEquals(result.size(), combination);

        for (String s : ss) {
            assertTrue(result.contains(s));
        }
        System.out.println("\nTotal cnumber of combination: " + combination);
    }

}
