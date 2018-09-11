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

import aiai.ai.beans.StationExperimentSequence;
import aiai.ai.repositories.StationExperimentSequenceRepository;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StationExperimentService {

    private final StationExperimentSequenceRepository stationExperimentSequenceRepository;
    private final SequenceProcessor sequenceProcessor;

    public StationExperimentService(StationExperimentSequenceRepository stationExperimentSequenceRepository, SequenceProcessor sequenceProcessor) {
        this.stationExperimentSequenceRepository = stationExperimentSequenceRepository;
        this.sequenceProcessor = sequenceProcessor;
    }

    public List<StationExperimentSequence> getForReporting() {
        List<StationExperimentSequence> list = stationExperimentSequenceRepository.findAllByFinishedOnIsNotNull();
        List<StationExperimentSequence> result = new ArrayList<>();
        for (StationExperimentSequence seq : list) {
            if (!seq.isReported() || (seq.isReported() && !seq.isDelivered() && (seq.getReportedOn()==null || (System.currentTimeMillis() - seq.getReportedOn())>60_000)) ) {
                result.add(seq);
            }
        }
        return result;
    }

    public void saveReported(List<StationExperimentSequence> list) {
        stationExperimentSequenceRepository.saveAll(list);
    }

    public boolean isNeedNewExperimentSequence(String stationId) {
        if (stationId==null) {
            return false;
        }
        List<StationExperimentSequence> seqs = stationExperimentSequenceRepository.findAllByFinishedOnIsNull();
        for (StationExperimentSequence seq : seqs) {
            final SequenceYaml sequenceYaml = SequenceYamlUtils.toSequenceYaml(seq.getParams());
            if (sequenceProcessor.STATE.isStarted(sequenceYaml.experimentId)) {
                return false;
            }

        }
        return true;
    }
}
