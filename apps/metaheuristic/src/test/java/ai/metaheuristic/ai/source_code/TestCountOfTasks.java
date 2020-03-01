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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
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
@ActiveProfiles("dispatcher")
@Slf4j
public class TestCountOfTasks extends PreparingPlan {

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Autowired
    public TaskService taskService;
    @Autowired
    public TaskPersistencer taskPersistencer;
    @Autowired
    public ExecContextService execContextService;

    @Test
    public void testCountNumberOfTasks() {
        log.info("Start TestCountOfTasks.testCountNumberOfTasks()");
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(getSourceCodeYamlAsString());

        assertFalse(sourceCodeParamsYaml.source.processes.isEmpty());

        EnumsApi.SourceCodeValidateStatus status = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status);

        ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContext(sourceCode);
        execContextForFeature = result.execContext;
        assertFalse(result.isErrorMessages());
        assertNotNull(execContextForFeature);
        assertEquals(EnumsApi.ExecContextState.NONE.code, execContextForFeature.getState());


        EnumsApi.TaskProducingStatus producingStatus = execContextService.toProducing(execContextForFeature.id);
        execContextForFeature = execContextCache.findById(this.execContextForFeature.id);
        assertNotNull(execContextForFeature);
        assertEquals(EnumsApi.ExecContextState.PRODUCING.code, execContextForFeature.getState());

        List<Object[]> tasks01 = taskCollector.getTasks(result.execContext);
        assertTrue(tasks01.isEmpty());

        long mills = System.currentTimeMillis();
        SourceCodeApiData.TaskProducingResultComplex taskResult = sourceCodeService.produceAllTasks(false, sourceCode, execContextForFeature);
        log.info("Number of tasks was counted for " + (System.currentTimeMillis() - mills )+" ms.");

        assertEquals(EnumsApi.TaskProducingStatus.OK, taskResult.taskProducingStatus);
        int numberOfTasks = taskResult.numberOfTasks;

        List<Object[]> tasks02 = taskCollector.getTasks(taskResult.execContext);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        taskResult = sourceCodeService.produceAllTasks(true, sourceCode, execContextForFeature);
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        execContextForFeature = (ExecContextImpl)taskResult.execContext;
        assertEquals(EnumsApi.TaskProducingStatus.OK, taskResult.taskProducingStatus);
        assertEquals(EnumsApi.ExecContextState.PRODUCED.code, execContextForFeature.getState());

        experiment = experimentCache.findById(experiment.getId());

        List<Object[]> tasks = taskCollector.getTasks(taskResult.execContext);

        assertNotNull(taskResult);
        assertNotNull(taskResult.execContext);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
        // todo 2020-03-01 right now counting of tasks is disabled
//        assertEquals(numberOfTasks, tasks.size());

        taskResult = sourceCodeService.produceAllTasks(false, sourceCode, execContextForFeature);
        List<Object[]> tasks03 = taskCollector.getTasks(execContextForFeature);
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
