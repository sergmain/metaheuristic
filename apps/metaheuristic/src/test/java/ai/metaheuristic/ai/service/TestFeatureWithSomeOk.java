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
package ai.metaheuristic.ai.service;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
@DirtiesContext
@AutoConfigureCache
public class TestFeatureWithSomeOk extends FeatureMethods {

    @Test
    public void testFeatureCompletionWithPartialError() {
        createExperiment();

        assertTrue(isCorrectInit);

        long mills = System.currentTimeMillis();
        log.info("Start produceTasks()");
        produceTasks();
        log.info("produceTasks() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        ExecContextSyncService.getWithSync(execContextForTest.id, () -> {
            txSupportForTestingService.toStarted(execContextForTest.id);
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.getId()));

            assertEquals(EnumsApi.ExecContextState.STARTED.code, execContextForTest.getState());

            return null;
        });

        String sessionId = step_1_0_init_session_id();
        step_1_1_register_function_statuses(sessionId);

        findTaskForRegisteringInQueueAndWait(execContextForTest.id);
        TaskQueue.TaskGroup taskGroup =
                ExecContextGraphSyncService.getWithSync(execContextForTest.execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSync(execContextForTest.execContextTaskStateId, ()->
                                execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(
                                        execContextForTest.id, execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId)));

        DispatcherCommParamsYaml.AssignedTask assignedTask = getTaskAndAssignToProcessor_mustBeNewTask();

        // this processor already got task, so don't provide any new
        DispatcherCommParamsYaml.AssignedTask task = taskProviderService.findTask(processor.getId(), false);
        // we still didn't finish task
        // so we will get the same task
        assertNotNull(task);
        assertEquals(assignedTask.taskId, task.taskId);

        storeConsoleResultAsError();

        DispatcherCommParamsYaml.AssignedTask task1 = taskProviderService.findTask(processor.getId(), false);

        assertNull(task1);
    }

}
