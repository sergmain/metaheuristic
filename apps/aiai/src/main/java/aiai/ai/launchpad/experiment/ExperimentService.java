/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.launchpad.experiment;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Monitoring;
import aiai.ai.comm.Protocol;
import aiai.api.v1.launchpad.Process;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.data.TasksData;
import aiai.ai.launchpad.experiment.task.TaskWIthType;
import aiai.ai.launchpad.flow.FlowInstanceService;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.utils.holders.BoolHolder;
import aiai.ai.utils.holders.IntHolder;
import aiai.ai.utils.permutation.Permutation;
import aiai.ai.yaml.hyper_params.HyperParams;
import aiai.ai.yaml.metrics.MetricValues;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.api.v1.EnumsApi;
import aiai.api.v1.launchpad.Task;
import aiai.apps.commons.CommonConsts;
import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.yaml.snippet.SnippetConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static aiai.ai.launchpad.data.ExperimentData.*;

@Service
@EnableTransactionManagement
@Slf4j
@Profile("launchpad")
public class ExperimentService {

    private final ApplicationEventMulticaster eventMulticaster;

    private final ParamsSetter paramsSetter;
    private final MetricsMaxValueCollector metricsMaxValueCollector;
    private final ExperimentCache experimentCache;
    private final TaskRepository taskRepository;
    private final ExperimentTaskFeatureRepository taskExperimentFeatureRepository;
    private final TaskPersistencer taskPersistencer;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentTaskFeatureRepository experimentTaskFeatureRepository;
    private final FlowInstanceRepository flowInstanceRepository;

    @Autowired
    public ExperimentService(ApplicationEventMulticaster eventMulticaster, MetricsMaxValueCollector metricsMaxValueCollector, ExperimentCache experimentCache, TaskRepository taskRepository, ExperimentTaskFeatureRepository taskExperimentFeatureRepository, TaskPersistencer taskPersistencer, ExperimentFeatureRepository experimentFeatureRepository, SnippetService snippetService, FlowInstanceRepository flowInstanceRepository, ExperimentRepository experimentRepository, ExperimentTaskFeatureRepository experimentTaskFeatureRepository, ParamsSetter paramsSetter, SnippetRepository snippetRepository) {
        this.eventMulticaster = eventMulticaster;
        this.metricsMaxValueCollector = metricsMaxValueCollector;
        this.experimentCache = experimentCache;
        this.taskRepository = taskRepository;
        this.taskExperimentFeatureRepository = taskExperimentFeatureRepository;
        this.taskPersistencer = taskPersistencer;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.snippetService = snippetService;
        this.experimentRepository = experimentRepository;
        this.experimentTaskFeatureRepository = experimentTaskFeatureRepository;
        this.paramsSetter = paramsSetter;
        this.flowInstanceRepository = flowInstanceRepository;
        this.snippetRepository = snippetRepository;
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

    public static void sortSnippetsByType(List<ExperimentSnippet> snippets) {
        snippets.sort(Comparator.comparing(ExperimentSnippet::getType));
    }

    public PlotData getPlotData(Long experimentId, Long featureId, String[] params, String[] paramsAxis) {
        Experiment experiment= experimentCache.findById(experimentId);
        ExperimentFeature feature = experimentFeatureRepository.findById(featureId).orElse(null);

        //noinspection UnnecessaryLocalVariable
        PlotData data = findExperimentTaskForPlot(experiment, feature, params, paramsAxis);
        return data;
    }

    public void updateMaxValueForExperimentFeatures(Long flowInstanceId) {
        Experiment e = experimentRepository.findByFlowInstanceId(flowInstanceId);
        if (e==null) {
            return;
        }
        List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(e.getId());
        log.info("Start calculatingMaxValueOfMetrics");
        for (ExperimentFeature feature : features) {
            double value = metricsMaxValueCollector.calcMaxValueForMetrics(feature.getId());
            log.info("\tFeature #{}, max value: {}", feature.getId(), value);
            feature.setMaxValue(value);
            experimentFeatureRepository.save(feature);
        }
    }

    public void reconcileStationTasks(String stationIdAsStr, List<Protocol.StationTaskStatus.SimpleStatus> statuses) {
        final long stationId = Long.parseLong(stationIdAsStr);
        List<Object[]> tasks = taskRepository.findAllByStationIdAndResultReceivedIsFalse(stationId);
        for (Object[] obj : tasks) {
            long taskId = ((Number)obj[0]).longValue();
            Long assignedOn = obj[1]!=null ? ((Number)obj[1]).longValue() : null;

            boolean isFound = false;
            for (Protocol.StationTaskStatus.SimpleStatus status : statuses) {
                if (status.taskId ==taskId) {
                    isFound = true;
                }
            }

            boolean isExpired = assignedOn!=null && (System.currentTimeMillis() - assignedOn > 90_000);
            if (!isFound && isExpired) {
                log.info("De-assign task #{} from station #{}", taskId, stationIdAsStr);
                log.info("\tstatuses: {}", statuses.stream().map( o -> Long.toString(o.taskId)).collect(Collectors.toList()));
                log.info("\ttasks: {}", tasks.stream().map( o -> ""+o[0] + ',' + o[1]).collect(Collectors.toList()));
                log.info("\tisFound: {}, is expired: {}", isFound, isExpired);
                Task result = taskPersistencer.resetTask(taskId);
                if (result==null) {
                    log.error("Resetting of task {} was failed. See log for more info.", taskId);
                }
            }
        }
    }

    public Slice<TaskWIthType> findTasks(Pageable pageable, Experiment experiment, ExperimentFeature feature, String[] params) {
        if (experiment == null || feature == null) {
            return Page.empty();
        } else {
            Slice<TaskWIthType> slice;
            if (isEmpty(params)) {
                slice = taskRepository.findPredictTasks(pageable, feature.getId());
            } else {
                List<Task> selected = findTaskWithFilter(experiment, feature.getId(), params);
                List<Task> subList = selected.subList((int)pageable.getOffset(), (int)Math.min(selected.size(), pageable.getOffset() + pageable.getPageSize()));
                List<TaskWIthType> list = new ArrayList<>();
                for (Task task : subList) {
                    list.add(new TaskWIthType(task, 0));
                }
                slice = new PageImpl<>(list, pageable, selected.size());
                for (TaskWIthType taskWIthType : slice) {
                    ExperimentTaskFeature etf = experimentTaskFeatureRepository.findByTaskId(taskWIthType.task.getId());
                    taskWIthType.type = Enums.ExperimentTaskType.from( etf.getTaskType() ).value;
                }
            }
            return slice;
        }
    }

    public PlotData findExperimentTaskForPlot(Experiment experiment, ExperimentFeature feature, String[] params, String[] paramsAxis) {
        if (experiment == null || feature == null) {
            return EMPTY_PLOT_DATA;
        } else {
            List<Task> selected;
            if (isEmpty(params)) {
                selected = taskRepository.findByIsCompletedIsTrueAndFeatureId(feature.getId());
            } else {
                selected = findTaskWithFilter(experiment, feature.getId(), params);
            }
            return collectDataForPlotting(experiment, selected, paramsAxis);
        }
    }

    @SuppressWarnings("Duplicates")
    private PlotData collectDataForPlotting(Experiment experiment, List<Task> selected, String[] paramsAxis) {
        final PlotData data = new PlotData();
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
            throw new IllegalStateException("Wrong number of params for axes. Expected: 2, actual: " + paramCleared.size());
        }
        Map<String, Map<String, Integer>> map = experiment.getHyperParamsAsMap(false);
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

            final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());
            int idxX = mapX.get(taskParamYaml.hyperParams.get(paramCleared.get(0)));
            int idxY = mapY.get(taskParamYaml.hyperParams.get(paramCleared.get(1)));
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
        final Map<String, Map<String, Integer>> paramByIndex = experiment.getHyperParamsAsMap();

        List<Task> list = taskRepository.findByIsCompletedIsTrueAndFeatureId(featureId);

        List<Task> selected = new ArrayList<>();
        for (Task task : list) {
            final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());
            boolean[] isOk = new boolean[taskParamYaml.hyperParams.size()];
            int idx = 0;
            for (Map.Entry<String, String> entry : taskParamYaml.hyperParams.entrySet()) {
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

    public ExperimentFeatureExtendedResult prepareExperimentFeatures(Experiment experiment, ExperimentFeature experimentFeature) {
        TasksData.TasksResult tasksResult = new TasksData.TasksResult();

        tasksResult.items = taskRepository.findPredictTasks(Consts.PAGE_REQUEST_10_REC, experimentFeature.getId());

        HyperParamResult hyperParamResult = new HyperParamResult();
        for (ExperimentHyperParams hyperParam : experiment.getHyperParams()) {
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParam.getValues());
            HyperParamList list = new HyperParamList(hyperParam.getKey());
            for (String value : variants.values) {
                list.getList().add( new HyperParamElement(value, false));
            }
            if (list.getList().isEmpty()) {
                list.getList().add( new HyperParamElement("<Error value>", false));
            }
            hyperParamResult.getElements().add(list);
        }

        MetricsResult metricsResult = new MetricsResult();
        List<Map<String, BigDecimal>> values = new ArrayList<>();

        List<Task> tasks = taskRepository.findByIsCompletedIsTrueAndFeatureId(experimentFeature.getId());
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

        List<MetricElement> elements = new ArrayList<>();
        for (Map<String, BigDecimal> value : values) {
            MetricElement element = new MetricElement();
            for (String metricName : metricsResult.metricNames) {
                element.values.add(value.get(metricName));
            }
            elements.add(element);
        }
        elements.sort(MetricElement::compare);

        metricsResult.metrics.addAll( elements.subList(0, Math.min(20, elements.size())) );

        ExperimentFeatureExtendedResult result = new ExperimentFeatureExtendedResult();
        result.metricsResult = metricsResult;
        result.hyperParamResult = hyperParamResult;
        result.tasksResult = tasksResult;
        result.experiment = experiment;
        result.experimentFeature = experimentFeature;
        result.consoleResult = new ConsoleResult();

        return result;
    }

    private static Map<String, String> toMap(List<ExperimentHyperParams> experimentHyperParams, int seed) {
        List<ExperimentHyperParams> params = new ArrayList<>();
        ExperimentHyperParams p1 = new ExperimentHyperParams();
        p1.setKey(Consts.SEED);
        p1.setValues(Integer.toString(seed));
        params.add(p1);

        for (ExperimentHyperParams param : experimentHyperParams) {
            //noinspection UseBulkOperation
            params.add(param);
        }
        return toMap(params);
    }

    private static Map<String, String> toMap(List<ExperimentHyperParams> experimentHyperParams) {
        return experimentHyperParams.stream().collect(Collectors.toMap(ExperimentHyperParams::getKey, ExperimentHyperParams::getValues, (a, b) -> b, HashMap::new));
    }

    public void resetExperiment(long flowInstanceId) {

        Experiment e = experimentRepository.findByFlowInstanceId(flowInstanceId);
        if (e==null) {
            return;
        }
        experimentFeatureRepository.deleteByExperimentId(e.getId());

        e = experimentCache.findById(e.getId());

        e.setFlowInstanceId(null);
        e.setAllTaskProduced(false);
        e.setFeatureProduced(false);
        e.setAllTaskProduced(false);
        e.setNumberOfTask(0);
        //noinspection UnusedAssignment
        e = experimentCache.save(e);
    }

    public EnumsApi.FlowProducingStatus produceTasks(
            boolean isPersist, Flow flow, FlowInstance flowInstance, Process process,
            Experiment experiment, Map<String, List<String>> collectedInputs, Map<String, String> inputStorageUrls, IntHolder numberOfTasks) {
        if (process.type!= EnumsApi.ProcessType.EXPERIMENT) {
            throw new IllegalStateException("Wrong type of process, " +
                    "expected: "+ EnumsApi.ProcessType.EXPERIMENT+", " +
                    "actual: " + process.type);
        }

        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experiment.getId());
        SnippetService.sortSnippetsByType(experimentSnippets);

        final Map<String, String> map = toMap(experiment.getHyperParams(), experiment.getSeed());
        final List<HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);

        final List<Object[]> features = experimentFeatureRepository.getAsExperimentFeatureSimpleByExperimentId(experiment.getId());
        final Map<String, Snippet> localCache = new HashMap<>();
        final IntHolder size = new IntHolder();
        final Set<String> taskParams = paramsSetter.getParamsInTransaction(isPersist, flowInstance, experiment, size);

        // there is 2 because we have 2 types of snippets - fit and predict
        // feature has real value only when isPersist==true
        int totalVariants = features.size() * allHyperParams.size() * 2;

        numberOfTasks.value = allHyperParams.size() * experimentSnippets.size();

        log.debug("total size of tasks' params is {} bytes", size.value);
        final BoolHolder boolHolder = new BoolHolder();
        final Consumer<Long> longConsumer = o -> {
            if (flowInstance.getId().equals(o)) {
                boolHolder.value = true;
            }
        };
        final FlowInstanceService.FlowInstanceDeletionListener listener =
                new FlowInstanceService.FlowInstanceDeletionListener(flowInstance.getId(), longConsumer);

        int processed = 0;
        try {
            eventMulticaster.addApplicationListener(listener);
            FlowInstance instance = flowInstanceRepository.findById(flowInstance.getId()).orElse(null);
            if (instance==null) {
                return EnumsApi.FlowProducingStatus.FLOW_INSTANCE_NOT_FOUND_ERROR;
            }

            for (Object[] feature : features) {
                ExperimentUtils.NumberOfVariants numberOfVariants = ExperimentUtils.getNumberOfVariants((String) feature[1]);
                if (!numberOfVariants.status) {
                    log.warn("empty list of feature, feature: {}", feature);
                    continue;
                }
                List<String> inputResourceCodes = numberOfVariants.values;

                for (HyperParams hyperParams : allHyperParams) {

                    int orderAdd = 0;
                    Task prevTask;
                    Task task = null;
                    for (ExperimentSnippet experimentSnippet : experimentSnippets) {
                        if (boolHolder.value) {
                            return EnumsApi.FlowProducingStatus.FLOW_INSTANCE_NOT_FOUND_ERROR;
                        }
                        prevTask = task;

                        // create empty task. we need task id for initialization
                        task = new TaskImpl();
                        task.setParams("");
                        task.setFlowInstanceId(flowInstance.getId());
                        task.setOrder(process.order + (orderAdd++));
                        task.setProcessType(process.type.value);
                        if (isPersist) {
                            taskRepository.save((TaskImpl)task);
                        }
                        // inc number of tasks
                        numberOfTasks.value++;

                        TaskParamYaml yaml = new TaskParamYaml();
                        yaml.resourceStorageUrls = new HashMap<>(inputStorageUrls);

                        yaml.setHyperParams(hyperParams.toSortedMap());
                        // TODO need to implement an unit-test for a flow without metas in experiment
                        // TODO and see that features are correctly defined
                        yaml.inputResourceCodes.computeIfAbsent("feature", k -> new ArrayList<>()).addAll(inputResourceCodes);
                        for (Map.Entry<String, List<String>> entry : collectedInputs.entrySet()) {

                            // TODO 2019.04.24 need to decide do we need this check or not
                            // if ("feature".equals(entry.getKey())) {
                            //     log.info("Output type is the same as flowInstance inputResourceParam:\n"+ flowInstance.inputResourceParam );
                            // }
                            Process.Meta meta = process.getMetas()
                                    .stream()
                                    .filter(o -> o.value.equals(entry.getKey()))
                                    .findFirst()
                                    .orElse(null);

                            if (meta != null) {
                                yaml.inputResourceCodes.computeIfAbsent(meta.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                            }
                        }
                        Snippet snippet = localCache.get(experimentSnippet.getSnippetCode());
                        if (snippet == null) {
                            snippet = snippetRepository.findByCode(experimentSnippet.getSnippetCode());
                            if (snippet != null) {
                                localCache.put(experimentSnippet.getSnippetCode(), snippet);
                            }
                        }
                        if (snippet == null) {
                            log.warn("Snippet wasn't found for code: {}", experimentSnippet.getSnippetCode());
                            continue;
                        }

                        Enums.ExperimentTaskType type;
                        if (CommonConsts.FIT_TYPE.equals(snippet.getType())) {
                            yaml.outputResourceCode = getModelFilename(task);
                            type = Enums.ExperimentTaskType.FIT;
                        } else if (CommonConsts.PREDICT_TYPE.equals(snippet.getType())) {
                            if (prevTask == null) {
                                throw new IllegalStateException("prevTask is null");
                            }
                            String modelFilename = getModelFilename(prevTask);
                            yaml.inputResourceCodes.computeIfAbsent("model", k -> new ArrayList<>()).add(modelFilename);
                            yaml.outputResourceCode = "task-" + task.getId() + "-output-stub-for-predict";
                            type = Enums.ExperimentTaskType.PREDICT;

                            // TODO add implementation of disk storage for models
                            yaml.resourceStorageUrls.put(modelFilename, Consts.LAUNCHPAD_STORAGE_URL);
//                            yaml.resourceStorageUrls.put(modelFilename, StringUtils.isBlank(process.outputStorageUrl) ? Consts.LAUNCHPAD_STORAGE_URL : process.outputStorageUrl);
                        } else {
                            throw new IllegalStateException("Not supported type of snippet encountered, type: " + snippet.getType());
                        }
                        yaml.resourceStorageUrls.put(yaml.outputResourceCode, StringUtils.isBlank(process.outputStorageUrl) ? Consts.LAUNCHPAD_STORAGE_URL : process.outputStorageUrl);

                        yaml.inputResourceCodes.forEach((key, value) -> {
                            HashSet<String> set = new HashSet<>(value);
                            value.clear();
                            value.addAll(set);
                        });

                        ExperimentTaskFeature tef = new ExperimentTaskFeature();
                        tef.setFlowInstanceId(flowInstance.getId());
                        tef.setTaskId(task.getId());
                        tef.setFeatureId((Long) feature[0]);
                        tef.setTaskType(type.value);
                        if (isPersist) {
                            taskExperimentFeatureRepository.save(tef);
                        }

                        yaml.snippet = SnippetConfigUtils.to(snippet.params);
                        yaml.clean = flow.clean;

                        String currTaskParams = TaskParamYamlUtils.toString(yaml);

                        processed++;
                        if (processed % 100 == 0) {
                            log.info("total tasks: {}, created: {}", totalVariants, processed);
                        }
                        if (taskParams.contains(currTaskParams)) {
                            log.info("Params already processed, skip");
                            continue;
                        }
                        task.setParams(currTaskParams);
                        if (isPersist) {
                            task = taskPersistencer.setParams(task.getId(), currTaskParams);
                            if (task == null) {
                                return EnumsApi.FlowProducingStatus.PRODUCING_OF_EXPERIMENT_ERROR;
                            }
                        }
                    }
                }
            }
            log.info("Created {} tasks, total: {}", processed, totalVariants);
        }
        finally {
            eventMulticaster.removeApplicationListener(listener);
        }

        if (experiment.getNumberOfTask() != totalVariants && experiment.getNumberOfTask() != 0) {
            log.warn("! Number of sequence is different. experiment.getNumberOfTask(): {}, totalVariants: {}", experiment.getNumberOfTask(), totalVariants);
        }
        if (isPersist) {
            Experiment experimentTemp = experimentCache.findById(experiment.getId());
            if (experimentTemp == null) {
                log.warn("Experiment for id {} doesn't exist anymore", experiment.getId());
                return EnumsApi.FlowProducingStatus.PRODUCING_OF_EXPERIMENT_ERROR;
            }
            experimentTemp.setNumberOfTask(totalVariants);
            experimentTemp.setAllTaskProduced(true);
            //noinspection UnusedAssignment
            experimentTemp = experimentCache.save(experimentTemp);
        }
        return EnumsApi.FlowProducingStatus.OK;
    }

    private String getModelFilename(Task task) {
        return "task-"+task.getId()+"-"+ Consts.ML_MODEL_BIN;
    }

    public void produceFeaturePermutations(boolean isPersist, final Long experimentId, List<String> inputResourceCodes, IntHolder total) {
        final List<String> list = experimentFeatureRepository.getChecksumIdCodesByExperimentId(experimentId);

        final Permutation<String> permutation = new Permutation<>();
        Monitoring.log("##040", Enums.Monitor.MEMORY);
        for (int i = 0; i < inputResourceCodes.size(); i++) {
            Monitoring.log("##041", Enums.Monitor.MEMORY);
            permutation.printCombination(inputResourceCodes, i+1,
                    data -> {
                        Monitoring.log("##042", Enums.Monitor.MEMORY);
                        final String listAsStr = String.valueOf(data);
                        final String checksumMD5;
                        try {
                            checksumMD5 = Checksum.Type.MD5.getChecksum(listAsStr);
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
                        feature.setExperimentId(experimentId);
                        feature.setResourceCodes(listAsStr);
                        feature.setChecksumIdCodes(checksumIdCodes);
                        if (isPersist) {
                            experimentFeatureRepository.save(feature);
                        }
                        //noinspection UnusedAssignment
                        feature = null;
                        total.value++;
                        return true;
                    }
            );
        }
        Experiment e = experimentCache.findById(experimentId);
        if (e==null) {
            throw new IllegalStateException("Experiment wasn't found for id " + experimentId);
        }
        e.setFeatureProduced(true);
        if (isPersist) {
            experimentCache.save(e);
        }
    }
}
