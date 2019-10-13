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
package ai.metaheuristic.ai.launchpad.experiment;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.launchpad.beans.Experiment;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.utils.holders.IntHolder;
import ai.metaheuristic.ai.utils.permutation.Permutation;
import ai.metaheuristic.ai.yaml.hyper_params.HyperParams;
import ai.metaheuristic.ai.yaml.metrics.MetricValues;
import ai.metaheuristic.ai.yaml.metrics.MetricsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.BaseMetricElement;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.task.TaskWIthType;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.api.launchpad.process.Process;
import ai.metaheuristic.api.launchpad.process.SnippetDefForPlan;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.PlanProducingStatus.TOO_MANY_TASKS_PER_PLAN_ERROR;
import static ai.metaheuristic.api.data.experiment.ExperimentParamsYaml.*;

@Service
@EnableTransactionManagement
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class ExperimentService {

    private final Globals globals;
    private final ApplicationEventMulticaster eventMulticaster;

    private final ParamsSetter paramsSetter;
    private final MetricsMaxValueCollector metricsMaxValueCollector;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final SnippetService snippetService;
    private final WorkbookService workbookService;
    private final WorkbookCache workbookCache;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;

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
        ed.workbookId = e.workbookId;
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
        ed.workbookId = e.workbookId;
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
            List<WorkbookParamsYaml.TaskVertex> taskVertices,
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
                log.warn("Experiment wasn't found for id: {}", experimentId);
                continue;
            }
            if (e.workbookId==null) {
                log.warn("This shouldn't be happened");
                continue;
            }
            WorkbookImpl wb = workbookCache.findById(e.workbookId);
            if (wb==null) {
                log.info("Can't calc max values and export to atlas because workbookId is null");
                continue;
            }
            if (wb.execState!=EnumsApi.WorkbookExecState.FINISHED.code) {
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
        static ParamFilter of(String filetr) {
            return new ParamFilter(filetr);
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
            throw new IllegalStateException("#179.15 Wrong number of params for axes. Expected: 2, actual: " + paramCleared.size());
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

            MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(task.getMetrics()) );
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

            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            int idxX = mapX.get(taskParamYaml.taskYaml.hyperParams.get(paramCleared.get(0)));
            int idxY = mapY.get(taskParamYaml.taskYaml.hyperParams.get(paramCleared.get(1)));
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

    private boolean isEmpty(String[] params) {
        for (String param : params) {
            if (StringUtils.isNotBlank(param)) {
                return false;
            }
        }
        return true;
    }

    public ExperimentApiData.ExperimentFeatureExtendedResult prepareExperimentFeatures(Experiment experiment, Long featureId ) {
        if (experiment.workbookId==null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#285.040 workbookId is null");
        }
        WorkbookImpl workbook = workbookCache.findById(experiment.workbookId);

        ExperimentParamsYaml.ExperimentFeature experimentFeature = experiment.getExperimentParamsYaml().getFeature(featureId);
        if (experimentFeature == null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#179.050 feature wasn't found, experimentFeatureId: " + featureId);
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
            MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(seq.getMetrics()) );
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

        List<WorkbookParamsYaml.TaskVertex> taskVertices = workbookService.findAll(workbook);

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

    public void resetExperimentByWorkbookId(Long workbookId) {

        Experiment e = experimentRepository.findByWorkbookIdForUpdate(workbookId);
        if (e==null) {
            return;
        }

        ExperimentParamsYaml epy = e.getExperimentParamsYaml();
        epy.processing = new ExperimentProcessing();
        e.updateParams(epy);
        e.setWorkbookId(null);

        //noinspection UnusedAssignment
        e = experimentCache.save(e);
    }

    public void bindExperimentToWorkbook(Long experimentId, Long workbookId) {

        Experiment e = experimentRepository.findByIdForUpdate(experimentId);
        if (e==null) {
            return;
        }

        ExperimentParamsYaml epy = e.getExperimentParamsYaml();
        epy.processing = new ExperimentProcessing();
        e.updateParams(epy);
        e.setWorkbookId(workbookId);

        //noinspection UnusedAssignment
        e = experimentCache.save(e);
    }

    @SuppressWarnings("Duplicates")
    PlanService.ProduceTaskResult result = new PlanService.ProduceTaskResult();

    public EnumsApi.PlanProducingStatus produceTasks(
            boolean isPersist, PlanParamsYaml planParams, Long workbookId, Process process,
            Experiment experiment, Map<String, List<String>> collectedInputs,
            Map<String, DataStorageParams> inputStorageUrls, IntHolder numberOfTasks, List<Long> parentTaskIds) {
        if (process.type!= EnumsApi.ProcessType.EXPERIMENT) {
            throw new IllegalStateException("#179.190 Wrong type of process, " +
                    "expected: "+ EnumsApi.ProcessType.EXPERIMENT+", " +
                    "actual: " + process.type);
        }

        ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        if (StringUtils.isBlank(epy.experimentYaml.fitSnippet)|| StringUtils.isBlank(epy.experimentYaml.predictSnippet)) {
            throw new IllegalStateException("(StringUtils.isBlank(epy.yaml.fitSnippet)|| StringUtils.isBlank(epy.yaml.predictSnippet))" +
                    ", "+epy.experimentYaml.fitSnippet +", " + epy.experimentYaml.predictSnippet);
        }
        List<String> experimentSnippets = List.of(epy.experimentYaml.fitSnippet, epy.experimentYaml.predictSnippet);

        final Map<String, String> map = toMap(epy.experimentYaml.getHyperParams(), epy.experimentYaml.seed);
        final int calcTotalVariants = ExperimentUtils.calcTotalVariants(map);

        final List<ExperimentFeature> features = epy.processing.features;

        // there is 2 because we have 2 types of snippets - fit and predict
        // feature has the real value only when isPersist==true
        int totalVariants = features.size() * calcTotalVariants * 2;

        if (totalVariants > globals.maxTasksPerWorkbook) {
            log.error("#179.200 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                                "Allowed maximum number of tasks per workbook: " + globals.maxTasksPerWorkbook +", tasks in this workbook: " + totalVariants);
            return TOO_MANY_TASKS_PER_PLAN_ERROR;
        }
        final List<HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);

        final Map<String, Snippet> localCache = new HashMap<>();
        final IntHolder size = new IntHolder();
        final Set<String> taskParams = paramsSetter.getParamsInTransaction(isPersist, workbookId, experiment, size);

        numberOfTasks.value = 0;

        log.debug("total size of tasks' params received from db is {} bytes", size.value);
        final AtomicBoolean boolHolder = new AtomicBoolean();
        final Consumer<Long> longConsumer = o -> {
            if (workbookId.equals(o)) {
                boolHolder.set(true);
            }
        };
        final WorkbookService.WorkbookDeletionListener listener =
                new WorkbookService.WorkbookDeletionListener(workbookId, longConsumer);

        int processed = 0;
        long prevMills = System.currentTimeMillis();
        try {
            eventMulticaster.addApplicationListener(listener);
            Workbook wb = workbookCache.findById(workbookId);
            if (wb==null) {
                return EnumsApi.PlanProducingStatus.WORKBOOK_NOT_FOUND_ERROR;
            }
            AtomicLong id = new AtomicLong(0);
            AtomicLong taskIdForEmulation = new AtomicLong(0);
            for (ExperimentFeature feature : features) {
                ExperimentUtils.NumberOfVariants numberOfVariants = ExperimentUtils.getNumberOfVariants(feature.resourceCodes);
                if (!numberOfVariants.status) {
                    log.warn("#179.25 empty list of feature, feature: {}", feature);
                    continue;
                }
                List<String> inputResourceCodes = numberOfVariants.values;
                for (HyperParams hyperParams : allHyperParams) {

                    TaskImpl prevTask;
                    TaskImpl task = null;
                    List<Long> prevParentTaskIds = new ArrayList<>(parentTaskIds);
                    for (String snippetCode : experimentSnippets) {
                        if (boolHolder.get()) {
                            return EnumsApi.PlanProducingStatus.WORKBOOK_NOT_FOUND_ERROR;
                        }
                        prevTask = task;

                        // create empty task. we need task id for initialization
                        task = new TaskImpl();
                        task.setParams("");
                        task.setWorkbookId(workbookId);
                        task.setProcessType(process.type.value);
                        if (isPersist) {
                            task = taskRepository.save(task);
                        }
                        else {
                            task.id = taskIdForEmulation.incrementAndGet();
                        }
                        // inc number of tasks
                        numberOfTasks.value++;

                        TaskParamsYaml yaml = new TaskParamsYaml();
                        yaml.taskYaml.resourceStorageUrls = new HashMap<>(inputStorageUrls);

                        yaml.taskYaml.setHyperParams(hyperParams.toSortedMap());
                        // TODO need to implement an unit-test for a plan without metas in experiment
                        //  and check that features are correctly defined
                        // TODO 2019-07-17 right now it doesn't work
                        //  - you need to specify 'feature', dataset' (not sure) in metas
                        yaml.taskYaml.inputResourceCodes.computeIfAbsent("feature", k -> new ArrayList<>()).addAll(inputResourceCodes);
                        for (Map.Entry<String, List<String>> entry : collectedInputs.entrySet()) {

                            // TODO 2019.04.24 need to decide do we need this check or not
                            // TODO 2019-07-17 see comment above about required metas
                            // if ("feature".equals(entry.getKey())) {
                            //     log.info("Output type is the same as workbook inputResourceParam:\n"+ workbook.inputResourceParam );
                            // }
                            process.getMetas()
                                    .stream()
                                    .filter(o -> o.value.equals(entry.getKey()))
                                    .findFirst()
                                    .ifPresent(meta -> yaml.taskYaml.inputResourceCodes
                                            .computeIfAbsent(meta.getKey(), k -> new ArrayList<>())
                                            .addAll(entry.getValue())
                                    );

                        }
                        Snippet snippet = localCache.get(snippetCode);
                        if (snippet == null) {
                            snippet = snippetService.findByCode(snippetCode);
                            if (snippet != null) {
                                localCache.put(snippetCode, snippet);
                            }
                        }
                        if (snippet == null) {
                            log.warn("#179.27 Snippet wasn't found for code: {}", snippetCode);
                            continue;
                        }

                        EnumsApi.ExperimentTaskType type;
                        if (CommonConsts.FIT_TYPE.equals(snippet.getType())) {
                            yaml.taskYaml.outputResourceCode = getModelFilename(task);
                            type = EnumsApi.ExperimentTaskType.FIT;
                        } else if (CommonConsts.PREDICT_TYPE.equals(snippet.getType())) {
                            if (prevTask == null) {
                                throw new IllegalStateException("#179.29 prevTask is null");
                            }
                            String modelFilename = getModelFilename(prevTask);
                            yaml.taskYaml.inputResourceCodes.computeIfAbsent("model", k -> new ArrayList<>()).add(modelFilename);
                            yaml.taskYaml.outputResourceCode = "task-" + task.getId() + "-output-stub-for-predict";
                            type = EnumsApi.ExperimentTaskType.PREDICT;

                            // TODO 2019.05.02 add implementation of disk storage for models
                            yaml.taskYaml.resourceStorageUrls.put(modelFilename, new DataStorageParams(EnumsApi.DataSourcing.launchpad));
//                            yaml.resourceStorageUrls.put(modelFilename, StringUtils.isBlank(process.outputStorageUrl) ? Consts.LAUNCHPAD_STORAGE_URL : process.outputStorageUrl);
                        } else {
                            throw new IllegalStateException("#179.31 Not supported type of snippet encountered, type: " + snippet.getType());
                        }
                        yaml.taskYaml.resourceStorageUrls.put(yaml.taskYaml.outputResourceCode, process.outputParams);

                        yaml.taskYaml.inputResourceCodes.forEach((key, value) -> {
                            HashSet<String> set = new HashSet<>(value);
                            value.clear();
                            value.addAll(set);
                        });

                        ExperimentTaskFeature tef = new ExperimentTaskFeature();
                        tef.id = id.incrementAndGet();
                        tef.setWorkbookId(workbookId);
                        tef.setTaskId(task.getId());
                        tef.setFeatureId(feature.id);
                        tef.setTaskType(type.value);
                        if (isPersist) {
                            epy.processing.taskFeatures.add(tef);
                        }

                        yaml.taskYaml.snippet = snippet.getSnippetConfig(true);
                        yaml.taskYaml.preSnippets = new ArrayList<>();
                        if (process.getPreSnippets() != null) {
                            for (SnippetDefForPlan snDef : process.getPreSnippets()) {
                                yaml.taskYaml.preSnippets.add(snippetService.getSnippetConfig(snDef));
                            }
                        }
                        yaml.taskYaml.postSnippets = new ArrayList<>();
                        if (process.getPostSnippets()!=null) {
                            for (SnippetDefForPlan snDef : process.getPostSnippets()) {
                                yaml.taskYaml.postSnippets.add(snippetService.getSnippetConfig(snDef));
                            }
                        }
                        yaml.taskYaml.clean = planParams.planYaml.clean;
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
                                return EnumsApi.PlanProducingStatus.PRODUCING_OF_EXPERIMENT_ERROR;
                            }
                            workbookService.addNewTasksToGraph(workbookId, prevParentTaskIds, taskIds);
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
            log.warn("#179.33 ! Number of tasks is different. experiment.getNumberOfTask(): {}, totalVariants: {}", epy.processing.getNumberOfTask(), totalVariants);
        }
        if (isPersist) {
            Experiment experimentTemp = experimentRepository.findByIdForUpdate(experiment.getId());
            if (experimentTemp == null) {
                log.warn("#179.36 Experiment for id {} doesn't exist anymore", experiment.getId());
                return EnumsApi.PlanProducingStatus.PRODUCING_OF_EXPERIMENT_ERROR;
            }
            epy.processing.setNumberOfTask(totalVariants);
            epy.processing.setAllTaskProduced(true);
            experimentTemp.updateParams(epy);

            //noinspection UnusedAssignment
            experimentTemp = experimentCache.save(experimentTemp);
        }
        return EnumsApi.PlanProducingStatus.OK;
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

    public static void produceFeaturePermutations(final Experiment experiment, List<String> inputResourceCodes, IntHolder total) {
//        @Query("SELECT f.checksumIdCodes FROM ExperimentFeature f where f.experimentId=:experimentId")
//        List<String> getChecksumIdCodesByExperimentId(long experimentId);
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        final List<String> list = epy.processing.features.stream().map(o->o.checksumIdCodes).collect(Collectors.toList());

        final Permutation<String> permutation = new Permutation<>();
        Monitoring.log("##040", Enums.Monitor.MEMORY);
        AtomicLong featureId = new AtomicLong(0);
        for (int i = 0; i < inputResourceCodes.size(); i++) {
            Monitoring.log("##041", Enums.Monitor.MEMORY);
            permutation.printCombination(inputResourceCodes, i+1,
                    data -> {
                        Monitoring.log("##042", Enums.Monitor.MEMORY);
                        final String listAsStr = String.valueOf(data);
                        final String checksumMD5;
                        try {
                            checksumMD5 = Checksum.getChecksum(EnumsApi.Type.MD5, listAsStr);
                        } catch (IOException e) {
                            String es = "Error while calculating MD5 for string " + listAsStr;
                            log.error(es, e);
                            throw new IllegalStateException(es);
                        }
                        String checksumIdCodes = StringUtils.substring(listAsStr, 0, 20) + "###" + checksumMD5;
                        if (list.contains(checksumIdCodes)) {
                            // already exist
                            return true;
                        }

                        ExperimentFeature feature = new ExperimentFeature();
                        feature.id = featureId.incrementAndGet();
                        feature.setExperimentId(experiment.id);
                        feature.setResourceCodes(listAsStr);
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
