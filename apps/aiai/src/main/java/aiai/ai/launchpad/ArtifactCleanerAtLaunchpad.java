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
import aiai.ai.utils.BoolHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
@Slf4j
@Profile("launchpad")
public class ArtifactCleanerAtLaunchpad {

    private final Globals globals;
    private final CleanerTasks cleanerTasks;
    private final FlowInstanceRepository flowInstanceRepository;

    @Service
    @Profile("launchpad")
    public static class CleanerTasks {
        private final TaskRepository taskRepository;

        public CleanerTasks(TaskRepository taskRepository) {
            this.taskRepository = taskRepository;
        }

        @Transactional
        public int cleanTasks(Set<Long> ids, int page, BoolHolder isFound) {
            try (Stream<Object[]> stream = taskRepository.findAllAsTaskSimple(PageRequest.of(page++, 100))) {
                stream
                        .forEach(t -> {
                            isFound.value = true;
                            if (!ids.contains((Long) t[1])) {
                                log.info("Found orphan task.id: {}", t[0]);
                                taskRepository.deleteById((Long) t[0]);
                            }
                        });
            }
            return page;
        }
    }

    public ArtifactCleanerAtLaunchpad(Globals globals, FlowInstanceRepository flowInstanceRepository, TaskRepository taskRepository, CleanerTasks cleanerTasks) {
        this.globals = globals;
        this.flowInstanceRepository = flowInstanceRepository;
        this.cleanerTasks = cleanerTasks;
    }

    public void fixedDelay() {
        if (!globals.isStationEnabled) {
            // don't delete anything until station will receive the list of actual flow instances
            return;
        }

        Set<Long> ids = new HashSet<>();
        flowInstanceRepository.findAll().forEach( o -> ids.add(o.getId()));

        int page = 0;
        final BoolHolder isFound = new BoolHolder();
        do {
            isFound.value = false;
            page = cleanerTasks.cleanTasks(ids, page, isFound);
        } while (isFound.value);
    }

}
