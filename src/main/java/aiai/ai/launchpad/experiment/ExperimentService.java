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
import aiai.ai.beans.ExperimentMetadata;
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
    public static String toYaml(Yaml yaml, Map<String, Long> producedMap) {
        if (producedMap==null) {
            return null;
        }
        String mapYaml;
        mapYaml = yaml.dump(producedMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new))
        );
        return mapYaml;
    }


    public static Map<String, String> toMap(List<ExperimentMetadata> experimentMetadatas) {
        return experimentMetadatas.stream().collect(Collectors.toMap(ExperimentMetadata::getKey, ExperimentMetadata::getValue, (a, b) -> b, HashMap::new));
    }

    /**
     * this scheduler is being runned at station side
     *
     * long fixedDelay()
     * Execute the annotated method with a fixed period in milliseconds between the end of the last invocation and the start of the next.
     */
    @Scheduled(fixedDelayString = "#{ new Integer(environment.getProperty('aiai.station.request.launchpad.timeout')) > 10 ? new Integer(environment.getProperty('aiai.station.request.launchpad.timeout'))*1000 : 10000 }")
    public void fixedDelayExperimentParticleProducer() {

        for (Experiment experiment : experimentRepository.findByAllSequenceProducedIsFalse()) {

            Set<String> particles = new LinkedHashSet<>();

            for (ExperimentSequence experimentSequence : experimentSequenceRepository.findByExperiment_Id(experiment.getId())) {
                if (particles.contains(experimentSequence.getMeta())) {
                    // delete doubles records
                    System.out.println("!!! Found doubles. ExperimentId: " + experiment.getId()+", meta: " + experimentSequence.getMeta());
                    experimentSequenceRepository.delete(experimentSequence);
                    continue;
                }
                particles.add(experimentSequence.getMeta());
            }

            Map<String, String> map = toMap(experiment.getMetadata());
            List<Map<String, Long>> allPaths = ExperimentUtils.getAllPaths(map);

            for (Map<String, Long> allPath : allPaths) {
                String currParticle = toYaml(yaml, allPath);
                if (particles.contains(currParticle)) {
                    continue;
                }

//                ... add new ExperimentParticle
            }


        }

    }
}
