/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final EntityManager em;

    @Transactional(readOnly = true, propagation = Propagation.NEVER)
    public DispatcherCommParamsYaml.ResendTaskOutputs variableReceivingChecker(Long processorId) {
        List<Task> tasks = taskRepository.findForMissingResultVariables(processorId, System.currentTimeMillis(), EnumsApi.TaskExecState.OK.value);
        DispatcherCommParamsYaml.ResendTaskOutputs result = new DispatcherCommParamsYaml.ResendTaskOutputs();
        for (Task task : tasks) {
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                if (!output.uploaded) {
                    result.resends.add(new DispatcherCommParamsYaml.ResendTaskOutput(task.getId(), output.id));
                }
            }
        }
        return result;
    }

    public TaskImpl save(TaskImpl task) {
        TxUtils.checkTxExists();

        task.setUpdatedOn( System.currentTimeMillis() );

        if (task.id==null) {
            final TaskImpl t = taskRepository.save(task);
            return t;
        }
        TaskSyncService.checkWriteLockPresent(task.id);

        if (!em.contains(task) ) {
//            https://stackoverflow.com/questions/13135309/how-to-find-out-whether-an-entity-is-detached-in-jpa-hibernate
            throw new IllegalStateException(S.f("Bean %s isn't managed by EntityManager", task));
        }
        return task;
    }


    @Transactional
    public void updateAccessByProcessorOn(Long taskId) {
        taskRepository.updateAccessByProcessorOn(taskId, System.currentTimeMillis());
    }
}
