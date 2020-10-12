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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherCacheRemoveSourceCodeEvent;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForProcessingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskFinishingService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 3:25 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskProviderTransactionalService {

    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final TaskRepository taskRepository;
    private final DispatcherEventService dispatcherEventService;
    private final TaskService taskService;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;
    private final ExecContextTaskStateService execContextTaskStateService;

    private final LinkedList<Long> taskIds = new LinkedList<>();

    private final Map<Long, AtomicLong> bannedSince = new HashMap<>();

    public void registerTask(Long taskId) {
        taskIds.add(taskId);
    }

    public void deRegisterTask(Long taskId) {
        taskIds.remove(taskId);
    }

    @Nullable
    @Transactional
    public TaskImpl findUnassignedTaskAndAssign(ExecContextImpl execContext, Long processorId, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {

        if (taskIds.isEmpty()) {
            return null;
        }

        AtomicLong longHolder = bannedSince.computeIfAbsent(processorId, o -> new AtomicLong(0));
        if (longHolder.get() != 0 && System.currentTimeMillis() - longHolder.get() < TimeUnit.MINUTES.toMillis(30)) {
            return null;
        }

        TaskImpl resultTask = null;
        for (Long taskId : taskIds) {
            TaskImpl task = taskRepository.findById(taskId).orElse(null);
            if (task == null) {
                continue;
            }
            final TaskParamsYaml taskParamYaml;
            try {
                taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            } catch (YAMLException e) {
                log.error("#303.440 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.toString(), task.getParams());
                execContextTaskFinishingService.finishWithError(task, null);
                continue;
            }
            if (task.execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
                // may be happened because of multi-threaded processing of internal function
                continue;
            }
            if (task.execState != EnumsApi.TaskExecState.NONE.value) {
                log.warn("#303.460 Task #{} with function '{}' was already processed with status {}",
                        task.getId(), taskParamYaml.task.function.code, EnumsApi.TaskExecState.from(task.execState));
                continue;
            }
            // all tasks with internal function will be processed by scheduler
            if (taskParamYaml.task.context == EnumsApi.FunctionExecContext.internal) {
                continue;
            }

            if (TaskUtils.gitUnavailable(taskParamYaml.task, psy.gitStatusInfo.status != Enums.GitStatus.installed)) {
                log.warn("#303.480 Can't assign task #{} to processor #{} because this processor doesn't correctly installed git, git status info: {}",
                        processorId, task.getId(), psy.gitStatusInfo
                );
                longHolder.set(System.currentTimeMillis());
                continue;
            }

            if (!S.b(taskParamYaml.task.function.env)) {
                String interpreter = psy.env.getEnvs().get(taskParamYaml.task.function.env);
                if (interpreter == null) {
                    log.warn("#303.500 Can't assign task #{} to processor #{} because this processor doesn't have defined interpreter for function's env {}",
                            task.getId(), processorId, taskParamYaml.task.function.env
                    );
                    longHolder.set(System.currentTimeMillis());
                    continue;
                }
            }

            final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(taskParamYaml.task.function.metas);
            if (psy.os != null && !supportedOS.isEmpty() && !supportedOS.contains(psy.os)) {
                log.info("#303.520 Can't assign task #{} to processor #{}, " +
                                "because this processor doesn't support required OS version. processor: {}, function: {}",
                        processorId, task.getId(), psy.os, supportedOS
                );
                longHolder.set(System.currentTimeMillis());
                continue;
            }

            if (isAcceptOnlySigned) {
                if (taskParamYaml.task.function.checksumMap == null || taskParamYaml.task.function.checksumMap.keySet().stream().noneMatch(o -> o.isSigned)) {
                    log.warn("#303.540 Function with code {} wasn't signed", taskParamYaml.task.function.getCode());
                    continue;
                }
            }
            resultTask = task;
            // check that downgrading is supported
            try {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, psy.taskParamsVersion);
            } catch (DowngradeNotSupportedException e) {
                log.warn("#303.560 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                        resultTask.getId(), processorId, psy.taskParamsVersion);
                longHolder.set(System.currentTimeMillis());
                resultTask = null;
            }
            if (resultTask != null) {
                break;
            }
        }
        if (resultTask == null) {
            return null;
        }

        // normal way of operation
        longHolder.set(0);

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setProcessorId(processorId);
        resultTask.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        resultTask.setResultResourceScheduledOn(0);
        TaskImpl t = taskService.save(resultTask);

        execContextTaskStateService.updateTaskExecStates(execContext, t, EnumsApi.TaskExecState.IN_PROGRESS, null);
        dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, processorId, resultTask.getId(), execContext.id);

        return t;
    }


}
