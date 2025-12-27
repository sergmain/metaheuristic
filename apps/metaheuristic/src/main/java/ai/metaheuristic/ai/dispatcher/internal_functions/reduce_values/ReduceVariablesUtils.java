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

package ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values;

import ai.metaheuristic.ai.dispatcher.data.ReduceVariablesData;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYaml;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYamlUtils;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYaml;
import ai.metaheuristic.commons.yaml.ml.fitting.FittingYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricValues;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.Consts.MH_METADATA_YAML_FILE_NAME;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:27 PM
 */
public class ReduceVariablesUtils {

    @AllArgsConstructor
    public static class ProgressData {
        public String file;
        public int total;
        public int current;
    }

    public static ReduceVariablesData.ReduceVariablesResult reduceVariables(
            Path actualTemp, Path zipFile, ReduceVariablesConfigParamsYaml config, ReduceVariablesData.Request request) {
        return reduceVariables(actualTemp, List.of(zipFile), config, request, null, (o)->{}, (o)->false);
    }

    public static ReduceVariablesData.ReduceVariablesResult reduceVariables(
            Path actualTemp, List<Path> zipFiles, ReduceVariablesConfigParamsYaml config, ReduceVariablesData.Request request, @Nullable Function<String, Boolean> filter,
            Consumer<ProgressData> progressConsumer, Function<ReduceVariablesData.PermutedVariables, Boolean> attentionSelector
            ) {

        ReduceVariablesData.VariablesData data = new ReduceVariablesData.VariablesData();
        for (Path zipFile : zipFiles) {
            loadData(actualTemp, data, zipFile, config, progressConsumer);
        }

        Map<String, Map<String, Pair<AtomicInteger, AtomicInteger>>> freqValues = getFreqValues(data, filter);
        System.out.println("\n=====================");
        for (Map.Entry<String, Map<String, Pair<AtomicInteger, AtomicInteger>>> entry : freqValues.entrySet()) {
            System.out.println(entry.getKey());
            for (Map.Entry<String, Pair<AtomicInteger, AtomicInteger>> en : entry.getValue().entrySet()) {
                System.out.printf("\t%-15s  %5d %5d\n", en.getKey(), en.getValue().getLeft().get(), en.getValue().getRight().get());
            }
        }

        Map<String, Pair<AtomicInteger, AtomicInteger>> freqVariables = getFreqVariables(config, data, filter);
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

        result.attentionsAndExperimentMetrics = getExperimentMetrics(data, attentionSelector);

        return result;
    }

    private static Map<String, Map<String, Pair<AtomicInteger, AtomicInteger>>> getFreqValues(
            ReduceVariablesData.VariablesData data, @Nullable Function<String, Boolean> filter) {

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
                    if (filter!=null && (subPermutedVariable.metricValues==null || !filter.apply(subPermutedVariable.metricValues.comment))) {
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



    private static ReduceVariablesData.AttentionsAndExperimentMetrics getExperimentMetrics(
            ReduceVariablesData.VariablesData data, Function<ReduceVariablesData.PermutedVariables, Boolean> attentionSelector) {
        ReduceVariablesData.AttentionsAndExperimentMetrics r = new ReduceVariablesData.AttentionsAndExperimentMetrics();

        boolean emptyMetrics = false;
        for (ReduceVariablesData.TopPermutedVariables permutedVariable : data.permutedVariables) {
            for (ReduceVariablesData.PermutedVariables subPermutedVariable : permutedVariable.subPermutedVariables) {
                if (attentionSelector.apply(subPermutedVariable)) {
                    if (subPermutedVariable.metricValues==null || S.b(subPermutedVariable.metricValues.comment)) {
                        System.out.println("(subPermutedVariable.metricValues==null || S.b(subPermutedVariable.metricValues.comment))");
                        continue;
                    }
                    final ReduceVariablesData.Attention attention = new ReduceVariablesData.Attention();
                    attention.params.putAll(permutedVariable.values);
                    attention.params.remove("predicted");
                    for (Map.Entry<String, String> entry : subPermutedVariable.values.entrySet()) {
                        if (!entry.getKey().startsWith("is")) {
                            continue;
                        }
                        attention.dataset.put(entry.getKey(), entry.getValue());
                    }
                    attention.result = subPermutedVariable.metricValues.comment;
                    r.attentions.add(attention);
                }
                if (subPermutedVariable.fitting== EnumsApi.Fitting.UNDERFITTING) {
                    continue;
                }
                if (subPermutedVariable.metricValues==null) {
                    emptyMetrics = true;
                    continue;
                }
                ReduceVariablesData.ExperimentMetrics em = new ReduceVariablesData.ExperimentMetrics();
                em.metricValues = subPermutedVariable.metricValues;
                em.data = subPermutedVariable.values.entrySet().stream().filter(o->"true".equals(o.getValue())).map(Map.Entry::getKey).collect(Collectors.joining(", "));
                em.hyper = permutedVariable.values.entrySet().stream().map(o->""+o.getKey()+":"+o.getValue()).collect(Collectors.joining(", "));
                em.metrics = subPermutedVariable.metricValues.values.entrySet().stream().map(o->""+o.getKey()+":"+o.getValue()).collect(Collectors.joining(","));
                em.dir = subPermutedVariable.dir;

                r.metricsList.add(em);
            }
        }
        if (emptyMetrics) {
            System.out.println("Found an empty metrics");
        }
        final String metricsName = "sum_norm_6";
        r.metricsList.sort((o1, o2) -> o2.metricValues.values.get(metricsName).compareTo(o1.metricValues.values.get(metricsName)));
        return r;
    }

    private static Map<String, Pair<AtomicInteger, AtomicInteger>> getFreqVariables(
            ReduceVariablesConfigParamsYaml config, ReduceVariablesData.VariablesData data, @Nullable Function<String, Boolean> filter) {
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
                    if (filter!=null && (subPermutedVariable.metricValues==null || !filter.apply(subPermutedVariable.metricValues.comment))) {
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

    private static final IOFileFilter filter = FileFileFilter.INSTANCE.and(new SuffixFileFilter(new String[]{".zip"}));

    @SneakyThrows
    public static ReduceVariablesData.VariablesData loadData(Path actualTemp, ReduceVariablesData.VariablesData data, Path zipFile, ReduceVariablesConfigParamsYaml config,
                                                             Consumer<ProgressData> consumer) {

        Path tempDir = Files.createTempDirectory(actualTemp, "reduce-variables-");
        Path zipDir = actualTemp.resolve("zip");
        Files.createDirectories(zipDir);

        ZipUtils.unzipFolder(zipFile, zipDir, false, Collections.emptyList(), false);

        Collection<Path> files = PathUtils.walk(zipDir, filter, Integer.MAX_VALUE, false, FileVisitOption.FOLLOW_LINKS).collect(Collectors.toList());
        int i=0;
        for (Path f : files) {
            consumer.accept(new ProgressData(""+ zipFile.normalize(), files.size(), ++i));
            Path tmp = Files.createTempDirectory(tempDir, "load-data-");
            Path zipDataDir = tmp.resolve("zip");
            ZipUtils.unzipFolder(f, zipDataDir, false, Collections.emptyList(), false);

            List<Path> top;
            // do not remove try(){}
            try (final Stream<Path> stream = Files.list(zipDataDir)) {
                top = stream.collect(Collectors.toList());
            }

            if (top.isEmpty()) {
                throw new RuntimeException("can't find any dir in " + zipDataDir.normalize());
            }
            if (top.size()>1) {
                throw new RuntimeException("actual size: " +top.size()+", " + top);
            }

            Collection<Path> ctxDirs;
            // do not remove try(Stream<Path>){}
            try (final Stream<Path> stream = Files.list(top.get(0))) {
                ctxDirs = stream.collect(Collectors.toList());
            }
            if (ctxDirs.isEmpty()) {
                throw new RuntimeException("can't find any dir in " + top.get(0).normalize());
            }

            ReduceVariablesData.TopPermutedVariables pvs = new ReduceVariablesData.TopPermutedVariables();
            data.permutedVariables.add(pvs);

            pvs.subPermutedVariables = new ArrayList<>();
            for (Path ctxDir : ctxDirs) {
                Path metadata = ctxDir.resolve(MH_METADATA_YAML_FILE_NAME);
                MetadataAggregateFunctionParamsYaml mafpy;
                if (Files.exists(metadata)) {
                    String yaml = Files.readString(metadata, StandardCharsets.UTF_8);
                    mafpy = MetadataAggregateFunctionParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
                }
                else {
                    mafpy = new MetadataAggregateFunctionParamsYaml();
                }

                Map<String, String> values = new HashMap<>();
                EnumsApi.Fitting fitting = null;
                MetricValues metricValues = null;

                Collection<Path> ffs;
                // do not remove try(Stream<Path>){}
                try (final Stream<Path> stream = Files.list(ctxDir)) {
                    ffs = stream.collect(Collectors.toList());
                }

                for (Path file : ffs) {
                    final String fileName = file.getFileName().toString();
                    if (MH_METADATA_YAML_FILE_NAME.equals(fileName)) {
                        continue;
                    }
                    String content = Files.readString(file, StandardCharsets.UTF_8);
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

                if (ctxDir.getFileName().toString().equals("1")) {
                    pvs.values.putAll(values);
                }
                else {
                    final ReduceVariablesData.PermutedVariables permutedVariables = new ReduceVariablesData.PermutedVariables();
                    permutedVariables.dir = ctxDir.toString();
                    permutedVariables.fitting = fitting;
                    permutedVariables.metricValues = metricValues;
                    permutedVariables.values.putAll(values);
                    pvs.subPermutedVariables.add(permutedVariables);

                }
            }

            int ii=0;
        }
        PathUtils.delete(tempDir);
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
