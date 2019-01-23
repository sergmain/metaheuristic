/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad;

import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.StationsRepository;
import aiai.ai.launchpad.task.TaskService;
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
    private final FlowService flowService;
    private final ArtifactCleanerAtLaunchpad artifactCleanerAtLaunchpad;

    public RealLaunchpadService(StationsRepository stationsRepository, ExperimentService experimentService, TaskService taskService, FlowService flowService, ArtifactCleanerAtLaunchpad artifactCleanerAtLaunchpad) {
        this.stationsRepository = stationsRepository;
        this.experimentService = experimentService;
        this.taskService = taskService;
        this.flowService = flowService;
        this.artifactCleanerAtLaunchpad = artifactCleanerAtLaunchpad;
    }
}

