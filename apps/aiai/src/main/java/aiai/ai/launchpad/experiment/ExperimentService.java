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
import aiai.ai.Globals;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.repositories.ExperimentFeatureRepository;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.utils.BigDecimalHolder;
import aiai.ai.utils.permutation.Permutation;
import aiai.ai.yaml.hyper_params.HyperParams;
import aiai.ai.yaml.metrics.MetricValues;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.sequence.SimpleResourceInfo;
import aiai.ai.yaml.sequence.SimpleSnippet;
import aiai.ai.yaml.sequence.TaskParamYaml;
import aiai.ai.yaml.sequence.TaskParamYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.omg.CORBA.IntHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@EnableTransactionManagement
@Slf4j
@Profile("launchpad")
public class ExperimentService {

    private static final HashMap<String, Integer> HASH_MAP = new HashMap<>();

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

    private final Globals globals;
    private final ExperimentCache experimentCache;
    private final TaskRepository taskRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final SnippetCache snippetCache;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final SnippetService snippetService;
    private final ExperimentRepository experimentRepository;

    public ExperimentService(Globals globals, ExperimentRepository experimentRepository, ExperimentCache experimentCache, TaskRepository taskRepository, ExperimentFeatureRepository experimentFeatureRepository, SnippetCache snippetCache, TaskParamYamlUtils taskParamYamlUtils, SnippetService snippetService, FlowInstanceRepository flowInstanceRepository, ExperimentRepository experimentRepository1) {
        this.globals = globals;
        this.experimentCache = experimentCache;
        this.taskRepository = taskRepository;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.snippetCache = snippetCache;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.snippetService = snippetService;
        this.experimentRepository = experimentRepository1;
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

    private void checkForFinished() {
        List<ExperimentFeature> features = experimentFeatureRepository.findAllForActiveExperiments(Enums.FlowInstanceExecState.FINISHED.code);
        Set<Long> ids = new HashSet<>();
        // ugly but ok for first version
        for (ExperimentFeature feature : features) {
            ids.add(feature.getExperimentId());
        }
        for (long id : ids) {
            boolean isFinished = true;
            for (ExperimentFeature feature : features) {
                if (id==feature.getExperimentId() && !feature.isFinished) {
                    isFinished = false;
                    break;
                }
            }
            if (isFinished) {
                Experiment experiment = experimentCache.findById(id);
                if (experiment==null) {
                    continue;
                }
                experiment.setExecState(Enums.FlowInstanceExecState.FINISHED.code);
                experimentCache.save(experiment);
            }
        }
    }

    public void reconcileStationTasks(String stationIdAsStr, List<Protocol.StationTaskStatus.SimpleStatus> statuses) {
        final long stationId = Long.parseLong(stationIdAsStr);
        List<Task> seqs = taskRepository.findByStationIdAndIsCompletedIsFalse(stationId);
        for (Task seq : seqs) {
            boolean isFound = false;
            for (Protocol.StationTaskStatus.SimpleStatus status : statuses) {
                if (status.experimentSequenceId ==seq.getId()) {
                    isFound = true;
                }
            }
            if(!isFound && (seq.getAssignedOn()!=null && (System.currentTimeMillis() - seq.getAssignedOn() > 90_000))) {
                log.info("De-assign sequence from station #{}, {}", stationIdAsStr, seq);
                seq.setStationId(null);
                seq.setAssignedOn(null);
                taskRepository.save(seq);
            }
        }
    }

    Slice<Task> findTasks(Pageable pageable, Experiment experiment, ExperimentFeature feature, String[] params) {
        if (experiment == null || feature == null) {
            return Page.empty();
        } else {
            if (isEmpty(params)) {
                if (true) throw new IllegalStateException("Not implemented yet");
                return null;
//                return taskRepository.findByIsCompletedIsTrueAndFeatureId(pageable, feature.getId());
            } else {
                List<Task> selected = findTaskWithFilter(experiment, feature.getId(), params);
                List<Task> subList = selected.subList((int)pageable.getOffset(), (int)Math.min(selected.size(), pageable.getOffset() + pageable.getPageSize()));
                //noinspection UnnecessaryLocalVariable
                final PageImpl<Task> page = new PageImpl<>(subList, pageable, selected.size());
                return page;

            }
        }
    }

    PlotData findExperimentSequenceForPlot(Experiment experiment, ExperimentFeature feature, String[] params, String[] paramsAxis) {
        if (experiment == null || feature == null) {
            return EMPTY_PLOT_DATA;
        } else {
            List<Task> selected;
            if (isEmpty(params)) {
                if (true) throw new IllegalStateException("Not implemented yet");
                selected = null;
//                selected = taskRepository.findByIsCompletedIsTrueAndFeatureId(feature.getId());
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
        for (Task sequence : selected) {

            MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(sequence.metrics) );
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

            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(sequence.getParams());
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

        if (true) throw new IllegalStateException("Not implemented yet");
        List<Task> list = null;
//        List<Task> list = taskRepository.findByIsCompletedIsTrueAndFeatureId(featureId);

        List<Task> selected = new ArrayList<>();
        for (Task sequence : list) {
            final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(sequence.getParams());
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
                selected.add(sequence);
            }
        }
        //noinspection UnnecessaryLocalVariable
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
        if (true) throw new IllegalStateException("Not implemented yet");
//        result.items = taskRepository.findByIsCompletedIsTrueAndFeatureId(Consts.PAGE_REQUEST_10_REC, experimentFeature.getId());

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

        if (true) throw new IllegalStateException("Not implemented yet");
        List<Task> seqs = null;
//        List<Task> seqs = taskRepository.findByIsCompletedIsTrueAndFeatureId(experimentFeature.getId());
        for (Task seq : seqs) {
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

    /**
     * this scheduler is being run at launchpad side
     *
     * Execute the annotated method with a fixed period in milliseconds between the end of the last invocation and the start of the next.
     */
    public void produceTasksForFlowInstances() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isLaunchpadEnabled) {
            return;
        }
/*
        for (final Experiment experiment : experimentRepository.findByIsLaunchedIsTrueAndIsAllTaskProducedIsFalse()) {
            List<String> inputResourceCodes = new ArrayList<>();
            produceFeaturePermutations(experiment, inputResourceCodes);
            produceTasks(experiment, inputResourceCodes);
        }
*/
    }

    public void resetExperiment(FlowInstance flowInstance) {

        Experiment e = experimentRepository.findByFlowInstanceId(flowInstance.getId());
        if (e==null) {
            return;
        }
        experimentFeatureRepository.deleteByExperimentId(e.getId());

        e.setFlowInstanceId(null);
        e.setExecState( Enums.FlowInstanceExecState.NONE.code);
        e.setAllTaskProduced(false);
        e.setLaunchedOn(null);
        e.setEpochVariant(0);
        e.setFeatureProduced(false);
        e.setAllTaskProduced(false);
        e.setNumberOfTask(0);
        experimentCache.save(e);
    }

    public void produceTasks(FlowInstance flowInstance, int order, Experiment experiment) {
        int totalVariants = 0;

        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experiment.getId());
        snippetService.sortSnippetsByType(experimentSnippets);

        List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(experiment.getId());
        for (ExperimentFeature feature : features) {
            ExperimentUtils.NumberOfVariants numberOfVariants = ExperimentUtils.getNumberOfVariants(feature.getResourceCodes());
            if (!numberOfVariants.status) {
                log.warn("empty list of feature, feature: {}", feature);
                continue;
            }
            List<String> inputResourceCodes = numberOfVariants.values;
            Set<String> taskParams = new LinkedHashSet<>();

            for (Task task : taskRepository.findByFlowInstanceId(flowInstance.getId())) {
                if (taskParams.contains(task.getParams())) {
                    // delete doubles records
                    log.warn("!!! Found doubles. ExperimentId: {}, experimentFeatureId: {}, hyperParams: {}", experiment.getId(), feature.getId(), task.getParams());
                    taskRepository.delete(task);
                    continue;
                }
                taskParams.add(task.getParams());
            }

            final Map<String, String> map = toMap(experiment.getHyperParams(), experiment.getSeed(), experiment.getEpoch());
            final List<HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);

            // there is 2 because we have 2 snippets - fit and predict
            totalVariants += allHyperParams.size() * 2;

            final ExperimentUtils.NumberOfVariants ofVariants = ExperimentUtils.getNumberOfVariants(feature.getResourceCodes());
            final List<SimpleResourceInfo> simpleFeatureResources = Collections.unmodifiableList(
                    ofVariants.values.stream()
                            .map(s -> SimpleResourceInfo.of(Enums.BinaryDataType.DATA, s))
                            .collect(Collectors.toList()));

            Map<String, Snippet> localCache = new HashMap<>();
            boolean isNew = false;
            for (HyperParams hyperParams : allHyperParams) {

                int orderAdd = 0;
                for (ExperimentSnippet experimentSnippet : experimentSnippets) {
                    TaskParamYaml yaml = new TaskParamYaml();
                    yaml.setHyperParams( hyperParams.toSortedMap() );
                    yaml.inputResourceCodes = inputResourceCodes;

                    final SnippetVersion snippetVersion = SnippetVersion.from(experimentSnippet.getSnippetCode());
                    Snippet snippet =  localCache.get(experimentSnippet.getSnippetCode());
                    if (snippet==null) {
                        snippet = snippetCache.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
                        if (snippet!=null) {
                            localCache.put(experimentSnippet.getSnippetCode(), snippet);
                        }
                    }
                    if (snippet==null) {
                        log.warn("Snippet wasn't found for code: {}", experimentSnippet.getSnippetCode());
                        continue;
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

                    String currTaskParams = taskParamYamlUtils.toString(yaml);

                    if (taskParams.contains(currTaskParams)) {
                        continue;
                    }

                    Task task = new Task();
                    task.setParams(currTaskParams);
                    task.setFlowInstanceId(flowInstance.getId());
                    // we can just increment order here
                    // because experiment is always last process in flow
                    task.setOrder(order + (orderAdd++));
                    taskRepository.save(task);
                    isNew = true;
                }
            }
            if (isNew) {
                boolean isOk = false;
                for (int i = 0; i <3; i++) {
                    try {
                        ExperimentFeature f = experimentFeatureRepository.findById(feature.getId()).orElse(null);
                        if (f==null) {
                            log.warn("Unexpected behaviour, feature with id {} wasn't found", feature.getId());
                            break;
                        }
                        f.setFinished(false);
                        experimentFeatureRepository.save(f);
                        isOk = true;
                        break;
                    }
                    catch (ObjectOptimisticLockingFailureException e) {
                        log.info("Feature record was changed. {}", e.getMessage());
                    }
                }
                if (!isOk) {
                    log.warn("The new tasks were produced but feature wasn't changed");
                }
            }
        }
        if (experiment.getNumberOfTask() != totalVariants && experiment.getNumberOfTask() != 0) {
            log.warn("! Number of sequence is different. experiment.getNumberOfTask(): {}, totalVariants: {}", experiment.getNumberOfTask(), totalVariants);
        }
        Experiment experimentTemp = experimentCache.findById(experiment.getId());
        if (experimentTemp==null) {
            log.warn("Experiment for id {} doesn't exist anymore", experiment.getId());
            return;
        }
        experimentTemp.setNumberOfTask(totalVariants);
        experimentTemp.setAllTaskProduced(true);
        experimentCache.save(experimentTemp);
    }

    public void produceFeaturePermutations(Experiment experiment, List<String> inputResourceCodes) {
        final List<ExperimentFeature> list = experimentFeatureRepository.findByExperimentId(experiment.getId());

        final List<String> codes = new ArrayList<>(inputResourceCodes);
        final Permutation<String> permutation = new Permutation<>();
        final IntHolder total = new IntHolder();
        for (int i = 0; i < codes.size(); i++) {
            permutation.printCombination(codes, i+1,
                    data -> {
                        final String listAsStr = String.valueOf(data);
                        if (isExist(list, listAsStr)) {
                            return true;
                        }

                        final ExperimentFeature feature = new ExperimentFeature();
                        feature.setExperimentId(experiment.getId());
                        feature.setResourceCodes(listAsStr);
                        experimentFeatureRepository.save(feature);
                        total.value++;
                        return true;
                    }
            );
        }
        experiment.setFeatureProduced(true);
        experimentCache.save(experiment);
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
