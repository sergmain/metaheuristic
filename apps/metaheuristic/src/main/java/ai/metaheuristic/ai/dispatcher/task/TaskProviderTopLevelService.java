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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.MetaheuristicThreadLocal;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.QuotasData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.*;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextReadinessStateService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor_core.ProcessorCoreCache;
import ai.metaheuristic.ai.dispatcher.quotas.QuotasUtils;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 5:38 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskProviderTopLevelService {

    private final Globals globals;
    private final TaskRepository taskRepository;
    private final ProcessorCache processorCache;
    private final ProcessorCoreCache processorCoreCache;
    private final ExecContextStatusService execContextStatusService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ExecContextCache execContextCache;
    private final ExecContextReadinessStateService execContextReadinessStateService;
    private final ExecContextService execContextService;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskProviderUnassignedTaskTopLevelService taskProviderUnassignedTaskTopLevelService;
    private final VariableService variableService;

    public void registerTask(ExecContextImpl execContext, Long taskId) {
        TaskQueueSyncStaticService.getWithSyncVoid(()-> {
            if (TaskQueueService.alreadyRegistered(taskId)) {
                return;
            }
            final TaskImpl task = taskRepository.findById(taskId).orElse(null);
            if (task == null) {
                log.warn("#393.040 Can't register task #{}, task doesn't exist", taskId);
                return;
            }
            final TaskParamsYaml taskParamYaml;
            try {
                taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            } catch (YAMLException e) {
                String es = S.f("#393.080 Task #%s has broken params yaml and will be skipped, error: %s, params:\n%s", task.getId(), e.toString(), task.getParams());
                log.error(es, e.getMessage());
                eventPublisher.publishEvent(new TaskFinishWithErrorEvent(task.id, es));
                return;
            }
            if (taskParamYaml.task.context== EnumsApi.FunctionExecContext.internal) {
                registerInternalTaskWithoutSync(execContext.id, taskId, taskParamYaml);
                eventPublisher.publishEvent(new TaskWithInternalContextEvent(execContext.sourceCodeId, execContext.id, taskId));
            }
            else {
                registerTaskLambda(execContext, task, taskParamYaml);
            }
        });
    }

    public static boolean registerTask(final ExecContextImpl execContext, TaskImpl task, final TaskParamsYaml taskParamYaml) {
        return TaskQueueSyncStaticService.getWithSync(()-> {
            if (TaskQueueService.alreadyRegistered(task.id)) {
                return false;
            }
            registerTaskLambda(execContext, task, taskParamYaml);
            return true;
        });
    }

    private static void registerTaskLambda(final ExecContextImpl ec, TaskImpl task, final TaskParamsYaml taskParamYaml) {
        final ExecContextParamsYaml execContextParamsYaml = ec.getExecContextParamsYaml();

        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParamYaml.task.processCode);
        if (p==null) {
            log.warn("#393.120 Can't register task #{}, process {} doesn't exist in execContext #{}", task.id, taskParamYaml.task.processCode, ec.id);
            return;
        }

        final TaskQueue.QueuedTask queuedTask = new TaskQueue.QueuedTask(EnumsApi.FunctionExecContext.external, task.execContextId, task.id, task, taskParamYaml, p.tag, p.priority);
        TaskQueueService.addNewTask(queuedTask);
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Async
    @EventListener
    public void processDeletedExecContext(TaskQueueCleanByExecContextIdEvent event) {
        try {
            TaskQueueSyncStaticService.getWithSyncVoid(()-> TaskQueueService.deleteByExecContextId(event.execContextId));
        } catch (Throwable th) {
            log.error("#393.160 Error, need to investigate ", th);
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Async
    @EventListener
    public void processStartTaskProcessing(StartTaskProcessingEvent event) {
        try {
            TaskQueueSyncStaticService.getWithSyncVoid(()-> TaskQueueService.startTaskProcessing(event));
        } catch (Throwable th) {
            log.error("#393.200 Error, need to investigate ", th);
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Async
    @EventListener
    public void processUnAssignTaskEvent(UnAssignTaskEvent event) {
        try {
            TaskQueueSyncStaticService.getWithSyncVoid(()-> TaskQueueService.unAssignTask(event));
        } catch (Throwable th) {
            log.error("#393.240 Error, need to investigate ", th);
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Async
    @EventListener
    public void deregisterTasksByExecContextId(DeregisterTasksByExecContextIdEvent event) {
        try {
            TaskQueueSyncStaticService.getWithSyncVoid(()->TaskQueueService.deleteByExecContextId(event.execContextId));
        } catch (Throwable th) {
            log.error("#393.280 Error, need to investigate ", th);
        }
    }

    public static void deregisterTask(Long execContextId, Long taskId) {
        TaskQueueSyncStaticService.getWithSyncVoid(()->TaskQueueService.deRegisterTask(execContextId, taskId));
    }

    @Nullable
    public static TaskQueue.TaskGroup getFinishedTaskGroup(Long execContextId) {
        return TaskQueueSyncStaticService.getWithSync(()-> TaskQueueService.getFinishedTaskGroup(execContextId));
    }

    @Nullable
    public static TaskQueue.TaskGroup getTaskGroupForTransfering(Long execContextId) {
        return TaskQueueSyncStaticService.getWithSync(()-> TaskQueueService.getTaskGroupForTransfering(execContextId));
    }

    public static boolean allTaskGroupFinished(Long execContextId) {
        return TaskQueueSyncStaticService.getWithSync(()-> TaskQueueService.allTaskGroupFinished(execContextId));
    }

    @Nullable
    public static TaskQueue.AllocatedTask getTaskExecState(Long execContextId, Long taskId) {
        return TaskQueueSyncStaticService.getWithSync(()-> TaskQueueService.getTaskExecState(execContextId, taskId));
    }

    public static Map<Long, TaskQueue.AllocatedTask> getTaskExecStates(Long execContextId) {
        return TaskQueueSyncStaticService.getWithSync(()-> TaskQueueService.getTaskExecStates(execContextId));
    }

    public static void lock(Long execContextId) {
        TaskQueueSyncStaticService.getWithSyncVoid(()-> TaskQueueService.lock(execContextId));
    }

    public static boolean registerInternalTask(Long execContextId, Long taskId, TaskParamsYaml taskParamYaml) {
        return TaskQueueSyncStaticService.getWithSync(()-> registerInternalTaskWithoutSync(execContextId, taskId, taskParamYaml));
    }

    private static boolean registerInternalTaskWithoutSync(Long execContextId, Long taskId, TaskParamsYaml taskParamYaml) {
        if (TaskQueueService.alreadyRegistered(taskId)) {
            return false;
        }
        TaskQueueService.addNewInternalTask(execContextId, taskId, taskParamYaml);
        return true;
    }

    @Async
    @EventListener
    public void setTaskExecStateInQueue(SetTaskExecStateEvent event) {
        try {
            setTaskExecStateInQueue(event.execContextId, event.taskId, event.state);
        } catch (Throwable th) {
            log.error("#393.320 Error, need to investigate ", th);
        }
    }

    public void setTaskExecStateInQueue(Long execContextId, Long taskId, EnumsApi.TaskExecState state) {
        log.debug("#393.360 set task #{} as {}", taskId, state);
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        TaskQueueSyncStaticService.getWithSyncVoid(()-> {
            boolean b = TaskQueueService.setTaskExecState(execContextId, taskId, state);
            log.debug("#393.400 task #{}, state: {}, result: {}", taskId, state, b);
            if (b) {
                applicationEventPublisher.publishEvent(new TransferStateFromTaskQueueToExecContextEvent(
                        execContextId, execContext.execContextGraphId, execContext.execContextTaskStateId));
            }
        });
    }

    private static final ConcurrentHashMap<Long, AtomicLong> coreCheckedOn = new ConcurrentHashMap<>();

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTask(Long coreId, boolean isAcceptOnlySigned) {
        if (!globals.isTesting()) {
            throw new IllegalStateException("(!globals.isTesting())");
        }
        return findTask(coreId, isAcceptOnlySigned, new DispatcherData.TaskQuotas(0), List.of(), false);
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTask(Long coreId, boolean isAcceptOnlySigned, DispatcherData.TaskQuotas quotas, List<Long> taskIds, boolean queueEmpty) {
        TxUtils.checkTxNotExists();

        if (queueEmpty) {
            AtomicLong mills = coreCheckedOn.computeIfAbsent(coreId, o -> new AtomicLong());
            final boolean b = System.currentTimeMillis() - mills.get() < 60_000;
            log.debug("#393.445 queue is empty, suspend finding of new tasks: {}", b );
            if (b) {
                return null;
            }
            mills.set(System.currentTimeMillis());
        }

        final ProcessorCore core = processorCoreCache.findById(coreId);
        if (core == null) {
            log.error("#393.440 Core #{} wasn't found", coreId);
            return null;
        }
        if (core.processorId==null) {
            log.info("#393.442 Core #{} wasn't linked with Processor", coreId);
            return null;
        }
        CoreStatusYaml csy = toCoreStatusYaml(core);
        if (csy==null) {
            return null;
        }

        final Processor processor = processorCache.findById(core.processorId);
        if (processor == null) {
            log.error("#393.445 Processor #{} wasn't found", core.processorId);
            return null;
        }

        ProcessorStatusYaml psy = toProcessorStatusYaml(processor);
        if (psy==null) {
            return null;
        }

        DispatcherCommParamsYaml.AssignedTask assignedTask =
                MetaheuristicThreadLocal.getExecutionStat().getNullable("findTask -> getTaskAndAssignToProcessor()",
                        ()-> getTaskAndAssignToProcessor(core.id, psy, csy, isAcceptOnlySigned, quotas, taskIds));

        if (assignedTask!=null && log.isDebugEnabled()) {
            TaskImpl task = taskRepository.findById(assignedTask.taskId).orElse(null);
            if (task==null) {
                log.debug("#393.480 findTask(), task #{} wasn't found", assignedTask.taskId);
            }
            else {
                log.debug("#393.520 findTask(), task id: #{}, ver: {}, task: {}", task.id, task.version, task);
            }
        }
        return assignedTask;
    }

    @Nullable
    private static ProcessorStatusYaml toProcessorStatusYaml(Processor processor) {
        ProcessorStatusYaml ss;
        try {
            ss = processor.getProcessorStatusYaml();
            return ss;
        } catch (Throwable e) {
            log.error("#393.560 Error parsing current status of processor:\n{}", processor.getStatus());
            log.error("#393.570 Error ", e);
            return null;
        }
    }

    @Nullable
    private static CoreStatusYaml toCoreStatusYaml(ProcessorCore core) {
        CoreStatusYaml ss;
        try {
            ss = core.getCoreStatusYaml();
            return ss;
        } catch (Throwable e) {
            log.error("#393.580 Error parsing current status of core:\n{}", core.getStatus());
            log.error("#393.585 Error ", e);
            return null;
        }
    }

    @Nullable
    private DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(
            Long coreId, ProcessorStatusYaml psy, CoreStatusYaml csy, boolean isAcceptOnlySigned, DispatcherData.TaskQuotas quotas, List<Long> taskIds) {
        TxUtils.checkTxNotExists();

        final TaskData.AssignedTask task =
                MetaheuristicThreadLocal.getExecutionStat().getNullable("getTaskAndAssignToProcessor -> getTaskAndAssignToProcessorInternal()",
                        ()-> getTaskAndAssignToProcessorInternal(coreId, psy, csy, isAcceptOnlySigned, quotas, taskIds));

        // task won't be returned for an internal function
        if (task==null) {
            return null;
        }
        try {
            String params;
            try {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.task.getParams());

                for (TaskParamsYaml.InputVariable input : tpy.task.inputs) {
                    SimpleVariable sv = variableService.getVariableAsSimple(input.id);
                    if (sv==null) {
                        final String es = "#211.120 Can't find a variable " + input.id;
                        log.error(es);
                        eventPublisher.publishEvent(new TaskFinishWithErrorEvent(task.task.id, es));
                        return null;
                    }
                    input.empty = sv.nullified;
                }
                params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, psy.taskParamsVersion);

            } catch (DowngradeNotSupportedException e) {
                // TODO 2020-09-26 there is a possible situation when a check in ExecContextFSM.findUnassignedTaskAndAssign() would be ok
                //  but this one fails. that could occur because of prepareVariables(task);
                //  need a better solution for checking
                log.warn("#393.600 Task #{} can't be assigned to core #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                        task.task.getId(), coreId, psy.taskParamsVersion);
                return null;
            }

            // because we were already being provided with task that means that execContext was started
            return new DispatcherCommParamsYaml.AssignedTask(params, task.task.getId(), task.task.getExecContextId(), EnumsApi.ExecContextState.STARTED, task.tag, task.quota);

        } catch (Throwable th) {
            String es = "#393.640 Something wrong";
            log.error(es, th);
            throw new IllegalStateException(es, th);
        }
    }

    @Nullable
    private TaskData.AssignedTask getTaskAndAssignToProcessorInternal(
            Long coreId, ProcessorStatusYaml psy, CoreStatusYaml csy, boolean isAcceptOnlySigned, DispatcherData.TaskQuotas quotas, List<Long> taskIds) {

        TxUtils.checkTxNotExists();

        // find all tasks which were assigned to this core. and try to assign one again
        List<Object[]> tasks = taskRepository.findExecStateByCoreId(coreId);
        for (Object[] obj : tasks) {
            Long taskId = ((Number)obj[0]).longValue();
            int execState = ((Number)obj[1]).intValue();
            Long execContextId = ((Number)obj[2]).longValue();

            if (!execContextStatusService.isStarted(execContextId) || execContextReadinessStateService.isNotReady(execContextId)) {
                continue;
            }
            // processor's core has lost a task, so we'll try to assign it again
            if (!taskIds.contains(taskId)) {
                if (execState==EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    log.warn("#393.680 already assigned task, core: #{}, task #{}, task execStatus: {}",
                            coreId, taskId, EnumsApi.TaskExecState.from(execState));
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task!=null) {
                        if (psy.env==null) {
                            log.error("#393.720 Core #{} has empty env.yaml", coreId);
                            return null;
                        }
                        ExecContextImpl ec =  execContextService.findById(execContextId);
                        if (ec==null) {
                            log.warn("#393.750 Can't re-assign task #{}, execContext #{} doesn't exist", taskId, execContextId);
                            continue;
                        }
                        final ExecContextParamsYaml execContextParamsYaml = ec.getExecContextParamsYaml();

                        final TaskParamsYaml taskParamYaml;
                        try {
                            taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                        } catch (YAMLException e) {
                            String es = S.f("#393.780 Task #%s has broken params yaml and will be finished with error, error: %s, params:\n%s", task.getId(), e.toString(), task.getParams());
                            log.error(es, e.getMessage());
                            eventPublisher.publishEvent(new TaskFinishWithErrorEvent(task.id, es));
                            continue;
                        }

                        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParamYaml.task.processCode);
                        if (p==null) {
                            String es = S.f("#393.810 Can't register task #%s, process %s doesn't exist in execContext #%s", taskId, taskParamYaml.task.processCode, execContextId);
                            log.warn("#393.815 Can't register task #{}, process {} doesn't exist in execContext #{}", taskId, taskParamYaml.task.processCode, execContextId);
                            eventPublisher.publishEvent(new TaskFinishWithErrorEvent(task.id, es));
                            continue;
                        }

                        QuotasData.ActualQuota quota = QuotasUtils.getQuotaAmount(psy.env.quotas, p.tag);

                        if (!QuotasUtils.isEnough(psy.env.quotas, quotas, quota)) {
                            log.warn("#393.840 Not enough quotas, start re-setting task #{}, execContext #{}", taskId, execContextId);
                            eventPublisher.publishEvent(new ResetTaskEvent(ec.id, task.id));
                            continue;
                        }

                        quotas.allocated.add(new DispatcherData.AllocatedQuotas(task.id, p.tag, quota.amount));

                        return new TaskData.AssignedTask(task, p.tag, quota.amount);
                    }
                }
            }
        }

        // there isn't any lost task which could be assigned, let's find unassigned task

        TaskData.AssignedTask result =
                MetaheuristicThreadLocal.getExecutionStat().getNullable("getTaskAndAssignToProcessorInternal -> findUnassignedTaskAndAssign()",
                        ()-> taskProviderUnassignedTaskTopLevelService.findUnassignedTaskAndAssign(coreId, psy, csy, isAcceptOnlySigned, quotas));

        return result;
    }


}
