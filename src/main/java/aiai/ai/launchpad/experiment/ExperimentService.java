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

import aiai.ai.beans.Experiment;
import aiai.ai.beans.ExperimentHyperParams;
import aiai.ai.beans.ExperimentSequence;
import aiai.ai.repositories.ExperimentRepository;
import aiai.ai.repositories.ExperimentSequenceRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentSequenceRepository experimentSequenceRepository;

    private final Yaml yaml;

    public ExperimentService(ExperimentRepository experimentRepository, ExperimentSequenceRepository experimentSequenceRepository) {
        this.experimentRepository = experimentRepository;
        this.experimentSequenceRepository = experimentSequenceRepository;

        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        this.yaml = new Yaml(options);

    }


    public Experiment getExperimentAndAssignToStation(String stationId) {

        return null;
    }
    public static String toYaml(Yaml yaml, ExperimentUtils.HyperParams hyperParams) {
        if (hyperParams==null) {
            return null;
        }
        String mapYaml;
        mapYaml = yaml.dump(hyperParams.params.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new))
        );
        return mapYaml;
    }


    public static Map<String, String> toMap(List<ExperimentHyperParams> experimentHyperParams, int seed, String epochs) {
        List<ExperimentHyperParams> params = new ArrayList<>();
        ExperimentHyperParams p1 = new ExperimentHyperParams();
        p1.setKey("seed");
        p1.setValues(Integer.toString(seed));
        params.add(p1);

        ExperimentHyperParams p2 = new ExperimentHyperParams();
        p2.setKey("epoch");
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
            
            Set<String> sequnces = new LinkedHashSet<>();

            for (ExperimentSequence experimentSequence : experimentSequenceRepository.findByExperimentId(experiment.getId())) {
                if (sequnces.contains(experimentSequence.getHyperParams())) {
                    // delete doubles records
                    System.out.println("!!! Found doubles. ExperimentId: " + experiment.getId()+", hyperParams: " + experimentSequence.getHyperParams());
                    experimentSequenceRepository.delete(experimentSequence);
                    continue;
                }
                sequnces.add(experimentSequence.getHyperParams());
            }

            Map<String, String> map = ExperimentService.toMap(experiment.getHyperParams(), experiment.getSeed(), experiment.getEpoch());
            List<ExperimentUtils.HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);

            if (experiment.getNumberOfSequence()!=allHyperParams.size()) {
                System.out.println(String.format(
                        "!!! number of sequnce is different. experiment.getNumberOfSequence():  %d, allHyperParams.size(): %d",
                        experiment.getNumberOfSequence(), allHyperParams.size()));
            }

            for (ExperimentUtils.HyperParams hyperParams : allHyperParams) {
                String currSequence = toYaml(yaml, hyperParams);
                if (sequnces.contains(currSequence)) {
                    continue;
                }

//                ... add new ExperimentSequence
            }
        }
    }
}
