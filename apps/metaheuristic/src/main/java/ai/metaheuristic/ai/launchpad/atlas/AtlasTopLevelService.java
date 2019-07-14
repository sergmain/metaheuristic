/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.atlas;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.beans.Atlas;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.data.AtlasData;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentUtils;
import ai.metaheuristic.ai.launchpad.repositories.AtlasRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookGraphService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlUtils;
import ai.metaheuristic.ai.yaml.atlas.AtlasParamsYamlWithCache;
import ai.metaheuristic.ai.yaml.metrics.MetricValues;
import ai.metaheuristic.ai.yaml.metrics.MetricsUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.atlas.AtlasParamsYaml;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.data.experiment.ExperimentParamsYaml.ExperimentFeature;
import static ai.metaheuristic.api.data.experiment.ExperimentParamsYaml.HyperParam;

@SuppressWarnings("Duplicates")
@Slf4j
@Service
@Profile("launchpad")
@RequiredArgsConstructor
public class AtlasTopLevelService {

    private final AtlasRepository atlasRepository;
    private final WorkbookGraphService workbookGraphService;

    private static class ParamFilter {
        String key;
        int idx;

        ParamFilter(String filter) {
            final int endIndex = filter.lastIndexOf('-');
            this.key = filter.substring( 0, endIndex);
            this.idx = Integer.parseInt(filter.substring( endIndex+1));
        }
        static ParamFilter of(String filetr) {
            return new ParamFilter(filetr);
        }
    }

    public AtlasData.ExperimentInfoExtended getExperimentInfo(Long id) {

        Atlas atlas = atlasRepository.findById(id).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentInfoExtended("#280.02 experiment wasn't found in atlas, id: " + id);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(AtlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params));
        } catch (YAMLException e) {
            String es = "#280.05 Can't parse an atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.ExperimentInfoExtended(es);
        }
        if (ypywc.atlasParams.experiment == null) {
            return new AtlasData.ExperimentInfoExtended("#280.07 experiment wasn't found, experimentId: " + id);
        }
        if (ypywc.atlasParams.workbook == null) {
            return new AtlasData.ExperimentInfoExtended("#280.16 experiment has broken ref to workbook, experimentId: " + id);
        }
        if (ypywc.atlasParams.workbook.workbookId==null ) {
            return new AtlasData.ExperimentInfoExtended("#280.12 experiment wasn't startet yet, experimentId: " + id);
        }

        ExperimentApiData.ExperimentData experiment = new ExperimentApiData.ExperimentData();
        experiment.id = ypywc. atlasParams.experiment.experimentId;
        experiment.workbookId = ypywc.atlasParams.workbook.workbookId;

        ExperimentParamsYaml epy = ypywc.getExperimentParamsYaml();
        experiment.code = epy.experimentYaml.code;
        experiment.name = epy.experimentYaml.name;
        experiment.description = epy.experimentYaml.description;
        experiment.seed = epy.experimentYaml.seed;
        experiment.isAllTaskProduced = epy.processing.isAllTaskProduced;
        experiment.isFeatureProduced = epy.processing.isFeatureProduced;
        experiment.createdOn = epy.createdOn;
        experiment.numberOfTask = epy.processing.numberOfTask;
        experiment.hyperParams = epy.experimentYaml.hyperParams;



        for (HyperParam hyperParams : ypywc.getExperimentParamsYaml().experimentYaml.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants(variants.status ? variants.count : 0);
        }

        AtlasData.ExperimentInfoExtended result = new AtlasData.ExperimentInfoExtended();
        if (experiment.getWorkbookId() == null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }
        result.atlas = atlas;

        WorkbookImpl workbook = new WorkbookImpl();
        workbook.params = ypywc.atlasParams.workbook.workbookParams;
        workbook.id = ypywc.atlasParams.workbook.workbookId;
        workbook.execState = ypywc.atlasParams.workbook.execState;
        List<WorkbookParamsYaml.TaskVertex> taskVertices = workbookGraphService.findAll(workbook);

        AtlasData.ExperimentInfo experimentInfoResult = new AtlasData.ExperimentInfo();
        experimentInfoResult.features = ypywc.getExperimentParamsYaml().processing.features
                .stream()
                .map(e -> ExperimentService.asExperimentFeatureData(e, taskVertices, epy.processing.taskFeatures)).collect(Collectors.toList());

        experimentInfoResult.workbook = workbook;
        experimentInfoResult.workbookExecState = EnumsApi.WorkbookExecState.toState(workbook.execState);

        result.experiment = experiment;
        result.experimentInfo = experimentInfoResult;
        return result;
    }

    public OperationStatusRest experimentDeleteCommit(Long id) {
        Atlas atlas = atlasRepository.findById(id).orElse(null);
        if (atlas == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#280.19 experiment wasn't found in atlas, id: " + id);
        }
        atlasRepository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public AtlasData.PlotData getPlotData(Long atlasId, Long experimentId, Long featureId, String[] params, String[] paramsAxis) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.PlotData("#280.22 experiment wasn't found in atlas, id: " + atlasId);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(AtlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params));
        } catch (YAMLException e) {
            String es = "#280.05 Can't parse an atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.PlotData(es);
        }
        ExperimentFeature feature = ypywc.getFeature(featureId);

        //noinspection UnnecessaryLocalVariable
        AtlasData.PlotData data = findExperimentTaskForPlot(ypywc, feature, params, paramsAxis);
        return data;
    }

    public AtlasData.PlotData findExperimentTaskForPlot(
            AtlasParamsYamlWithCache estb, ExperimentFeature feature, String[] params, String[] paramsAxis) {
        if (estb.atlasParams.experiment == null || estb.getExperimentParamsYaml().processing.features == null ) {
            return AtlasData.EMPTY_PLOT_DATA;
        } else {
            List<AtlasParamsYaml.TaskWithParams> selected = getTasksForFeatureIdAndParams(estb, feature, params);
            return collectDataForPlotting(estb, selected, paramsAxis);
        }
    }

    public List<AtlasParamsYaml.TaskWithParams> getTasksForFeatureIdAndParams(AtlasParamsYamlWithCache estb1, ExperimentFeature feature, String[] params) {
        final Map<Long, Integer> taskToTaskType = estb1.getExperimentParamsYaml().processing.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(feature.getId()))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

        List<AtlasParamsYaml.TaskWithParams> selected = estb1.atlasParams.tasks.stream()
                .filter(o -> taskToTaskType.containsKey(o.taskId) && o.execState > 1)
                .collect(Collectors.toList());

        if (!isEmpty(params)) {
            selected = filterTasks(estb1.getExperimentParamsYaml(), params, selected);
        }
        return selected;
    }

    private AtlasData.PlotData collectDataForPlotting(AtlasParamsYamlWithCache estb, List<AtlasParamsYaml.TaskWithParams> selected, String[] paramsAxis) {
        final AtlasData.PlotData data = new AtlasData.PlotData();
        final List<String> paramCleared = new ArrayList<>();
        for (String param : paramsAxis) {
            if (StringUtils.isBlank(param)) {
                continue;
            }
            if (!paramCleared.contains(param)) {
                paramCleared.add(param);
            }
        }
        if (paramCleared.size()!=2) {
            throw new IllegalStateException("#280.27 Wrong number of params for axes. Expected: 2, actual: " + paramCleared.size());
        }
        Map<String, Map<String, Integer>> map = estb.getHyperParamsAsMap(false);
        data.x.addAll(map.get(paramCleared.get(0)).keySet());
        data.y.addAll(map.get(paramCleared.get(1)).keySet());

        Map<String, Integer> mapX = new HashMap<>();
        int idx=0;
        for (String x : data.x) {
            mapX.put(x, idx++);
        }
        Map<String, Integer> mapY = new HashMap<>();
        idx=0;
        for (String y : data.y) {
            mapY.put(y, idx++);
        }

        data.z = new BigDecimal[data.y.size()][data.x.size()];
        for (int i = 0; i < data.y.size(); i++) {
            for (int j = 0; j < data.x.size(); j++) {
                data.z[i][j] = BigDecimal.ZERO;
            }
        }

        String metricKey = null;
        for (AtlasParamsYaml.TaskWithParams task : selected) {

            MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(task.metrics) );
            if (metricValues==null) {
                continue;
            }
            if (metricKey==null) {
                //noinspection LoopStatementThatDoesntLoop
                for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                    metricKey = entry.getKey();
                    break;
                }
            }

            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.taskParams);
            int idxX = mapX.get(taskParamYaml.taskYaml.hyperParams.get(paramCleared.get(0)));
            int idxY = mapY.get(taskParamYaml.taskYaml.hyperParams.get(paramCleared.get(1)));
            data.z[idxY][idxX] = data.z[idxY][idxX].add(metricValues.values.get(metricKey));
        }

        return data;
    }


    private List<AtlasParamsYaml.TaskWithParams> filterTasks(ExperimentParamsYaml epy, String[] params, List<AtlasParamsYaml.TaskWithParams> tasks) {
        final Set<String> paramSet = new HashSet<>();
        final Set<String> paramFilterKeys = new HashSet<>();
        for (String param : params) {
            if (StringUtils.isBlank(param)) {
                continue;
            }
            paramSet.add(param);
            paramFilterKeys.add(ParamFilter.of(param).key);
        }
        final Map<String, Map<String, Integer>> paramByIndex = ExperimentService.getHyperParamsAsMap(epy);

        List<AtlasParamsYaml.TaskWithParams> selected = new ArrayList<>();
        for (AtlasParamsYaml.TaskWithParams task : tasks) {
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.taskParams);
            boolean[] isOk = new boolean[taskParamYaml.taskYaml.hyperParams.size()];
            int idx = 0;
            for (Map.Entry<String, String> entry : taskParamYaml.taskYaml.hyperParams.entrySet()) {
                try {
                    if (!paramFilterKeys.contains(entry.getKey())) {
                        isOk[idx] = true;
                        continue;
                    }
                    final Map<String, Integer> map = paramByIndex.getOrDefault(entry.getKey(), new HashMap<>());
                    if (map.isEmpty()) {
                        continue;
                    }
                    if (map.size()==1) {
                        isOk[idx] = true;
                        continue;
                    }

                    boolean isFilter = paramSet.contains(entry.getKey() + "-" + paramByIndex.get(entry.getKey()).get(entry.getKey() + "-" + entry.getValue()));
                    if (isFilter) {
                        isOk[idx] = true;
                    }
                }
                finally {
                    idx++;
                }
            }
            if (isInclude(isOk)) {
                selected.add(task);
            }
        }
        return selected;
    }

    private boolean isInclude(boolean[] isOk ) {
        for (boolean b : isOk) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmpty(String[] params) {
        for (String param : params) {
            if (StringUtils.isNotBlank(param)) {
                return false;
            }
        }
        return true;
    }

    public AtlasData.ExperimentFeatureExtendedResult getExperimentFeatureExtended(long atlasId, Long experimentId, Long featureId) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#280.31 experiment wasn't found in atlas, id: " + atlasId);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(AtlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params));
        } catch (YAMLException e) {
            final String es = "#280.35 Can't extract experiment from atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.ExperimentFeatureExtendedResult(es);
        }

        ExperimentFeature experimentFeature = ypywc.getFeature(featureId);
        if (experimentFeature == null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#280.37 feature wasn't found, experimentFeatureId: " + featureId);
        }

        AtlasData.ExperimentFeatureExtendedResult result = prepareExperimentFeatures(ypywc, experimentFeature);
        if (result==null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#280.40 can't prepare experiment data");
        }
        return result;
    }

    private AtlasData.ExperimentFeatureExtendedResult prepareExperimentFeatures(
            AtlasParamsYamlWithCache ypywc, final ExperimentFeature experimentFeature) {

        ExperimentParamsYaml epy = ypywc.getExperimentParamsYaml();
        final Map<Long, Integer> taskToTaskType = epy.processing.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(experimentFeature.id))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

        List<AtlasParamsYaml.TaskWithParams> taskWIthTypes = ypywc.atlasParams.tasks.stream()
                .filter(o->taskToTaskType.containsKey(o.taskId))
                .sorted(Comparator.comparingLong(o -> o.taskId))
                .limit(Consts.PAGE_REQUEST_10_REC.getPageSize() + 1)
                .collect(Collectors.toList());

        Slice<AtlasParamsYaml.TaskWithParams> tasks = new SliceImpl<>(
                taskWIthTypes.subList(0,
                        taskWIthTypes.size()>Consts.PAGE_REQUEST_10_REC.getPageSize()
                                ? Consts.PAGE_REQUEST_10_REC.getPageSize()
                                : taskWIthTypes.size()),
                Consts.PAGE_REQUEST_10_REC,taskWIthTypes.size()>10
        );

        AtlasData.HyperParamResult hyperParamResult = new AtlasData.HyperParamResult();
        for (HyperParam hyperParam : epy.experimentYaml.getHyperParams()) {
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParam.getValues());
            ExperimentApiData.HyperParamList list = new ExperimentApiData.HyperParamList(hyperParam.getKey());
            for (String value : variants.values) {
                list.getList().add( new ExperimentApiData.HyperParamElement(value, false));
            }
            if (list.getList().isEmpty()) {
                list.getList().add( new ExperimentApiData.HyperParamElement("<Error value>", false));
            }
            hyperParamResult.getElements().add(list);
        }

        final AtlasData.MetricsResult metricsResult = new AtlasData.MetricsResult();
        final List<Map<String, BigDecimal>> values = new ArrayList<>();

        ypywc.atlasParams.tasks.stream()
                .filter(o->taskToTaskType.containsKey(o.taskId) && o.execState > 1)
                .forEach( o-> {
                    MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(o.metrics) );
                    if (metricValues==null) {
                        return;
                    }
                    for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                        metricsResult.metricNames.add(entry.getKey());
                    }
                    values.add(metricValues.values);

                });

//        List<Task> tasks = taskRepository.findByIsCompletedIsTrueAndIds(experimentFeature.getId());
//        for (Task seq : tasks) {
//            MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(seq.metrics) );
//            if (metricValues==null) {
//                continue;
//            }
//            for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
//                metricsResult.metricNames.add(entry.getKey());
//            }
//            values.add(metricValues.values);
//        }

        List<AtlasData.MetricElement> elements = new ArrayList<>();
        for (Map<String, BigDecimal> value : values) {
            AtlasData.MetricElement element = new AtlasData.MetricElement();
            for (String metricName : metricsResult.metricNames) {
                element.values.add(value.get(metricName));
            }
            elements.add(element);
        }
        elements.sort(ExperimentService::compareMetricElement);

        metricsResult.metrics.addAll( elements.subList(0, Math.min(20, elements.size())) );

        WorkbookImpl workbook = new WorkbookImpl();
        workbook.params = ypywc.atlasParams.workbook.workbookParams;
        workbook.id = ypywc.atlasParams.workbook.workbookId;
        workbook.execState = ypywc.atlasParams.workbook.execState;
        List<WorkbookParamsYaml.TaskVertex> taskVertices = workbookGraphService.findAll(workbook);

        AtlasData.ExperimentFeatureExtendedResult result = new AtlasData.ExperimentFeatureExtendedResult();
        result.metricsResult = metricsResult;
        result.hyperParamResult = hyperParamResult;
        result.tasks = tasks;
        result.experimentFeature = ExperimentService.asExperimentFeatureData(experimentFeature, taskVertices, epy.processing.taskFeatures);
        result.consoleResult = new AtlasData.ConsoleResult();

        return result;
    }

    public AtlasData.ConsoleResult getTasksConsolePart(long atlasId, long taskId) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ConsoleResult("#280.42 experiment wasn't found in atlas, id: " + atlasId);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(AtlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params));
        } catch (YAMLException e) {
            final String es = "#280.45 Can't extract experiment from atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.ConsoleResult(es);
        }
        AtlasParamsYaml.TaskWithParams task = ypywc.atlasParams.tasks.stream()
                .filter(t->t.taskId.equals(taskId)).findFirst().orElse(null);

        if (task==null ) {
            return new AtlasData.ConsoleResult("#280.47 Can't find a console output");
        }
        SnippetApiData.SnippetExec snippetExec = SnippetExecUtils.to(task.exec);
        return new AtlasData.ConsoleResult(snippetExec.exec.exitCode, snippetExec.exec.isOk, snippetExec.exec.console);
    }

    public AtlasData.ExperimentFeatureExtendedResult getFeatureProgressPart(long atlasId, Long experimentId, Long featureId, String[] params, Pageable pageable) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#280.57 experiment wasn't found in atlas, id: " + atlasId);
        }

        AtlasParamsYamlWithCache ypywc;
        try {
            ypywc = new AtlasParamsYamlWithCache(AtlasParamsYamlUtils.BASE_YAML_UTILS.to(atlas.params));
        } catch (YAMLException e) {
            final String es = "#280.60 Can't extract experiment from atlas, error: " + e.toString();
            log.error(es, e);
            return new AtlasData.ExperimentFeatureExtendedResult(es);
        }

        ExperimentFeature feature = ypywc.getFeature(featureId);

        WorkbookImpl workbook = new WorkbookImpl();
        workbook.params = ypywc.atlasParams.workbook.workbookParams;
        workbook.id = ypywc.atlasParams.workbook.workbookId;
        workbook.execState = ypywc.atlasParams.workbook.execState;
        List<WorkbookParamsYaml.TaskVertex> taskVertices = workbookGraphService.findAll(workbook);

        AtlasData.ExperimentFeatureExtendedResult result = new AtlasData.ExperimentFeatureExtendedResult();
        result.tasks = findTasks(ypywc, ControllerUtils.fixPageSize(10, pageable), feature, params);
        result.experimentFeature = ExperimentService.asExperimentFeatureData(feature, taskVertices, ypywc.getExperimentParamsYaml().processing.taskFeatures);;
        result.consoleResult = new AtlasData.ConsoleResult();
        return result;
    }

    private Slice<AtlasParamsYaml.TaskWithParams> findTasks(AtlasParamsYamlWithCache estb, Pageable pageable, ExperimentFeature feature, String[] params) {
        if (feature == null) {
            return Page.empty();
        }
        List<AtlasParamsYaml.TaskWithParams> selected = getTasksForFeatureIdAndParams(estb, feature, params);
        List<AtlasParamsYaml.TaskWithParams> subList = selected.subList((int)pageable.getOffset(), (int)Math.min(selected.size(), pageable.getOffset() + pageable.getPageSize()));
        //noinspection UnnecessaryLocalVariable
        Slice<AtlasParamsYaml.TaskWithParams> slice = new PageImpl<>(subList, pageable, selected.size());
        return slice;
    }
}
