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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 9/23/2020
 * Time: 11:39 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskTransactionalService {

    private final TaskRepository taskRepository;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskProducingService taskProducingService;
    private final VariableService variableService;
    private final TaskHelperService taskHelperService;

    public TaskImpl initOutputVariables(Long execContextId, TaskImpl task, ExecContextParamsYaml.Process p, TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxExists();

        variableService.initOutputVariables(taskParamsYaml, execContextId, p);
        task.setUpdatedOn( System.currentTimeMillis() );
        task.setParams(TaskParamsYamlUtils.BASE_YAML_UTILS.toString(taskParamsYaml));
        return task;
    }

    public TaskData.ProduceTaskResult produceTaskForProcess(
            ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, ExecContextImpl execContext,
            List<Long> parentTaskIds) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContext.id);

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        // for external Functions internalContextId==process.internalContextId
        TaskImpl t = taskProducingService.createTaskInternal(execContext.id, execContextParamsYaml, process, process.internalContextId,
                execContextParamsYaml.variables.inline);
        if (t == null) {
            return new TaskData.ProduceTaskResult(
                    EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR, "#375.080 Unknown reason of error while task creation");
        }

        TaskImpl task = prepareVariables(execContext, t);
        if (task == null) {
            return new TaskData.ProduceTaskResult(
                    EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR, "#303.640 The task is null after prepareVariables(task)");
        }

        result.taskId = t.getId();
        List<TaskApiData.TaskWithContext> taskWithContexts = List.of(new TaskApiData.TaskWithContext( t.getId(), process.internalContextId));
        execContextGraphService.addNewTasksToGraph(execContext, parentTaskIds, taskWithContexts);

        result.status = EnumsApi.TaskProducingStatus.OK;
        return result;
    }

    /**
     // we dont need to create inputs because all inputs are outputs of previous processes,
     // except globals and startInputAs
     // but we need to initialize descriptor of input variable
     *
     * @param execContext
     * @param task
     * @return
     */
    @Nullable
    public TaskImpl prepareVariables(ExecContextImpl execContext, TaskImpl task) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        TaskParamsYaml taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        final Long execContextId = task.execContextId;
        ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParams.task.processCode);
        if (p==null) {
            log.warn("#303.600 can't find process '"+taskParams.task.processCode+"' in execContext with Id #"+ execContextId);
            return null;
        }

        p.inputs.stream()
                .map(v -> taskHelperService.toInputVariable(v, taskParams.task.taskContextId, execContextId))
                .collect(Collectors.toCollection(()->taskParams.task.inputs));

        return initOutputVariables(execContext.id, task, p, taskParams);
    }


    @Transactional
    public Void deleteOrphanTasks(Long execContextId) {
        List<Long> ids = taskRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId) ;
        if (ids.isEmpty()) {
            return null;
        }
        for (Long id : ids) {
            log.info("Found orphan task #{}, execContextId: #{}", id, execContextId);
            taskRepository.deleteById(id);
        }
        return null;
    }


}
