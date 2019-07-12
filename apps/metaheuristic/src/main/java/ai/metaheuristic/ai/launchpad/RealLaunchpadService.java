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
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.station.StationTopLevelService;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Getter
@Profile("launchpad")
@RequiredArgsConstructor
public class RealLaunchpadService implements LaunchpadService{
    private final StationCache stationCache;
    private final ExperimentService experimentService;
    private final TaskService taskService;
    private final PlanService planService;
    private final ArtifactCleanerAtLaunchpad artifactCleanerAtLaunchpad;
    private final StationTopLevelService stationTopLevelService;
    private final WorkbookService workbookService;
}

