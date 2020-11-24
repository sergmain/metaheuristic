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
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.event.EventSenderService;
import ai.metaheuristic.ai.dispatcher.event.ProcessDeletedExecContextEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskCreatedEvent;
import ai.metaheuristic.ai.dispatcher.event.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:56 PM
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ExecContextTopLevelService {

    private final ExecContextService execContextService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final TaskRepository taskRepository;
    private final ExecContextTaskAssigningService execContextTaskAssigningService;
    private final ExecContextTaskResettingService execContextTaskResettingService;
    private final EventSenderService eventSenderService;
    private final ExecContextStatusService execContextStatusService;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;
    private final ExecContextVariableService execContextVariableService;
    private final TaskSyncService taskSyncService;

    private static boolean isManagerRole(String role) {
        switch (role) {
            case "ROLE_ADMIN":
            case "ROLE_DATA":
            case "MANAGER":
            case "OPERATOR":
                return true;
            default:
                return false;
        }
    }

    public ExecContextApiData.ExecContextStateResult getExecContextState(Long sourceCodeId, Long execContextId, DispatcherContext context, Authentication authentication) {
        boolean managerRole = authentication.getAuthorities().stream().anyMatch(o->isManagerRole(o.getAuthority()));

        ExecContextApiData.RawExecContextStateResult raw = execContextService.getRawExecContextState(sourceCodeId, execContextId, context);
        if (raw.isErrorMessages()) {
            return new ExecContextApiData.ExecContextStateResult(raw.getErrorMessagesAsList());
        }
        ExecContextApiData.ExecContextStateResult r = ExecContextService.getExecContextStateResult(execContextId, raw, managerRole);
        return r;
    }

    public List<Long> storeAllConsoleResults(List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results) {
        List<Long> ids = new ArrayList<>();
        for (ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result : results) {
            Long taskId = storeExecResult(result);
            if (taskId!=null) {
                ids.add(taskId);
            }
        }
        return ids;
    }

    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        SourceCodeApiData.ExecContextResult result = execContextService.getExecContextExtended(execContextId);

        if (result.isErrorMessages()) {
            return result;
        }

        if (!result.sourceCode.getId().equals(result.execContext.getSourceCodeId())) {
            execContextSyncService.getWithSyncNullable(execContextId,
                    ()-> execContextService.changeValidStatus(execContextId, false));
            return new SourceCodeApiData.ExecContextResult("#210.020 sourceCodeId doesn't match to execContext.sourceCodeId, " +
                    "sourceCodeId: " + result.execContext.getSourceCodeId() + ", execContext.sourceCodeId: " + result.execContext.getSourceCodeId());
        }
        return result;
    }

    public void findUnassignedTasksAndRegisterInQueue() {
        List<Long> execContextIds = execContextRepository.findAllStartedIds();
        execContextIds.sort((Comparator.naturalOrder()));
        for (Long execContextId : execContextIds) {
            findTaskForRegisteringInQueue(execContextId);
        }
    }

    public void findTaskForRegisteringInQueue(Long execContextId) {
        try (DataHolder holder = new DataHolder()) {
            execContextSyncService.getWithSyncNullable(execContextId,
                    ()->execContextTaskAssigningService.findUnassignedTasksAndRegisterInQueue(execContextId, holder));
        }
    }

    public OperationStatusRest changeExecContextState(String state, Long execContextId, DispatcherContext context) {
        EnumsApi.ExecContextState execState = EnumsApi.ExecContextState.from(state.toUpperCase());
        if (execState== EnumsApi.ExecContextState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#210.060 Unknown exec state, state: " + state);
        }
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.changeExecContextStateWithTx(execState, execContextId, context.getCompanyId()));
    }

    public OperationStatusRest execContextTargetState(Long execContextId, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        return execContextSyncService.getWithSync(execContextId,
                ()-> execContextFSM.changeExecContextStateWithTx(execState, execContextId, companyUniqueId));
    }

    public void updateExecContextStatus(Long execContextId, boolean needReconciliation) {
        execContextSyncService.getWithSyncNullable(execContextId, () -> execContextFSM.updateExecContextStatus(execContextId, needReconciliation));
    }

    public OperationStatusRest resetTask(Long taskId) {
        Long execContextId = taskRepository.getExecContextId(taskId);
        if (execContextId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#210.080 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }

        return execContextSyncService.getWithSync(execContextId, () -> execContextTaskResettingService.resetTaskWithTx(execContextId, taskId));
    }

    @Nullable
    private Long storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        Long execContextId = taskRepository.getExecContextId(result.taskId);
        if (execContextId==null) {
            log.warn("#210.100 Reporting about non-existed task #{}", result.taskId);
            return null;
        }
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("#303.100 Reporting about non-existed task #{}", result.taskId);
            return null;
        }
        if (task.execState== EnumsApi.TaskExecState.ERROR .value|| task.execState== EnumsApi.TaskExecState.OK.value ){
            return task.id;
        }
        try {
            storeExecResultInternal(result);
        }
        catch (ObjectOptimisticLockingFailureException e) {
            log.warn("#210.105 ObjectOptimisticLockingFailureException as caught, let try to store exec result one more time");
            storeExecResultInternal(result);
        }
        return task.id;
    }

    private void storeExecResultInternal(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        try (DataHolder holder = new DataHolder()) {
            execContextFSM.storeExecResultWithTx(result, holder);
            eventSenderService.sendEvents(holder);
        }
    }

    public void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        switch (status) {
            case SEND_SCHEDULED:
                log.info("#303.380 Processor #{} scheduled sending of output variables of task #{} for sending. This is normal operation of Processor", processorId, taskId);
                break;
            case VARIABLE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:

                execContextSyncService.getWithSyncNullable(taskId,
                        ()-> taskSyncService.getWithSyncNullable(taskId,
                                () -> execContextTaskFinishingService.finishWithErrorWithTx(taskId, "#303.390 Task was finished while resending variable with status " + status)));

                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                taskSyncService.getWithSyncNullable(taskId, ()-> {
                    try (DataHolder holder = new DataHolder()) {
                        UploadResult statusResult = execContextVariableService.updateStatusOfVariable(taskId, variableId, holder);
                        if (statusResult.status == Enums.UploadVariableStatus.OK) {
                            log.info("#303.400 the output resource of task #{} is stored on external storage which was defined by disk://. This is normal operation of sourceCode", taskId);
                        } else {
                            log.info("#303.420 can't update isCompleted field for task #{}", taskId);
                        }
                        eventSenderService.sendEvents(holder);
                    }
                    return null;
                });
                break;
        }
    }

    public void registerCreatedTask(TaskCreatedEvent event) {
        execContextSyncService.getWithSyncNullable(event.taskVariablesInfo.execContextId,
                () -> execContextStatusService.registerCreatedTask(event));
    }

    public void registerVariableState(VariableUploadedEvent event) {
        execContextSyncService.getWithSyncNullable(event.execContextId,
                () -> execContextStatusService.registerVariableState(event));
    }

    public void deleteOrphanExecContexts(List<Long> execContextIds) {
        for (Long execContextId : execContextIds) {
            log.info("Found orphan execContext #{}", execContextId);
            execContextSyncService.getWithSyncNullable(execContextId, ()-> execContextService.deleteExecContext(execContextId));
            eventPublisher.publishEvent(new ProcessDeletedExecContextEvent(execContextId));
        }

    }
}
