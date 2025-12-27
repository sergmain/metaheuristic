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

import ai.metaheuristic.ai.dispatcher.data.ReduceVariablesData;
import ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values.ReduceVariablesUtils;
import ai.metaheuristic.commons.utils.JsonUtils;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYaml;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYamlUtils;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.commons.io.FileUtils;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:53 PM
 */
public class UtilsForTestReduceVariables {

    public static class ParamsAsJson {
        public final List<Map<String, String>> params = new ArrayList<>();
    }

    private static final Pattern FILTER = Pattern.compile(".*\\[[3-9],.*");
    private static final Pattern ATTENTION_SELECTOR = Pattern.compile(".*_prediction: \\[[4-9],.*");

    public static boolean filterStr(String o) {
        return FILTER.matcher(o).find();
    }

    public static void extracted(String pathname) throws IOException {
        extracted(List.of(pathname), null);
    }

    public static void extracted(String pathname, @Nullable Function<String, Boolean> filter) throws IOException {
        extracted(List.of(pathname), filter);
    }

    public static ReduceVariablesData.ReduceVariablesResult extracted(List<String> pathnames, @Nullable Function<String, Boolean> filter) throws IOException {
        final Date startDate = new Date();
        System.out.println("Start time: " + startDate);
        System.out.println("Total files: " + pathnames.size());
        List<Path> files = new ArrayList<>();

        for (String pathname : pathnames) {
            Path zip = new File(pathname).toPath();
            assertTrue(Files.exists(zip), zip.toString());
            assertTrue(Files.isRegularFile(zip));
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

        System.out.println("Start reducing variables at " + new Date());

        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
        Path temp = fs.getPath("/temp");
        Path actualTemp = Files.createDirectory(temp);
//        Path actualTemp = Files.createTempDirectory("reduce-variable-");

        ReduceVariablesData.ReduceVariablesResult result = ReduceVariablesUtils.reduceVariables(actualTemp, files, config, r, filter,
                (o)->{
                    if (o.current==1) {
                        System.out.println("======================");
                        System.out.println(o.file);
                    }
                },
                (o)-> o.metricValues!=null && o.metricValues.comment!=null && ATTENTION_SELECTOR.matcher(o.metricValues.comment).find());

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

        ParamsAsJson paramsAsJson = new ParamsAsJson();

        System.out.println("\n=== Attention =================_");
        System.out.println("found for attention: " + result.attentionsAndExperimentMetrics.attentions.size());
        for (ReduceVariablesData.Attention attention : result.attentionsAndExperimentMetrics.attentions) {
            Map<String, String> map = new HashMap<>();
            map.putAll(attention.dataset);
            map.putAll(attention.params);
            paramsAsJson.params.add(map);
            System.out.println(attention.result);
            System.out.println(attention.dataset);
            System.out.println(attention.params);
            System.out.println();
        }

        System.out.println("\n=== Json ==================");
        for (Map<String, String> param : paramsAsJson.params) {
            String json = JsonUtils.getMapper().writeValueAsString(param);
            System.out.println(json);
        }

        System.out.println("\n=====================");
        for (ReduceVariablesData.ExperimentMetrics experimentMetrics : result.attentionsAndExperimentMetrics.metricsList) {
            System.out.println(experimentMetrics.hyper);
            System.out.println(experimentMetrics.data);
            System.out.println(experimentMetrics.metrics);
            System.out.println(experimentMetrics.metricValues.comment);
            System.out.println(experimentMetrics.dir);
            System.out.println();
        }

        System.out.println("Start time: " + startDate);
        System.out.println("End time:   " + new Date());

        writeResult(result);

        return result;
    }

    private static void writeResult(ReduceVariablesData.ReduceVariablesResult result) throws IOException {
        File d = new File("result");
        if (!d.exists()) {
            d.mkdir();
        }
        File f = File.createTempFile("reduce-result-", ".json", d);
        String json = JsonUtils.getMapper().writeValueAsString(result);
        FileUtils.write(f, json, StandardCharsets.UTF_8);
        System.out.println("A result was stored to " + f.getAbsolutePath());
    }

    //    в этом методе не используется binaryDrawWithFrequency
    public static ReduceVariablesData.ReduceVariablesResult extracted_1(List<String> pathnames, @Nullable Function<String, Boolean> filter) throws IOException {
        final Date startDate = new Date();
        System.out.println("Start time: " + startDate);
        System.out.println("Total files: " + pathnames.size());
        List<Path> files = new ArrayList<>();
        for (String pathname : pathnames) {
            Path zip = new File(pathname).toPath();
            assertTrue(Files.exists(zip), zip.toString());
            assertTrue(Files.isRegularFile(zip));
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

        System.out.println("Start reducing variables at " + new Date());

        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
        Path temp = fs.getPath("/temp");
        Path actualTemp = Files.createDirectory(temp);
//        Path actualTemp = Files.createTempDirectory("reduce-variable-");

        ReduceVariablesData.ReduceVariablesResult result = ReduceVariablesUtils.reduceVariables(actualTemp, files, config, r, filter,
                (o)->{
                    if (o.current==1) {
                        System.out.println("======================");
                        System.out.println(o.file);
                    }
//                    System.out.println(S.f("%-10d %d", o.current, o.total));
                },
                (o)-> o.metricValues!=null && o.metricValues.comment!=null && ATTENTION_SELECTOR.matcher(o.metricValues.comment).find());

        assertFalse(result.byValue.isEmpty());
        assertFalse(result.byInstance.isEmpty());
        assertEquals(5, result.byInstance.size());

        assertTrue(result.byInstance.get("isBinaryClusters1New1"));
//        assertTrue(result.byInstance.get("isBinaryDrawWithFrequencyNew1"));
        assertTrue(result.byInstance.get("isClusterCount1New1"));
        assertTrue(result.byInstance.get("isClusterSize1New1"));
        assertTrue(result.byInstance.get("isDistribOfFreqFullNew1"));
        assertTrue(result.byInstance.get("isMatrixOfWinningNew1"));

//        assertFalse(result.byInstance.isEmpty());

        System.out.println("\n=====================");
        for (Map.Entry<String, String> entry : result.byValue.entrySet()) {
            System.out.printf("%-15s, %s\n", entry.getKey(), entry.getValue());
        }

        System.out.println("\n==== Attention =================");
        System.out.println("found for attention: " + result.attentionsAndExperimentMetrics.attentions.size());
        for (ReduceVariablesData.Attention attention : result.attentionsAndExperimentMetrics.attentions) {
            System.out.println(attention.result);
            System.out.println(attention.dataset);
            System.out.println(attention.params);
            System.out.println();
        }

        System.out.println("\n=====================");
        for (ReduceVariablesData.ExperimentMetrics experimentMetrics : result.attentionsAndExperimentMetrics.metricsList) {
            System.out.println(experimentMetrics.hyper);
            System.out.println(experimentMetrics.data);
            System.out.println(experimentMetrics.metrics);
            System.out.println(experimentMetrics.metricValues.comment);
            System.out.println(experimentMetrics.dir);
            System.out.println();
        }

        System.out.println("Start time: " + startDate);
        System.out.println("End time:   " + new Date());

        writeResult(result);

        return result;
    }
}
