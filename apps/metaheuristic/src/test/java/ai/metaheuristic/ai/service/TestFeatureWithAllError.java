/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNull;;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
public class TestFeatureWithAllError extends FeatureMethods {

    @Test
    public void testFeatureCompletionWithAllError() {
        assertTrue(isCorrectInit);

        long mills = System.currentTimeMillis();
        log.info("Start produceTasks()");
        produceTasks();
        log.info("produceTasks() was finished for {}", System.currentTimeMillis() - mills);

        toStarted();

        mills = System.currentTimeMillis();
        log.info("Start getTaskAndAssignToProcessor_mustBeNewTask()");
        DispatcherCommParamsYaml.AssignedTask simpleTask = getTaskAndAssignToProcessor_mustBeNewTask();
        log.info("getTaskAndAssignToProcessor_mustBeNewTask() was finished for {}", System.currentTimeMillis() - mills);

        noNewTask();

        mills = System.currentTimeMillis();
        log.info("Start finishCurrentWithError()");
        finishCurrentWithError();
        log.info("finishCurrentWithError() was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start noNewTask()");

        noNewTask();

        log.info("noNewTask() was finished for {}", System.currentTimeMillis() - mills);

    }

    public void noNewTask() {
        DispatcherCommParamsYaml.AssignedTask task = execContextService.getTaskAndAssignToProcessor(new ProcessorCommParamsYaml.ReportProcessorTaskStatus(), processor.getId(), false, experiment.getExecContextId());
        assertNull(task);

        task = execContextService.getTaskAndAssignToProcessor(new ProcessorCommParamsYaml.ReportProcessorTaskStatus(), processor.getId() + 1, false, experiment.getExecContextId());
        assertNull(task);
    }

}
