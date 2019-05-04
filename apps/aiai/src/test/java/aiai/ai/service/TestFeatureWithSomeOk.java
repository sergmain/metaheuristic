/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.service;

import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.preparing.FeatureMethods;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@Slf4j
public class TestFeatureWithSomeOk extends FeatureMethods {

    @Test
    public void testFeatureCompletionWithPartialError() {
        assertTrue(isCorrectInit);

        long mills = System.currentTimeMillis();
        log.info("Start produceTasks()");
        produceTasks();
        log.info("produceTasks() was finished for {}", System.currentTimeMillis() - mills);

        flowInstance = flowService.toStarted(flowInstance);

        getTaskAndAssignToStation_mustBeNewTask();

        // this station already got sequences, so don't provide any new
        TaskService.TasksAndAssignToStationResult sequences = taskService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getFlowInstanceId());
        assertNotNull(sequences);
        // sequences is empty cos we still didn't finish those sequences
        assertNull(sequences.getSimpleTask());

        finishCurrentWithError(1);

        TaskService.TasksAndAssignToStationResult sequences1 = taskService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getFlowInstanceId());
        assertNotNull(sequences1);

        // TODO 2019.05.04 this test needs to be rewritten completely
/*
        if (true) throw new NotImplementedException("Not implemented yet");
        final ExperimentFeature feature = null;
//        final ExperimentFeature feature = sequences1.getFeature();
        assertNotNull(feature);
        assertNotNull(sequences1.getSimpleTask());
        assertNotNull(sequences1.getSimpleTask());

        finishCurrentWithOk(2);

        getTaskAndAssignToStation_mustBeNewTask();


        System.out.println();
*/
    }

}
