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

package ai.metaheuristic.ai.dispatcher.exec_context_variable_state;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.event.TaskCreatedEvent;
import ai.metaheuristic.ai.dispatcher.event.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 3/20/2021
 * Time: 1:03 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextVariableStateTopLevelService {

    public final ExecContextVariableStateSyncService execContextTaskStateSyncService;
    public final ExecContextSyncService execContextSyncService;
    public final ExecContextVariableStateService execContextVariableStateService;
    public final ExecContextCache execContextCache;

    public void registerCreatedTask(TaskCreatedEvent event) {
        Long execContextTaskStateId = getExecContextTaskStateId(event.taskVariablesInfo.execContextId);
        if (execContextTaskStateId == null) {
            return;
        }
        execContextTaskStateSyncService.getWithSyncNullable(execContextTaskStateId,
                () -> execContextVariableStateService.registerCreatedTask(event));
    }

    public void registerVariableState(VariableUploadedEvent event) {
        Long execContextTaskStateId = getExecContextTaskStateId(event.execContextId);
        if (execContextTaskStateId == null) {
            return;
        }
        execContextTaskStateSyncService.getWithSyncNullable(execContextTaskStateId,
                () -> registerVariableStateInternal(execContextTaskStateId, event));
    }

    @Nullable
    private Long getExecContextTaskStateId(Long execContextId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return null;
        }
        Long execContextTaskStateId = execContext.execContextTaskStateId;
        if (execContextTaskStateId==null) {
            execContextTaskStateId = execContextSyncService.getWithSync(execContext.id, ()->{
                ExecContextImpl ec = execContextCache.findById(execContext.id);
                if (ec==null) {
                    return null;
                }
                Long id = execContext.execContextTaskStateId;
                if (id==null) {
                    id = execContextVariableStateService.initExecContextTaskState(execContext.id);
                }
                return id;
            });
        }
        return execContextTaskStateId;
    }

    // this method is here to work around some strange situation
    // about calling transactional method from lambda
    private Void registerVariableStateInternal(Long execContextTaskStateId, VariableUploadedEvent event) {
        return execContextVariableStateService.registerVariableState(execContextTaskStateId, event);
    }


}
