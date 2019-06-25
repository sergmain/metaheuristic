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

package ai.metaheuristic.ai.plan;

import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.launchpad.process.Process;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.input_resource_param.InputResourceParamUtils;
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@Slf4j
public class TestCountOfTasks extends PreparingPlan {

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    @Autowired
    public TaskService taskService;
    @Autowired
    public TaskPersistencer taskPersistencer;

    @Test
    public void testCountNumberOfTasks() {
        log.info("Start TestCountOfTasks.testCountNumberOfTasks()");

        assertFalse(planParamsYaml.planYaml.processes.isEmpty());
        assertEquals(EnumsApi.ProcessType.EXPERIMENT, planParamsYaml.planYaml.processes.get(planParamsYaml.planYaml.processes.size()-1).type);

        EnumsApi.PlanValidateStatus status = planService.validate(plan);
        assertEquals(EnumsApi.PlanValidateStatus.OK, status);

        PlanApiData.TaskProducingResultComplex result = planService.createWorkbook(plan.getId(), InputResourceParamUtils.toString(inputResourceParam));
        workbook = result.workbook;
        assertEquals(EnumsApi.PlanProducingStatus.OK, result.planProducingStatus);
        assertNotNull(workbook);
        assertEquals(EnumsApi.WorkbookExecState.NONE.code, workbook.getExecState());


        EnumsApi.PlanProducingStatus producingStatus = planService.toProducing(workbook);
        assertEquals(EnumsApi.PlanProducingStatus.OK, producingStatus);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCING.code, workbook.getExecState());

        List<Object[]> tasks01 = taskCollector.getTasks(result.workbook);
        assertTrue(tasks01.isEmpty());

        long mills = System.currentTimeMillis();
        result = planService.produceAllTasks(false, plan, workbook);
        log.info("Number of tasks was counted for " + (System.currentTimeMillis() - mills )+" ms.");

        assertEquals(EnumsApi.PlanProducingStatus.OK, result.planProducingStatus);
        int numberOfTasks = result.numberOfTasks;

        List<Object[]> tasks02 = taskCollector.getTasks(result.workbook);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        result = planService.produceAllTasks(true, plan, workbook);
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        workbook = result.workbook;
        assertEquals(EnumsApi.PlanProducingStatus.OK, result.planProducingStatus);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCED.code, workbook.getExecState());

        experiment = experimentCache.findById(experiment.getId());

        List<Object[]> tasks = taskCollector.getTasks(result.workbook);

        assertNotNull(result);
        assertNotNull(result.workbook);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
        assertEquals(numberOfTasks, tasks.size());

        result = planService.produceAllTasks(false, plan, workbook);
        List<Object[]> tasks03 = taskCollector.getTasks(workbook);
        assertFalse(tasks03.isEmpty());
        assertEquals(numberOfTasks, tasks.size());

        int taskNumber = 0;
        for (Process process : planParamsYaml.planYaml.processes) {
            if (process.type== EnumsApi.ProcessType.EXPERIMENT) {
                continue;
            }
            taskNumber += process.snippets.size();
        }
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        assertEquals( 1+1+3+ 2*12*7, taskNumber +  epy.processing.getNumberOfTask());

    }

}
