/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.utils.holders.IntHolder;
import ai.metaheuristic.ai.utils.permutation.Permutation;
import ai.metaheuristic.ai.yaml.hyper_params.HyperParams;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.experiment.BaseMetricElement;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.task.TaskWIthType;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.TaskMachineLearningYaml;
import ai.metaheuristic.commons.yaml.task_ml.TaskMachineLearningYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricValues;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.SourceCodeProducingStatus.TOO_MANY_TASKS_PER_SOURCE_CODE_ERROR;
import static ai.metaheuristic.api.data.experiment.ExperimentParamsYaml.*;

@SuppressWarnings("DuplicatedCode")
@Service
@EnableTransactionManagement
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentService {

    private final Globals globals;
    private final ApplicationEventMulticaster eventMulticaster;

    private final ParamsSetter paramsSetter;
    private final MetricsMaxValueCollector metricsMaxValueCollector;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final FunctionService functionService;
    private final ExecContextCache execContextCache;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    @Async
    @EventListener
    public void handleAsync(DispatcherInternalEvent.ExperimentResetEvent event) {
        resetExperimentByExecContextId(event.execContextId);
    }

    public static int compareMetricElement(BaseMetricElement o2, BaseMetricElement o1) {
        for (int i = 0; i < Math.min(o1.getValues().size(), o2.getValues().size()); i++) {
            final BigDecimal holder1 = o1.getValues().get(i);
            if (holder1 == null) {
                return -1;
            }
            final BigDecimal holder2 = o2.getValues().get(i);
            if (holder2 == null) {
                return -1;
            }
            int c = ObjectUtils.compare(holder1, holder2);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(o1.getValues().size(), o2.getValues().size());
    }

    public static ExperimentApiData.ExperimentData asExperimentData(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();

        ExperimentApiData.ExperimentData ed = new ExperimentApiData.ExperimentData();
        ed.id = e.id;
        ed.version = e.version;
        ed.code = e.code;
        ed.execContextId = e.execContextId;
        ed.name = params.experimentYaml.name;
        ed.seed = params.experimentYaml.seed;
        ed.description = params.experimentYaml.description;
        ed.hyperParams = params.experimentYaml.hyperParams==null ? new ArrayList<>() : params.experimentYaml.hyperParams;
        ed.hyperParamsAsMap = getHyperParamsAsMap(ed.hyperParams);
        ed.createdOn = params.createdOn;
        ed.numberOfTask = params.processing.numberOfTask;

        return ed;
    }

    public static ExperimentApiData.ExperimentData asExperimentDataShort(Experiment e) {
        ExperimentParamsYaml params = e.getExperimentParamsYaml();

        ExperimentApiData.ExperimentData ed = new ExperimentApiData.ExperimentData();
        ed.id = e.id;
        ed.version = e.version;
        ed.code = e.code;
        ed.execContextId = e.execContextId;
        ed.name = params.experimentYaml.name;
        ed.seed = params.experimentYaml.seed;
        ed.description = params.experimentYaml.description;
        ed.hyperParams = null;
        ed.hyperParamsAsMap = null;
        ed.createdOn = params.createdOn;
        ed.numberOfTask = params.processing.numberOfTask;

        return ed;
    }

    public static ExperimentApiData.ExperimentFeatureData asExperimentFeatureData(
            ExperimentFeature experimentFeature,
            List<ExecContextParamsYaml.TaskVertex> taskVertices,
            List<ExperimentTaskFeature> taskFeatures) {
        final ExperimentApiData.ExperimentFeatureData featureData = new ExperimentApiData.ExperimentFeatureData();
        BeanUtils.copyProperties(experimentFeature, featureData);

        List<ExperimentTaskFeature> etfs = taskFeatures.stream().filter(tf->tf.featureId.equals(featureData.id)).collect(Collectors.toList());

        Set<EnumsApi.TaskExecState> statuses = taskVertices
                .stream()
                .filter(t -> etfs
                        .stream()
                        .filter(etf-> etf.taskId.equals(t.taskId))
                        .findFirst()
                        .orElse(null) !=null ).map(o->o.execState)
                .collect(Collectors.toSet());

        Enums.FeatureExecStatus execStatus = statuses.isEmpty() ? Enums.FeatureExecStatus.empty : Enums.FeatureExecStatus.unknown;
        if (statuses.contains(EnumsApi.TaskExecState.OK)) {
            execStatus = Enums.FeatureExecStatus.finished;
        }
        if (statuses.contains(EnumsApi.TaskExecState.ERROR)|| statuses.contains(EnumsApi.TaskExecState.BROKEN)) {
            execStatus = Enums.FeatureExecStatus.finished_with_errors;
        }
        if (statuses.contains(EnumsApi.TaskExecState.NONE) || statuses.contains(EnumsApi.TaskExecState.IN_PROGRESS)) {
            execStatus = Enums.FeatureExecStatus.processing;
        }
        featureData.execStatusAsString = execStatus.info;
        return featureData;
    }

    // TODO 2019-07-13 Need to optimize the set of getHyperParamsAsMap() methods
    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(ExperimentParamsYaml epy) {
        return getHyperParamsAsMap(epy.experimentYaml.hyperParams, true);
    }

    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(Experiment experiment, boolean isFull) {
        return getHyperParamsAsMap(experiment.getExperimentParamsYaml().experimentYaml.hyperParams, isFull);
    }

    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(List<HyperParam> experimentHyperParams) {
        return getHyperParamsAsMap(experimentHyperParams, true);
    }

    public static Map<String, Map<String, Integer>> getHyperParamsAsMap(List<HyperParam> experimentHyperParams, boolean isFull) {
        final Map<String, Map<String, Integer>> paramByIndex = new LinkedHashMap<>();
        for (HyperParam hyperParam : experimentHyperParams) {
            ExperimentUtils.NumberOfVariants ofVariants = ExperimentUtils.getNumberOfVariants(hyperParam.getValues() );
            Map<String, Integer> map = new LinkedHashMap<>();
            paramByIndex.put(hyperParam.getKey(), map);
            for (int i = 0; i <ofVariants.values.size(); i++) {
                String value = ofVariants.values.get(i);


                map.put(isFull ? hyperParam.getKey()+'-'+value : value , i);
            }
        }
        return paramByIndex;
    }

    public void experimentFinisher() {

        List<Long> experimentIds = experimentRepository.findAllIds();
        for (Long experimentId : experimentIds) {
            Experiment e = experimentCache.findById(experimentId);
            if (e==null) {
                log.warn("#179.010 Experiment wasn't found for id: {}", experimentId);
                continue;
            }
            if (e.execContextId ==null) {
                log.warn("#179.020 This shouldn't be happened");
                continue;
            }
            ExecContextImpl wb = execContextCache.findById(e.execContextId);
            if (wb==null) {
                log.info("#179.030 Can't calc max values and export to atlas because execContext is null");
                continue;
            }
            if (wb.state != EnumsApi.ExecContextState.FINISHED.code) {
                continue;
            }
            ExperimentParamsYaml epy = e.getExperimentParamsYaml();
            if (!epy.processing.maxValueCalculated) {
                updateMaxValueForExperimentFeatures(e.id);
            }
        }
    }

    private static class ParamFilter {
        String key;
        int idx;

        ParamFilter(String filter) {
            final int endIndex = filter.lastIndexOf('-');
            this.key = filter.substring( 0, endIndex);
            this.idx = Integer.parseInt(filter.substring( endIndex+1));
        }
        static ParamFilter of(String filter) {
            return new ParamFilter(filter);
        }
    }
    private boolean isInclude(boolean[] isOk ) {
        for (boolean b : isOk) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    public ExperimentApiData.PlotData getPlotData(Long experimentId, Long featureId, String[] params, String[] paramsAxis) {
        Experiment experiment= experimentCache.findById(experimentId);
        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        ExperimentFeature feature =
                epy.processing.features.stream().filter(o->o.id.equals(featureId)).findFirst().orElse(null);

        ExperimentApiData.PlotData data = findExperimentTaskForPlot(experiment, feature, params, paramsAxis);
        // TODO 2019-07-23 right now 2D lines plot isn't working. need to investigate
        //  so it'll be 3D with a fake zero data
        fixData(data);
        return data;
    }

    @SuppressWarnings("Duplicates")
    private void fixData(ExperimentApiData.PlotData data) {
        if (data.x.size()==1) {
            data.x.add("stub");
            BigDecimal[][] z = new BigDecimal[data.z.length][2];
            for (int i = 0; i < data.z.length; i++) {
                z[i][0] = data.z[i][0];
                z[i][1] = BigDecimal.ZERO;
            }
            data.z = z;
        }
        else if (data.y.size()==1) {
            data.y.add("stub");
            BigDecimal[][] z = new BigDecimal[2][data.z[0].length];
            for (int i = 0; i < data.z[0].length; i++) {
                z[0][i] = data.z[0][i];
                z[1][i] = BigDecimal.ZERO;
            }
            data.z = z;
        }
    }

    private void updateMaxValueForExperimentFeatures(Long experimentId) {
        Experiment experiment = experimentRepository.findByIdForUpdate(experimentId);
        if (experiment==null) {
            return;
        }
        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        List<ExperimentFeature> features = epy.processing.features;
        log.info("Start calculatingMaxValueOfMetrics");
        for (ExperimentFeature feature : features) {
            double value = metricsMaxValueCollector.calcMaxValueForMetrics(epy, feature.getId());
            log.info("\tFeature #{}, max value: {}", feature.getId(), value);
            feature.setMaxValue(value);
        }
        epy.processing.maxValueCalculated = true;
        experiment.updateParams(epy);
        experimentCache.save(experiment);
    }

    private void setExportedToAtlas(Long experimentId) {
        Experiment experiment = experimentRepository.findByIdForUpdate(experimentId);
        if (experiment==null) {
            return;
        }
        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        epy.processing.exportedToAtlas = true;
        experiment.updateParams(epy);
        experimentCache.save(experiment);
    }

    public Slice<TaskWIthType> findTasks(Pageable pageable, Experiment experiment, ExperimentFeature feature, String[] params) {
        if (experiment == null || feature == null) {
            return Page.empty();
        }
        else {
            Slice<TaskWIthType> slice;
            if (isEmpty(params)) {
                slice = findPredictTasks(pageable, experiment, feature.getId());
            } else {
                List<Task> selected = findTaskWithFilter(experiment, feature.getId(), params);
                List<Task> subList = selected.subList((int)pageable.getOffset(), (int)Math.min(selected.size(), pageable.getOffset() + pageable.getPageSize()));
                List<TaskWIthType> list = new ArrayList<>();
                for (Task task : subList) {
                    list.add(new TaskWIthType(task, EnumsApi.ExperimentTaskType.UNKNOWN.value));
                }
                slice = new PageImpl<>(list, pageable, selected.size());
                for (TaskWIthType taskWIthType : slice) {
                    experiment.getExperimentParamsYaml().processing.taskFeatures
                            .stream()
                            .filter(t -> t.taskId.equals(taskWIthType.task.getId()))
                            .findFirst()
                            .ifPresent(etf -> taskWIthType.type = EnumsApi.ExperimentTaskType.from(etf.getTaskType()).value);
                }
            }
            return slice;
        }
    }

    private Slice<TaskWIthType> findPredictTasks(Pageable pageable, Experiment experiment, Long featureId) {
        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        long total = epy.processing.taskFeatures
                .stream()
                .filter(o-> o.featureId.equals(featureId)).count();

        List<ExperimentTaskFeature> etfs = epy.processing.taskFeatures
                .stream()
                .filter(o-> o.featureId.equals(featureId))
                .sorted(Comparator.comparing(o -> o.id))
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toList());

        if (etfs.isEmpty()) {
            return Page.empty();
        }

        List<Long> ids = etfs.stream().mapToLong(ExperimentTaskFeature::getTaskId).boxed().collect(Collectors.toList());

        List<TaskImpl> tasks = ids.isEmpty() ? List.of() : taskRepository.findTasksByIds(ids);
        List<TaskWIthType> list = new ArrayList<>();
        for (TaskImpl task : tasks) {
            ExperimentTaskFeature tf = etfs.stream().filter(o->o.taskId.equals(task.id)).findFirst().orElse(null);
            list.add( new TaskWIthType(task, tf!=null ? tf.taskType : EnumsApi.ExperimentTaskType.UNKNOWN.value));
        }

        //noinspection UnnecessaryLocalVariable
        PageImpl<TaskWIthType> page = new PageImpl<>(list, pageable, total);
        return page;
    }

    private ExperimentApiData.PlotData findExperimentTaskForPlot(Experiment experiment, ExperimentFeature feature, String[] params, String[] paramsAxis) {
        if (experiment == null || feature == null) {
            return ExperimentApiData.EMPTY_PLOT_DATA;
        } else {
            List<Task> selected;
            if (isEmpty(params)) {
                selected = findByIsCompletedIsTrueAndFeatureId(experiment.getExperimentParamsYaml(), feature.id);
            } else {
                selected = findTaskWithFilter(experiment, feature.getId(), params);
            }
            return collectDataForPlotting(experiment, selected, paramsAxis);
        }
    }

    @SuppressWarnings("Duplicates")
    private ExperimentApiData.PlotData collectDataForPlotting(Experiment experiment, List<Task> selected, String[] paramsAxis) {
        final ExperimentApiData.PlotData data = new ExperimentApiData.PlotData();
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
            throw new IllegalStateException("#179.040 Wrong number of params for axes. Expected: 2, actual: " + paramCleared.size());
        }
        Map<String, Map<String, Integer>> map = getHyperParamsAsMap(experiment, false);
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
        for (Task task : selected) {

            if (S.b((task.getMetrics()))) {
                continue;
            }
            TaskMachineLearningYaml tmly = TaskMachineLearningYamlUtils.BASE_YAML_UTILS.to(task.getMetrics());
            MetricValues metricValues = MetricsUtils.getValues( tmly.metrics );
            if (metricValues==null) {
                continue;
            }
            if (metricKey==null) {
                for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                    metricKey = entry.getKey();
                    break;
                }
            }

            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            int idxX = mapX.get(taskParamYaml.taskYaml.taskMl.hyperParams.get(paramCleared.get(0)));
            int idxY = mapY.get(taskParamYaml.taskYaml.taskMl.hyperParams.get(paramCleared.get(1)));
            data.z[idxY][idxX] = data.z[idxY][idxX].add(metricValues.values.get(metricKey));
        }

        return data;
    }

    @SuppressWarnings("Duplicates")
    private List<Task> findTaskWithFilter(Experiment experiment, long featureId, String[] params) {
        final Set<String> paramSet = new HashSet<>();
        final Set<String> paramFilterKeys = new HashSet<>();
        for (String param : params) {
            if (StringUtils.isBlank(param)) {
                continue;
            }
            paramSet.add(param);
            paramFilterKeys.add(ParamFilter.of(param).key);
        }
        final Map<String, Map<String, Integer>> paramByIndex = getHyperParamsAsMap(experiment.getExperimentParamsYaml());

        List<Task> list = findByIsCompletedIsTrueAndFeatureId(experiment.getExperimentParamsYaml(), featureId);

        List<Task> selected = new ArrayList<>();
        for (Task task : list) {
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            boolean[] isOk = new boolean[taskParamYaml.taskYaml.taskMl.hyperParams.size()];
            int idx = 0;
            for (Map.Entry<String, String> entry : taskParamYaml.taskYaml.taskMl.hyperParams.entrySet()) {
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

    private boolean isEmpty(String[] params) {
        for (String param : params) {
            if (StringUtils.isNotBlank(param)) {
                return false;
            }
        }
        return true;
    }

    public ExperimentApiData.ExperimentFeatureExtendedResult prepareExperimentFeatures(Experiment experiment, Long featureId ) {
        if (experiment.execContextId ==null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#179.050 execContextId is null");
        }
        ExecContextImpl execContext = execContextCache.findById(experiment.execContextId);

        ExperimentParamsYaml.ExperimentFeature experimentFeature = experiment.getExperimentParamsYaml().getFeature(featureId);
        if (experimentFeature == null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#179.060 feature wasn't found, experimentFeatureId: " + featureId);
        }

        TaskApiData.TasksResult tasksResult = new TaskApiData.TasksResult();

        tasksResult.items = findPredictTasks(Consts.PAGE_REQUEST_10_REC, experiment, experimentFeature.getId());

        ExperimentApiData.HyperParamResult hyperParamResult = new ExperimentApiData.HyperParamResult();
        for (HyperParam hyperParam : experiment.getExperimentParamsYaml().experimentYaml.hyperParams) {
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

        ExperimentApiData.MetricsResult metricsResult = new ExperimentApiData.MetricsResult();
        List<Map<String, BigDecimal>> values = new ArrayList<>();

        List<Task> tasks = findByIsCompletedIsTrueAndFeatureId(experiment.getExperimentParamsYaml(), experimentFeature.id);
        for (Task seq : tasks) {
            if (S.b((seq.getMetrics()))) {
                continue;
            }
            TaskMachineLearningYaml tmly = TaskMachineLearningYamlUtils.BASE_YAML_UTILS.to(seq.getMetrics());
            MetricValues metricValues = MetricsUtils.getValues( tmly.metrics );
            if (metricValues==null) {
                continue;
            }
            for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                metricsResult.metricNames.add(entry.getKey());
            }
            values.add(metricValues.values);
        }

        List<ExperimentApiData.MetricElement> elements = new ArrayList<>();
        for (Map<String, BigDecimal> value : values) {
            ExperimentApiData.MetricElement element = new ExperimentApiData.MetricElement();
            for (String metricName : metricsResult.metricNames) {
                element.values.add(value.get(metricName));
            }
            elements.add(element);
        }
        elements.sort(ExperimentService::compareMetricElement);

        metricsResult.metrics.addAll( elements.subList(0, Math.min(20, elements.size())) );

        List<ExecContextParamsYaml.TaskVertex> taskVertices = execContextGraphTopLevelService.findAll(execContext);

        ExperimentApiData.ExperimentFeatureExtendedResult result = new ExperimentApiData.ExperimentFeatureExtendedResult();
        result.metricsResult = metricsResult;
        result.hyperParamResult = hyperParamResult;
        result.tasksResult = tasksResult;
        result.experiment = asExperimentData(experiment);
        result.experimentFeature = asExperimentFeatureData(experimentFeature, taskVertices, experiment.getExperimentParamsYaml().processing.taskFeatures);
        result.consoleResult = new ExperimentApiData.ConsoleResult();

        return result;
    }

    public List<Task> findByIsCompletedIsTrueAndFeatureId(ExperimentParamsYaml epy, Long featureId) {
        List<Long> ids = epy.getTaskFeatureIds(featureId);
        if (ids.isEmpty()) {
            return List.of();
        }
        //noinspection UnnecessaryLocalVariable
        List<Task> tasks = taskRepository.findByIsCompletedIsTrueAndIds(ids);
        return tasks;
    }

    private static Map<String, String> toMap(List<HyperParam> experimentHyperParams, int seed) {
        List<HyperParam> params = new ArrayList<>();
        HyperParam p1 = new HyperParam();
        p1.setKey(Consts.SEED);
        p1.setValues(Integer.toString(seed));
        params.add(p1);

        for (HyperParam param : experimentHyperParams) {
            //noinspection UseBulkOperation
            params.add(param);
        }
        return toMap(params);
    }

    private static Map<String, String> toMap(List<HyperParam> experimentHyperParams) {
        return experimentHyperParams.stream().collect(Collectors.toMap(HyperParam::getKey, HyperParam::getValues, (a, b) -> b, HashMap::new));
    }

    private void resetExperimentByExecContextId(Long execContextId) {
        Experiment e = experimentRepository.findByExecContextIdForUpdate(execContextId);
        if (e==null) {
            return;
        }

        ExperimentParamsYaml epy = e.getExperimentParamsYaml();
        epy.processing = new ExperimentProcessing();
        e.updateParams(epy);
        e.setExecContextId(null);

        //noinspection UnusedAssignment
        e = experimentCache.save(e);
    }

    public void bindExperimentToExecContext(Long experimentId, Long execContextId) {

        Experiment e = experimentRepository.findByIdForUpdate(experimentId);
        if (e==null) {
            return;
        }

        ExperimentParamsYaml epy = e.getExperimentParamsYaml();
        epy.processing = new ExperimentProcessing();
        e.updateParams(epy);
        e.setExecContextId(execContextId);

        //noinspection UnusedAssignment
        e = experimentCache.save(e);
    }

    @SuppressWarnings("Duplicates")
    SourceCodeService.ProduceTaskResult result = new SourceCodeService.ProduceTaskResult();

    @Data
    @AllArgsConstructor
    private static class ExperimentFunctionItem {
        public EnumsApi.ExperimentFunction type;
        public String functionCode;
    }

    public EnumsApi.SourceCodeProducingStatus produceTasks(
            boolean isPersist, SourceCodeParamsYaml sourceCodeParams, Long execContextId, SourceCodeParamsYaml.Process process,
            Experiment experiment, Map<String, List<String>> collectedInputs,
            Map<String, SourceCodeParamsYaml.Variable> inputStorageUrls, IntHolder numberOfTasks, List<Long> parentTaskIds) {

        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        if (StringUtils.isBlank(epy.experimentYaml.fitFunction)|| StringUtils.isBlank(epy.experimentYaml.predictFunction)) {
            throw new IllegalStateException("#179.080 (StringUtils.isBlank(epy.yaml.fitFunction)|| StringUtils.isBlank(epy.yaml.predictFunction))" +
                    ", "+epy.experimentYaml.fitFunction +", " + epy.experimentYaml.predictFunction);
        }
        List<ExperimentFunctionItem> experimentFunctions =
                List.of(new ExperimentFunctionItem(EnumsApi.ExperimentFunction.FIT, epy.experimentYaml.fitFunction),
                        new ExperimentFunctionItem(EnumsApi.ExperimentFunction.PREDICT, epy.experimentYaml.predictFunction));

        final Map<String, String> map = toMap(epy.experimentYaml.getHyperParams(), epy.experimentYaml.seed);
        final int calcTotalVariants = ExperimentUtils.calcTotalVariants(map);

        final List<ExperimentFeature> features = epy.processing.features;

        // there is 2 because we have 2 types of functions - fit and predict
        // feature has the real value only when isPersist==true
        int totalVariants = features.size() * calcTotalVariants * 2;

        if (totalVariants > globals.maxTasksPerExecContext) {
            log.error("#179.090 number of tasks for this execContext exceeded the allowed maximum number. ExecContext was created but its status is 'not valid'. " +
                                "Allowed maximum number of tasks per execContext: " + globals.maxTasksPerExecContext +", tasks in this execContext: " + totalVariants);
            return TOO_MANY_TASKS_PER_SOURCE_CODE_ERROR;
        }
        final List<HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);

        final Map<String, Function> localCache = new HashMap<>();
        final IntHolder size = new IntHolder();
        final Set<String> taskParams = paramsSetter.getParamsInTransaction(isPersist, execContextId, experiment, size);

        numberOfTasks.value = 0;

        log.debug("total size of tasks' params received from db is {} bytes", size.value);
        final AtomicBoolean boolHolder = new AtomicBoolean();
        final Consumer<Long> longConsumer = o -> {
            if (execContextId.equals(o)) {
                boolHolder.set(true);
            }
        };
        final DispatcherInternalEvent.ExecContextDeletionListener listener =
                new DispatcherInternalEvent.ExecContextDeletionListener(execContextId, longConsumer);

        int processed = 0;
        long prevMills = System.currentTimeMillis();
        try {
            eventMulticaster.addApplicationListener(listener);
            ExecContext wb = execContextCache.findById(execContextId);
            if (wb==null) {
                return EnumsApi.SourceCodeProducingStatus.EXEC_CONTEXT_NOT_FOUND_ERROR;
            }
            AtomicLong id = new AtomicLong(0);
            AtomicLong taskIdForEmulation = new AtomicLong(0);
            for (ExperimentFeature feature : features) {
                ExperimentUtils.NumberOfVariants numberOfVariants = ExperimentUtils.getNumberOfVariants(feature.variables);
                if (!numberOfVariants.status) {
                    log.warn("#179.100 empty list of feature, feature: {}", feature);
                    continue;
                }
                List<String> inputResourceCodes = numberOfVariants.values;
                for (HyperParams hyperParams : allHyperParams) {

                    TaskImpl prevTask;
                    TaskImpl task = null;
                    List<Long> prevParentTaskIds = new ArrayList<>(parentTaskIds);
                    for (ExperimentFunctionItem functionItem : experimentFunctions) {
                        if (boolHolder.get()) {
                            return EnumsApi.SourceCodeProducingStatus.EXEC_CONTEXT_NOT_FOUND_ERROR;
                        }
                        prevTask = task;

                        // create empty task. we need task id for initialization
                        task = new TaskImpl();
                        task.setParams("");
                        task.setExecContextId(execContextId);
                        if (isPersist) {
                            task = taskRepository.save(task);
                        }
                        else {
                            task.id = taskIdForEmulation.incrementAndGet();
                        }
                        // inc number of tasks
                        numberOfTasks.value++;

                        TaskParamsYaml yaml = new TaskParamsYaml();
                        yaml.taskYaml.execContextId = execContextId;
                        yaml.taskYaml.resourceStorageUrls = new HashMap<>(inputStorageUrls);

                        if (!hyperParams.params.isEmpty()) {
                            if (yaml.taskYaml.taskMl==null) {
                                yaml.taskYaml.taskMl = new TaskParamsYaml.TaskMachineLearning();
                            }
                            yaml.taskYaml.taskMl.setHyperParams(hyperParams.toSortedMap());
                        }
                        // TODO need to implement an unit-test for a SourceCode without metas in experiment
                        //  and check that features are correctly defined
                        // TODO 2019-07-17 right now it doesn't work
                        //  - you need to specify 'feature', dataset'(not sure about 'dataset') in metas
                        yaml.taskYaml.inputResourceIds.computeIfAbsent("feature", k -> new ArrayList<>()).addAll(inputResourceCodes);
                        for (Map.Entry<String, List<String>> entry : collectedInputs.entrySet()) {

                            process.getMetas()
                                    .stream()
                                    .filter(o -> o.value.equals(entry.getKey()))
                                    .findFirst()
                                    .ifPresent(meta -> yaml.taskYaml.inputResourceIds
                                            .computeIfAbsent(meta.getKey(), k -> new ArrayList<>())
                                            .addAll(entry.getValue())
                                    );

                        }
                        final String functionCode = functionItem.functionCode;
                        Function function = getFunction(localCache, functionCode);
                        if (function == null) {
                            log.warn("#179.110 Function wasn't found for code: {}", functionCode);
                            continue;
                        }

                        EnumsApi.ExperimentTaskType type;
                        if (CommonConsts.FIT_TYPE.equals(function.getType())) {
                            yaml.taskYaml.outputResourceIds.put("default-output", getModelFilename(task));
                            type = EnumsApi.ExperimentTaskType.FIT;
                        } else if (CommonConsts.PREDICT_TYPE.equals(function.getType())) {
                            if (prevTask == null) {
                                throw new IllegalStateException("#179.120 prevTask is null");
                            }
                            String modelFilename = getModelFilename(prevTask);
                            yaml.taskYaml.inputResourceIds.computeIfAbsent("model", k -> new ArrayList<>()).add(modelFilename);
                            yaml.taskYaml.outputResourceIds.put("default-output", "task-" + task.getId() + "-output-stub-for-predict");
                            type = EnumsApi.ExperimentTaskType.PREDICT;

                            // TODO 2019.05.02 add implementation of disk storage for models
                            yaml.taskYaml.resourceStorageUrls.put(modelFilename, new SourceCodeParamsYaml.Variable("mode"));
//                            yaml.resourceStorageUrls.put(modelFilename, StringUtils.isBlank(process.outputStorageUrl) ? Consts.DISPATCHER_STORAGE_URL : process.outputStorageUrl);
                        } else {
                            throw new IllegalStateException("#179.130 Not supported type of function encountered, type: " + function.getType());
                        }
                        for (SourceCodeParamsYaml.Variable variable : process.output) {
                            String resourceId = "1L";
                            yaml.taskYaml.resourceStorageUrls.put(resourceId, variable);
                        }

                        yaml.taskYaml.inputResourceIds.forEach((key, value) -> {
                            HashSet<String> set = new HashSet<>(value);
                            value.clear();
                            value.addAll(set);
                        });

                        ExperimentTaskFeature tef = new ExperimentTaskFeature();
                        tef.id = id.incrementAndGet();
                        tef.setExecContextId(execContextId);
                        tef.setTaskId(task.getId());
                        tef.setFeatureId(feature.id);
                        tef.setTaskType(type.value);
                        if (isPersist) {
                            epy.processing.taskFeatures.add(tef);
                        }

                        yaml.taskYaml.function = TaskParamsUtils.toFunctionConfig(function.getFunctionConfig(true));
                        yaml.taskYaml.preFunctions = new ArrayList<>();
                        if (process.getPreFunctions() != null) {
                            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.getPreFunctions()) {
                                yaml.taskYaml.preFunctions.add(functionService.getFunctionConfig(snDef));
                            }
                        }
                        yaml.taskYaml.postFunctions = new ArrayList<>();
                        if (functionItem.type== EnumsApi.ExperimentFunction.PREDICT) {
                            Meta m = MetaUtils.getMeta(function.getFunctionConfig(false).metas, ConstsApi.META_MH_FITTING_DETECTION_SUPPORTED);
                            if (MetaUtils.isTrue(m) && !S.b(epy.experimentYaml.checkFittingFunction)) {
                                Function cos = getFunction(localCache, epy.experimentYaml.checkFittingFunction);
                                if (function == null) {
                                    log.warn("#179.140 Function wasn't found for code: {}", functionCode);
                                    continue;
                                }
                                yaml.taskYaml.postFunctions.add(TaskParamsUtils.toFunctionConfig(cos.getFunctionConfig(false)));
                            }
                        }
                        if (process.getPostFunctions()!=null) {
                            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.getPostFunctions()) {
                                yaml.taskYaml.postFunctions.add(functionService.getFunctionConfig(snDef));
                            }
                        }
                        yaml.taskYaml.clean = sourceCodeParams.source.clean;
                        yaml.taskYaml.timeoutBeforeTerminate = process.timeoutBeforeTerminate;

                        String currTaskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(yaml);

                        processed++;
                        if (processed % 100 == 0) {
                            log.info("total tasks: {}, created: {}. last batch of tasks was created for {} seconds", totalVariants, processed, ((float)(System.currentTimeMillis() - prevMills))/1000);
                            prevMills = System.currentTimeMillis();
                        }
                        // TODO 2019.07.08 this isn't good solution. need to rewrite
                        if (taskParams.contains(currTaskParams)) {
                            log.info("Params already processed, skip");
                            continue;
                        }
                        task.setParams(currTaskParams);
                        final List<Long> taskIds = List.of(task.getId());
                        if (isPersist) {
                            task = taskPersistencer.setParams(task.getId(), currTaskParams);
                            if (task == null) {
                                return EnumsApi.SourceCodeProducingStatus.PRODUCING_OF_EXPERIMENT_ERROR;
                            }
                            execContextGraphTopLevelService.addNewTasksToGraph(execContextId, prevParentTaskIds, taskIds);
                        }
                        prevParentTaskIds = taskIds;
                    }
                }
            }
            log.info("Created {} tasks, total: {}", processed, totalVariants);
        }
        finally {
            eventMulticaster.removeApplicationListener(listener);
        }

        if (epy.processing.getNumberOfTask() != totalVariants && epy.processing.getNumberOfTask() != 0) {
            log.warn("#179.150 ! Number of tasks is different. experiment.getNumberOfTask(): {}, totalVariants: {}", epy.processing.getNumberOfTask(), totalVariants);
        }
        if (isPersist) {
            Experiment experimentTemp = experimentRepository.findByIdForUpdate(experiment.getId());
            if (experimentTemp == null) {
                log.warn("#179.160 Experiment for id {} doesn't exist anymore", experiment.getId());
                return EnumsApi.SourceCodeProducingStatus.PRODUCING_OF_EXPERIMENT_ERROR;
            }
            epy.processing.setNumberOfTask(totalVariants);
            epy.processing.setAllTaskProduced(true);
            experimentTemp.updateParams(epy);

            //noinspection UnusedAssignment
            experimentTemp = experimentCache.save(experimentTemp);
        }
        return EnumsApi.SourceCodeProducingStatus.OK;
    }

    public Function getFunction(Map<String, Function> localCache, String functionCode) {
        Function function = localCache.get(functionCode);
        if (function == null) {
            function = functionService.findByCode(functionCode);
            if (function != null) {
                localCache.put(functionCode, function);
            }
        }
        return function;
    }

    private String getModelFilename(Task task) {
        return "task-"+task.getId()+"-"+ Consts.ML_MODEL_BIN;
    }

    public void produceFeaturePermutations(boolean isPersist, final Experiment experiment, List<String> inputResourceCodes, IntHolder total) {
        produceFeaturePermutations(experiment, inputResourceCodes, total);
        if (isPersist) {
            experimentCache.save(experiment);
        }

    }

    public static void produceFeaturePermutations(final Experiment experiment, List<String> inputVariables, IntHolder total) {
//        @Query("SELECT f.checksumIdCodes FROM ExperimentFeature f where f.experimentId=:experimentId")
//        List<String> getChecksumIdCodesByExperimentId(long experimentId);
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        final List<String> list = epy.processing.features.stream().map(o->o.checksumIdCodes).collect(Collectors.toList());

        final Permutation<String> permutation = new Permutation<>();
        AtomicLong featureId = new AtomicLong(0);
        for (int i = 0; i < inputVariables.size(); i++) {
            permutation.printCombination(inputVariables, i+1,
                    permutedVariables -> {
                        final String permutedVariablesAsStr = String.valueOf(permutedVariables);
                        final String checksumMD5;
                        try {
                            checksumMD5 = Checksum.getChecksum(EnumsApi.Type.MD5, permutedVariablesAsStr);
                        } catch (IOException e) {
                            String es = "Error while calculating MD5 for string " + permutedVariablesAsStr;
                            log.error(es, e);
                            throw new IllegalStateException(es);
                        }
                        String checksumIdCodes = StringUtils.substring(permutedVariablesAsStr, 0, 20) + "###" + checksumMD5;
                        if (list.contains(checksumIdCodes)) {
                            // already exist
                            return true;
                        }

                        ExperimentFeature feature = new ExperimentFeature();
                        feature.id = featureId.incrementAndGet();
                        feature.setExperimentId(experiment.id);
                        feature.setVariables(permutedVariablesAsStr);
                        feature.setChecksumIdCodes(checksumIdCodes);
                        epy.processing.features.add(feature);

                        total.value++;
                        return true;
                    }
            );
        }
        epy.processing.setFeatureProduced(true);
        experiment.updateParams(epy);
    }
}