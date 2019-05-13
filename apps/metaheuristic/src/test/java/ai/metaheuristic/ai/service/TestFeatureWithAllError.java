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

import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
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
        log.info("Start getTaskAndAssignToStation_mustBeNewTask()");
        Protocol.AssignedTask.Task simpleTask = getTaskAndAssignToStation_mustBeNewTask();
        log.info("getTaskAndAssignToStation_mustBeNewTask() was finished for {}", System.currentTimeMillis() - mills);

        noNewTask();

        mills = System.currentTimeMillis();
        log.info("Start finishCurrentWithError()");
        finishCurrentWithError(1);
        log.info("finishCurrentWithError() was finished for {}", System.currentTimeMillis() - mills);

        noNewTask();

        taskPersistencer.setResultReceived(simpleTask.taskId, true);

        noNewTask();

        planService.markOrderAsProcessed();

        mills = System.currentTimeMillis();
        log.info("Start getTaskAndAssignToStation_mustBeNewTask()");
        getTaskAndAssignToStation_mustBeNewTask();
        log.info("getTaskAndAssignToStation_mustBeNewTask() was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start finishCurrentWithError()");
        finishCurrentWithError(1);
        log.info("finishCurrentWithError() was finished for {}", System.currentTimeMillis() - mills);

        // TODO add some new tests here

        System.out.println();
    }

    public void noNewTask() {
        TaskService.TasksAndAssignToStationResult task;
        task = taskService.getTaskAndAssignToStation(station.getId(), false, experiment.getWorkbookId());
        assertNotNull(task);
        assertNull(task.getSimpleTask());

        task = taskService.getTaskAndAssignToStation(station.getId() + 1, false, experiment.getWorkbookId());
        assertNotNull(task);
        assertNull(task.getSimpleTask());
    }

}
