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

package ai.metaheuristic.ai.internal_function.reduce_variables;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.util.List;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:53 PM
 */
@Disabled
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("experiments postponed for very long period of time")
public class TestReduceVariablesUtils_1 {

    @Test
    public void testExternal_17() throws IOException {
        UtilsForTestReduceVariables.extracted_1(List.of("variable-4487360-aggregatedResult11.zip"), UtilsForTestReduceVariables::filterStr);
    }

    @Test
    public void testExternal_18() throws IOException {
        UtilsForTestReduceVariables.extracted_1(List.of("variable-857258-aggregatedResult1.zip"), UtilsForTestReduceVariables::filterStr);
    }

}
