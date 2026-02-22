/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskTxService {

    private final TaskRepository taskRepository;

    public TaskImpl save(TaskImpl task) {
        TxUtils.checkTxExists();

        task.setUpdatedOn( System.currentTimeMillis() );

        if (task.id==null) {
            final TaskImpl t = taskRepository.save(task);
            return t;
        }
        TaskSyncService.checkWriteLockPresent(task.id);
        return task;
    }

    @Transactional(readOnly = true)
    public Map<Long, TaskApiData.TaskState> getExecStateOfTasks(Long execContextId) {
        long mills = System.currentTimeMillis();
        List<Long> ids = taskRepository.findAllTaskIdsByExecContextId(execContextId);
        Map<Long, TaskApiData.TaskState> states = new HashMap<>(ids.size() + 1);
        if (ids.isEmpty()) {
            return states;
        }
        try (Stream<TaskImpl> stream = taskRepository.findByIds(ids)) {
            stream.forEach(t-> {
                long updatedOn = t.updatedOn!=null ? t.updatedOn : 0;
                TaskParamsYaml taskParamsYaml = t.getTaskParamsYaml();
                TaskApiData.TaskState taskState = new TaskApiData.TaskState(
                    t.id, t.execState, updatedOn, taskParamsYaml.task.fromCache, taskParamsYaml.task.taskContextId, taskParamsYaml.task.processCode);
                states.put(taskState.taskId(), taskState);
                log.info("540.045 task #{}, execState={}, taskContextId='{}', processCode='{}'", t.id, t.execState, taskParamsYaml.task.taskContextId, taskParamsYaml.task.processCode);
            });
        }
        log.info("540.040 getExecStateOfTasks() with {} tasks was finished for {} mills", ids.size(), System.currentTimeMillis()-mills);
        return states;
    }

    @Transactional
    public void updateAccessByProcessorOn(Long taskId) {
        taskRepository.updateAccessByProcessorOn(taskId, System.currentTimeMillis());
    }
}
