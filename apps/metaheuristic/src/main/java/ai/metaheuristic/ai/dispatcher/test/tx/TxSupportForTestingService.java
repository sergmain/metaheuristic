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
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataTxService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
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
    private final ExecContextTaskResettingService execContextTaskResettingService;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache sourceCodeCache;

    @Transactional(rollbackFor = CommonRollbackException.class)
    public ExecContextCreatorService.ExecContextCreationResult createExecContext(SourceCodeImpl sourceCode, ExecContextApiData.UserExecContext context) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        try {
            return SourceCodeSyncService.getWithSyncForCreation(sourceCode.id,
                    () -> execContextCreatorService.createExecContext(sourceCode, context, null));
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
            throw new IllegalStateException("(v==null || v.inited)");
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
     * Reset all tasks in the ExecContext to NONE state and change ExecContext from FINISHED to STARTED.
     * Only for testing — simulates the storeObjectiveAndResetTask flow from RgObjectiveTxService.
     */
    @Transactional
    public void resetAllTasksToNone(Long execContextId, Long execContextTaskStateId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        ExecContextSyncService.checkWriteLockPresent(execContextId);
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskStateId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec == null) {
            return;
        }

        // Change ExecContext state from FINISHED to STARTED
        if (ec.state == EnumsApi.ExecContextState.FINISHED.code) {
            ec.setState(EnumsApi.ExecContextState.STARTED.code);
            ec.setCompletedOn(null);
            execContextCache.save(ec);
            log.info("resetAllTasksToNone: ExecContext #{} state changed from FINISHED to STARTED", execContextId);
        }

        // Reset every task in the DAG to NONE
        java.util.List<ExecContextData.TaskVertex> allVertices = execContextGraphService.findAll(ec.execContextGraphId);
        for (ExecContextData.TaskVertex vertex : allVertices) {
            TaskSyncService.getWithSyncVoid(vertex.taskId, () ->
                    execContextTaskResettingService.resetTask(ec, vertex.taskId, EnumsApi.TaskExecState.NONE));
        }

        // Update graph state
        ExecContextTaskState execContextTaskState = execContextTaskStateRepository.findById(execContextTaskStateId).orElse(null);
        if (execContextTaskState == null) {
            log.error("resetAllTasksToNone: ExecContextTaskState #{} not found", execContextTaskStateId);
            return;
        }
        ExecContextTaskStateParamsYaml stateParams = execContextTaskState.getExecContextTaskStateParamsYaml();
        for (ExecContextData.TaskVertex vertex : allVertices) {
            stateParams.states.put(vertex.taskId, EnumsApi.TaskExecState.NONE);
        }
        execContextTaskState.updateParams(stateParams);
        execContextTaskStateRepository.save(execContextTaskState);
        log.info("resetAllTasksToNone: Reset {} tasks to NONE", allVertices.size());
    }


}
