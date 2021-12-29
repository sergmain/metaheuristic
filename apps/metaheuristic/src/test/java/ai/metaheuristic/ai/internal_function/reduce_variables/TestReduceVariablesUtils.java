/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values.ReduceVariablesUtils;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYaml;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYamlUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:53 PM
 */
public class TestReduceVariablesUtils {

    @Test
    public void test() {
        final URL url = TestReduceVariablesUtils.class.getResource("/bin/variable-75492-aggregatedResult.zip");
        assertNotNull(url);
        File zip = new File(url.getFile());
        assertTrue(zip.exists());

        String yaml = """
                version: 1
                config:
                    fixName: true
                    fittingVar: fitting
                    metricsVar: metrics
                    metricsName: sum
                    reduceByValue:
                        activation: activation1
                        batchSize: batchSize1
                        epoch: epoch1
                        optimizer: optimizer1
                        RNN: RNN1
                        seed: seed1
                        timeSteps: timeSteps1
                    reduceByInstance:
                        - input: binaryClusters1
                          inputIs: isBinaryClusters1
                          outputIs: isBinaryClusters1New1
                        - input: binaryDrawWithFrequency
                          inputIs: isBinaryDrawWithFrequency
                          outputIs: isBinaryDrawWithFrequencyNew1
                        - input: clusterCount1
                          inputIs: isClusterCount1
                          outputIs: isClusterCount1News1
                        - input: clusterSize1
                          inputIs: isClusterSize1
                          outputIs: isClusterSize1New1
                        - input: distribOfFreqFull
                          inputIs: isDistribOfFreqFull
                          outputIs: isDistribOfFreqFullNew1
                        - input: matrixOfWinning
                          inputIs: isMatrixOfWinning
                          outputIs: isMatrixOfWinningNew1
                    reduces:
                        - policy: reduce_value
                          reducePercent: 50
                          variable: activation
                """;

        ReduceVariablesConfigParamsYaml config = ReduceVariablesConfigParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        ReduceVariablesData.VariablesData data = new ReduceVariablesData.VariablesData();
        ReduceVariablesUtils.loadData(data, zip, config);

        assertFalse(data.permutedVariables.isEmpty());
    }

    @Disabled @Test public void testExternal() {
        UtilsForTestReduceVariables.extracted("variable-2653425-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_1() {
        UtilsForTestReduceVariables.extracted("variable-4016080-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_2() {
        UtilsForTestReduceVariables.extracted("variable-3967531-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_3() {
        UtilsForTestReduceVariables.extracted("variable-4095959-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_4() {
        UtilsForTestReduceVariables.extracted("variable-4064629-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_5() {
        //  repeating of variable-3967531-aggregatedResult1.zip
    }

    @Disabled @Test public void testExternal_6() {
        UtilsForTestReduceVariables.extracted("variable-4189389-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_7() {
        UtilsForTestReduceVariables.extracted("variable-4189373-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_8() {
        // repeating of variable-4403511-aggregatedResult1.zip
    }

    @Disabled @Test public void testExternal_9() {
        // replaced by variable-4450218-aggregatedResult1.zip, UtilsForTestReduceVariables.extracted("variable-4403511-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_10() {
        // replaced by variable-4434649-aggregatedResult1, UtilsForTestReduceVariables.extracted("variable-4387942-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_11() {
        // broken UtilsForTestReduceVariables.extracted("variable-4419080-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_12() {
        UtilsForTestReduceVariables.extracted("variable-4434649-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_13() {
        UtilsForTestReduceVariables.extracted("variable-4450218-aggregatedResult1.zip", UtilsForTestReduceVariables::filterStr);
    }

    @Disabled @Test public void testExternal_14() {
        UtilsForTestReduceVariables.extracted("variable-4477574-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_15() {
        UtilsForTestReduceVariables.extracted("variable-4469716-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_16() {
        UtilsForTestReduceVariables.extracted("variable-4481503-aggregatedResult1.zip");
    }

    @Disabled @Test public void testExternal_1_2_3_4_6_7_12_13() {
        UtilsForTestReduceVariables.extracted(List.of(
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
