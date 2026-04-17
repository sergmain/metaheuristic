/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.test.tx;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.batch.BatchCache;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataTxService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.LogDataRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskResetTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 5:29 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TxSupportForTestingService {

    private final Globals globals;
    private final VariableRepository variableRepository;
    private final VariableTxService variableTxService;
    private final ExecContextTaskProducingService execContextTaskProducingService;
    private final ExecContextGraphService execContextGraphService;
    private final FunctionCache functionCache;
    private final FunctionDataTxService functionDataService;
    private final ProcessorCache processorCache;
    private final ExecContextCreatorService execContextCreatorService;
    private final BatchCache batchCache;
    private final ExecContextCache execContextCache;
    private final TaskResetTxService taskResetTxService;
    private final ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache sourceCodeCache;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final LogDataRepository logDataRepository;

    @Transactional
    public LogData saveLog(LogData logData) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return logDataRepository.save(logData);
    }

    @Transactional
    public void deleteLog(Long id) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        logDataRepository.deleteById(id);
    }

    @Transactional(rollbackFor = CommonRollbackException.class)
    public ExecContextCreatorService.ExecContextCreationResult createExecContext(SourceCodeImpl sourceCode, ExecContextApiData.UserExecContext context) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        try {
            return SourceCodeSyncService.getWithSyncForCreation(sourceCode.id,
                    () -> execContextCreatorService.createExecContext(sourceCode, context, null, new ExecContextData.ExecContextCreationInfo("For testing")));
        } catch (CommonRollbackException e) {
            return new ExecContextCreatorService.ExecContextCreationResult(e.messages);
        }
    }

    @Transactional
    public void batchCacheDeleteById(Long batchId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        batchCache.deleteById(batchId);
    }

    @Transactional
    public ai.metaheuristic.ai.dispatcher.beans.Batch batchCacheSave(ai.metaheuristic.ai.dispatcher.beans.Batch batch) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return batchCache.save(batch);
    }

    @Transactional
    public void execContextCacheDeleteById(Long execContextId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        execContextCache.deleteById(execContextId);
    }

    @Transactional
    public void saveProcessor(Processor processor) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        processorCache.save(processor);
    }

    @Transactional
    public void deleteFunctionById(@Nullable Long functionId) {
        if (!globals.testing) {
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
    public void storeOutputVariableWithTaskContextId(Long execContextId, String variableName, String variableData, String taskContextId, Long taskId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        Variable v = variableRepository.findByNameAndTaskContextIdAndExecContextId(variableName, taskContextId, execContextId);
        if (v==null || v.inited) {
            throw new IllegalStateException("(v==null || v.inited), variableName: " + variableName + ", taskContextId: " + taskContextId + ", execContextId: " + execContextId + ", v==null: " + (v==null) + (v!=null ? ", v.inited: " + v.inited + ", v.id: " + v.id : ""));
        }
        byte[] bytes = variableData.getBytes();
        Variable finalV = v;
        VariableSyncService.getWithSyncVoidForCreation(v.id, ()-> variableTxService.storeVariable(new ByteArrayInputStream(bytes), bytes.length, execContextId, taskId, finalV.id));

        v = variableRepository.findByNameAndTaskContextIdAndExecContextId(variableName, taskContextId, execContextId);
        if (v==null) {
            throw new IllegalStateException("(v==null)");
        }
        if (!v.inited) {
            throw new IllegalStateException("(!v.inited)");
        }
    }

    @Transactional
    public ExecContextOperationStatusWithTaskList updateTaskExecState(ExecContextData.ExecContextDAC execContextDAC, Long execContextTaskStateId, Long taskId, EnumsApi.TaskExecState execState, String taskContextId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return execContextGraphService.updateTaskExecState(execContextDAC, execContextTaskStateId, List.of(new TaskData.TaskWithStateAndTaskContextId(taskId, execState, taskContextId)));
    }

    @Transactional
    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasksWithTx(ExecContextData.ExecContextDAC execContextDAC, Long execContextTaskStateId, Long taskId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return execContextGraphService.updateTaskStatesWithResettingAllChildrenTasks(execContextDAC, execContextTaskStateId, taskId);
    }

    @Transactional
    public void setStateForAllChildrenTasksInternal(ExecContextData.ExecContextDAC execContextDAC, Long execContextTaskStateId, Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        execContextGraphService.setStateForAllChildrenTasks(execContextDAC, execContextTaskStateId, taskId, withTaskList, state);
    }

    @Transactional
    public void deleteVariableByName(String name) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        variableRepository.deleteByName(name);
    }

    @Transactional
    public Void deleteByExecContextId(Long execContextId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        variableRepository.deleteByExecContextId(execContextId);
        return null;
    }


    @Transactional
    public void produceAndStartAllTasks(SourceCodeImpl sourceCode, Long execContextId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            throw new IllegalStateException("Need better solution for this state");
        }
        execContextTaskProducingService.produceAndStartAllTasks(sourceCode, execContext);
        execContextCache.save(execContext);
    }

    /**
     * Only for testing
     */
    @Transactional
    public void toStarted(Long execContextId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        ExecContextSyncService.checkWriteLockPresent(execContextId);
        TxUtils.checkTxExists();
        ExecContextSyncService.checkWriteLockPresent(execContextId);
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        execContext.setState(EnumsApi.ExecContextState.STARTED.code);
        execContextCache.save(execContext);
    }

    @Transactional
    public OperationStatusRest addTasksToGraphWithTx(Long execContextId, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return addTasksToGraph(execContextCache.findById(execContextId, true), parentTaskIds, taskIds, EnumsApi.TaskExecState.NONE);
    }

    @Transactional
    public OperationStatusRest addTasksToGraphWithTx(Long execContextId, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds, EnumsApi.TaskExecState initialState) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return addTasksToGraph(execContextCache.findById(execContextId, true), parentTaskIds, taskIds, initialState);
    }

    private OperationStatusRest addTasksToGraph(@Nullable ExecContextImpl execContext, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds, EnumsApi.TaskExecState initialState) {
        TxUtils.checkTxExists();

        if (execContext==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        ExecContextSyncService.checkWriteLockPresent(execContext.id);
        ExecContextData.GraphAndStates graphAndStates = execContextGraphService.prepareGraphAndStates(execContext.execContextGraphId, execContext.execContextTaskStateId);
        OperationStatusRest osr = execContextGraphService.addNewTasksToGraph(graphAndStates, parentTaskIds, taskIds, initialState);
        execContextGraphService.save(graphAndStates);
        return osr;
    }

    @Transactional
    public void deleteSourceCodeById(Long sourceCodeId) {
        sourceCodeCache.deleteById(sourceCodeId);
    }

    /**
     * Reset the specified task and all its descendants to NONE, change ExecContext from FINISHED to STARTED.
     * Only for testing — delegates to TaskResetTxService.
     */
    @Transactional
    public void resetTaskAndDescendants(Long execContextId, Long taskId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        taskResetTxService.resetTaskAndExecContext(execContextId, taskId);
    }

    @Transactional
    public Long createExecContextForVariableStateTest() {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState ecvs = new ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState();
        ecvs.createdOn = System.currentTimeMillis();
        ai.metaheuristic.api.data.exec_context.ExecContextApiData.ExecContextVariableStates info =
                new ai.metaheuristic.api.data.exec_context.ExecContextApiData.ExecContextVariableStates();
        ecvs.updateParams(info);
        ecvs = execContextVariableStateRepository.save(ecvs);

        ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl ec = new ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl();
        ec.sourceCodeId = 1L;
        ec.companyId = 1L;
        ec.accountId = 1L;
        ec.createdOn = System.currentTimeMillis();
        ec.state = ai.metaheuristic.api.EnumsApi.ExecContextState.STARTED.code;
        ec.execContextVariableStateId = ecvs.id;
        ec.execContextGraphId = 0L;
        ec.execContextTaskStateId = 0L;
        ec.setParams("version: 1\nprocesses: []\nvariables:\n  inline: {}\n  inputs: []\n  outputs: []\n");
        ec = execContextCache.save(ec);

        ecvs.execContextId = ec.id;
        execContextVariableStateRepository.save(ecvs);

        return ec.id;
    }

    @Transactional
    public void deleteExecContextForVariableStateTest(Long execContextId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec != null) {
            variableRepository.deleteByExecContextId(execContextId);
            if (ec.execContextVariableStateId != null) {
                execContextVariableStateRepository.deleteById(ec.execContextVariableStateId);
            }
            execContextCache.deleteById(execContextId);
        }
    }

    @Transactional
    public ai.metaheuristic.ai.dispatcher.beans.Variable createInitializedVariable(String name, Long execContextId, String taskContextId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        byte[] data = "test-data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return variableTxService.createInitializedTx(
                new java.io.ByteArrayInputStream(data), data.length, name, null,
                execContextId, taskContextId, ai.metaheuristic.api.EnumsApi.VariableType.text);
    }

    @Transactional
    public ai.metaheuristic.ai.dispatcher.beans.Variable findVariableInAllInternalContexts(String name, String taskContextId, Long execContextId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return variableTxService.findVariableInAllInternalContexts(name, taskContextId, execContextId);
    }

    @Transactional
    public void deleteVariable(Long variableId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        variableRepository.deleteById(variableId);
    }

}
