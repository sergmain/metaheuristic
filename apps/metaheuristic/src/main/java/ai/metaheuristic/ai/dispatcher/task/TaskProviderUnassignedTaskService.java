/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.QuotasData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.events.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.quotas.QuotasUtils;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Serge
 * Date: 12/1/2021
 * Time: 12:35 AM
 */
@SuppressWarnings("LombokGetterMayBeUsed")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskProviderUnassignedTaskService {

    private final TaskProviderTransactionalService taskProviderTransactionalService;
    private final DispatcherEventService dispatcherEventService;
    private final TaskRepository taskRepository;
    private final ExecContextStatusService execContextStatusService;
    private final TaskCheckCachingService taskCheckCachingTopLevelService;

    @Nullable
    public TaskData.AssignedTask findUnassignedTaskAndAssign(Long coreId, ProcessorStatusYaml psy, CoreStatusYaml csy, boolean isAcceptOnlySigned, DispatcherData.TaskQuotas quotas) {
        TxUtils.checkTxNotExists();

        if (TaskQueueService.isQueueEmptyWithSync()) {
            return null;
        }

        TaskData.AssignedTask task = findUnassignedTaskAndAssignInternal(coreId, psy, csy, isAcceptOnlySigned, quotas);

        if (task!=null) {
            dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, coreId, task.task.id, task.task.execContextId, null, null);
        }
        return task;
    }

    /**
     * this Map contains an AtomicLong which contains millisecond value which is specify how long to not use concrete processor
     * if there is any problem with such Processor. After cool-down period of time this processor can be used in processing.
     *
     * key - processorId
     * value - milliseconds when a processor was banned;
     */
    private final Map<Long, AtomicLong> bannedSince = new HashMap<>();

    public Map<Long, AtomicLong> getBannedSince() {
        return bannedSince;
    }

    @SuppressWarnings("TextBlockMigration")
    @Nullable
    private TaskData.AssignedTask findUnassignedTaskAndAssignInternal(
            Long coreId, ProcessorStatusYaml psy, CoreStatusYaml csy, boolean isAcceptOnlySigned, final DispatcherData.TaskQuotas currentQuotas) {
        TaskQueueSyncStaticService.checkWriteLockNotPresent();

        if (TaskQueueSyncStaticService.getWithSync(TaskQueueService::isQueueEmpty)) {
            return null;
        }

        // Environment of Processor must be initialized before getting any task
        if (psy.env==null) {
            log.error("#317.070 Processor {} has empty env.yaml", coreId);
            return null;
        }

        AtomicLong longHolder = getBannedSince().computeIfAbsent(coreId, o -> new AtomicLong(0));
        if (longHolder.get() != 0 && System.currentTimeMillis() - longHolder.get() < TimeUnit.MINUTES.toMillis(30)) {
            return null;
        }

        TaskQueue.AllocatedTask resultTask = null;
        List<TaskQueue.QueuedTask> forRemoving = new ArrayList<>();

        QuotasData.ActualQuota quota = null;
        ExecContextData.ExecContextStates statuses = execContextStatusService.getExecContextStatuses();
        try {
            TaskQueue.GroupIterator iter = TaskQueueService.getIterator();
            while (iter.hasNext()) {
                TaskQueue.AllocatedTask allocatedTask;
                try {
                    allocatedTask = iter.next();
                } catch (NoSuchElementException e) {
                    log.error("#317.035 TaskGroup was modified, this situation shouldn't be happened.");
                    break;
                }
                TaskQueue.QueuedTask queuedTask = allocatedTask.queuedTask;

                // tasks with internal function could not be processed at this point
                // because internal tasks are processed by async events
                // see ai.metaheuristic.ai.dispatcher.task.TaskProviderTransactionalService#registerInternalTask
                if (queuedTask.execContext == EnumsApi.FunctionExecContext.internal) {
                    continue;
                }

                final EnumsApi.ExecContextState execContextState = execContextStatusService.getExecContextState(queuedTask.execContextId);
                if ((execContextState == EnumsApi.ExecContextState.STOPPED || execContextState == EnumsApi.ExecContextState.FINISHED)) {
                    log.warn("#317.036 task #{} in execContext #{} has a status as {}", queuedTask.taskId, queuedTask.execContextId, execContextState);
                    forRemoving.add(queuedTask);
                    continue;
                }

                if (queuedTask.task==null || queuedTask.taskParamYaml==null) {
                    // TODO 2021.03.14 this could happened when execContext is deleted while executing of task was active
                    log.warn("#317.037 (queuedTask.task==null || queuedTask.taskParamYaml==null). shouldn't happened,\n" +
                                    "assigned: {}, state: {}\n" +
                                    "taskId: {}, queuedTask.execContext: {}\n" +
                                    "queuedTask.task is null: {}\n" +
                                    "queuedTask.taskParamYaml is null: {}",
                            allocatedTask.assigned, allocatedTask.state, queuedTask.taskId, queuedTask.execContext, queuedTask.task==null, queuedTask.taskParamYaml==null);
                    continue;
                }

                if (!execContextStatusService.isStarted(queuedTask.execContextId)) {
                    continue;
                }

                if (EnumsApi.TaskExecState.isFinishedState(queuedTask.task.execState)) {
                    log.info("#317.040 task #{} already in a finished state as {}", queuedTask.taskId, queuedTask.task.execState);
                    forRemoving.add(queuedTask);
                    continue;
                }

                if (queuedTask.task.execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    // this can happened because of async call of StartTaskProcessingTxEvent
                    log.info("#317.045 task #{} already assigned for processing", queuedTask.taskId);
                    forRemoving.add(queuedTask);
                    continue;
                }

                if (queuedTask.task.execState==EnumsApi.TaskExecState.CHECK_CACHE.value) {
                    log.error("#317.050 Task #{} with function '{}' is in state CHECK_CACHE",
                            queuedTask.task.getId(), queuedTask.taskParamYaml.task.function.code);
                    taskCheckCachingTopLevelService.putToQueue(new RegisterTaskForCheckCachingEvent(queuedTask.execContextId, queuedTask.taskId));
                    forRemoving.add(queuedTask);
                    continue;
                }

                // check of git availability
                if (TaskUtils.gitUnavailable(queuedTask.taskParamYaml.task, psy.gitStatusInfo.status != Enums.GitStatus.installed)) {
                    log.warn("#317.060 Can't assign task #{} to core #{} because this processor doesn't correctly installed git, git status info: {}",
                            coreId, queuedTask.task.getId(), psy.gitStatusInfo
                    );
                    continue;
                }

                // check of tag
                if (!CollectionUtils.checkTagAllowed(queuedTask.tag, csy.tags)) {
                    log.debug("#317.077 Check of !CollectionUtils.checkTagAllowed(queuedTask.tag, psy.env.tags) was failed");
                    continue;
                }

                if (!S.b(queuedTask.taskParamYaml.task.function.env)) {
                    String interpreter = psy.env.getEnvs().get(queuedTask.taskParamYaml.task.function.env);
                    if (interpreter == null) {
                        log.warn("#317.080 Can't assign task #{} to core #{} because this processor doesn't have defined interpreter for function's env {}",
                                queuedTask.task.getId(), coreId, queuedTask.taskParamYaml.task.function.env
                        );
                        longHolder.set(System.currentTimeMillis());
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(queuedTask.taskParamYaml.task.function.metas);
                if (psy.os != null && !supportedOS.isEmpty() && !supportedOS.contains(psy.os)) {
                    log.info("#317.100 Can't assign task #{} to core #{}, " +
                                    "because this processor doesn't support required OS version. processor: {}, function: {}",
                            coreId, queuedTask.task.getId(), psy.os, supportedOS
                    );
                    longHolder.set(System.currentTimeMillis());
                    continue;
                }

                if (isAcceptOnlySigned) {
                    if (queuedTask.taskParamYaml.task.function.checksumMap == null || queuedTask.taskParamYaml.task.function.checksumMap.keySet().stream().noneMatch(o -> o.isSigned)) {
                        log.warn("#317.120 Function with code {} wasn't signed", queuedTask.taskParamYaml.task.function.getCode());
                        continue;
                    }
                }
                if (notAllFunctionsReady(coreId, psy, queuedTask.taskParamYaml)) {
                    log.debug("#317.123 Core #{} isn't ready to process task #{}", coreId, queuedTask.taskId);
                    continue;
                }

                quota = QuotasUtils.getQuotaAmount(psy.env.quotas, queuedTask.tag);

                if (!QuotasUtils.isEnough(psy.env.quotas, currentQuotas, quota)) {
                    continue;
                }

                resultTask = allocatedTask;
                // check that downgrading is being supported
                try {
                    ParamsVersion v = YamlForVersioning.getParamsVersion(queuedTask.task.getParams());
                    if (v.getActualVersion()!=psy.taskParamsVersion) {
                        log.info("#317.138 check downgrading is possible, actual version: {}, required version: {}", v.getActualVersion(), psy.taskParamsVersion);
                        TaskParamsYaml tpy = queuedTask.task.getTaskParamsYaml();
                        //noinspection unused
                        String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, psy.taskParamsVersion);
                    }
                } catch (DowngradeNotSupportedException e) {
                    log.warn("#317.140 Task #{} can't be assigned to core #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                            queuedTask.task.id, coreId, psy.taskParamsVersion);
                    longHolder.set(System.currentTimeMillis());
                    resultTask = null;
                }

                if (queuedTask.task.execState != EnumsApi.TaskExecState.NONE.value) {
                    continue;
                }

                if (resultTask != null) {
                    break;
                }
            }
        }
        finally {
            if (!forRemoving.isEmpty()) {
                TaskQueueSyncStaticService.getWithSyncVoid(() -> TaskQueueService.removeAll(forRemoving));
            }
        }

        if (resultTask == null) {
            return null;
        }

        if (resultTask.queuedTask.task == null) {
            log.error("#317.160 (resultTask.queuedTask.task == null). shouldn't happened");
            return null;
        }

        TaskImpl t = taskRepository.findById(resultTask.queuedTask.task.id).orElse(null);
        if (t==null) {
            log.warn("#317.180 Can't assign task #{}, task doesn't exist", resultTask.queuedTask.task.id);
            return null;
        }
        if (t.execState!= EnumsApi.TaskExecState.NONE.value) {
            log.warn("#317.200 Can't assign task #{}, task state isn't NONE, actual: {}", t.id, EnumsApi.TaskExecState.from(t.execState));
            return null;
        }
        if (quota==null) {
            throw new IllegalStateException("(quota==null)");
        }

        final TaskQueue.AllocatedTask resultTaskFinal = resultTask;
        final QuotasData.ActualQuota quotaFinal = quota;
        return TaskSyncService.getWithSyncNullable(resultTask.queuedTask.task.id,
                ()->taskProviderTransactionalService.findUnassignedTaskAndAssign(coreId, currentQuotas, resultTaskFinal, quotaFinal));
    }


    private static boolean notAllFunctionsReady(Long processorId, ProcessorStatusYaml status, TaskParamsYaml taskParamYaml) {
        AtomicBoolean result = new AtomicBoolean(false);
        notAllFunctionsReadyInternal(processorId, status, taskParamYaml.task.function, result);
        for (TaskParamsYaml.FunctionConfig preFunction : taskParamYaml.task.preFunctions) {
            notAllFunctionsReadyInternal(processorId, status, preFunction, result);
        }
        for (TaskParamsYaml.FunctionConfig postFunction : taskParamYaml.task.postFunctions) {
            notAllFunctionsReadyInternal(processorId, status, postFunction, result);
        }
        return result.get();
    }

    private static void notAllFunctionsReadyInternal(Long processorId, ProcessorStatusYaml status, TaskParamsYaml.FunctionConfig functionConfig, AtomicBoolean result) {
        EnumsApi.FunctionState state = status.functions.entrySet().stream()
                .filter(o->o.getKey().equals(functionConfig.code))
                .findFirst()
                .map(Map.Entry::getValue).orElse(null);

        if (state != EnumsApi.FunctionState.ready) {
            log.debug("#317.240 function {} at processor #{} isn't ready, state: {}", functionConfig.code, processorId, state==null ? "'not prepared yet'" : state);
            result.set(true);
        }
    }
}
