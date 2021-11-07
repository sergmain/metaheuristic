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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.event.ProcessDeletedExecContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 6/26/2021
 * Time: 12:34 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextCleanerService {

    private final ExecContextGraphService execContextGraphService;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final ExecContextVariableStateService execContextVariableStateService;

    @Async
    @EventListener
    public void fixedDelayExecContextRelatives(ProcessDeletedExecContextEvent event) {
        deleteOrphanExecContextGraphs(event);
        deleteOrphanExecContextTaskState(event);
        deleteOrphanExecContextVariableState(event);
    }

    private void deleteOrphanExecContextGraphs(ProcessDeletedExecContextEvent event) {
        ExecContextSyncService.getWithSyncNullable(event.execContextId,
                () -> execContextGraphService.deleteOrphanGraphs(List.of(event.execContextGraphId)));
    }

    private void deleteOrphanExecContextTaskState(ProcessDeletedExecContextEvent event) {
        ExecContextSyncService.getWithSyncNullable(event.execContextId,
                () -> execContextTaskStateService.deleteOrphanTaskStates(List.of(event.execContextTaskStateId)));
    }

    private void deleteOrphanExecContextVariableState(ProcessDeletedExecContextEvent event) {
        ExecContextSyncService.getWithSyncNullable(event.execContextId,
                () -> execContextVariableStateService.deleteOrphanVariableStates(List.of(event.execContextVariableStateId)));
    }
}
