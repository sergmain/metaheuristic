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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.QuotasData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.*;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.quotas.QuotasUtils;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static ai.metaheuristic.ai.dispatcher.task.TaskQueue.*;
import static ai.metaheuristic.ai.dispatcher.task.TaskQueueSyncStaticService.checkWriteLockPresent;

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

    private final TaskRepository taskRepository;
    private final ExecContextStatusService execContextStatusService;
    private final ApplicationEventPublisher eventPublisher;
    private final EventPublisherService eventPublisherService;

    /**
     * this Map contains an AtomicLong which contains millisecond value which is specify how long to not use concrete processor
     * if there is any problem with such Processor. After cool-down period of time this processor can be used in processing.
     *
     * key - processorId
     * value - milliseconds when a processor was banned;
     */
    private final Map<Long, AtomicLong> bannedSince = new HashMap<>();

    @Nullable
    @Transactional
    public TaskData.AssignedTask findUnassignedTaskAndAssign(Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned, final DispatcherData.TaskQuotas currentQuotas) {
        checkWriteLockPresent();

        if (TaskQueueService.isQueueEmpty()) {
            return null;
        }

        // Environment of Processor must be initialized before getting any task
        if (psy.env==null) {
            log.error("#317.070 Processor {} has empty env.yaml", processor.id);
            return null;
        }

        AtomicLong longHolder = bannedSince.computeIfAbsent(processor.id, o -> new AtomicLong(0));
        if (longHolder.get() != 0 && System.currentTimeMillis() - longHolder.get() < TimeUnit.MINUTES.toMillis(30)) {
            return null;
        }

        AllocatedTask resultTask = null;
        List<QueuedTask> forRemoving = new ArrayList<>();

        QuotasData.ActualQuota quota = null;
        KeepAliveResponseParamYaml.ExecContextStatus statuses = execContextStatusService.getExecContextStatuses();
        try {
            GroupIterator iter = TaskQueueService.getIterator();
            while (iter.hasNext()) {
                AllocatedTask allocatedTask;
                try {
                    allocatedTask = iter.next();
                } catch (NoSuchElementException e) {
                    log.error("#317.035 TaskGroup was modified, this situation shouldn't be happened.");
                    break;
                }
                QueuedTask queuedTask = allocatedTask.queuedTask;

                // tasks with internal function could not be processed at this point
                // because internal tasks are processed by async events
                // see ai.metaheuristic.ai.dispatcher.task.TaskProviderTransactionalService#registerInternalTask
                if (queuedTask.execContext == EnumsApi.FunctionExecContext.internal) {
                    continue;
                }

                final KeepAliveResponseParamYaml.ExecContextStatus.SimpleStatus simpleStatus = statuses.getStatus(queuedTask.execContextId);
                if (simpleStatus!=null && (simpleStatus.getState() == EnumsApi.ExecContextState.STOPPED || simpleStatus.getState() == EnumsApi.ExecContextState.FINISHED)) {
                    log.warn("#317.036 task #{} in execContext #{} has a status as {}", queuedTask.taskId, queuedTask.execContextId, simpleStatus.getState());
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

                if (!statuses.isStarted(queuedTask.execContextId)) {
                    continue;
                }

                if (queuedTask.task.execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    // this can happened because of async call of StartTaskProcessingTxEvent
                    log.info("#317.039 task #{} already assigned for processing", queuedTask.taskId);
                    continue;
                }

                if (queuedTask.task.execState != EnumsApi.TaskExecState.NONE.value) {
                    log.error("#317.040 Task #{} with function '{}' was already processed with status {}",
                            queuedTask.task.getId(), queuedTask.taskParamYaml.task.function.code, EnumsApi.TaskExecState.from(queuedTask.task.execState));
                    continue;
                }

                // check of git availability
                if (TaskUtils.gitUnavailable(queuedTask.taskParamYaml.task, psy.gitStatusInfo.status != Enums.GitStatus.installed)) {
                    log.warn("#317.060 Can't assign task #{} to processor #{} because this processor doesn't correctly installed git, git status info: {}",
                            processor.id, queuedTask.task.getId(), psy.gitStatusInfo
                    );
                    continue;
                }

                // check of tag
                if (!CollectionUtils.checkTagAllowed(queuedTask.tag, psy.env.tags)) {
                    log.debug("#317.077 Check of !CollectionUtils.checkTagAllowed(queuedTask.tag, psy.env.tags) was failed");
                    continue;
                }

                if (!S.b(queuedTask.taskParamYaml.task.function.env)) {
                    String interpreter = psy.env.getEnvs().get(queuedTask.taskParamYaml.task.function.env);
                    if (interpreter == null) {
                        log.warn("#317.080 Can't assign task #{} to processor #{} because this processor doesn't have defined interpreter for function's env {}",
                                queuedTask.task.getId(), processor.id, queuedTask.taskParamYaml.task.function.env
                        );
                        longHolder.set(System.currentTimeMillis());
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(queuedTask.taskParamYaml.task.function.metas);
                if (psy.os != null && !supportedOS.isEmpty() && !supportedOS.contains(psy.os)) {
                    log.info("#317.100 Can't assign task #{} to processor #{}, " +
                                    "because this processor doesn't support required OS version. processor: {}, function: {}",
                            processor.id, queuedTask.task.getId(), psy.os, supportedOS
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
                ProcessorStatusYaml status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);

                if (notAllFunctionsReady(processor.id, status, queuedTask.taskParamYaml)) {
                    log.debug("#317.123 Processor #{} isn't ready to process task #{}", processor.id, queuedTask.taskId);
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
                        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(queuedTask.task.getParams());
                        //noinspection unused
                        String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, psy.taskParamsVersion);
                    }
                } catch (DowngradeNotSupportedException e) {
                    log.warn("#317.140 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                            queuedTask.task.id, processor.id, psy.taskParamsVersion);
                    longHolder.set(System.currentTimeMillis());
                    resultTask = null;
                }

                if (resultTask != null) {
                    break;
                }
            }
        }
        finally {
            TaskQueueService.removeAll(forRemoving);
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

        // normal way of operation for this Processor
        longHolder.set(0);

        t.setAssignedOn(System.currentTimeMillis());
        t.setProcessorId(processor.id);
        t.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        t.setResultResourceScheduledOn(0);

        taskRepository.save(t);
        resultTask.assigned = true;

        eventPublisherService.publishUnAssignTaskTxEvent(new UnAssignTaskTxEvent(t.execContextId, t.id));
        eventPublisherService.publishStartTaskProcessingTxEvent(new StartTaskProcessingTxEvent(t.execContextId, t.id));

        final AllocatedTask finalResultTask = resultTask;
        if (quota==null) {
            throw new IllegalStateException("(quota==null)");
        }
        final QuotasData.ActualQuota finalQuota = quota;
        eventPublisher.publishEvent(new PostTaskAssigningTxEvent(()->{
            currentQuotas.allocated.add(new DispatcherData.AllocatedQuotas(t.id, finalResultTask.queuedTask.tag, finalQuota.amount));
        }));
        eventPublisher.publishEvent(new PostTaskAssigningRollbackTxEvent (()->{
            finalResultTask.assigned = false;
        }));

        return new TaskData.AssignedTask(t, resultTask.queuedTask.tag, quota.amount);
    }

    @SuppressWarnings("MethodMayBeStatic")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStartTaskProcessingTxEvent(PostTaskAssigningTxEvent event) {
        event.runnable.run();
    }

    @SuppressWarnings("MethodMayBeStatic")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleStartTaskProcessingRollbackTxEvent(PostTaskAssigningRollbackTxEvent event) {
        event.runnable.run();
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
        ProcessorStatusYaml.DownloadStatus ds = status.downloadStatuses.stream().filter(o->o.functionCode.equals(functionConfig.code)).findFirst().orElse(null);

        if (ds==null || ds.functionState!= Enums.FunctionState.ready) {
            log.debug("#317.180 function {} at processor #{} isn't ready, state: {}", functionConfig.code, processorId, ds==null ? "'not prepared yet'" : ds.functionState);
            result.set(true);
        }
    }
}
