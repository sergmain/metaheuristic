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
import org.springframework.lang.Nullable;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:53 PM
 */
public class UtilsForTestReduceVariables {

    private static final Pattern FILTER = Pattern.compile(".*\\[[3-9],.*");

    public static boolean filterStr(String o) {
        return FILTER.matcher(o).find();
    }

    public static void extracted(String pathname) {
        extracted(List.of(pathname), null);
    }

    public static void extracted(String pathname, @Nullable Function<String, Boolean> filter) {
        extracted(List.of(pathname), filter);
    }

    public static void extracted(List<String> pathnames, @Nullable Function<String, Boolean> filter) {
        final Date startDate = new Date();
        System.out.println("Start time: " + startDate);
        List<File> files = new ArrayList<>();
        for (String pathname : pathnames) {
            File zip = new File(pathname);
            assertTrue(zip.exists());
            assertTrue(zip.isFile());
            files.add(zip);
        }

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

        ReduceVariablesData.ReduceVariablesResult result = ReduceVariablesUtils.reduceVariables(files, config, r, filter);

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

        System.out.println("\n=====================");
        for (ReduceVariablesData.ExperimentMetrics experimentMetrics : result.metricsList) {
            System.out.println(experimentMetrics.hyper);
            System.out.println(experimentMetrics.data);
            System.out.println(experimentMetrics.metrics);
            System.out.println(experimentMetrics.metricValues.comment);
            System.out.println(experimentMetrics.dir);
            System.out.println();
        }
        System.out.println("Start time: " + startDate);
        System.out.println("End time:   " + new Date());
    }

    public static void extracted_1(List<String> pathnames, @Nullable Function<String, Boolean> filter) {
        final Date startDate = new Date();
        System.out.println("Start time: " + startDate);
        List<File> files = new ArrayList<>();
        for (String pathname : pathnames) {
            File zip = new File(pathname);
            assertTrue(zip.exists());
            assertTrue(zip.isFile());
            files.add(zip);
        }

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

        ReduceVariablesData.ReduceVariablesResult result = ReduceVariablesUtils.reduceVariables(files, config, r, filter);

        assertFalse(result.byValue.isEmpty());
        assertFalse(result.byInstance.isEmpty());
        assertEquals(5, result.byInstance.size());

        assertTrue(result.byInstance.get("isClusterCount1New1"));
        assertTrue(result.byInstance.get("isClusterSize1New1"));
        assertTrue(result.byInstance.get("isBinaryClusters1New1"));
//        assertTrue(result.byInstance.get("isBinaryDrawWithFrequencyNew1"));
        assertTrue(result.byInstance.get("isDistribOfFreqFullNew1"));
        assertTrue(result.byInstance.get("isMatrixOfWinningNew1"));

//        assertFalse(result.byInstance.isEmpty());

        System.out.println("\n=====================");
        for (Map.Entry<String, String> entry : result.byValue.entrySet()) {
            System.out.printf("%-15s, %s\n", entry.getKey(), entry.getValue());
        }

        System.out.println("\n=====================");
        for (ReduceVariablesData.ExperimentMetrics experimentMetrics : result.metricsList) {
            System.out.println(experimentMetrics.hyper);
            System.out.println(experimentMetrics.data);
            System.out.println(experimentMetrics.metrics);
            System.out.println(experimentMetrics.metricValues.comment);
            System.out.println(experimentMetrics.dir);
            System.out.println();
        }
        System.out.println("Start time: " + startDate);
        System.out.println("End time:   " + new Date());
    }
}
