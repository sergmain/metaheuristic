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
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
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

        workbookFSM.toStarted(workbook);
        workbook = workbookCache.findById(workbook.getId());
        assertEquals(EnumsApi.WorkbookExecState.STARTED.code, workbook.getExecState());

        getTaskAndAssignToStation_mustBeNewTask();

        // this station already got task, so don't provide any new
        LaunchpadCommParamsYaml.AssignedTask task = workbookService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getWorkbookId());
        // task is empty cos we still didn't finish those task
        assertNull(task);

        finishCurrentWithError(1);

        LaunchpadCommParamsYaml.AssignedTask task1 = workbookService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getWorkbookId());

        assertNull(task1);

        // TODO 2019.05.04 this test needs to be rewritten completely
/*
        if (true) throw new NotImplementedException("Not implemented yet");
        final ExperimentFeature feature = null;
//        final ExperimentFeature feature = task1.getFeature();
        assertNotNull(feature);
        assertNotNull(task1.getSimpleTask());
        assertNotNull(task1.getSimpleTask());

        finishCurrentWithOk(2);

        getTaskAndAssignToStation_mustBeNewTask();


        System.out.println();
*/
    }

}
