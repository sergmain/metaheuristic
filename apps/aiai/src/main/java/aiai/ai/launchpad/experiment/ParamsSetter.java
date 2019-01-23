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

package aiai.ai.launchpad.experiment;

import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.utils.holders.IntHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

@Service
@Profile("launchpad")
@Slf4j
public class ParamsSetter {

    private final TaskRepository taskRepository;

    public ParamsSetter(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public Set<String> getParamsInTransaction(boolean isPersist, FlowInstance flowInstance, Experiment experiment, IntHolder size) {
        Set<String> taskParams;
        taskParams = new LinkedHashSet<>();

        size.value = 0;
        try (Stream<Object[]> stream = taskRepository.findByFlowInstanceId(flowInstance.getId()) ) {
            stream
                    .forEach(o -> {
                        if (taskParams.contains((String) o[1])) {
                            // delete doubles records
                            log.warn("!!! Found doubles. ExperimentId: {}, hyperParams: {}", experiment.getId(), o[1]);
                            if (isPersist) {
                                taskRepository.deleteById((Long) o[0]);
                            }

                        }
                        taskParams.add((String) o[1]);
                        size.value += ((String) o[1]).length();
                    });
        }
        return taskParams;
    }
}
