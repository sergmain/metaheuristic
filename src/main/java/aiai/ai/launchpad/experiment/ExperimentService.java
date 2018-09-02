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
import aiai.ai.Globals;
import aiai.ai.beans.*;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.launchpad.snippet.SnippetVersion;
import aiai.ai.repositories.*;
import aiai.ai.utils.permutation.Permutation;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.ai.yaml.hyper_params.HyperParams;
import aiai.ai.yaml.sequence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.*;
import java.util.stream.Collectors;

@Service
@EnableScheduling
@EnableTransactionManagement
@Slf4j
public class ExperimentService {

    private final Globals globals;
    private final ExperimentRepository experimentRepository;
    private final ExperimentSequenceRepository experimentSequenceRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final SnippetRepository snippetRepository;
    private final DatasetRepository datasetRepository;

    public ExperimentService(Globals globals, ExperimentRepository experimentRepository, ExperimentSequenceRepository experimentSequenceRepository, ExperimentFeatureRepository experimentFeatureRepository, SnippetRepository snippetRepository, DatasetRepository datasetRepository) {
        this.globals = globals;
        this.experimentRepository = experimentRepository;
        this.experimentSequenceRepository = experimentSequenceRepository;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.snippetRepository = snippetRepository;
        this.datasetRepository = datasetRepository;
    }

    public synchronized List<Protocol.AssignedExperimentSequence.SimpleSequence> getSequncesAndAssignToStation(long stationId, int recordNumber) {

        // check and mark all completed features
        List<ExperimentFeature> fs = experimentFeatureRepository.findAllForLaunchedExperiments();
        for (ExperimentFeature feature : fs) {
            if (experimentSequenceRepository.findTop1ByIsCompletedIsFalseAndFeatureId(feature.getId())==null) {

                feature.setAnyGoodResults(....);

                feature.setFinished(true);
                experimentFeatureRepository.save(feature);
            }
        }

        // main part, prepare new batch of sequences for station

        ExperimentFeature feature = experimentFeatureRepository.findTop1ByIsFinishedIsFalseAndIsInProgressIsTrue();
        if (feature==null) {
            feature = experimentFeatureRepository.findTop1ByIsFinishedIsFalseAndIsInProgressIsFalse();
        }

        if (feature==null) {
            return null;
        }

        ExperimentSequence sequence = experimentSequenceRepository.findTop1ByStationIdIsNotNullAndIsCompletedIsFalseAndFeatureId(feature.getId());
        if (sequence!=null) {
            return new ArrayList<>();
        }

        sequence = experimentSequenceRepository.findTop1ByStationIdIsNotNullAndIsCompletedIsFalseAndFeatureId(feature.getId());
        if (sequence!=null) {
            return new ArrayList<>();
        }


        Slice<ExperimentSequence> seqs = experimentSequenceRepository.findAllByStationIdIsNullAndFeatureId(PageRequest.of(0, recordNumber), feature.getId());
        List<Protocol.AssignedExperimentSequence.SimpleSequence> result = new ArrayList<>(recordNumber+1);
        for (ExperimentSequence seq : seqs) {
            Protocol.AssignedExperimentSequence.SimpleSequence ss = new Protocol.AssignedExperimentSequence.SimpleSequence();
            ss.setExperimentSequenceId(seq.getId());
            ss.setParams(seq.getParams());

            seq.setAssignedOn(System.currentTimeMillis());
            seq.setStationId(stationId);
            result.add(ss);
        }
        experimentSequenceRepository.saveAll(seqs);

        return result;

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

            Experiment experiment = experimentRepository.findById(seq.getId()).orElse(null);
            if (experiment==null) {
                log.warn("Can't find Experiment for Id: {}", seq.getId());
                continue;
            }

            SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(seq.getSnippetExecResults());
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
            seq.setCompleted(true);
            list.add(seq);
        }
        experimentSequenceRepository.saveAll(list);
        return ids;
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

    public static Map<String, String> toMap(List<ExperimentHyperParams> experimentHyperParams) {
        return experimentHyperParams.stream().collect(Collectors.toMap(ExperimentHyperParams::getKey, ExperimentHyperParams::getValues, (a, b) -> b, HashMap::new));
    }

    /**
     * this scheduler is being run at launchpad side
     *
     * long fixedDelay()
     * Execute the annotated method with a fixed period in milliseconds between the end of the last invocation and the start of the next.
     */
    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.create-sequence.timeout'), 10, 20, 10)*1000 }")
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

            int totalVariants = 0;

            final List<ExperimentFeature> list = experimentFeatureRepository.findByExperimentId(experiment.getId());

            List<Long> ids = new ArrayList<>();
            for (DatasetGroup datasetGroup : dataset.getDatasetGroups()) {
                ids.add(datasetGroup.getId());
            }
            Permutation<Long> permutation = new Permutation<>();
            for (int i = 0; i < ids.size(); i++) {
                permutation.printCombination(ids, i+1,
                        data -> {
                            final String idsAsStr = String.valueOf(data);
                            if (isExist(list, idsAsStr)) {
                                return true;
                            }
                            final ExperimentFeature feature = new ExperimentFeature();
                            feature.setExperimentId(experiment.getId());;
                            feature.setFeatureIds(idsAsStr);
                            experimentFeatureRepository.save(feature);
                            return true;
                        }
                );
            }

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

                if (experiment.getNumberOfSequence()!=allHyperParams.size()) {
                    log.warn("!!! number of sequnce is different. experiment.getNumberOfSequence(): {}, allHyperParams.size(): {}", experiment.getNumberOfSequence(), allHyperParams.size());
                }

                final ExperimentUtils.NumberOfVariants ofVariants = ExperimentUtils.getNumberOfVariants(feature.getFeatureIds());
                final List<SimpleFeature> simpleFeatures = Collections.unmodifiableList(ofVariants.values.stream().map(SimpleFeature::of).collect(Collectors.toList()));

                Map<String, Snippet> localCache = new HashMap<>();
                boolean isNew = false;
                for (HyperParams hyperParams : allHyperParams) {
                    SequenceYaml yaml = new SequenceYaml();
                    yaml.setHyperParams( hyperParams.toSortedMap() );
                    yaml.setExperimentId( experiment.getId() );
                    yaml.setDataset( SimpleDataset.of(experiment.getDatasetId() ));
                    yaml.setFeatures( simpleFeatures ); ;

                    final List<SimpleSnippet> snippets = new ArrayList<>();
                    experiment.sortSnippetsByOrder();
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

                    String sequenceParams = SequenceYamlUtils.toString(yaml);

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
            experiment.setNumberOfSequence(totalVariants);
            experiment.setAllSequenceProduced(true);
            experimentRepository.save(experiment);
        }
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
