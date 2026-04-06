/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Sergio Lissner
 * Date: 4/6/2026
 * Time: 4:37 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskResetService {

    private final ExecContextCache execContextCache;
    private final TaskResetTxService taskResetTxService;

    public void resetTaskAndExecContext(Long execContextId, Long taskId) {
        TxUtils.checkTxNotExists();

        ExecContextImpl ec = execContextCache.findById(execContextId, true);
        if (ec == null) {
            return;
        }

        ExecContextSyncService.getWithSyncVoid(ec.id, ()->
            ExecContextGraphSyncService.getWithSyncVoid(ec.execContextGraphId, ()->
                ExecContextTaskStateSyncService.getWithSyncVoid(ec.execContextTaskStateId, ()->
                    taskResetTxService.resetTaskAndExecContextTx(ec.id, taskId)
                )));
    }
}
