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

package ai.metaheuristic.ai.dispatcher.internal_functions.exec_source_code;

import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingService;
import ai.metaheuristic.ai.dispatcher.task.TaskStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.yaml.dispatcher.DispatcherParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 6/19/2021
 * Time: 11:20 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExecSourceCodeTopLevelService {

    private final TaskFinishingService taskFinishingService;
    public final TaskStateService taskStateService;
    public final TaskWithInternalContextTopLevelService taskWithInternalContextTopLevelService;

    public void finishLongRunningTask(DispatcherParamsYaml.LongRunningExecContext longRunningExecContext, EnumsApi.ExecContextState state) {
        TaskSyncService.getWithSyncNullable(longRunningExecContext.taskId,
                () -> {
                    switch (state) {
                        case ERROR:
                            taskFinishingService.finishWithErrorWithTx(longRunningExecContext.taskId,
                                    S.f("#035.020 long-running execContext #%d was finished with an error", longRunningExecContext.execContextId));
                            break;
                        case FINISHED:
                            try {
                                taskWithInternalContextTopLevelService.storeResult(longRunningExecContext.taskId, longRunningExecContext.execContextId);
                                taskFinishingService.finishAsOk(longRunningExecContext.taskId);
                            } catch (Throwable th) {
                                String es = S.f("#035.040 error while finishing a long-running task #%s in execContext #%s with OK, error: %s",
                                        longRunningExecContext.taskId, longRunningExecContext.execContextId, th.getMessage());
                                log.error(es, th);
                                taskFinishingService.finishWithErrorWithTx(longRunningExecContext.taskId, es);
                            }
                            break;
                        default:
                            throw new IllegalStateException("#035.100 must be FINISHED or ERROR only, actual: " + state);
                    }
                    return null;
                });
    }
}
