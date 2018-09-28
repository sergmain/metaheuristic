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
import aiai.ai.launchpad.beans.*;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.feature.FeatureExecStatus;
import aiai.ai.yaml.snippet.SnippetType;
import aiai.ai.yaml.snippet.SnippetVersion;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.utils.BigDecimalHolder;
import aiai.ai.utils.permutation.Permutation;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.ai.yaml.hyper_params.HyperParams;
import aiai.ai.yaml.metrics.MetricValues;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.sequence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
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
public class ExperimentService {

    private static final List<Protocol.AssignedExperimentSequence.SimpleSequence> EMPTY_SIMPLE_SEQUENCES = Collections.unmodifiableList(new ArrayList<>());
    private static final HashMap<String, Integer> HASH_MAP = new HashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SequencesAndAssignToStationResult {
        ExperimentFeature feature;
        List<Protocol.AssignedExperimentSequence.SimpleSequence> simpleSequences;
    }

    private static final SequencesAndAssignToStationResult EMPTY_RESULT = new SequencesAndAssignToStationResult(null, EMPTY_SIMPLE_SEQUENCES);

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
    private final ExperimentRepository experimentRepository;
    private final ExperimentSequenceRepository experimentSequenceRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final SnippetRepository snippetRepository;
    private final DatasetRepository datasetRepository;
    private final SequenceYamlUtils sequenceYamlUtils;

    public ExperimentService(Globals globals, ExperimentRepository experimentRepository, ExperimentSequenceRepository experimentSequenceRepository, ExperimentFeatureRepository experimentFeatureRepository, SnippetRepository snippetRepository, DatasetRepository datasetRepository, SequenceYamlUtils sequenceYamlUtils) {
        this.globals = globals;
        this.experimentRepository = experimentRepository;
        this.experimentSequenceRepository = experimentSequenceRepository;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.snippetRepository = snippetRepository;
        this.datasetRepository = datasetRepository;
        this.sequenceYamlUtils = sequenceYamlUtils;
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

    // for implementing cache later. there is ExperimentCache class for that
    public Experiment findById(long id) {
        return experimentRepository.findById(id).orElse(null);
    }

    public synchronized SequencesAndAssignToStationResult getSequencesAndAssignToStation(long stationId, int recordNumber, boolean isAcceptOnlySigned, Long experimentId) {

        // check and mark all completed features
        List<ExperimentFeature> fsTemp = experimentFeatureRepository.findAllForLaunchedExperiments( Enums.ExperimentExecState.STARTED.code);
        List<ExperimentFeature> fs = new ArrayList<>();
        if (experimentId!=null) {
            Experiment experiment = findById(experimentId);
            if (experiment==null) {
                log.warn("there isn't the experiment for #id {}", experimentId);
                return EMPTY_RESULT;
            }
            if (isAcceptOnlySigned) {
                for (ExperimentSnippet experimentSnippet : experiment.getSnippets()) {
                    final SnippetVersion snippetVersion = SnippetVersion.from(experimentSnippet.getSnippetCode());
                    Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
                    if (snippet!=null && !snippet.isSigned()) {
                        // this experiment with #experimentId contains non-signed snippet but we were asked for singed snippets only
                        return EMPTY_RESULT;
                    }
                }
            }
            for (ExperimentFeature feature : fsTemp) {
                if (feature.experimentId.equals(experimentId)) {
                    fs.add(feature);
                }
            }
        }
        else {
            if (isAcceptOnlySigned) {
                for (ExperimentFeature feature : fsTemp) {
                    Experiment experiment = findById(feature.experimentId);
                    if (experiment==null) {
                        log.warn("there isn't the experiment for #id {}", experimentId);
                        continue;
                    }
                    for (ExperimentSnippet experimentSnippet : experiment.getSnippets()) {
                        final SnippetVersion snippetVersion = SnippetVersion.from(experimentSnippet.getSnippetCode());
                        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
                        if (snippet!=null && snippet.isSigned()) {
                            // add only feature for signed experiments
                            fs.add(feature);
                        }
                    }
                }
            }
            else {
                fs = fsTemp;
            }
        }
        Set<Long> idsForError = new HashSet<>();
        Set<Long> idsForOk = new HashSet<>();
        Boolean isContinue = null;
        for (ExperimentFeature feature : fs) {
            final Experiment e = findById(feature.experimentId);
            if (!e.isAllSequenceProduced() || !e.isFeatureProduced()) {
                continue;
            }
            // collect all experiment which has feature with FeatureExecStatus.error
            if (feature.getExecStatus()==FeatureExecStatus.error.code) {
                idsForOk.remove(feature.getExperimentId());
                idsForError.add(feature.getExperimentId());
            }

            // skip this feature from follow processing if it was finished
            if (feature.isFinished) {
                continue;
            }

            if (experimentSequenceRepository.findTop1ByFeatureId(feature.getId())==null) {
                feature.setExecStatus(FeatureExecStatus.empty.code);
                feature.setFinished(true);
                feature.setInProgress(false);
                experimentFeatureRepository.save(feature);
                continue;
            }

            // collect this experiment if it wasn't marked as erorr before
            if (!idsForError.contains(feature.getExperimentId())) {
                idsForOk.add(feature.getExperimentId());
            }

            if (experimentSequenceRepository.findTop1ByIsCompletedIsFalseAndFeatureId(feature.getId())==null) {

                // 'good results' meaning that at least one sequence was finished without system error (all snippets returned exit code 0)
                feature.setExecStatus(
                        experimentSequenceRepository.findTop1ByIsAllSnippetsOkIsTrueAndFeatureId(feature.getId())!=null
                                ? FeatureExecStatus.ok.code
                                : FeatureExecStatus.error.code
                );

                if (isContinue==null || !isContinue) {
                    isContinue = feature.getExecStatus() == FeatureExecStatus.ok.code;
                }

                feature.setFinished(true);
                feature.setInProgress(false);
                experimentFeatureRepository.save(feature);
            }
        }

        checkForFinished();

        // check that there isn't feature with FeatureExecStatus.error
        if (!Boolean.TRUE.equals(isContinue)) {
            return EMPTY_RESULT;
        }

        // check for case when there is feature with FeatureExecStatus.error and there is only one experiment (which has feature with FeatureExecStatus.error)
        if (idsForOk.isEmpty()) {
            return EMPTY_RESULT;
        }

        // main part, prepare new batch of sequences for station

        // is there any feature which was started(or in progress) and not finished yet for specific station?
        List<ExperimentFeature> features = experimentSequenceRepository.findAnyStartedButNotFinished(Consts.PAGE_REQUEST_1_REC, stationId, Enums.ExperimentExecState.STARTED.code);
        ExperimentFeature feature = null;
        // all sequences, which were assigned to station, are finished
        if (features == null || features.isEmpty()) {
            if (experimentId!=null) {
                features = experimentFeatureRepository.findTop1ByIsFinishedIsFalseAndIsInProgressIsTrueAndExperimentId(Consts.PAGE_REQUEST_1_REC, Enums.ExperimentExecState.STARTED.code, experimentId);
                if (!features.isEmpty()) {
                    feature = features.get(0);
                }
            }
            else {
                features = experimentFeatureRepository.findTop1ByIsFinishedIsFalseAndIsInProgressIsTrue(Consts.PAGE_REQUEST_1_REC, Enums.ExperimentExecState.STARTED.code);
                if (!features.isEmpty()) {
                    feature = features.get(0);
                }
            }
            if (feature==null) {
                // is there any feature which wasn't started and not finished yet?
                feature = experimentFeatureRepository.findTop1ByIsFinishedIsFalseAndIsInProgressIsFalse();
            }
        } else {
            feature = features.get(0);
        }


        // there isn't any feature to process
        if (feature==null) {
            return EMPTY_RESULT;
        }

        //
        ExperimentSequence sequence = experimentSequenceRepository.findTop1ByStationIdAndIsCompletedIsFalseAndFeatureId(stationId, feature.getId());
        if (sequence!=null) {
            return EMPTY_RESULT;
        }

        Slice<ExperimentSequence> seqs;
        if (experimentId!=null) {
            seqs = experimentSequenceRepository.findAllByStationIdIsNullAndFeatureIdAndExperimentId(PageRequest.of(0, recordNumber), feature.getId(), experimentId);
        }
        else {
            seqs = experimentSequenceRepository.findAllByStationIdIsNullAndFeatureId(PageRequest.of(0, recordNumber), feature.getId());
        }
        List<Protocol.AssignedExperimentSequence.SimpleSequence> result = new ArrayList<>(recordNumber+1);
        for (ExperimentSequence seq : seqs) {
            Protocol.AssignedExperimentSequence.SimpleSequence ss = new Protocol.AssignedExperimentSequence.SimpleSequence();
            ss.setExperimentSequenceId(seq.getId());
            ss.setParams(seq.getParams());

            seq.setAssignedOn(System.currentTimeMillis());
            seq.setStationId(stationId);
            result.add(ss);
        }
        if (!feature.isInProgress) {
            feature.setInProgress(true);
            experimentFeatureRepository.save(feature);
        }
        experimentSequenceRepository.saveAll(seqs);

        return new SequencesAndAssignToStationResult(feature, result);

    }

    private void checkForFinished() {
        List<ExperimentFeature> features = experimentFeatureRepository.findAllForActiveExperiments(Enums.ExperimentExecState.FINISHED.code);
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
                Experiment experiment = experimentRepository.findById(id).orElse(null);
                if (experiment==null) {
                    continue;
                }
                experiment.setExecState(Enums.ExperimentExecState.FINISHED.code);
                experimentRepository.save(experiment);
            }
        }
    }

    public List<Long> storeAllResults(List<SimpleSequenceExecResult> results) {
        List<ExperimentSequence> list = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (SimpleSequenceExecResult result : results) {
            ids.add(result.sequenceId);
            ExperimentSequence seq = experimentSequenceRepository.findById(result.sequenceId).orElse(null);
            if (seq==null) {
                log.warn("Can't find ExperimentSequence for Id: {}", result.sequenceId);
                continue;
            }

            Experiment experiment = experimentRepository.findById(seq.getExperimentId()).orElse(null);
            if (experiment==null) {
                log.warn("Can't find Experiment for Id: {}", seq.getId());
                continue;
            }

            SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(result.getResult());
            experiment.getSnippets().sort(Comparator.comparingInt(ExperimentSnippet::getOrder));
            boolean isAllOk = true;
            for (ExperimentSnippet snippet : experiment.getSnippets()) {
                ProcessService.Result r = snippetExec.getExecs().get(snippet.getOrder());
                if (r==null || !r.isOk()) {
                    isAllOk = false;
                    break;
                }
            }
            seq.setAllSnippetsOk(isAllOk);
            seq.setSnippetExecResults(result.getResult());
            seq.setMetrics(result.getMetrics());
            seq.setCompleted(true);
            seq.setCompletedOn(System.currentTimeMillis());
            list.add(seq);
        }
        experimentSequenceRepository.saveAll(list);
        return ids;
    }

    public void reconcileStationSequences(String stationIdAsStr, List<Protocol.StationSequenceStatus.SimpleStatus> statuses) {
        final long stationId = Long.parseLong(stationIdAsStr);
        List<ExperimentSequence> seqs = experimentSequenceRepository.findByStationIdAndIsCompletedIsFalse(stationId);
        for (ExperimentSequence seq : seqs) {
            boolean isFound = false;
            for (Protocol.StationSequenceStatus.SimpleStatus status : statuses) {
                if (status.experimentSequenceId ==seq.getId()) {
                    isFound = true;
                }
            }
            if(!isFound && (seq.getAssignedOn()!=null && (System.currentTimeMillis() - seq.getAssignedOn() > 90_000))) {
                log.info("De-assign sequence from station #{}, {}", stationIdAsStr, seq);
                seq.setStationId(null);
                seq.setAssignedOn(null);
                experimentSequenceRepository.save(seq);
            }
        }
    }

    Slice<ExperimentSequence> findExperimentSequence(Pageable pageable, Experiment experiment, ExperimentFeature feature, String[] params) {
        if (experiment == null || feature == null) {
            return Page.empty();
        } else {
            if (isEmpty(params)) {
                return experimentSequenceRepository.findByIsCompletedIsTrueAndFeatureId(pageable, feature.getId());
            } else {
                List<ExperimentSequence> selected = findExperimentSequenceWithFilter(experiment, feature.getId(), params);
                List<ExperimentSequence> subList = selected.subList((int)pageable.getOffset(), (int)Math.min(selected.size(), pageable.getOffset() + pageable.getPageSize()));
                //noinspection UnnecessaryLocalVariable
                final PageImpl<ExperimentSequence> page = new PageImpl<>(subList, pageable, selected.size());
                return page;

            }
        }
    }

    PlotData findExperimentSequenceForPlot(Experiment experiment, ExperimentFeature feature, String[] params, String[] paramsAxis) {
        if (experiment == null || feature == null) {
            return EMPTY_PLOT_DATA;
        } else {
            List<ExperimentSequence> selected;
            if (isEmpty(params)) {
                selected = experimentSequenceRepository.findByIsCompletedIsTrueAndFeatureId(feature.getId());
            } else {
                selected = findExperimentSequenceWithFilter(experiment, feature.getId(), params);
            }
            return collectDataForPlotting(experiment, selected, paramsAxis);
        }
    }

    private PlotData collectDataForPlotting(Experiment experiment, List<ExperimentSequence> selected, String[] paramsAxis) {
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
        for (ExperimentSequence sequence : selected) {

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

            final SequenceYaml sequenceYaml = sequenceYamlUtils.toSequenceYaml(sequence.getParams());
            int idxX = mapX.get(sequenceYaml.hyperParams.get(paramCleared.get(0)));
            int idxY = mapY.get(sequenceYaml.hyperParams.get(paramCleared.get(1)));
            data.z[idxY][idxX] = data.z[idxY][idxX].add(metricValues.values.get(metricKey));
        }

        return data;
    }


    private List<ExperimentSequence> findExperimentSequenceWithFilter(Experiment experiment, long featureId, String[] params) {
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

        List<ExperimentSequence> list = experimentSequenceRepository.findByIsCompletedIsTrueAndFeatureId(featureId);

        List<ExperimentSequence> selected = new ArrayList<>();
        for (ExperimentSequence sequence : list) {
            final SequenceYaml sequenceYaml = sequenceYamlUtils.toSequenceYaml(sequence.getParams());
            boolean[] isOk = new boolean[sequenceYaml.hyperParams.size()];
            int idx = 0;
            for (Map.Entry<String, String> entry : sequenceYaml.hyperParams.entrySet()) {
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

    public Map<String, Object> prepareExperimentFeatures(Experiment experiment, ExperimentFeature feature) {
        ExperimentsController.SequencesResult result = new ExperimentsController.SequencesResult();
        result.items = experimentSequenceRepository.findByIsCompletedIsTrueAndFeatureId(PageRequest.of(0, 10), feature.getId());

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

        List<ExperimentSequence> seqs = experimentSequenceRepository.findByIsCompletedIsTrueAndFeatureId(feature.getId());
        for (ExperimentSequence seq : seqs) {
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
        map.put("feature", feature);
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
     * long fixedDelay()
     * Execute the annotated method with a fixed period in milliseconds between the end of the last invocation and the start of the next.
     */
    public void fixedDelayExperimentSequencesProducer() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isLaunchpadEnabled) {
            return;
        }

        for (final Experiment experiment : experimentRepository.findByIsLaunchedIsTrueAndIsAllSequenceProducedIsFalse()) {
            if (experiment.getDatasetId()==null) {
                experiment.setLaunched(false);
                experiment.setNumberOfSequence(0);
                experiment.setAllSequenceProduced(false);
                experimentRepository.save(experiment);
                continue;
            }

            Dataset dataset = datasetRepository.findById(experiment.getDatasetId()).orElse(null);
            if (dataset == null) {
                experiment.setDatasetId(null);
                experiment.setNumberOfSequence(0);
                experiment.setAllSequenceProduced(false);
                experimentRepository.save(experiment);
                continue;
            }

            produceFeaturePermutations(dataset, experiment);

            produceSequences(experiment);
        }
    }

    public void produceSequences(Experiment experiment) {
        int totalVariants = 0;

        experiment.sortSnippetsByOrder();

        List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(experiment.getId());
        for (ExperimentFeature feature : features) {
            Set<String> sequnces = new LinkedHashSet<>();

            for (ExperimentSequence experimentSequence : experimentSequenceRepository.findByExperimentIdAndFeatureId(experiment.getId(), feature.getId())) {
                if (sequnces.contains(experimentSequence.getParams())) {
                    // delete doubles records
                    log.warn("!!! Found doubles. ExperimentId: {}, featureId: {}, hyperParams: {}", experiment.getId(), feature.getId(), experimentSequence.getParams());
                    experimentSequenceRepository.delete(experimentSequence);
                    continue;
                }
                sequnces.add(experimentSequence.getParams());
            }

            final Map<String, String> map = ExperimentService.toMap(experiment.getHyperParams(), experiment.getSeed(), experiment.getEpoch());
            final List<HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);
            totalVariants += allHyperParams.size();

            final ExperimentUtils.NumberOfVariants ofVariants = ExperimentUtils.getNumberOfVariants(feature.getFeatureIds());
            final List<SimpleFeature> simpleFeatures = Collections.unmodifiableList(ofVariants.values.stream().map(SimpleFeature::of).collect(Collectors.toList()));

            Map<String, Snippet> localCache = new HashMap<>();
            boolean isNew = false;
            for (HyperParams hyperParams : allHyperParams) {
                SequenceYaml yaml = new SequenceYaml();
                yaml.setHyperParams( hyperParams.toSortedMap() );
                yaml.setExperimentId( experiment.getId() );
                yaml.setDataset( SimpleDataset.of(experiment.getDatasetId() ));
                yaml.setFeatures( simpleFeatures );

                final List<SimpleSnippet> snippets = new ArrayList<>();
                for (ExperimentSnippet experimentSnippet : experiment.getSnippets()) {
                    final SnippetVersion snippetVersion = SnippetVersion.from(experimentSnippet.getSnippetCode());
                    Snippet snippet =  localCache.get(experimentSnippet.getSnippetCode());
                    if (snippet==null) {
                        snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
                        if (snippet!=null) {
                            localCache.put(experimentSnippet.getSnippetCode(), snippet);
                        }
                    }
                    if (snippet==null) {
                        log.warn("Snippet wasn't found for code: {}", experimentSnippet.getSnippetCode());
                        continue;
                    }
                    snippets.add(new SimpleSnippet(
                            SnippetType.valueOf(experimentSnippet.getType()),
                            experimentSnippet.getSnippetCode(),
                            snippet.getFilename(),
                            snippet.checksum,
                            snippet.env,
                            experimentSnippet.getOrder()
                    ));
                }
                yaml.snippets = snippets;

                String sequenceParams = sequenceYamlUtils.toString(yaml);

                if (sequnces.contains(sequenceParams)) {
                    continue;
                }

                ExperimentSequence sequence = new ExperimentSequence();
                sequence.setExperimentId(experiment.getId());
                sequence.setParams(sequenceParams);
                sequence.setFeatureId(feature.getId());
                experimentSequenceRepository.save(sequence);
                isNew = true;
            }
            if (isNew) {
                boolean isOk = false;
                for (int i = 0; i <3; i++) {
                    try {
                        ExperimentFeature f = experimentFeatureRepository.findById(feature.getId()).orElse(null);
                        if (f==null) {
                            log.warn("Unxpected behaviour, feature with id {} wasn't found", feature.getId());
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
                    log.warn("The new sequences were produced but feature wasn't changed");
                }
            }
        }
        if (experiment.getNumberOfSequence() != totalVariants && experiment.getNumberOfSequence() != 0) {
            log.warn("! Number of sequnece is different. experiment.getNumberOfSequence(): {}, totalVariants: {}", experiment.getNumberOfSequence(), totalVariants);
        }
        Experiment experimentTemp = experimentRepository.findById(experiment.getId()).orElse(null);
        if (experimentTemp==null) {
            log.warn("Experiment for id {} doesn't exist anymore", experiment.getId());
            return;
        }
        experimentTemp.setNumberOfSequence(totalVariants);
        experimentTemp.setAllSequenceProduced(true);
        experimentRepository.save(experimentTemp);
    }

    public void produceFeaturePermutations(Dataset dataset, Experiment experiment) {
        final List<ExperimentFeature> list = experimentFeatureRepository.findByExperimentId(experiment.getId());

        final List<Long> ids = new ArrayList<>();
        final Set<Long> requiredIds = new HashSet<>();
        for (DatasetGroup datasetGroup : dataset.getDatasetGroups()) {
            ids.add(datasetGroup.getId());
            if (datasetGroup.isRequired()) {
                requiredIds.add(datasetGroup.getId());
            }
        }
        if (!requiredIds.isEmpty()) {
            for (ExperimentFeature feature : list) {
                final ExperimentUtils.NumberOfVariants ofVariants = ExperimentUtils.getNumberOfVariants(feature.getFeatureIds());
                boolean isFound = false;
                for (String value : ofVariants.values) {
                    if (requiredIds.contains(Long.parseLong(value))) {
                        isFound = true;
                        break;
                    }
                }
                if (!isFound){
                    experimentFeatureRepository.delete(feature);
                }
            }
        }

        Permutation<Long> permutation = new Permutation<>();
        for (int i = 0; i < ids.size(); i++) {
            permutation.printCombination(ids, i+1,
                    data -> {
                        if (isSkip(data, requiredIds)) {
                            return true;
                        }
                        final String idsAsStr = String.valueOf(data);
                        if (isExist(list, idsAsStr)) {
                            return true;
                        }
                        if (isNotAll(requiredIds, data)) {
                            return true;
                        }
                        final ExperimentFeature feature = new ExperimentFeature();
                        feature.setExperimentId(experiment.getId());
                        feature.setFeatureIds(idsAsStr);
                        experimentFeatureRepository.save(feature);
                        return true;
                    }
            );
        }
        experiment.setFeatureProduced(true);
        experimentRepository.save(experiment);
    }

    private boolean isNotAll(Set<Long> requiredIds, List<Long> data) {
        for (Long requiredId : requiredIds) {
            if (!data.contains(requiredId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSkip(List<Long> data, Set<Long> requiredIds) {
        if (requiredIds.isEmpty()) {
            return false;
        }
        boolean isFound = false;
        for (Long datum : data) {
            if (requiredIds.contains(datum)) {
                isFound = true;
                break;
            }
        }
        return !isFound;
    }

    private boolean isExist(List<ExperimentFeature> features, String f) {
        for (ExperimentFeature feature : features) {
            if (feature.getFeatureIds().equals(f)) {
                return true;
            }
        }
        return false;
    }

}
