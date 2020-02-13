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
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        LaunchpadCommParamsYaml.AssignedTask simpleTask = getTaskAndAssignToStation_mustBeNewTask();
        log.info("getTaskAndAssignToStation_mustBeNewTask() was finished for {}", System.currentTimeMillis() - mills);

        noNewTask();

        mills = System.currentTimeMillis();
        log.info("Start finishCurrentWithError()");
        finishCurrentWithError(1);
        log.info("finishCurrentWithError() was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start noNewTask()");

        noNewTask();

        log.info("noNewTask() was finished for {}", System.currentTimeMillis() - mills);

    }

    public void noNewTask() {
        LaunchpadCommParamsYaml.AssignedTask task = execContextService.getTaskAndAssignToStation(station.getId(), false, experiment.getWorkbookId());
        assertNull(task);

        task = execContextService.getTaskAndAssignToStation(station.getId() + 1, false, experiment.getWorkbookId());
        assertNull(task);
    }

}
