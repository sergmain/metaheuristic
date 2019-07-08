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

public interface LaunchpadService {
    StationTopLevelService getStationTopLevelService();
    StationCache getStationCache();
    ExperimentService getExperimentService();
    TaskService getTaskService();
    PlanService getPlanService();
    WorkbookService getWorkbookService();

    ArtifactCleanerAtLaunchpad getArtifactCleanerAtLaunchpad();
}
