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
import aiai.ai.beans.Experiment;
import aiai.ai.beans.ExperimentHyperParams;
import aiai.ai.beans.ExperimentSequence;
import aiai.ai.beans.ExperimentSnippet;
import aiai.ai.comm.Protocol;
import aiai.ai.repositories.ExperimentRepository;
import aiai.ai.repositories.ExperimentSequenceRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;

@Service
@EnableTransactionManagement
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentSequenceRepository experimentSequenceRepository;

    private final Yaml yamlProcessor;

    public ExperimentService(ExperimentRepository experimentRepository, ExperimentSequenceRepository experimentSequenceRepository) {
        this.experimentRepository = experimentRepository;
        this.experimentSequenceRepository = experimentSequenceRepository;

        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        this.yamlProcessor = new Yaml(options);
    }

    @Data
    @AllArgsConstructor
    public static class SimpleSnippet {
        String type;
        String code;
    }

    @Data
    public static class SequenceYaml {
        Long datasetId;
        Long experimentId;
        List<SimpleSnippet> snippets;
        Map<String, String> hyperParams;
    }


    public synchronized List<Protocol.AssignedExperimentSequence.SimpleSequence> getSequncesAndAssignToStation(long stationId) {

        List<ExperimentSequence> seqAssigned = experimentSequenceRepository.findAllByStationIdIsNotNullAndIsCompletedIsFalse();
        if (!seqAssigned.isEmpty()) {
            return new ArrayList<>();
        }

        Slice<ExperimentSequence> seqs = experimentSequenceRepository.findAllByStationIdIsNull(PageRequest.of(0, 10));
        List<Protocol.AssignedExperimentSequence.SimpleSequence> result = new ArrayList<>(11);
        for (ExperimentSequence seq : seqs) {
            Protocol.AssignedExperimentSequence.SimpleSequence ss = new Protocol.AssignedExperimentSequence.SimpleSequence();
            ss.setExperimentSequenceId(seq.getId());
            ss.setParams(seq.getParams());

            seq.setAssignedOn(System.currentTimeMillis());
            seq.setStationId(stationId);;
            result.add(ss);
        }
        experimentSequenceRepository.saveAll(seqs);
        return result;
    }

    public static String toYaml(Yaml yaml, ExperimentUtils.HyperParams hyperParams) {
        if (hyperParams==null) {
            return null;
        }
        String mapYaml;
        mapYaml = yaml.dump(sortHyperParams(hyperParams));
        return mapYaml;
    }

    private static LinkedHashMap<String, String> sortHyperParams(ExperimentUtils.HyperParams hyperParams) {
        return hyperParams.params.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
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
     * this scheduler is being runned at station side
     *
     * long fixedDelay()
     * Execute the annotated method with a fixed period in milliseconds between the end of the last invocation and the start of the next.
     */
    @Scheduled(fixedDelayString = "#{ new Integer(environment.getProperty('aiai.station.request.launchpad.timeout')) > 10 ? new Integer(environment.getProperty('aiai.station.request.launchpad.timeout'))*1000 : 10000 }")
    public void fixedDelayExperimentSequencesProducer() {

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
            List<ExperimentUtils.HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);

            if (experiment.getNumberOfSequence()!=allHyperParams.size()) {
                System.out.println(String.format(
                        "!!! number of sequnce is different. experiment.getNumberOfSequence():  %d, allHyperParams.size(): %d",
                        experiment.getNumberOfSequence(), allHyperParams.size()));
            }

            for (ExperimentUtils.HyperParams hyperParams : allHyperParams) {
                SequenceYaml yaml = new SequenceYaml();
                yaml.hyperParams = sortHyperParams(hyperParams);
                yaml.experimentId = experiment.getId();
                yaml.datasetId = experiment.getDatasetId();

                List<SimpleSnippet> snippets = new ArrayList<>();
                for (ExperimentSnippet snippet : experiment.getSnippets()) {
                    snippets.add(new SimpleSnippet(snippet.getType(), snippet.getSnippetCode()));
                }
                yaml.snippets = snippets;

                String sequenceParams = yamlProcessor.dump(yaml);

                if (sequnces.contains(sequenceParams)) {
                    continue;
                }

                ExperimentSequence sequence = new ExperimentSequence();
                sequence.setExperimentId(experiment.getId());
                sequence.setParams(sequenceParams);
                experimentSequenceRepository.save(sequence);
            }
            experiment.setAllSequenceProduced(true);
            experimentRepository.save(experiment);
        }
    }
}
