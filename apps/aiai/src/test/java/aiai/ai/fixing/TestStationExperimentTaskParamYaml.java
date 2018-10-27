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
package aiai.ai.fixing;

import aiai.ai.launchpad.repositories.ExperimentSequenceRepository;
import aiai.ai.station.StationTaskService;
import aiai.ai.yaml.sequence.TaskParamYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class TestStationExperimentTaskParamYaml {


    @Autowired
    public ExperimentSequenceRepository experimentSequenceRepository;

    @Autowired
    public StationTaskService stationTaskService;
    @Autowired
    private TaskParamYamlUtils taskParamYamlUtils;

    @Test
    public void fix() {
/*
        for (StationExperimentSequenceOld seq : stationExperimentSequenceOldRepository.findAll()) {
            StationTask seqTrg = new StationTask();
            BeanUtils.copyProperties(seq, seqTrg);

            final TaskParamYaml sequenceYaml = sequenceYamlUtils.toTaskYaml(seq.getParams());
            final long experimentId = sequenceYaml.experimentId;
            seqTrg.experimentId = experimentId;

            stationExperimentService.save(seqTrg);
        }
*/
    }

}
