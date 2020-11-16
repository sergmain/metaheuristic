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

package ai.metaheuristic.ai.dispatcher.tx;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSyncService;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Blob;
import java.util.List;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 5:29 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TxSupportForTestingService {

    private final Globals globals;
    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final ExecContextService execContextService;
    private final ExecContextTaskProducingService execContextTaskProducingService;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;
    private final TaskRepository taskRepository;
    private final ExecContextVariableService execContextVariableService;
    private final FunctionCache functionCache;
    private final FunctionDataService functionDataService;
    private final ProcessorCache processorCache;
    private final ExecContextCreatorService execContextCreatorService;
    private final SourceCodeSyncService sourceCodeSyncService;

    @Transactional
    public ExecContextCreatorService.ExecContextCreationResult createExecContext(SourceCodeImpl sourceCode, Long companyId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        return sourceCodeSyncService.getWithSyncForCreation(sourceCode.id,
                () -> execContextCreatorService.createExecContext(sourceCode, companyId));
    }

    @Transactional
    public void saveProcessor(Processor processor) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        processorCache.save(processor);
    }

    @Transactional
    public void deleteFunctionById(@Nullable Long functionId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        if (functionId != null) {
            Function f = functionCache.findById(functionId);
            if (f!=null) {
                functionCache.delete(functionId);
                functionDataService.deleteByFunctionCode(f.code);
            }
        }
    }

    @Transactional
    public void storeOutputVariableWithTaskContextId(Long execContextId, String variableName, String variableData, String taskContextId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        SimpleVariable v = variableRepository.findByNameAndTaskContextIdAndExecContextId(variableName, taskContextId, execContextId);
        if (v==null || v.inited) {
            throw new IllegalStateException("(v==null || v.inited)");
        }
        Variable variable = variableRepository.findById(v.id).orElse(null);
        if (variable==null) {
            throw new IllegalStateException("(v==null || v.inited)");
        }
        byte[] bytes = variableData.getBytes();
        variableService.update(new ByteArrayInputStream(bytes), bytes.length, variable);

        v = variableRepository.findByNameAndTaskContextIdAndExecContextId(variableName, taskContextId, execContextId);
        if (v==null) {
            throw new IllegalStateException("(v==null)");
        }
        if (!v.inited) {
            throw new IllegalStateException("(!v.inited)");
        }
    }

    @Transactional
    public ExecContextOperationStatusWithTaskList updateTaskExecState(Long execContextId, Long taskId, EnumsApi.TaskExecState execState, @Nullable String taskContextId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            throw new IllegalStateException("Need better solution for this state");
        }
        return execContextGraphService.updateTaskExecState(execContext, taskId, execState, taskContextId);
    }

    @Transactional
    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasksWithTx(Long execContextId, Long taskId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            throw new IllegalStateException("Need better solution for this state");
        }
        return execContextGraphService.updateGraphWithResettingAllChildrenTasks(execContext, taskId);
    }

    @Transactional
    public void setStateForAllChildrenTasksInternal(Long execContextId, Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            throw new IllegalStateException("Need better solution for this state");
        }
        execContextGraphService.setStateForAllChildrenTasks(execContext, taskId, withTaskList, state);
    }

    @Transactional
    public void deleteVariableByName(String name) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        variableRepository.deleteByName(name);
    }

    @Transactional
    public Void deleteByExecContextId(Long execContextId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        variableRepository.deleteByExecContextId(execContextId);
        return null;
    }

    @Transactional
    public Variable createInitializedWithTx(InputStream is, long size, String variable, @Nullable String filename, Long execContextId, String taskContextId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        return variableService.createInitialized(is, size, variable, filename, execContextId, taskContextId);
    }

    @Transactional
    public Void updateWithTx(InputStream is, long size, Long variableId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        Variable v = variableRepository.findById(variableId).orElse(null);
        if (v==null) {
            throw new IllegalStateException("(v==null)");
        }
        variableService.update(is, size, v);
        return null;
    }

    @Transactional
    public Enums.UploadVariableStatus setVariableReceivedWithTx(Long taskId, Long variableId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        final TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            return Enums.UploadVariableStatus.TASK_NOT_FOUND;
        }
        return execContextVariableService.setVariableReceived(task, variableId);
    }

    @Transactional
    public Void finishWithErrorWithTx(Long taskId, String console, @Nullable String taskContextId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            throw new IllegalStateException("Reporting about non-existed task #" + taskId);
        }
        return execContextTaskFinishingService.finishWithError(task, console, taskContextId);
    }

    @Transactional
    public List<ExecContextData.TaskVertex> findAllWithTx(ExecContextImpl execContext) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        return execContextGraphService.findAll(execContext);
    }

    @Transactional
    public List<ExecContextData.TaskVertex> findAllForAssigningWithTx(ExecContextImpl execContext) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        return execContextGraphService.findAllForAssigning(execContext, true);
    }

    @Transactional
    public void produceAndStartAllTasks(SourceCodeImpl sourceCode, Long execContextId, ExecContextParamsYaml execContextParamsYaml, DataHolder holder) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            throw new IllegalStateException("Need better solution for this state");
        }
        execContextTaskProducingService.produceAndStartAllTasks(sourceCode, execContext, execContextParamsYaml, holder);
    }

    /**
     * Only for testing
     */
    @Transactional
    public void toStarted(Long execContextId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        execContextSyncService.checkWriteLockPresent(execContextId);
        execContextFSM.toState(execContextId, EnumsApi.ExecContextState.STARTED);
    }

    @Nullable
    @Transactional(readOnly = true)
    public Variable getVariableWithData(Long id) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }

        try {
            Variable v = variableRepository.findById(id).orElse(null);
            if (v==null) {
                return null;
            }
            Blob blob = v.getData();
            v.bytes = blob==null ? new byte[0] : blob.getBytes(1, (int) blob.length());
            return v;
        } catch (Throwable th) {
            throw new VariableCommonException("#087.020 Error: " + th.getMessage(), id);
        }
    }

    @Transactional
    public EnumsApi.TaskProducingStatus toProducing(Long execContextId) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            return EnumsApi.TaskProducingStatus.EXEC_CONTEXT_NOT_FOUND_ERROR;
        }
        if (execContext.state == EnumsApi.ExecContextState.PRODUCING.code) {
            return EnumsApi.TaskProducingStatus.OK;
        }
        execContext.setState(EnumsApi.ExecContextState.PRODUCING.code);
        execContextService.save(execContext);
        return EnumsApi.TaskProducingStatus.OK;
    }

    @Transactional
    public OperationStatusRest addTasksToGraphWithTx(Long execContextId, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("Only for testing");
        }
        return addTasksToGraph(execContextService.findById(execContextId), parentTaskIds, taskIds);
    }

    private OperationStatusRest addTasksToGraph(@Nullable ExecContextImpl execContext, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds) {
        TxUtils.checkTxExists();

        if (execContext==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        execContextSyncService.checkWriteLockPresent(execContext.id);
        OperationStatusRest osr = execContextGraphService.addNewTasksToGraph(execContext, parentTaskIds, taskIds, EnumsApi.TaskExecState.NONE);
        return osr;
    }


}
