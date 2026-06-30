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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.event.events.InitVariablesEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.exceptions.TaskCreationException;
import ai.metaheuristic.ai.exceptions.VariableImmutabilityException;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
import ai.metaheuristic.api.EnumsApi;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * @author Sergio Lissner
 * Date: 6/10/2023
 * Time: 9:07 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskVariableInitService {

    private final TaskVariableInitTxService taskVariableInitTxService;
    private final TaskFinishingTxService taskFinishingTxService;
    private final TaskRepository taskRepository;
    private final ExecContextCache execContextCache;

    private final MultiTenantedQueue<Long, InitVariablesEvent> threadedPool =
            new MultiTenantedQueue<>(10, Duration.ZERO, true, "InitVariablesEvent-", this::intiVariables);

    @PreDestroy
    public void onDestroy() {
        threadedPool.shutdown();
    }

    @Async
    @EventListener
    public void handleEvent(InitVariablesEvent event) {
        try {
            threadedPool.putToQueue(event);
        } catch (Throwable th) {
            log.error("Error", th);
        }
    }

    public void intiVariables(InitVariablesEvent event) {
        try {
            TaskSyncService.getWithSyncVoid(event.taskId, () -> {
                // ExecContext is resolved OUTSIDE the @Transactional boundary; the tx-service
                // receives only the already-resolved execContextGraphId and ExecContextParamsYaml.
                final ExecContextImpl ec = execContextCache.findById(event.execContextId, true);
                if (ec==null) {
                    return;
                }
                taskVariableInitTxService.intiVariables(event, ec.execContextGraphId, ec.getExecContextParamsYaml());
            });
        } catch (CommonRollbackException e) {
            //
        } catch (VariableImmutabilityException e) {
            log.error("179.300 Variable immutability violation for task #{}: {}", event.taskId, e.getMessage());
            try {
                TaskSyncService.getWithSyncVoid(event.taskId,
                        () -> taskFinishingTxService.finishWithErrorWithTx(event.taskId, e.getMessage(), EnumsApi.TaskExecState.ERROR));
            } catch (Throwable th) {
                log.error("179.310 Failed to set task #{} to ERROR state after immutability violation", event.taskId, th);
            }
        } catch (TaskCreationException e) {
            log.error("179.320 Failed to init input variables for task #{}: {}", event.taskId, e.getMessage());
            try {
                TaskSyncService.getWithSyncVoid(event.taskId,
                        () -> taskFinishingTxService.finishWithErrorWithTx(event.taskId, e.getMessage()));
            } catch (Throwable th) {
                log.error("179.330 Failed to set task #{} to ERROR_WITH_RECOVERY state after input-variable init error", event.taskId, th);
            }
        }
    }

    @Nullable
    ExecContextImpl resolveExecContext(Long taskId) {
        final Long execContextId = taskRepository.getExecContextId(taskId);
        if (execContextId==null) {
            return null;
        }
        return execContextCache.findById(execContextId, true);
    }

}
