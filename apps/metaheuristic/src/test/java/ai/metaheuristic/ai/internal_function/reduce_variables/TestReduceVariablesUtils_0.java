/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.data.ReduceVariablesData;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * @author Serge
 * Date: 3/31/2022
 * Time: 5:17 PM
 */
@SuppressWarnings("NewClassNamingConvention")
@Disabled
public class TestReduceVariablesUtils_0 {

    @Test
    public void testExternal() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-2653425-aggregatedResult1.zip");
    }

    @Test public void testExternal_1() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4016080-aggregatedResult1.zip");
    }

    @Test public void testExternal_2() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-3967531-aggregatedResult1.zip");
    }

    @Test public void testExternal_3() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4095959-aggregatedResult1.zip");
    }

    @Test public void testExternal_4() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4064629-aggregatedResult1.zip");
    }

    @Test public void testExternal_5() {
        //  repeating of variable-3967531-aggregatedResult1.zip
    }

    @Test public void testExternal_6() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4189389-aggregatedResult1.zip");
    }

    @Test public void testExternal_7() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4189373-aggregatedResult1.zip");
    }

    @Test public void testExternal_8() {
        // repeating of variable-4403511-aggregatedResult1.zip
    }

    @Test public void testExternal_9() {
        // replaced by variable-4450218-aggregatedResult1.zip, UtilsForTestReduceVariables.extracted("variable-4403511-aggregatedResult1.zip");
    }

    @Test public void testExternal_10() {
        // replaced by variable-4434649-aggregatedResult1, UtilsForTestReduceVariables.extracted("variable-4387942-aggregatedResult1.zip");
    }

    @Test public void testExternal_11() {
        // broken UtilsForTestReduceVariables.extracted("variable-4419080-aggregatedResult1.zip");
    }

    @Test public void testExternal_12() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4434649-aggregatedResult1.zip");
    }

    @Test public void testExternal_13() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4450218-aggregatedResult1.zip", UtilsForTestReduceVariables::filterStr);
    }

    @Test public void testExternal_14() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4477574-aggregatedResult1.zip");
    }

    @Test public void testExternal_15() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4469716-aggregatedResult1.zip");
    }

    @Test public void testExternal_16() throws IOException {
        UtilsForTestReduceVariables.extracted("variable-4481503-aggregatedResult1.zip");
    }

    @Test public void testExternal_1_2_3_4_6_7_12_13() throws IOException {
        ReduceVariablesData.ReduceVariablesResult result = UtilsForTestReduceVariables.extracted(List.of(
                        "variable-4016080-aggregatedResult1.zip",
                        "variable-3967531-aggregatedResult1.zip",
                        "variable-4095959-aggregatedResult1.zip",
                        "variable-4064629-aggregatedResult1.zip",
                        "variable-4189389-aggregatedResult1.zip",
                        "variable-4189373-aggregatedResult1.zip",
                        "variable-4434649-aggregatedResult1.zip",
                        "variable-4450218-aggregatedResult1.zip"),
                UtilsForTestReduceVariables::filterStr);

    }


}
