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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.TaskCreationException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskService taskService;
    private final TaskProducingService taskProducingService;
    private final VariableService variableService;

    @Nullable
    public TaskImpl initOutputVariables(ExecContextImpl execContext, Long taskId, ExecContextParamsYaml.Process p, TaskParamsYaml taskParamsYaml) {
        variableService.initOutputVariables(taskParamsYaml, execContext, p);
        return setParams(taskId, taskParamsYaml);
    }

    @Transactional
    public TaskData.ProduceTaskResult produceTaskForProcess(
            boolean isPersist, Long sourceCodeId, ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, Long execContextId,
            List<Long> parentTaskIds) {

        execContextSyncService.checkWriteLockPresent(execContextId);

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        if (isPersist) {
            try {
                // for external Functions internalContextId==process.internalContextId
                TaskImpl t = taskProducingService.createTaskInternal(execContextId, execContextParamsYaml, process, process.internalContextId,
                        execContextParamsYaml.variables.inline);
                if (t == null) {
                    result.status = EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR;
                    result.error = "#375.080 Unknown reason of error while task creation";
                    return result;
                }
                result.taskId = t.getId();
                List<TaskApiData.TaskWithContext> taskWithContexts = List.of(new TaskApiData.TaskWithContext( t.getId(), process.internalContextId));
                execContextGraphService.addNewTasksToGraph(execContextCache.findById(execContextId), parentTaskIds, taskWithContexts);
            } catch (TaskCreationException e) {
                result.status = EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR;
                result.error = "#375.100 task creation error " + e.getMessage();
                log.error(result.error);
                return result;
            }
        }
        result.numberOfTasks=1;
        result.status = EnumsApi.TaskProducingStatus.OK;
        return result;
    }

    public List<TaskData.SimpleTaskInfo> getSimpleTaskInfos(Long execContextId) {
        try (Stream<TaskImpl> stream = taskRepository.findAllByExecContextIdAsStream(execContextId)) {
            return stream.map(o-> {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params);
                return new TaskData.SimpleTaskInfo(o.id,  EnumsApi.TaskExecState.from(o.execState).toString(), tpy.task.taskContextId, tpy.task.processCode, tpy.task.function.code);
            }).collect(Collectors.toList());
        }
    }

    @Nullable
    public TaskImpl setParams(Long taskId, TaskParamsYaml params) {
        return setParams(taskId, TaskParamsYamlUtils.BASE_YAML_UTILS.toString(params));
    }

    @Nullable
    public TaskImpl setParams(Long taskId, String taskParams) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#307.082 Can't find Task for Id: {}", taskId);
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        return setParamsInternal(taskId, taskParams, task);
    }

    @Nullable
    private TaskImpl setParamsInternal(Long taskId, String taskParams, @Nullable TaskImpl task) {
        TxUtils.checkTxExists();
        try {
            if (task == null) {
                log.warn("#307.010 Task with taskId {} wasn't found", taskId);
                return null;
            }
            task.setParams(taskParams);
            taskService.save(task);
            return task;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#307.020 !!!NEED TO INVESTIGATE. Error set setParams to {}, taskId: {}, error: {}", taskParams, taskId, e.toString());
        }
        return null;
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
