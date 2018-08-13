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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class ExperimentService {

    private final ExperimentsController experimentsController;

    public ExperimentService(ExperimentsController experimentsController) {
        this.experimentsController = experimentsController;
    }


    public Experiment getExperimentAndAssignToStation(String stationId) {

        return null;
    }

    /**
     * this scheduler is being runned at station side
     *
     * long fixedDelay()
     * Execute the annotated method with a fixed period in milliseconds between the end of the last invocation and the start of the next.
     */
    @Scheduled(fixedDelayString = "#{ new Integer(environment.getProperty('aiai.station.request.launchpad.timeout')) > 10 ? new Integer(environment.getProperty('aiai.station.request.launchpad.timeout'))*1000 : 10000 }")
    public void fixedDelayExperimentParticleProducer() {
        Set<String> particles = new LinkedHashSet<>();

    }
}
