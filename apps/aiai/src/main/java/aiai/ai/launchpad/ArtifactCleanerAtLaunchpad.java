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
package aiai.ai.launchpad;

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@Profile("launchpad")
public class ArtifactCleanerAtLaunchpad {

    private final Globals globals;
    private final FlowInstanceRepository flowInstanceRepository;
    private final TaskRepository taskRepository;


    public ArtifactCleanerAtLaunchpad(Globals globals, FlowInstanceRepository flowInstanceRepository, TaskRepository taskRepository) {
        this.globals = globals;
        this.flowInstanceRepository = flowInstanceRepository;
        this.taskRepository = taskRepository;
    }

    public void fixedDelay() {
        if (!globals.isStationEnabled) {
            // don't delete anything until station will receive the list of actual flow instances
            return;
        }

        List<FlowInstance> flowInstances = flowInstanceRepository.findAll();
        Set<Long> ids = new HashSet<>();
        for (FlowInstance flowInstance : flowInstances) {
            ids.add(flowInstance.getId());
        }
        Slice<Task> tasks;
        int page = 0;
        while ((tasks = taskRepository.findAll(PageRequest.of(page++, 100))).hasContent()){
            for (Task task : tasks) {
                if (!ids.contains(task.flowInstanceId)) {
                    log.info("Found orphan task.id: {}", task.getId());
                    taskRepository.deleteById(task.getId());
                }
            }
        }
    }
}
