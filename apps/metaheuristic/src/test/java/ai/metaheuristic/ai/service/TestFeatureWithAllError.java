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

import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestFeatureWithAllError extends FeatureMethods {

    @Test
    public void testFeatureCompletionWithAllError() {
        createExperiment();
        assertTrue(isCorrectInit);

        long mills = System.currentTimeMillis();
        log.info("Start produceTasks()");
        produceTasks();
        log.info("produceTasks() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        toStarted();

        String sessionId = step_1_0_init_session_id();
        step_1_1_register_function_statuses(sessionId);

        findTaskForRegisteringInQueueAndWait(execContextForTest.id);

        mills = System.currentTimeMillis();
        log.info("Start getTaskAndAssignToProcessor_mustBeNewTask()");
        DispatcherCommParamsYaml.AssignedTask simpleTask = getTaskAndAssignToProcessor_mustBeNewTask();
        log.info("getTaskAndAssignToProcessor_mustBeNewTask() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        DispatcherCommParamsYaml.AssignedTask task = taskProviderService.findTask(processor.getId(), false);
        // there isn't a new task for processing
        // we will get the same task
        assertNotNull(task);
        assertEquals(simpleTask.taskId, task.taskId);

        mills = System.currentTimeMillis();
        log.info("Start storeConsoleResultAsError()");
        storeConsoleResultAsError();
        log.info("storeConsoleResultAsError() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start noNewTask()");

        task = taskProviderService.findTask(processor.getId(), false);
        assertNull(task);

        log.info("noNewTask() was finished for {} milliseconds", System.currentTimeMillis() - mills);

    }
}
