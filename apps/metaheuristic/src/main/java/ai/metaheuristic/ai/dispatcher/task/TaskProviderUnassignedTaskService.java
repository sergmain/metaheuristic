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

import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.QuotasData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.events.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.quotas.QuotasUtils;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.functions.FunctionRepositoryDispatcherService;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static ai.metaheuristic.ai.Enums.TaskRejectingStatus.*;
import static ai.metaheuristic.ai.Enums.TaskSearchingStatus.*;
import static ai.metaheuristic.ai.dispatcher.data.ProcessorData.*;

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
    private final TaskRepository taskRepository;
    private final ExecContextStatusService execContextStatusService;
    private final TaskCheckCachingService taskCheckCachingTopLevelService;
    private final FunctionRepositoryDispatcherService functionRepositoryDispatcherService;

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

    public TaskData.TaskSearching findUnassignedTaskAndAssign(ProcessorAndCoreParams processorAndCoreParams, boolean isAcceptOnlySigned, DispatcherData.TaskQuotas currentQuotas) {
        TaskQueueSyncStaticService.checkWriteLockNotPresent();
        TxUtils.checkTxNotExists();

        if (TaskProviderTopLevelService.isQueueEmpty()) {
            return new TaskData.TaskSearching(queue_is_empty);
        }

        // Environment of Processor must be initialized before getting any task
        if (processorAndCoreParams.psy().env==null) {
            log.error("317.070 Processor with core #{} has an empty env.yaml", processorAndCoreParams.coreId());
            return new TaskData.TaskSearching(environment_is_empty);
        }

        AtomicLong longHolder = getBannedSince().computeIfAbsent(processorAndCoreParams.coreId(), o -> new AtomicLong(0));
        if (longHolder.get() != 0 && System.currentTimeMillis() - longHolder.get() < TimeUnit.MINUTES.toMillis(30)) {
            return new TaskData.TaskSearching(core_is_banned);
        }

        TaskQueue.AllocatedTask resultTask = null;
        List<TaskQueue.QueuedTask> forRemoving = new ArrayList<>();

        QuotasData.ActualQuota quota = null;
        TaskData.TaskSearching searching = new TaskData.TaskSearching();
        try {
            TaskQueue.GroupIterator iter = TaskQueueService.getIterator();
            if (!iter.hasNext()) {
                return new TaskData.TaskSearching(iterator_over_queue_is_empty);
            }

            while (iter.hasNext()) {
                TaskQueue.AllocatedTask allocatedTask;
                try {
                    allocatedTask = iter.next();
                } catch (NoSuchElementException e) {
                    log.error("317.035 TaskGroup was modified, this situation shouldn't be happened.");
                    break;
                }
                TaskQueue.QueuedTask queuedTask = allocatedTask.queuedTask;

                // tasks with internal function could not be processed at this point
                // because internal tasks are processed by async events
                // see ai.metaheuristic.ai.dispatcher.task.TaskProviderTransactionalService#registerInternalTask
                if (queuedTask.execContext == EnumsApi.FunctionExecContext.internal) {
                    searching.rejected.put(queuedTask.taskId, internal_task);
                    continue;
                }

                final EnumsApi.ExecContextState execContextState = execContextStatusService.getExecContextState(queuedTask.execContextId);
                if ((execContextState == EnumsApi.ExecContextState.STOPPED || execContextState == EnumsApi.ExecContextState.FINISHED)) {
                    log.warn("317.036 task #{} in execContext #{} has a status as {}", queuedTask.taskId, queuedTask.execContextId, execContextState);
                    forRemoving.add(queuedTask);
                    searching.rejected.put(queuedTask.taskId, exec_context_stopped_or_finished);
                    continue;
                }

                if (queuedTask.task==null || queuedTask.taskParamYaml==null) {
                    // TODO 2021.03.14 this could happened when execContext is deleted while executing of task was active
                    log.warn("""
                            317.037 (queuedTask.task==null || queuedTask.taskParamYaml==null). shouldn't happened,
                            assigned: {}, state: {}
                            taskId: {}, queuedTask.execContext: {}
                            queuedTask.task is null: {}
                            queuedTask.taskParamYaml is null: {}""",
                            allocatedTask.assigned, allocatedTask.state, queuedTask.taskId, queuedTask.execContext, queuedTask.task==null, queuedTask.taskParamYaml==null);
                    searching.rejected.put(queuedTask.taskId, queued_task_or_params_is_null);
                    continue;
                }

                if (!execContextStatusService.isStarted(queuedTask.execContextId)) {
                    searching.rejected.put(queuedTask.taskId, exec_context_not_started);
                    continue;
                }

                if (EnumsApi.TaskExecState.isFinishedState(queuedTask.task.execState)) {
                    log.info("317.040 task #{} already in a finished state as {}", queuedTask.taskId, queuedTask.task.execState);
                    forRemoving.add(queuedTask);
                    searching.rejected.put(queuedTask.taskId, task_was_finished);
                    continue;
                }

                if (queuedTask.task.execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    // this can happened because of async call of StartTaskProcessingTxEvent
                    log.info("317.045 task #{} already assigned for processing", queuedTask.taskId);
                    forRemoving.add(queuedTask);
                    searching.rejected.put(queuedTask.taskId, task_in_progress_already);
                    continue;
                }

                if (queuedTask.task.execState==EnumsApi.TaskExecState.CHECK_CACHE.value) {
                    log.error("317.050 Task #{} with function '{}' is in state CHECK_CACHE",
                            queuedTask.task.getId(), queuedTask.taskParamYaml.task.function.code);
                    taskCheckCachingTopLevelService.putToQueue(new RegisterTaskForCheckCachingEvent(queuedTask.execContextId, queuedTask.taskId));
                    forRemoving.add(queuedTask);
                    searching.rejected.put(queuedTask.taskId, task_for_cache_checking);
                    continue;
                }

                // check of git availability
                if (TaskUtils.gitUnavailable(queuedTask.taskParamYaml.task, processorAndCoreParams.psy().gitStatusInfo.status != EnumsApi.GitStatus.installed)) {
                    log.warn("317.060 Can't assign task #{} to core #{} because this processor doesn't correctly installed git, git status info: {}",
                        processorAndCoreParams.coreId(), queuedTask.task.getId(), processorAndCoreParams.psy().gitStatusInfo
                    );
                    searching.rejected.put(queuedTask.taskId, git_required);
                    continue;
                }

                // check of tag
                if (!CollectionUtils.checkTagAllowed(queuedTask.tag, processorAndCoreParams.csy().tags)) {
                    log.debug("317.077 Check of !CollectionUtils.checkTagAllowed(queuedTask.tag, psy.env.tags) was failed");
                    searching.rejected.put(queuedTask.taskId, tags_arent_allowed);
                    continue;
                }

                if (!S.b(queuedTask.taskParamYaml.task.function.env)) {
                    String interpreter = processorAndCoreParams.psy().env.getEnvs().get(queuedTask.taskParamYaml.task.function.env);
                    if (interpreter == null) {
                        log.error("317.080 Can't assign task #{} to core #{} because this processor doesn't have defined interpreter for function's env {}",
                                queuedTask.task.getId(), processorAndCoreParams.coreId(), queuedTask.taskParamYaml.task.function.env
                        );
                        longHolder.set(System.currentTimeMillis());
                        searching.rejected.put(queuedTask.taskId, interpreter_is_undefined);
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(queuedTask.taskParamYaml.task.function.metas);
                if (processorAndCoreParams.psy().os != null && !supportedOS.isEmpty() && !supportedOS.contains(processorAndCoreParams.psy().os)) {
                    log.info("317.100 Can't assign task #{} to core #{}, " +
                                    "because this processor doesn't support required OS version. processor: {}, function: {}",
                        processorAndCoreParams.coreId(), queuedTask.task.getId(), processorAndCoreParams.psy().os, supportedOS
                    );
                    longHolder.set(System.currentTimeMillis());
                    searching.rejected.put(queuedTask.taskId, not_supported_operating_system);
                    continue;
                }

                if (isAcceptOnlySigned) {
                    if (queuedTask.taskParamYaml.task.function.checksumMap == null || queuedTask.taskParamYaml.task.function.checksumMap.keySet().stream().noneMatch(o -> o.isSigned)) {
                        log.warn("317.120 Function with code {} wasn't signed", queuedTask.taskParamYaml.task.function.getCode());
                        searching.rejected.put(queuedTask.taskId, accept_only_signed);
                        continue;
                    }
                }
                if (notAllFunctionsReady(processorAndCoreParams, queuedTask.taskParamYaml)) {
                    log.debug("317.123 Core #{} isn't ready to process task #{}", processorAndCoreParams.coreId(), queuedTask.taskId);
                    searching.rejected.put(queuedTask.taskId, functions_not_ready);
                    continue;
                }

                quota = QuotasUtils.getQuotaAmount(processorAndCoreParams.psy().env.quotas, queuedTask.tag);

                if (!QuotasUtils.isEnough(processorAndCoreParams.psy().env.quotas, currentQuotas, quota)) {
                    searching.rejected.put(queuedTask.taskId, not_enough_quotas);
                    continue;
                }

                resultTask = allocatedTask;
                // check that downgrading is being supported
                try {
                    ParamsVersion v = YamlForVersioning.getParamsVersion(queuedTask.task.getParams());
                    if (v.getActualVersion()!=processorAndCoreParams.psy().taskParamsVersion) {
                        log.info("317.138 check downgrading is possible, actual version: {}, required version: {}", v.getActualVersion(), processorAndCoreParams.psy().taskParamsVersion);
                        TaskParamsYaml tpy = queuedTask.task.getTaskParamsYaml();
                        //noinspection unused
                        String params = TaskParamsYamlUtils.UTILS.toStringAsVersion(tpy, processorAndCoreParams.psy().taskParamsVersion);
                    }
                } catch (DowngradeNotSupportedException e) {
                    log.warn("317.140 Task #{} can't be assigned to core #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                            queuedTask.task.id, processorAndCoreParams.coreId(), processorAndCoreParams.psy().taskParamsVersion);
                    longHolder.set(System.currentTimeMillis());
                    searching.rejected.put(queuedTask.taskId, downgrade_not_supported);
                    resultTask = null;
                }

                if (queuedTask.task.execState != EnumsApi.TaskExecState.NONE.value) {
                    searching.rejected.put(queuedTask.taskId, task_must_be_in_none_state);
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
            searching.status = task_not_found;
            return searching;
        }

        if (resultTask.queuedTask.task == null) {
            log.error("317.160 (resultTask.queuedTask.task == null). shouldn't happened");
            searching.status = illegal_state;
            return searching;
        }

        TaskImpl t = taskRepository.findById(resultTask.queuedTask.task.id).orElse(null);
        if (t==null) {
            log.warn("317.180 Can't assign task #{}, task doesn't exist", resultTask.queuedTask.task.id);
            searching.status = task_doesnt_exist;
            return searching;
        }
        if (t.execState!= EnumsApi.TaskExecState.NONE.value) {
            log.warn("317.200 Can't assign task #{}, task state isn't NONE, actual: {}", t.id, EnumsApi.TaskExecState.from(t.execState));
            searching.status = task_isnt_in_none_state;
            return searching;
        }
        if (quota==null) {
            throw new IllegalStateException("(quota==null)");
        }

        final TaskQueue.AllocatedTask resultTaskFinal = resultTask;
        final QuotasData.ActualQuota quotaFinal = quota;

        searching.task = TaskSyncService.getWithSyncNullable(resultTask.queuedTask.task.id,
            () -> taskProviderTransactionalService.assignTaskToCore(processorAndCoreParams.coreId(), currentQuotas, resultTaskFinal, quotaFinal));

        if (searching.task==null) {
            searching.status = task_assigning_was_failed;
        }

        return searching;
    }


    private static boolean notAllFunctionsReady(ProcessorAndCoreParams processorAndCoreParams, TaskParamsYaml taskParamYaml) {
        AtomicBoolean result = new AtomicBoolean(false);
        notAllFunctionsReadyInternal(processorAndCoreParams, taskParamYaml.task.function, result);
        for (TaskParamsYaml.FunctionConfig preFunction : taskParamYaml.task.preFunctions) {
            notAllFunctionsReadyInternal(processorAndCoreParams, preFunction, result);
        }
        for (TaskParamsYaml.FunctionConfig postFunction : taskParamYaml.task.postFunctions) {
            notAllFunctionsReadyInternal(processorAndCoreParams, postFunction, result);
        }
        return result.get();
    }

    private static void notAllFunctionsReadyInternal(ProcessorAndCoreParams processorAndCoreParams, TaskParamsYaml.FunctionConfig functionConfig, AtomicBoolean result) {
        boolean b = FunctionRepositoryDispatcherService.isProcessorReady(functionConfig.code, processorAndCoreParams.processorId());

        if (!b) {
            log.debug("317.240 function {} at processor #{} isn't ready.", functionConfig.code, processorAndCoreParams.processorId());
            result.set(true);
        }
    }

    private static void notAllFunctionsReadyInternal_old(Long processorId, ProcessorStatusYaml status, TaskParamsYaml.FunctionConfig functionConfig, AtomicBoolean result) {
/*
        EnumsApi.FunctionState state = status.functions.get(functionConfig.code);

        if (state != EnumsApi.FunctionState.ready) {
            log.debug("317.240 function {} at processor #{} isn't ready, state: {}", functionConfig.code, processorId, state==null ? "'not prepared yet'" : state);
            result.set(true);
        }
*/
    }
}
