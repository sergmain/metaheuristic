/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.launchpad;

import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import ai.metaheuristic.ai.launchpad.station.StationTopLevelService;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import lombok.Getter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Getter
@Profile("launchpad")
public class RealLaunchpadService implements LaunchpadService{
    private final StationsRepository stationsRepository;
    private final ExperimentService experimentService;
    private final TaskService taskService;
    private final PlanService planService;
    private final ArtifactCleanerAtLaunchpad artifactCleanerAtLaunchpad;
    private final StationTopLevelService stationTopLevelService;

    public RealLaunchpadService(StationsRepository stationsRepository, ExperimentService experimentService, TaskService taskService, PlanService planService, ArtifactCleanerAtLaunchpad artifactCleanerAtLaunchpad, StationTopLevelService stationTopLevelService) {
        this.stationsRepository = stationsRepository;
        this.experimentService = experimentService;
        this.taskService = taskService;
        this.planService = planService;
        this.artifactCleanerAtLaunchpad = artifactCleanerAtLaunchpad;
        this.stationTopLevelService = stationTopLevelService;
    }
}

