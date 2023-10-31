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

package ai.metaheuristic.ai.dispatcher.test.tx;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.batch.BatchCache;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataTxService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
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

    @Transactional
    public ExecContextCreatorService.ExecContextCreationResult createExecContext(SourceCodeImpl sourceCode, ExecContextData.UserExecContext context) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return SourceCodeSyncService.getWithSyncForCreation(sourceCode.id,
                () -> execContextCreatorService.createExecContext(sourceCode, context, null));
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
        return execContextGraphService.updateTaskExecState(execContextDAC, execContextTaskStateId, taskId, execState, taskContextId);
    }

    @Transactional
    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasksWithTx(Long execContextGraphId, Long execContextTaskStateId, Long taskId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return execContextGraphService.updateGraphWithResettingAllChildrenTasks(execContextGraphId, execContextTaskStateId, taskId);
    }

    @Transactional
    public void setStateForAllChildrenTasksInternal(Long execContextGraphId, Long execContextTaskStateId, Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        execContextGraphService.setStateForAllChildrenTasks(execContextGraphId, execContextTaskStateId, taskId, withTaskList, state);
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
    public EnumsApi.TaskProducingStatus toProducing(Long execContextId) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return EnumsApi.TaskProducingStatus.EXEC_CONTEXT_NOT_FOUND_ERROR;
        }
        if (execContext.state == EnumsApi.ExecContextState.PRODUCING.code) {
            return EnumsApi.TaskProducingStatus.OK;
        }
        execContext.setState(EnumsApi.ExecContextState.PRODUCING.code);
        execContextCache.save(execContext);
        return EnumsApi.TaskProducingStatus.OK;
    }

    @Transactional
    public OperationStatusRest addTasksToGraphWithTx(Long execContextId, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds) {
        if (!globals.testing) {
            throw new IllegalStateException("Only for testing");
        }
        return addTasksToGraph(execContextCache.findById(execContextId, true), parentTaskIds, taskIds);
    }

    private OperationStatusRest addTasksToGraph(@Nullable ExecContextImpl execContext, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds) {
        TxUtils.checkTxExists();

        if (execContext==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        ExecContextSyncService.checkWriteLockPresent(execContext.id);
        ExecContextData.GraphAndStates graphAndStates = execContextGraphService.prepareGraphAndStates(execContext.execContextGraphId, execContext.execContextTaskStateId);
        OperationStatusRest osr = execContextGraphService.addNewTasksToGraph(graphAndStates, parentTaskIds, taskIds, EnumsApi.TaskExecState.NONE);
        execContextGraphService.save(graphAndStates);
        return osr;
    }


}
