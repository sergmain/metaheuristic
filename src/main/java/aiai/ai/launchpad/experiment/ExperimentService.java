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
import aiai.ai.beans.*;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.launchpad.snippet.SnippetVersion;
import aiai.ai.repositories.ExperimentRepository;
import aiai.ai.repositories.ExperimentSequenceRepository;
import aiai.ai.repositories.SnippetRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.util.*;
import java.util.stream.Collectors;

@Service
@EnableTransactionManagement
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentSequenceRepository experimentSequenceRepository;
    private final SnippetRepository snippetRepository;

    private final Yaml yamlSequenceYaml;

    public ExperimentService(ExperimentRepository experimentRepository, ExperimentSequenceRepository experimentSequenceRepository, SnippetRepository snippetRepository) {
        this.experimentRepository = experimentRepository;
        this.experimentSequenceRepository = experimentSequenceRepository;
        this.snippetRepository = snippetRepository;

        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        this.yamlSequenceYaml = new Yaml(new Constructor(SequenceYaml.class), new Representer(), options);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SimpleSnippet {
        public SnippetType type;
        public String code;
        public String filename;
        public String checksum;
    }

    @Data
    public static class SequenceYaml {
        Long datasetId;
        Long experimentId;
        List<SimpleSnippet> snippets;
        Map<String, String> hyperParams;
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

    public String toString(SequenceYaml sequenceYaml) {
        return yamlSequenceYaml.dump(sequenceYaml);
    }

    public SequenceYaml toSequenceYaml(String s) {
        return yamlSequenceYaml.load(s);
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


            Map<String, Snippet> localCache = new HashMap<>();
            for (ExperimentUtils.HyperParams hyperParams : allHyperParams) {
                SequenceYaml yaml = new SequenceYaml();
                yaml.hyperParams = sortHyperParams(hyperParams);
                yaml.experimentId = experiment.getId();
                yaml.datasetId = experiment.getDatasetId();

                List<SimpleSnippet> snippets = new ArrayList<>();
                experiment.sortSnippetsByOrder();
                for (ExperimentSnippet experimentSnippet : experiment.getSnippets()) {
                    SnippetVersion snippetVersion = SnippetVersion.from(experimentSnippet.getSnippetCode());
                    Snippet snippet = localCache.putIfAbsent(experimentSnippet.getSnippetCode(), snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version));
                    if (snippet==null) {
                        System.out.println("Snippet wasn't found for code: " + experimentSnippet.getSnippetCode());
                        continue;
                    }
                    snippets.add(new SimpleSnippet(SnippetType.valueOf(experimentSnippet.getType()), experimentSnippet.getSnippetCode(), snippet.getFilename(), snippet.checksum));
                }
                yaml.snippets = snippets;

                String sequenceParams = toString(yaml);

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
