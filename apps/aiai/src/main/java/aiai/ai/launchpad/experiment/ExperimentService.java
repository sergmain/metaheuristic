/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.launchpad.experiment;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Monitoring;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.experiment.task.TaskWIthType;
import aiai.ai.launchpad.flow.FlowInstanceService;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.utils.BigDecimalHolder;
import aiai.ai.utils.BoolHolder;
import aiai.ai.utils.permutation.Permutation;
import aiai.ai.yaml.hyper_params.HyperParams;
import aiai.ai.yaml.metrics.MetricValues;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.task.SimpleSnippet;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.omg.CORBA.IntHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@EnableTransactionManagement
@Slf4j
@Profile("launchpad")
public class ExperimentService {

    private static final HashMap<String, Integer> HASH_MAP = new HashMap<>();

    private final ApplicationEventMulticaster eventMulticaster;

    @Service
    @Profile("launchpad")
    public static class ParamsSetter {

        private final TaskRepository taskRepository;

        public ParamsSetter(TaskRepository taskRepository) {
            this.taskRepository = taskRepository;
        }

        @Transactional
        public Set<String> getParamsInTransaction(boolean isPersist, FlowInstance flowInstance, Experiment experiment, IntHolder size) {
            Set<String> taskParams;
            taskParams = new LinkedHashSet<>();

            size.value = 0;
            try (Stream<Object[]> stream = taskRepository.findByFlowInstanceId(flowInstance.getId()) ) {
                stream
                        .forEach(o -> {
                            if (taskParams.contains((String) o[1])) {
                                // delete doubles records
                                log.warn("!!! Found doubles. ExperimentId: {}, hyperParams: {}", experiment.getId(), o[1]);
                                if (isPersist) {
                                    taskRepository.deleteById((Long) o[0]);
                                }

                            }
                            taskParams.add((String) o[1]);
                            size.value += ((String) o[1]).length();
                        });
            }
            return taskParams;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamElement {
        String param;
        boolean isSelected;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamList {
        String key;
        public final List<HyperParamElement> list = new ArrayList<>();
        public boolean isSelectable() {
            return list.size()>1;
        }
    }

    @Data
    public static class HyperParamResult {
        public final List<HyperParamList> elements = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricElement {
        public final List<BigDecimalHolder> values = new ArrayList<>();
        public String params;

        public static int compare(MetricElement o2, MetricElement o1) {
            for (int i = 0; i < Math.min(o1.values.size(), o2.values.size()); i++) {
                final BigDecimalHolder holder1 = o1.values.get(i);
                if (holder1 == null) {
                    return -1;
                }
                final BigDecimalHolder holder2 = o2.values.get(i);
                if (holder2 == null) {
                    return -1;
                }
                int c = ObjectUtils.compare(holder1.value, holder2.value);
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(o1.values.size(), o2.values.size());        }
    }

    @Data
    public static class MetricsResult {
        public final LinkedHashSet<String> metricNames = new LinkedHashSet<>();
        public final List<MetricElement> metrics = new ArrayList<>();
    }

    public static final PlotData EMPTY_PLOT_DATA = new PlotData();

    @Data
    @NoArgsConstructor
    public static class PlotData {
        public List<String> x = new ArrayList<>();
        public List<String> y = new ArrayList<>();
        public BigDecimal[][] z;
    }

    private final ParamsSetter paramsSetter;
    private final ExperimentCache experimentCache;
    private final TaskRepository taskRepository;
    private final ExperimentTaskFeatureRepository taskExperimentFeatureRepository;
    private final TaskPersistencer taskPersistencer;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final SnippetRepository snippetRepository;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final SnippetService snippetService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentTaskFeatureRepository experimentTaskFeatureRepository;
    private final FlowInstanceRepository flowInstanceRepository;

    @Autowired
    public ExperimentService(ApplicationEventMulticaster eventMulticaster, ExperimentCache experimentCache, TaskRepository taskRepository, ExperimentTaskFeatureRepository taskExperimentFeatureRepository, TaskPersistencer taskPersistencer, ExperimentFeatureRepository experimentFeatureRepository, TaskParamYamlUtils taskParamYamlUtils, SnippetService snippetService, FlowInstanceRepository flowInstanceRepository, ExperimentRepository experimentRepository, ExperimentTaskFeatureRepository experimentTaskFeatureRepository, ParamsSetter paramsSetter, SnippetRepository snippetRepository) {
        this.eventMulticaster = eventMulticaster;
        this.experimentCache = experimentCache;
        this.taskRepository = taskRepository;
        this.taskExperimentFeatureRepository = taskExperimentFeatureRepository;
        this.taskPersistencer = taskPersistencer;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.taskParamYamlUtils = taskParamYamlUtils;
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
                log.info("\tstatuses: {}", statuses);
                log.info("\ttasks: {}", tasks.stream().map( o -> ""+o[0] + ',' + o[1]).collect(Collectors.toList()));
                log.info("\tisFound: {}, is expired: {}", isFound, isExpired);
                Task result = taskPersistencer.resetTask(taskId);
                if (result==null) {
                    log.error("Resetting of task {} was failed. See log for more info.", taskId);
                }
            }
        }
    }

    Slice<TaskWIthType> findTasks(Pageable pageable, Experiment experiment, ExperimentFeature feature, String[] params) {
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

    PlotData findExperimentTaskForPlot(Experiment experiment, ExperimentFeature feature, String[] params, String[] paramsAxis) {
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

            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());
            int idxX = mapX.get(taskParamYaml.hyperParams.get(paramCleared.get(0)));
            int idxY = mapY.get(taskParamYaml.hyperParams.get(paramCleared.get(1)));
            data.z[idxY][idxX] = data.z[idxY][idxX].add(metricValues.values.get(metricKey));
        }

        return data;
    }


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
            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());
            boolean[] isOk = new boolean[taskParamYaml.hyperParams.size()];
            int idx = 0;
            for (Map.Entry<String, String> entry : taskParamYaml.hyperParams.entrySet()) {
                try {
                    if (!paramFilterKeys.contains(entry.getKey())) {
                        isOk[idx] = true;
                        continue;
                    }
                    final Map<String, Integer> map = paramByIndex.getOrDefault(entry.getKey(), HASH_MAP);
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

    public Map<String, Object> prepareExperimentFeatures(Experiment experiment, ExperimentFeature experimentFeature) {
        ExperimentsController.TasksResult result = new ExperimentsController.TasksResult();

        result.items = taskRepository.findPredictTasks(Consts.PAGE_REQUEST_10_REC, experimentFeature.getId());

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
            MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(seq.metrics) );
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
                BigDecimalHolder holder = new BigDecimalHolder();
                holder.value = value.get(metricName);
                element.values.add(holder);
            }
            elements.add(element);
        }
        elements.sort(MetricElement::compare);

        metricsResult.metrics.addAll( elements.subList(0, Math.min(20, elements.size())) );

        Map<String, Object> map = new HashMap<>();
        map.put("metrics", metricsResult);
        map.put("params", hyperParamResult);
        map.put("result", result);
        map.put("experiment", experiment);
        map.put("feature", experimentFeature);
        map.put("consoleResult", new ExperimentsController.ConsoleResult());

        return map;
    }

    public static Map<String, String> toMap(List<ExperimentHyperParams> experimentHyperParams, int seed, String epochs) {
        List<ExperimentHyperParams> params = new ArrayList<>();
        ExperimentHyperParams p1 = new ExperimentHyperParams();
        p1.setKey(Consts.SEED);
        p1.setValues(Integer.toString(seed));
        params.add(p1);

        ExperimentHyperParams p2 = new ExperimentHyperParams();
        p2.setKey(Consts.EPOCH);
        p2.setValues(epochs);
        params.add(p2);

        for (ExperimentHyperParams param : experimentHyperParams) {
            //noinspection UseBulkOperation
            params.add(param);
        }
        return toMap(params);
    }

    private static Map<String, String> toMap(List<ExperimentHyperParams> experimentHyperParams) {
        return experimentHyperParams.stream().collect(Collectors.toMap(ExperimentHyperParams::getKey, ExperimentHyperParams::getValues, (a, b) -> b, HashMap::new));
    }

    public void resetExperiment(FlowInstance flowInstance) {

        Experiment e = experimentRepository.findByFlowInstanceId(flowInstance.getId());
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
        e = experimentCache.save(e);
    }

    public Enums.FlowProducingStatus produceTasks(boolean isPersist, Flow flow, FlowInstance flowInstance, Process process, Experiment experiment, Map<String, List<String>> collectedInputs, IntHolder intHolder) {
        if (process.type!= Enums.ProcessType.EXPERIMENT) {
            throw new IllegalStateException("Wrong type of process, " +
                    "expected: "+ Enums.ProcessType.EXPERIMENT+", " +
                    "actual: " + process.type);
        }

        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experiment.getId());
        snippetService.sortSnippetsByType(experimentSnippets);

        final Map<String, String> map = toMap(experiment.getHyperParams(), experiment.getSeed(), experiment.getEpoch());
        final List<HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);

        final List<Object[]> features = experimentFeatureRepository.getAsExperimentFeatureSimpleByExperimentId(experiment.getId());
        final Map<String, Snippet> localCache = new HashMap<>();
        final IntHolder size = new IntHolder();
        final Set<String> taskParams = paramsSetter.getParamsInTransaction(isPersist, flowInstance, experiment, size);

        // there is 2 because we have 2 types of snippets - fit and predict
        // feature has real value only when isPersist==true
        int totalVariants = features.size() * allHyperParams.size() * 2;

        intHolder.value = allHyperParams.size() * experimentSnippets.size();

        log.info("total size of tasks' params is {} bytes", size.value);
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
                return Enums.FlowProducingStatus.FLOW_INSTANCE_WAS_DELETED;
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
                    Task prevTask = null;
                    Task task = null;
                    for (ExperimentSnippet experimentSnippet : experimentSnippets) {
                        if (boolHolder.value) {
                            return Enums.FlowProducingStatus.FLOW_INSTANCE_WAS_DELETED;
                        }
                        prevTask = task;

                        // create empty task. we need task id for initialization
                        task = new Task();
                        task.setParams("");
                        task.setFlowInstanceId(flowInstance.getId());
                        task.setOrder(process.order + (orderAdd++));
                        task.setProcessType(process.type.value);
                        if (isPersist) {
                            taskRepository.save(task);
                        }
                        // inc number of tasks
                        intHolder.value++;

                        TaskParamYaml yaml = new TaskParamYaml();
                        yaml.setHyperParams(hyperParams.toSortedMap());
                        yaml.inputResourceCodes.computeIfAbsent("feature", k -> new ArrayList<>()).addAll(inputResourceCodes);
                        for (Map.Entry<String, List<String>> entry : collectedInputs.entrySet()) {
                            if ("feature".equals(entry.getKey())) {
                                continue;
                            }
                            Process.Meta meta = process.getMetas()
                                    .stream()
                                    .filter(o -> o.value.equals(entry.getKey()))
                                    .findFirst()
                                    .orElse(null);
                            if (meta == null) {
                                log.error("Validation of flow #{} was failed. Meta with value {} wasn't found.", flowInstance.flowId, entry.getKey());
                                continue;
                            }
                            yaml.inputResourceCodes.computeIfAbsent(meta.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                        }
                        Snippet snippet = localCache.get(experimentSnippet.getSnippetCode());
                        if (snippet == null) {
                            final SnippetVersion snippetVersion = SnippetVersion.from(experimentSnippet.getSnippetCode());
                            if (snippetVersion==null) {
                                log.error("Snippet wasn't found for code: {}", experimentSnippet.getSnippetCode());
                                continue;
                            }
                            snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
                            if (snippet != null) {
                                localCache.put(experimentSnippet.getSnippetCode(), snippet);
                            }
                        }
                        if (snippet == null) {
                            log.warn("Snippet wasn't found for code: {}", experimentSnippet.getSnippetCode());
                            continue;
                        }

                        Enums.ExperimentTaskType type;
                        if ("fit".equals(snippet.getType())) {
                            yaml.outputResourceCode = getModelFilename(task);
                            type = Enums.ExperimentTaskType.FIT;
                        } else if ("predict".equals(snippet.getType())) {
                            if (prevTask == null) {
                                throw new IllegalStateException("prevTask is null");
                            }
                            yaml.inputResourceCodes.computeIfAbsent("model", k -> new ArrayList<>()).add(getModelFilename(prevTask));
                            yaml.outputResourceCode = "task-" + task.getId() + "-output-stub-for-predict";
                            type = Enums.ExperimentTaskType.PREDICT;
                        } else {
                            throw new IllegalStateException("Not supported type of snippet encountered, type: " + snippet.getType());
                        }
                        ExperimentTaskFeature tef = new ExperimentTaskFeature();
                        tef.setFlowInstanceId(flowInstance.getId());
                        tef.setTaskId(task.getId());
                        tef.setFeatureId((Long) feature[0]);
                        tef.setTaskType(type.value);
                        if (isPersist) {
                            taskExperimentFeatureRepository.save(tef);
                        }

                        yaml.snippet = new SimpleSnippet(
                                experimentSnippet.getType(),
                                experimentSnippet.getSnippetCode(),
                                snippet.getFilename(),
                                snippet.checksum,
                                snippet.env,
                                snippet.reportMetrics,
                                snippet.fileProvided,
                                snippet.params
                        );
                        yaml.clean = flow.clean;

                        String currTaskParams = taskParamYamlUtils.toString(yaml);

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
                                return Enums.FlowProducingStatus.PRODUCING_OF_EXPERIMENT_ERROR;
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
                return Enums.FlowProducingStatus.PRODUCING_OF_EXPERIMENT_ERROR;
            }
            experimentTemp.setNumberOfTask(totalVariants);
            experimentTemp.setAllTaskProduced(true);
            experimentTemp = experimentCache.save(experimentTemp);
        }
        return Enums.FlowProducingStatus.OK;
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

    private boolean isExist(List<ExperimentFeature> features, String f) {
        for (ExperimentFeature feature : features) {
            if (feature.getResourceCodes().equals(f)) {
                return true;
            }
        }
        return false;
    }

}
