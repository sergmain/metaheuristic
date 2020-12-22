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

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.TaskCreatedEvent;
import ai.metaheuristic.ai.dispatcher.event.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
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
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final ExecContextTaskAssigningService execContextTaskAssigningService;
    private final ExecContextTaskResettingService execContextTaskResettingService;
    private final ExecContextStatusService execContextStatusService;
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
        boolean managerRole = authentication.getAuthorities().stream().anyMatch(o -> isManagerRole(o.getAuthority()));

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
            if (taskId != null) {
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
                    () -> execContextService.changeValidStatus(execContextId, false));
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
        execContextSyncService.getWithSyncNullable(execContextId,
                () -> findUnassignedTasksAndRegisterInQueueInternal(execContextId));
    }

    private Void findUnassignedTasksAndRegisterInQueueInternal(Long execContextId) {
        return execContextTaskAssigningService.findUnassignedTasksAndRegisterInQueue(execContextId);
    }

    public OperationStatusRest changeExecContextState(String state, Long execContextId, DispatcherContext context) {
        EnumsApi.ExecContextState execState = EnumsApi.ExecContextState.from(state.toUpperCase());
        if (execState == EnumsApi.ExecContextState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#210.060 Unknown exec state, state: " + state);
        }
        return execContextSyncService.getWithSync(execContextId,
                () -> execContextFSM.changeExecContextStateWithTx(execState, execContextId, context.getCompanyId()));
    }

    public OperationStatusRest execContextTargetState(Long execContextId, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        return execContextSyncService.getWithSync(execContextId,
                () -> execContextFSM.changeExecContextStateWithTx(execState, execContextId, companyUniqueId));
    }

    public void updateExecContextStatus(Long execContextId, boolean needReconciliation) {
        execContextSyncService.getWithSyncNullable(execContextId, () -> execContextFSM.updateExecContextStatus(execContextId, needReconciliation));
    }

    public OperationStatusRest resetTask(Long taskId) {
        Long execContextId = taskRepository.getExecContextId(taskId);
        if (execContextId == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#210.080 Can't re-run task " + taskId + ", task with such taskId wasn't found");
        }

        return execContextSyncService.getWithSync(execContextId, () -> execContextTaskResettingService.resetTaskWithTx(execContextId, taskId));
    }

    @Nullable
    private Long storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task == null) {
            log.warn("#303.100 Reporting about non-existed task #{}", result.taskId);
            return null;
        }

        ExecContextImpl execContext = execContextCache.findById(task.execContextId);
        if (execContext == null) {
            log.warn("#210.110 Reporting about non-existed execContext #{}", task.execContextId);
            return null;
        }
        if (task.execState == EnumsApi.TaskExecState.ERROR.value || task.execState == EnumsApi.TaskExecState.OK.value) {
            return task.id;
        }
        try {
            taskSyncService.getWithSyncNullable(task.id,
                    () -> storeExecResultInternal(result));
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("#210.115 ObjectOptimisticLockingFailureException as caught, let try to store exec result one more time");
            taskSyncService.getWithSyncNullable(task.id,
                    () -> storeExecResultInternal(result));
        }
        return task.id;
    }

    private Void storeExecResultInternal(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        execContextFSM.storeExecResultWithTx(result);
        return null;
    }

    public void registerCreatedTask(TaskCreatedEvent event) {
        execContextSyncService.getWithSyncNullable(event.taskVariablesInfo.execContextId,
                () -> execContextStatusService.registerCreatedTask(event));
    }

    public void registerVariableState(VariableUploadedEvent event) {
        execContextSyncService.getWithSyncNullable(event.execContextId,
                () -> registerVariableStateInternal(event));
    }

    private Void registerVariableStateInternal(VariableUploadedEvent event) {
        return execContextStatusService.registerVariableState(event);
    }

    public void deleteOrphanExecContexts(Collection<Long> execContextIds) {
        for (Long execContextId : execContextIds) {
            log.info("Found orphan execContext #{}", execContextId);

            execContextSyncService.getWithSyncNullable(execContextId, ()-> execContextService.deleteExecContext(execContextId));
        }

    }

    public OperationStatusRest deleteExecContextById(Long execContextId, DispatcherContext context) {
        return execContextSyncService.getWithSync(execContextId, ()-> execContextService.deleteExecContextById(execContextId, context));
    }
}
