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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskProducingService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
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
    private final ExecContextService execContextService;
    private final ExecContextTaskProducingService execContextTaskProducingService;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;

    @Transactional
    public void produceAndStartAllTasks(SourceCodeImpl sourceCode, Long execContextId, ExecContextParamsYaml execContextParamsYaml) {
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            throw new IllegalStateException("Need better solution for this state");
        }
        execContextTaskProducingService.produceAndStartAllTasks(sourceCode, execContext, execContextParamsYaml);
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
    public Variable getBinaryData(Long id) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("#087.010 this method intended to be only for test cases");
        }

        try {
            Variable v = variableRepository.findById(id).orElse(null);
            if (v==null) {
                return null;
            }
            Blob blob = v.getData();
            v.bytes = blob.getBytes(1, (int) blob.length());
            return v;
        } catch (Throwable th) {
            throw new VariableCommonException("#087.020 Error: " + th.getMessage(), id);
        }
    }

    @Transactional
    public EnumsApi.TaskProducingStatus toProducing(Long execContextId) {
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
        return execContextFSM.addTasksToGraph(execContextService.findById(execContextId), parentTaskIds, taskIds);
    }


}
