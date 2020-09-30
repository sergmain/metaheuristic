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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.event.ReconcileStatesEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.data.source_code.SourceCodeApiData.ExecContextForDeletion;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:56 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ExecContextTopLevelService {

    private final ExecContextCache execContextCache;
    private final ExecContextService execContextService;
    private final SourceCodeCache sourceCodeCache;
    private final DispatcherParamsService dispatcherParamsService;
    private final TaskTransactionalService taskTransactionService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final TaskRepository taskRepository;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final TaskProducingService taskProducingService;

    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDesc(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        ExecContextApiData.ExecContextsResult result = execContextService.getExecContextsOrderByCreatedOnDescResult(sourceCodeId, pageable, context);
        result.sourceCodeId = sourceCodeId;
        initInfoAboutSourceCode(sourceCodeId, result);
        return result;
    }

    private void initInfoAboutSourceCode(Long sourceCodeId, ExecContextApiData.ExecContextsResult result) {
        SourceCodeImpl sc = sourceCodeCache.findById(sourceCodeId);
        if (sc != null) {
            result.sourceCodeUid = sc.uid;
            result.sourceCodeValid = sc.valid;
            result.sourceCodeType = getType(sc.uid);
        } else {
            result.sourceCodeUid = "SourceCode was deleted";
            result.sourceCodeValid = false;
            result.sourceCodeType = EnumsApi.SourceCodeType.not_exist;
        }
    }

    private EnumsApi.SourceCodeType getType(String uid) {
        if (dispatcherParamsService.getBatches().contains(uid)) {
            return EnumsApi.SourceCodeType.batch;
        } else if (dispatcherParamsService.getExperiments().contains(uid)) {
            return EnumsApi.SourceCodeType.experiment;
        }
        return EnumsApi.SourceCodeType.common;
    }

    public ExecContextApiData.ExecContextStateResult getExecContextState(Long sourceCodeId, Long execContextId, DispatcherContext context) {
        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult();
        result.sourceCodeId = sourceCodeId;
        initInfoAboutSourceCode(sourceCodeId, result);

        List<TaskData.SimpleTaskInfo> infos = taskTransactionService.getSimpleTaskInfos(execContextId);
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec == null) {
            ExecContextApiData.ExecContextStateResult resultWithError = new ExecContextApiData.ExecContextStateResult();
            resultWithError.addErrorMessage("Can't find execContext for Id " + execContextId);
            return resultWithError;
        }
        List<String> processCodes = ExecContextProcessGraphService.getTopologyOfProcesses(ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(ec.params));
        ExecContextApiData.ExecContextStateResult r = getExecContextStateResult(
                sourceCodeId, infos, processCodes, result.sourceCodeType, result.sourceCodeUid, result.sourceCodeValid);
        return r;
    }

    public static ExecContextApiData.ExecContextStateResult getExecContextStateResult(
            Long sourceCodeId, List<TaskData.SimpleTaskInfo> infos,
            List<String> processCodes, EnumsApi.SourceCodeType sourceCodeType, String sourceCodeUid, boolean sourceCodeValid) {

        ExecContextApiData.ExecContextStateResult r = new ExecContextApiData.ExecContextStateResult();
        r.sourceCodeId = sourceCodeId;
        r.sourceCodeType = sourceCodeType;
        r.sourceCodeUid = sourceCodeUid;
        r.sourceCodeValid = sourceCodeValid;

        Set<String> contexts = new HashSet<>();
        Map<String, List<TaskData.SimpleTaskInfo>> map = new HashMap<>();
        for (TaskData.SimpleTaskInfo info : infos) {
            contexts.add(info.context);
            map.computeIfAbsent(info.context, (o) -> new ArrayList<>()).add(info);
        }
        r.header = processCodes.stream().map(o -> new ExecContextApiData.ColumnHeader(o, o)).toArray(ExecContextApiData.ColumnHeader[]::new);
        r.lines = new ExecContextApiData.LineWithState[contexts.size()];

        List<String> sortedContexts = contexts.stream().sorted(String::compareTo).collect(Collectors.toList());
        for (int i = 0; i < r.lines.length; i++) {
            r.lines[i] = new ExecContextApiData.LineWithState();
        }
        for (ExecContextApiData.LineWithState line : r.lines) {
            line.cells = new ExecContextApiData.StateCell[r.header.length];
            for (int i = 0; i < r.header.length; i++) {
                line.cells[i] = new ExecContextApiData.StateCell();
            }
        }
        for (int i = 0; i < r.lines.length; i++) {
            r.lines[i].context = sortedContexts.get(i);
        }

        for (TaskData.SimpleTaskInfo taskInfo : infos) {
            for (int i = 0; i < r.lines.length; i++) {
                TaskData.SimpleTaskInfo simpleTaskInfo = null;
                List<TaskData.SimpleTaskInfo> tasksInContext = map.get(r.lines[i].context);
                for (TaskData.SimpleTaskInfo contextTaskInfo : tasksInContext) {
                    if (contextTaskInfo.taskId.equals(taskInfo.taskId)) {
                        simpleTaskInfo = contextTaskInfo;
                        break;
                    }
                }
                if (simpleTaskInfo == null) {
                    continue;
                }
                int j = findCol(r.header, simpleTaskInfo.process);
                r.lines[i].cells[j] = new ExecContextApiData.StateCell(simpleTaskInfo.taskId, simpleTaskInfo.state, simpleTaskInfo.context);
            }
        }
        return r;
    }

    private static int findCol(ExecContextApiData.ColumnHeader[] headers, String process) {
        int idx = -1;
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].process == null) {
                if (idx == -1) {
                    idx = i;
                }
                continue;
            }
            if (process.equals(headers[i].process)) {
                return i;
            }
        }
        if (idx == -1) {
            throw new IllegalStateException("(idx==-1)");
        }
        headers[idx].process = process;
        return idx;
    }

    public List<Long> storeAllConsoleResults(List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results) {
        List<Long> ids = new ArrayList<>();
        for (ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            storeExecResult(result);
        }
        return ids;
    }

    public ExecContextForDeletion getExecContextExtendedForDeletion(Long execContextId, DispatcherContext context) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new ExecContextForDeletion("#778.020 execContext wasn't found, execContextId: " + execContextId);
        }
        ExecContextParamsYaml ecpy = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
        ExecContextForDeletion result = new ExecContextForDeletion(execContext.sourceCodeId, execContext.id, ecpy.sourceCodeUid, EnumsApi.ExecContextState.from(execContext.state));
        return result;
    }

    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextResult("#705.180 execContext wasn't found, execContextId: " + execContextId);
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode == null) {
            return new SourceCodeApiData.ExecContextResult("#705.200 sourceCode wasn't found, sourceCodeId: " + execContext.getSourceCodeId());
        }

        if (!sourceCode.getId().equals(execContext.getSourceCodeId())) {
            execContextService.changeValidStatus(execContextId, false);
            return new SourceCodeApiData.ExecContextResult("#705.220 sourceCodeId doesn't match to execContext.sourceCodeId, sourceCodeId: " + execContext.getSourceCodeId() + ", execContext.sourceCodeId: " + execContext.getSourceCodeId());
        }

        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode, execContext);
        return result;
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTaskInExecContext(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned, Long execContextId) {
        DispatcherCommParamsYaml.AssignedTask assignedTask = execContextSyncService.getWithSync(execContextId, ()-> {
            ExecContextImpl execContext = execContextCache.findById(execContextId);
            if (execContext == null) {
                log.error("#705.315Cache doesn't contain ExecContext #{}", execContextId);
                return null;
            }
            if (execContext.state != EnumsApi.ExecContextState.STARTED.code) {
                return null;
            }
            DispatcherCommParamsYaml.AssignedTask task = getTaskAndAssignToProcessor(
                    reportProcessorTaskStatus, processorId, isAcceptOnlySigned, execContextId);
            return task;
        });
        return assignedTask;
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned, Long execContextId) {
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.getTaskAndAssignToProcessor(reportProcessorTaskStatus, processorId, isAcceptOnlySigned, execContextId));
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask findTaskInExecContext(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned) {
        List<Long> execContextIds = execContextRepository.findAllStartedIds();
        for (Long execContextId : execContextIds) {
            DispatcherCommParamsYaml.AssignedTask assignedTask = findTaskInExecContext(reportProcessorTaskStatus, processorId, isAcceptOnlySigned, execContextId);
            if (assignedTask != null) {
                return assignedTask;
            }
        }
        return null;
    }

    public OperationStatusRest changeExecContextState(String state, Long execContextId, DispatcherContext context) {
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.changeExecContextState(state, execContextId, context));
    }

    public OperationStatusRest execContextTargetState(Long execContextId, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.execContextTargetState(execContextId, execState, companyUniqueId));
    }

    public void updateExecContextStatus(Long execContextId, boolean needReconciliation) {
        execContextSyncService.getWithSyncNullable(execContextId, () -> {
            ExecContextImpl execContext = execContextCache.findById(execContextId);
            if (execContext==null) {
                return null;
            }
            long countUnfinishedTasks = execContextGraphTopLevelService.getCountUnfinishedTasks(execContext);
            if (countUnfinishedTasks==0) {
                // workaround for situation when states in graph and db are different
                reconcileStates(execContextId);
                execContext = execContextCache.findById(execContextId);
                if (execContext==null) {
                    return null;
                }
                countUnfinishedTasks = execContextGraphTopLevelService.getCountUnfinishedTasks(execContext);
                if (countUnfinishedTasks==0) {
                    log.info("ExecContext #{} was finished", execContextId);
                    execContextFSM.toFinished(execContextId);
                }
            }
            else {
                if (needReconciliation) {
                    reconcileStates(execContextId);
                }
            }
            return null;
        });
    }

    @Nullable
    private OperationStatusRest checkExecContext(Long execContextId, DispatcherContext context) {
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.400 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }

    public OperationStatusRest resetTask(Long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.080 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }

        return execContextSyncService.getWithSync(task.execContextId, () -> execContextFSM.resetTask(task.execContextId, taskId));
    }

    public EnumsApi.TaskProducingStatus toProducing(Long execContextId) {
        return execContextSyncService.getWithSync(execContextId, () -> execContextFSM.toProducing(execContextId));
    }

    public void storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("Reporting about non-existed task #{}", result.taskId);
            return;
        }
        execContextSyncService.getWithSyncNullable(task.execContextId, () -> {
            execContextFSM.storeExecResult(result);
            return null;
        });
    }

    @Async
    @EventListener
    public void reconcileStates(final ReconcileStatesEvent event) {
        reconcileStates(event.execContextId);
    }

    public void reconcileStates(Long execContextId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }

        // Reconcile states in db and in graph
        List<ExecContextData.TaskVertex> rootVertices = execContextGraphService.findAllRootVertices(execContext);
        if (rootVertices.size()>1) {
            log.error("Too many root vertices, count: " + rootVertices.size());
        }

        if (rootVertices.isEmpty()) {
            return;
        }
        Set<ExecContextData.TaskVertex> vertices = execContextGraphService.findDescendants(execContext, rootVertices.get(0).taskId);

        final Map<Long, TaskData.TaskState> states = getExecStateOfTasks(execContextId);

        Map<Long, TaskData.TaskState> taskStates = new HashMap<>();
        AtomicBoolean isNullState = new AtomicBoolean(false);

        for (ExecContextData.TaskVertex tv : vertices) {

            TaskData.TaskState taskState = states.get(tv.taskId);
            if (taskState==null) {
                isNullState.set(true);
            }
            else if (System.currentTimeMillis()-taskState.updatedOn>5_000 && tv.execState.value!=taskState.execState) {
                log.info("#751.040 Found different states for task #"+tv.taskId+", " +
                        "db: "+ EnumsApi.TaskExecState.from(taskState.execState)+", " +
                        "graph: "+tv.execState);

                if (taskState.execState== EnumsApi.TaskExecState.ERROR.value) {
                    execContextFSM.finishWithError(tv.taskId, execContext.id, null, null);
                }
                else {
                    execContextFSM.updateTaskExecStates(execContext, tv.taskId, taskState.execState, null);
                }
                break;
            }
        }

        if (isNullState.get()) {
            log.info("#751.060 Found non-created task, graph consistency is failed");
            execContextSyncService.getWithSyncNullable(execContext.id,
                    () -> {execContextFSM.toError(execContextId); return null;});
            return;
        }

        final Map<Long, TaskData.TaskState> newStates = getExecStateOfTasks(execContextId);

        // fix actual state of tasks (can be as a result of OptimisticLockingException)
        // fix IN_PROCESSING state
        // find and reset all hanging up tasks
        newStates.entrySet().stream()
                .filter(e-> EnumsApi.TaskExecState.IN_PROGRESS.value==e.getValue().execState)
                .forEach(e->{
                    Long taskId = e.getKey();
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task != null) {
                        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

                        // did this task hang up at processor?
                        if (task.assignedOn!=null && tpy.task.timeoutBeforeTerminate != null && tpy.task.timeoutBeforeTerminate!=0L) {
                            // +2 is for waiting network communications at the last moment. i.e. wait for 4 seconds more
                            final long multiplyBy2 = (tpy.task.timeoutBeforeTerminate + 2) * 2 * 1000;
                            final long oneHourToMills = TimeUnit.HOURS.toMillis(1);
                            long timeout = Math.min(multiplyBy2, oneHourToMills);
                            if ((System.currentTimeMillis() - task.assignedOn) > timeout) {
                                log.info("#751.080 Reset task #{}, multiplyBy2: {}, timeout: {}", task.id, multiplyBy2, timeout);
                                execContextSyncService.getWithSyncNullable(task.execContextId, () -> execContextFSM.resetTask(task.execContextId, task.id));
                            }
                        }
                        else if (task.resultReceived && task.isCompleted) {
                            execContextSyncService.getWithSyncNullable(task.execContextId,
                                    () -> execContextFSM.updateTaskExecStates(execContextCache.findById(execContextId), task.id, EnumsApi.TaskExecState.OK.value, tpy.task.taskContextId));
                        }
                    }
                });
    }

    @NonNull
    private Map<Long, TaskData.TaskState> getExecStateOfTasks(Long execContextId) {
        List<Object[]> list = taskRepository.findAllExecStateByExecContextId(execContextId);

        Map<Long, TaskData.TaskState> states = new HashMap<>(list.size()+1);
        for (Object[] o : list) {
            TaskData.TaskState taskState = new TaskData.TaskState(o);
            states.put(taskState.taskId, taskState);
        }
        return states;
    }

    public void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#317.020 Task obsolete and was already deleted");
            return;
        }

        execContextSyncService.getWithSyncNullable(task.execContextId, () -> {
            execContextFSM.processResendTaskOutputResourceResult(processorId, status, task, variableId);
            return null;
        });
    }

    // TODO 2019.05.19 add reporting of producing of tasks
    // TODO 2020.01.17 reporting to where? do we need to implement it?
    // TODO 2020.09.28 reporting is about dynamically inform a web application about the current status of creating
    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<ExecContextImpl> execContexts = execContextRepository.findByState(EnumsApi.ExecContextState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!execContexts.isEmpty()) {
            log.info("#701.020 Start producing tasks");
        }
        for (ExecContextImpl execContext : execContexts) {
            execContextSyncService.getWithSyncNullable(execContext.id, ()-> {
                SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
                if (sourceCode == null) {
                    execContextFSM.toStopped(execContext.id);
                    return null;
                }
                Monitoring.log("##021", Enums.Monitor.MEMORY);
                log.info("#701.030 Producing tasks for sourceCode.code: {}, input resource pool: \n{}", sourceCode.uid, execContext.getParams());
                taskProducingService.produceAllTasks(true, sourceCode, execContext);
                Monitoring.log("##022", Enums.Monitor.MEMORY);
                return null;
            });
        }
        if (!execContexts.isEmpty()) {
            log.info("#701.040 Producing of tasks was finished");
        }
    }



}
