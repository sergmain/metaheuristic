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
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.launchpad.snippet.SnippetVersion;
import aiai.ai.repositories.ExperimentRepository;
import aiai.ai.repositories.ExperimentSequenceRepository;
import aiai.ai.repositories.SnippetRepository;
import aiai.ai.yaml.hyper_params.HyperParams;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import aiai.ai.yaml.sequence.SimpleSnippet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.*;
import java.util.stream.Collectors;

@Service
@EnableTransactionManagement
@Slf4j
public class ExperimentService {

    private final Globals globals;
    private final ExperimentRepository experimentRepository;
    private final ExperimentSequenceRepository experimentSequenceRepository;
    private final SnippetRepository snippetRepository;

    public ExperimentService(Globals globals, ExperimentRepository experimentRepository, ExperimentSequenceRepository experimentSequenceRepository, SnippetRepository snippetRepository) {
        this.globals = globals;
        this.experimentRepository = experimentRepository;
        this.experimentSequenceRepository = experimentSequenceRepository;
        this.snippetRepository = snippetRepository;
    }

    public synchronized List<Protocol.AssignedExperimentSequence.SimpleSequence> getSequncesAndAssignToStation(long stationId) {

        ExperimentSequence sequence = experimentSequenceRepository.findTop1ByStationIdIsNotNullAndIsCompletedIsFalse();
        if (sequence!=null) {
            return new ArrayList<>();
        }

        Slice<ExperimentSequence> seqs = experimentSequenceRepository.findAllByStationIdIsNull(PageRequest.of(0, 10));
        List<Protocol.AssignedExperimentSequence.SimpleSequence> result = new ArrayList<>(11);
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

    public void storeAllResults(List<SimpleSequenceExecResult> results) {
        List<ExperimentSequence> list = new ArrayList<>();
        for (SimpleSequenceExecResult result : results) {
            ExperimentSequence seq = experimentSequenceRepository.findById(result.sequenceId).orElse(null);
            if (seq==null) {
                log.warn("Can't find ExperimentSequence for Id: {}", result.sequenceId);
                continue;
            }
            seq.setSnippetExecResults(result.getResult());
            seq.setCompleted(true);
            list.add(seq);
        }
        experimentSequenceRepository.saveAll(list);
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
     * this scheduler is being runned at launchpad side
     *
     * long fixedDelay()
     * Execute the annotated method with a fixed period in milliseconds between the end of the last invocation and the start of the next.
     */
    @Scheduled(fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.launchpad.create-sequence.timeout'), 10, 20, 10)*1000 }")
    public void fixedDelayExperimentSequencesProducer() {
        if (!globals.isLaunchpadEnabled) {
            return;
        }

        for (Experiment experiment : experimentRepository.findByIsLaunchedIsTrueAndIsAllSequenceProducedIsFalse()) {
            if (experiment.getDatasetId()==null) {
                experiment.setLaunched(false);
                experiment.setNumberOfSequence(0);
                experiment.setAllSequenceProduced(false);
                experimentRepository.save(experiment);
                continue;
            }

            Set<String> sequnces = new LinkedHashSet<>();

            for (ExperimentSequence experimentSequence : experimentSequenceRepository.findByExperimentId(experiment.getId())) {
                if (sequnces.contains(experimentSequence.getParams())) {
                    // delete doubles records
                    System.out.println("!!! Found doubles. ExperimentId: " + experiment.getId()+", hyperParams: " + experimentSequence.getParams());
                    experimentSequenceRepository.delete(experimentSequence);
                    continue;
                }
                sequnces.add(experimentSequence.getParams());
            }

            Map<String, String> map = ExperimentService.toMap(experiment.getHyperParams(), experiment.getSeed(), experiment.getEpoch());
            List<HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);

            if (experiment.getNumberOfSequence()!=allHyperParams.size()) {
                System.out.println(String.format(
                        "!!! number of sequnce is different. experiment.getNumberOfSequence():  %d, allHyperParams.size(): %d",
                        experiment.getNumberOfSequence(), allHyperParams.size()));
            }

            Map<String, Snippet> localCache = new HashMap<>();
            for (HyperParams hyperParams : allHyperParams) {
                SequenceYaml yaml = new SequenceYaml();
                yaml.setHyperParams( hyperParams.toSortedMap() );
                yaml.setExperimentId( experiment.getId() );
                yaml.setDatasetId( experiment.getDatasetId() );

                List<SimpleSnippet> snippets = new ArrayList<>();
                experiment.sortSnippetsByOrder();
                for (ExperimentSnippet experimentSnippet : experiment.getSnippets()) {
                    SnippetVersion snippetVersion = SnippetVersion.from(experimentSnippet.getSnippetCode());
                    Snippet snippet =  localCache.get(experimentSnippet.getSnippetCode());
                    if (snippet==null) {
                        snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
                        if (snippet!=null) {
                            localCache.put(experimentSnippet.getSnippetCode(), snippet);
                        }
                    }
//                    Snippet snippet = localCache.putIfAbsent(experimentSnippet.getSnippetCode(), snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version));
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
                experimentSequenceRepository.save(sequence);
            }
            experiment.setNumberOfSequence(allHyperParams.size());
            experiment.setAllSequenceProduced(true);
            experimentRepository.save(experiment);
        }
    }
}
