/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.long_running;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.event.events.FinishLongRunningExecContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.internal_functions.exec_source_code.ExecSourceCodeTopLevelService;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.api.EnumsApi.ExecContextState;

/**
 * @author Serge
 * Date: 6/16/2021
 * Time: 1:43 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class LongRunningTopLevelService {

    private final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;
    private final ExecContextCache execContextCache;
    private final ExecSourceCodeTopLevelService execSourceCodeTopLevelService;

    public void updateStateForLongRunning() {
        for (DispatcherParamsYaml.LongRunningExecContext longRunningExecContext : dispatcherParamsTopLevelService.getLongRunningExecContexts()) {
            processLongRunningExecContext(longRunningExecContext);
        }
    }

    @Async
    @EventListener
    public void processFinishLongRunningExecContextEvent(FinishLongRunningExecContextEvent event) {
        DispatcherParamsYaml.LongRunningExecContext longRunningExecContext = dispatcherParamsTopLevelService.getLongRunningExecContext(event.execContextId);
        if (longRunningExecContext==null) {
            return;
        }
        processLongRunningExecContext(longRunningExecContext);
    }

    private void processLongRunningExecContext(DispatcherParamsYaml.LongRunningExecContext longRunningExecContext) {
        try {
            ExecContextImpl execContext = execContextCache.findById(longRunningExecContext.execContextId, true);
            if (execContext==null) {
                dispatcherParamsTopLevelService.deRegisterLongRunningExecContext(longRunningExecContext.taskId);
                return;
            }
            ExecContextState state = ExecContextState.fromCode(execContext.state);
            if (ExecContextState.isFinishedState(state)) {
                execSourceCodeTopLevelService.finishLongRunningTask(longRunningExecContext, state);
                dispatcherParamsTopLevelService.deRegisterLongRunningExecContext(longRunningExecContext.taskId);
            }
        } catch (Throwable th) {
            log.error("018.030 Error while finishing a long-running task #"+ longRunningExecContext.taskId+", longRunningExecContextId: " + longRunningExecContext.execContextId, th);
        }
    }
}
