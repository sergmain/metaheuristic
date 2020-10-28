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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForProcessingEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.List;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 8:32 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextTaskAssigningService {

    private final ExecContextCache execContextCache;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;
    private final TaskRepository taskRepository;
    private final TaskProviderService taskProviderService;
    private final ApplicationEventPublisher eventPublisher;

    @Nullable
    @Transactional
    public Void findUnassignedTasksAndAssign(Long execContextId, VariableData.DataStreamHolder holder) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return null;
        }

        final List<ExecContextData.TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(execContext);
        if (vertices.isEmpty()) {
            return null;
        }

        int page = 0;
        List<Long> taskIds;
        RegisterTaskForProcessingEvent event = new RegisterTaskForProcessingEvent();
        while ((taskIds = execContextFSM.getAllByProcessorIdIsNullAndExecContextIdAndIdIn(execContextId, vertices, page++)).size()>0) {
            for (Long taskId : taskIds) {
                TaskImpl task = taskRepository.findById(taskId).orElse(null);
                if (task==null) {
                    continue;
                }
                final TaskParamsYaml taskParamYaml;
                if (task.execState== EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    log.warn("#703.240 task with IN_PROGRESS is here??");
                    continue;
                }
                try {
                    taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                }
                catch (YAMLException e) {
                    log.error("#703.260 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.toString(),task.getParams());
                    execContextTaskFinishingService.finishWithError(task, null);
                    continue;
                }
                if (task.execState!=EnumsApi.TaskExecState.NONE.value) {
                    log.warn("#703.280 Task #{} with function '{}' was already processed with status {}",
                            task.getId(), taskParamYaml.task.function.code, EnumsApi.TaskExecState.from(task.execState));
                    continue;
                }
                // all tasks with internal function will be processed in a different thread
                if (taskParamYaml.task.context == EnumsApi.FunctionExecContext.internal) {
                    log.info("#703.300 start processing an internal function {} for task #{}", taskParamYaml.task.function.code, task.id);
//                    taskWithInternalContextService.processInternalFunction(execContext, task, holder);
                    eventPublisher.publishEvent(new TaskWithInternalContextEvent(execContextId, taskId));
                }
                else {
                    event.tasks.add(new RegisterTaskForProcessingEvent.ExecContextWithTaskIds(execContextId, taskId));
                }
            }
        }
        taskProviderService.registerTask(event);
        return null;
    }



}
