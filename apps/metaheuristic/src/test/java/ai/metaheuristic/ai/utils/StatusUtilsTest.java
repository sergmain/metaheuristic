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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * @author Sergio Lissner
 * Date: 10/29/2023
 * Time: 12:19 AM
 */
@Execution(ExecutionMode.CONCURRENT)
public class StatusUtilsTest {


    @Test
    public void test_() {
        /*
         * Table to print in console in 2-dimensional array. Each sub-array is a row.
         */
        String[][] table = new String[][] { { "id", "First Name", "Last Name", "Age", "Profile" },
            { "1", "John", "Johnson", "45", "My name is John Johnson. My id is 1. My age is 45." },
            { "2", "Tom", "", "35", "My name is Tom. My id is 2. My age is 35." },
            { "3", "Rose", "Johnson Johnson Johnson Johnson Johnson Johnson Johnson Johnson Johnson Johnson", "22",
                "My name is Rose Johnson. My id is 3. My age is 22." },
            { "4", "Jimmy", "Kimmel", "", "My name is Jimmy Kimmel. My id is 4. My age is not specified. "
                + "I am the host of the late night show. I am not fan of Matt Damon. " } };

        StatusUtils.printTable(System.out::print, true, 50, false, table);
    }
}
