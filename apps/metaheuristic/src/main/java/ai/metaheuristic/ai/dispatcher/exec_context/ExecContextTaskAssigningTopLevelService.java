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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
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
public class ExecContextTaskAssigningTopLevelService {

    private final ExecContextCache execContextCache;
    private final ExecContextFSM execContextFSM;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final TaskRepository taskRepository;
    private final TaskCheckCachingTopLevelService taskCheckCachingTopLevelService;
    private final TaskFinishingService taskFinishingService;
    private final ApplicationEventPublisher eventPublisher;
    // TODO 2021-11-10 see TODO below
//    private final TaskProviderTopLevelService taskProviderService;
//    private final EventPublisherService eventPublisherService;

    public void findUnassignedTasksAndRegisterInQueue(Long execContextId) {
        ExecContextSyncService.checkWriteLockPresent(execContextId);

        final ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return;
        }

        final List<ExecContextData.TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(
                execContext.execContextGraphId, execContext.execContextTaskStateId, true);
        if (vertices.isEmpty()) {
            return;
        }

        int page = 0;
        List<Long> taskIds;
        while ((taskIds = execContextFSM.getAllByProcessorIdIsNullAndExecContextIdAndIdIn(execContextId, vertices, page++)).size()>0) {
            for (Long taskId : taskIds) {
                TaskImpl task = taskRepository.findById(taskId).orElse(null);
                if (task==null) {
                    continue;
                }
                if (task.execState==EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    // this state is occur when the state in graph is NONE or CHECK_CACHE, and the state in DB is IN_PROGRESS
                    if (log.isDebugEnabled()) {
                        try {
                            TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                            if (taskParamYaml.task.context != EnumsApi.FunctionExecContext.internal) {
                                log.warn("#703.020 task #{} with IN_PROGRESS is there? Function: {}", task.id, taskParamYaml.task.function.code);
                            }
                        }
                        catch (Throwable th) {
                            log.warn("#703.040 Error parsing taskParamsYaml, error: " + th.getMessage());
                            log.warn("#703.060 task #{} with IN_PROGRESS is there?", task.id);
                        }
                    }
                    continue;
                }
                final TaskParamsYaml taskParamYaml;
                try {
                    taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                }
                catch (YAMLException e) {
                    log.error("#703.260 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.getMessage(), task.getParams());
                    taskFinishingService.finishWithErrorWithTx(task.id, S.f("#703.260 Task #%s has broken params yaml and will be skipped", task.id));
                    continue;
                }
                if (task.execState == EnumsApi.TaskExecState.NONE.value) {
                    switch(taskParamYaml.task.context) {
                        case external:
                            TaskProviderTopLevelService.registerTask(execContext, task, taskParamYaml);
                            break;
                        case internal:
                            // all tasks with internal function will be processed in a different thread after registering in TaskQueue
                            log.debug("#703.300 start processing an internal function {} for task #{}", taskParamYaml.task.function.code, task.id);
                            TaskProviderTopLevelService.registerInternalTask(execContextId, taskId, taskParamYaml);
                            eventPublisher.publishEvent(new TaskWithInternalContextEvent(execContext.sourceCodeId, execContextId, taskId));
                            break;
                        case long_running:
                            break;
                    }
                }
                else if (task.execState == EnumsApi.TaskExecState.CHECK_CACHE.value) {
                    RegisterTaskForCheckCachingEvent event = new RegisterTaskForCheckCachingEvent(execContextId, taskId);
                    taskCheckCachingTopLevelService.putToQueue(event);
                    // cache will be checked via Scheduler.processCheckCaching()
                }
                else {
                    EnumsApi.TaskExecState state = EnumsApi.TaskExecState.from(task.execState);
                    log.warn("#703.280 Task #{} with function '{}' was already processed with status {}",
                            task.getId(), taskParamYaml.task.function.code, state);
                    // TODO 2021-11-01 actually, this situation must be handled while reconciliation stage
//                    eventPublisherService.publishSetTaskExecStateTxEvent(new SetTaskExecStateTxEvent(task.execContextId, task.id, state));
                }
            }
        }
        TaskProviderTopLevelService.lock(execContextId);
    }
}
