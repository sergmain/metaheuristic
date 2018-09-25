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
package aiai.ai.station;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.station.beans.StationExperimentSequence;
import aiai.ai.station.repositories.StationExperimentSequenceRepository;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class ArtifactCleaner {

    private final StationExperimentSequenceRepository stationExperimentSequenceRepository;
    private final SequenceYamlUtils sequenceYamlUtils;
    private final SequenceProcessor sequenceProcessor;
    private final Globals globals;

    public ArtifactCleaner(StationExperimentSequenceRepository stationExperimentSequenceRepository, SequenceYamlUtils sequenceYamlUtils, SequenceProcessor sequenceProcessor, Globals globals) {
        this.stationExperimentSequenceRepository = stationExperimentSequenceRepository;
        this.sequenceYamlUtils = sequenceYamlUtils;
        this.sequenceProcessor = sequenceProcessor;
        this.globals = globals;
    }

    public void fixedDelay() {
        if (!globals.isStationEnabled || !sequenceProcessor.STATE.isInit) {
            // don't delete anything until station will receive the list of actual experiments
            return;
        }

        for (StationExperimentSequence seq : stationExperimentSequenceRepository.findAll()) {
            final SequenceYaml sequenceYaml = sequenceYamlUtils.toSequenceYaml(seq.getParams());
            if (sequenceProcessor.STATE.isState(sequenceYaml.experimentId, Enums.ExperimentExecState.DOESNT_EXIST)) {
                log.info("Delete obsolete sequence {}", seq);
                stationExperimentSequenceRepository.deleteById(seq.getId());
            }
        }

        try {
            Files.newDirectoryStream(globals.stationExperimentDir.toPath()).forEach((Path path) -> {
                        final File file = path.toFile();
                        if (file.isFile()) {
                            log.warn("Found file {} in {}, should be directoru only", file.getPath());
                            return;
                        }
                        int experimentId = Integer.parseInt(file.getName());
                        if (sequenceProcessor.STATE.getState(experimentId) != null) {
                            return;
                        }
                        log.warn("Start deleting dir {}", file.getPath());
                        FileUtils.deleteQuietly(file);
                    }
            );
        }
        catch (IOException e) {
            log.error("Erorr", e);
            throw new RuntimeException("erorr", e);
        }
    }

}
