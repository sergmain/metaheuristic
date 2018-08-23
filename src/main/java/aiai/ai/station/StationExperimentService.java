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

import aiai.ai.Consts;
import aiai.ai.beans.StationExperimentSequence;
import aiai.ai.repositories.StationExperimentSequenceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StationExperimentService {

    private final StationExperimentSequenceRepository stationExperimentSequenceRepository;

    public StationExperimentService(StationExperimentSequenceRepository stationExperimentSequenceRepository) {
        this.stationExperimentSequenceRepository = stationExperimentSequenceRepository;
    }

    public List<StationExperimentSequence> getForReporting() {
        List<StationExperimentSequence> list = stationExperimentSequenceRepository.findAllByFinishedOnIsNotNullAndIsReportedIsFalse();
        return list;
    }

    public void saveReported(List<StationExperimentSequence> list) {
        stationExperimentSequenceRepository.saveAll(list);
    }

    public boolean isNeedNewExperimentSequence(String stationId) {
        if (stationId==null) {
            return false;
        }
        List<StationExperimentSequence> seqs = stationExperimentSequenceRepository.findAllByFinishedOnIsNull(Consts.PAGE_REQUEST_1_REC);
        return seqs.isEmpty();
    }
}
