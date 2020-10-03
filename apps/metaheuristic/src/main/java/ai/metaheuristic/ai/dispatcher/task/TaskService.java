/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ExecContextSyncService execContextSyncService;

    public DispatcherCommParamsYaml.ResendTaskOutputs resourceReceivingChecker(long processorId) {
        List<Task> tasks = taskRepository.findForMissingResultResources(processorId, System.currentTimeMillis(), EnumsApi.TaskExecState.OK.value);
        DispatcherCommParamsYaml.ResendTaskOutputs result = new DispatcherCommParamsYaml.ResendTaskOutputs();
        for (Task task : tasks) {
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                result.resends.add( new DispatcherCommParamsYaml.ResendTaskOutput(task.getId(), output.id));
            }
        }
        return result;
    }

    public TaskImpl save(TaskImpl task) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        try {
/*
            if (log.isDebugEnabled()) {
                log.debug("#462.010 save task, id: #{}, ver: {}, task: {}", task.id, task.version, task);
                try {
                    throw new RuntimeException("task stacktrace");
                }
                catch(RuntimeException e) {
                    log.debug("task stacktrace", e);
                }
            }
*/
            return taskRepository.save(task);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Current task:\n" + task+"\ntask in db: " + (task.id!=null ? taskRepository.findById(task.id) : null));
            throw e;
        }
    }


}
