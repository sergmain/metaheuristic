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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.launchpad.beans.ExecContextImpl;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.source_code.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
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
    @Autowired
    public WorkbookService workbookService;

    @Test
    public void testCountNumberOfTasks() {
        log.info("Start TestCountOfTasks.testCountNumberOfTasks()");
        SourceCodeParamsYaml sourceCodeParamsYaml = PlanParamsYamlUtils.BASE_YAML_UTILS.to(getPlanYamlAsString());

        assertFalse(sourceCodeParamsYaml.source.processes.isEmpty());

        EnumsApi.SourceCodeValidateStatus status = sourceCodeService.validate(plan);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status);

        SourceCodeApiData.TaskProducingResultComplex result = workbookService.createWorkbook(plan.getId(), workbookYaml);
        workbook = (ExecContextImpl)result.execContext;
        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, result.sourceCodeProducingStatus);
        assertNotNull(workbook);
        assertEquals(EnumsApi.WorkbookExecState.NONE.code, workbook.getExecState());


        EnumsApi.SourceCodeProducingStatus producingStatus = workbookService.toProducing(workbook.id);
        workbook = workbookCache.findById(this.workbook.id);
        assertNotNull(workbook);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCING.code, workbook.getExecState());

        List<Object[]> tasks01 = taskCollector.getTasks(result.execContext);
        assertTrue(tasks01.isEmpty());

        long mills = System.currentTimeMillis();
        result = sourceCodeService.produceAllTasks(false, plan, workbook);
        log.info("Number of tasks was counted for " + (System.currentTimeMillis() - mills )+" ms.");

        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, result.sourceCodeProducingStatus);
        int numberOfTasks = result.numberOfTasks;

        List<Object[]> tasks02 = taskCollector.getTasks(result.execContext);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        result = sourceCodeService.produceAllTasks(true, plan, workbook);
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        workbook = (ExecContextImpl)result.execContext;
        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, result.sourceCodeProducingStatus);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCED.code, workbook.getExecState());

        experiment = experimentCache.findById(experiment.getId());

        List<Object[]> tasks = taskCollector.getTasks(result.execContext);

        assertNotNull(result);
        assertNotNull(result.execContext);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
        assertEquals(numberOfTasks, tasks.size());

        result = sourceCodeService.produceAllTasks(false, plan, workbook);
        List<Object[]> tasks03 = taskCollector.getTasks(workbook);
        assertFalse(tasks03.isEmpty());
        assertEquals(numberOfTasks, tasks.size());

        int taskNumber = 0;
        for (SourceCodeParamsYaml.Process process : sourceCodeParamsYaml.source.processes) {
            if (process.subProcesses!=null) {
                if (true) {
                    throw new NotImplementedException("Need to calc number of tasks for parallel case");
                }
            }
            taskNumber++;
        }
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        assertEquals( 1+1+3+ 2*12*7, taskNumber +  epy.processing.getNumberOfTask());

    }

}
