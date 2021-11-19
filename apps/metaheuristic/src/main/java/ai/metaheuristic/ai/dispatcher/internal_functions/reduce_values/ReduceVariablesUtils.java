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

package ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values;

import ai.metaheuristic.ai.dispatcher.data.ReduceVariablesData;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYaml;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYamlUtils;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYaml;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricValues;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.MH_METADATA_YAML_FILE_NAME;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:27 PM
 */
public class ReduceVariablesUtils {

    public static ReduceVariablesData.ReduceVariablesResult reduceVariables(
            File zipFile, ReduceVariablesConfigParamsYaml config, ReduceVariablesData.Request request) {

        ReduceVariablesData.VariablesData data = loadData(zipFile, config);

        Map<String, Map<String, Pair<AtomicInteger, AtomicInteger>>> freqValues = getFreqValues(data);
        System.out.println("\n=====================");
        for (Map.Entry<String, Map<String, Pair<AtomicInteger, AtomicInteger>>> entry : freqValues.entrySet()) {
            System.out.println(entry.getKey());
            for (Map.Entry<String, Pair<AtomicInteger, AtomicInteger>> en : entry.getValue().entrySet()) {
                System.out.printf("\t%-15s  %5d %5d\n", en.getKey(), en.getValue().getLeft().get(), en.getValue().getRight().get());
            }
        }

        Map<String, Pair<AtomicInteger, AtomicInteger>> freqVariables = getFreqVariables(config, data);
        System.out.println("\n=====================");
        for (Map.Entry<String, Pair<AtomicInteger, AtomicInteger>> en : freqVariables.entrySet()) {
            System.out.printf("\t%-40s  %5d %5d\n", en.getKey(), en.getValue().getLeft().get(), en.getValue().getRight().get());
        }

        ReduceVariablesData.ReduceVariablesResult result = new ReduceVariablesData.ReduceVariablesResult();
        config.config.reduceByInstance.forEach(byInstance -> result.byInstance.put(byInstance.outputIs, request.nullifiedVars.get(byInstance.inputIs)));

        for (Map.Entry<String, Map<String, Pair<AtomicInteger, AtomicInteger>>> entry : freqValues.entrySet()) {

            String key = entry.getKey();
            if (!config.config.reduceByValue.containsKey(key)) {
                continue;
            }

            List<Pair<String, Integer>> values = new ArrayList<>();
            for (Map.Entry<String, Pair<AtomicInteger, AtomicInteger>> en : entry.getValue().entrySet()) {
                values.add(Pair.of(en.getKey(), en.getValue().getRight().get()));
            }

            ReduceVariablesConfigParamsYaml.Reduce reduce = config.config.reduces.stream().filter(o->o.variable.equals(key)).findFirst().orElse(null);

            long skip = 0;
            if (reduce!=null) {
                if (reduce.policy== ReduceVariablesEnums.Policy.reduce_value) {
                    values.sort(Comparator.comparingInt(Pair::getRight));
                    skip = (long) values.size()*reduce.reducePercent/100;
                }
            }
            String value = values.stream().skip(skip).map(Pair::getLeft).collect(Collectors.joining(", ", "[", "]"));
            result.byValue.put(key, value);
        }

        return result;
    }

    private static Map<String, Map<String, Pair<AtomicInteger, AtomicInteger>>> getFreqValues(ReduceVariablesData.VariablesData data) {
        Map<String, Map<String, Pair<AtomicInteger, AtomicInteger>>> freqValues = new HashMap<>();

        for (ReduceVariablesData.TopPermutedVariables permutedVariable : data.permutedVariables) {
            for (Map.Entry<String, String> entry : permutedVariable.values.entrySet()) {

                for (ReduceVariablesData.PermutedVariables subPermutedVariable : permutedVariable.subPermutedVariables) {

                    freqValues
                            .computeIfAbsent(entry.getKey(), (o)->new HashMap<>())
                            .computeIfAbsent(entry.getValue(), (o)->Pair.of(new AtomicInteger(), new AtomicInteger()))
                            .getLeft().incrementAndGet();

                    if (subPermutedVariable.fitting== EnumsApi.Fitting.UNDERFITTING) {
                        continue;
                    }

                    freqValues
                            .computeIfAbsent(entry.getKey(), (o)->new HashMap<>())
                            .computeIfAbsent(entry.getValue(), (o)->Pair.of(new AtomicInteger(), new AtomicInteger()))
                            .getRight().incrementAndGet();
                }

            }
        }
        return freqValues;
    }

    private static Map<String, Pair<AtomicInteger, AtomicInteger>> getFreqVariables(ReduceVariablesConfigParamsYaml config, ReduceVariablesData.VariablesData data) {
        Map<String, Pair<AtomicInteger, AtomicInteger>> freqValues = new HashMap<>();

        for (ReduceVariablesData.TopPermutedVariables permutedVariable : data.permutedVariables) {
            for (ReduceVariablesData.PermutedVariables subPermutedVariable : permutedVariable.subPermutedVariables) {
                for (Map.Entry<String, String> varValue : subPermutedVariable.values.entrySet()) {
                    if (config.config.reduceByInstance.stream().noneMatch(o->o.inputIs.equals(varValue.getKey()))) {
                        continue;
                    }
                    freqValues
                            .computeIfAbsent(varValue.getKey(), (o)->Pair.of(new AtomicInteger(), new AtomicInteger()))
                            .getLeft().incrementAndGet();

                    if (subPermutedVariable.fitting== EnumsApi.Fitting.UNDERFITTING) {
                        continue;
                    }
                    if ("true".equals(varValue.getValue())) {
                        freqValues
                                .computeIfAbsent(varValue.getKey(), (o) -> Pair.of(new AtomicInteger(), new AtomicInteger()))
                                .getRight().incrementAndGet();
                    }
                }
            }
        }
        return freqValues;
    }

    @SneakyThrows
    public static ReduceVariablesData.VariablesData loadData(File zipFile, ReduceVariablesConfigParamsYaml config) {

        File tempDir = DirUtils.createMhTempDir("reduce-variables-");
        if (tempDir==null) {
            throw new RuntimeException("Can't create temp dir in metaheuristic-temp dir");
        }
        File zipDir = new File(tempDir, "zip");
        ZipUtils.unzipFolder(zipFile, zipDir, false, Collections.emptyList(), false);

        ReduceVariablesData.VariablesData data = new ReduceVariablesData.VariablesData();

        Collection<File> files =  FileUtils.listFiles(zipDir, new String[]{"zip"}, true);
        for (File f : files) {
            File tmp = DirUtils.createTempDir(tempDir, "load-data");
            File zipDataDir = new File(tmp, "zip");
            ZipUtils.unzipFolder(f, zipDataDir, false, Collections.emptyList(), false);

            File[] top = zipDataDir.listFiles(File::isDirectory);
            if (top==null || top.length==0) {
                throw new RuntimeException("can't find any dir in " + zipDataDir.getAbsolutePath());
            }

            File[] ctxDirs = top[0].listFiles(File::isDirectory);
            if (ctxDirs==null) {
                throw new RuntimeException("can't read content od dir " + top[0].getAbsolutePath());
            }

            ReduceVariablesData.TopPermutedVariables pvs = new ReduceVariablesData.TopPermutedVariables();
            data.permutedVariables.add(pvs);

            pvs.subPermutedVariables = new ArrayList<>();
            for (File ctxDir : ctxDirs) {
                File metadata = new File(ctxDir, MH_METADATA_YAML_FILE_NAME);
                MetadataAggregateFunctionParamsYaml mafpy;
                if (metadata.exists()) {
                    String yaml = FileUtils.readFileToString(metadata, StandardCharsets.UTF_8);
                    mafpy = MetadataAggregateFunctionParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
                }
                else {
                    mafpy = new MetadataAggregateFunctionParamsYaml();
                }

                Map<String, String> values = new HashMap<>();
                EnumsApi.Fitting fitting = null;
                MetricValues metricValues = null;
                for (File file : FileUtils.listFiles(ctxDir, null, false)) {
                    final String fileName = file.getName();
                    if (MH_METADATA_YAML_FILE_NAME.equals(fileName)) {
                        continue;
                    }
                    String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                    String varName = mafpy.mapping.stream()
                            .filter(o->o.get(fileName)!=null)
                            .findFirst()
                            .map(o->o.get(fileName))
                            .orElse( fixVarName(fileName, config.config.fixName) );

                    if (varName.equals(config.config.fittingVar)) {
                        FittingYaml fittingYaml = FittingYamlUtils.BASE_YAML_UTILS.to(content);
                        fitting = fittingYaml.fitting;
                    }
                    else if (varName.equals(config.config.metricsVar)) {
                        metricValues = MetricsUtils.getMetricValues(content);
                    }
                    else {
                        values.put(varName, content);
                    }
                }

                if (ctxDir.getName().equals("1")) {
                    pvs.values.putAll(values);
                }
                else {
                    final ReduceVariablesData.PermutedVariables permutedVariables = new ReduceVariablesData.PermutedVariables();
                    permutedVariables.fitting = fitting;
                    permutedVariables.metricValues = metricValues;
                    permutedVariables.values.putAll(values);
                    pvs.subPermutedVariables.add(permutedVariables);

                }
            }

            int i=0;
        }

        return data;
    }

    private static String fixVarName(String name, boolean fix) {
        if (fix) {
            int idx = name.lastIndexOf('.');
            if (idx==-1) {
                return name;
            }
            return name.substring(0, idx);
        }
        return name;

    }
}
