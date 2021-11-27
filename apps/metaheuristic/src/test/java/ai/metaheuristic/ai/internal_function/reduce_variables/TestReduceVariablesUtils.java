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
import java.util.Map;

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

        ReduceVariablesData.VariablesData data = ReduceVariablesUtils.loadData(zip, config);

        assertFalse(data.permutedVariables.isEmpty());
    }

    @Disabled
    @Test
    public void testExternal() {
        File zip = new File("variable-2653425-aggregatedResult1.zip");
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
                          outputIs: isClusterCount1New1
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
                        - policy: reduce_value
                          reducePercent: 35
                          variable: optimizer
                """;

        ReduceVariablesConfigParamsYaml config = ReduceVariablesConfigParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        ReduceVariablesData.Request r = new ReduceVariablesData.Request();
        r.nullifiedVars.putAll(Map.of(
                "isBinaryClusters1", true,
                "isBinaryDrawWithFrequency", true,
                "isClusterCount1", true,
                "isClusterSize1", true,
                "isDistribOfFreqFull", true,
                "isMatrixOfWinning", true));

        ReduceVariablesData.ReduceVariablesResult result = ReduceVariablesUtils.reduceVariables(zip, config, r);

        assertFalse(result.byValue.isEmpty());
        assertFalse(result.byInstance.isEmpty());
        assertEquals(6, result.byInstance.size());

        assertTrue(result.byInstance.get("isBinaryClusters1New1"));
        assertTrue(result.byInstance.get("isBinaryDrawWithFrequencyNew1"));
        assertTrue(result.byInstance.get("isClusterCount1New1"));
        assertTrue(result.byInstance.get("isClusterSize1New1"));
        assertTrue(result.byInstance.get("isDistribOfFreqFullNew1"));
        assertTrue(result.byInstance.get("isMatrixOfWinningNew1"));

//        assertFalse(result.byInstance.isEmpty());

        System.out.println("\n=====================");
        for (Map.Entry<String, String> entry : result.byValue.entrySet()) {
            System.out.printf("%-15s, %s\n", entry.getKey(), entry.getValue());
        }
    }
}
