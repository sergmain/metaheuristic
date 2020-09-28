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

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
public class TestCountOfTasks extends PreparingExperiment {

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
        createExperiment();
        log.info("Start TestCountOfTasks.testCountNumberOfTasks()");
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(getSourceCodeYamlAsString());

        assertFalse(sourceCodeParamsYaml.source.processes.isEmpty());

        SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status.status, status.error);

        ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContext(sourceCode, company.getUniqueId());
        execContextForTest = result.execContext;
        assertFalse(result.isErrorMessages());
        assertNotNull(execContextForTest);
        assertEquals(EnumsApi.ExecContextState.NONE.code, execContextForTest.getState());


        EnumsApi.TaskProducingStatus producingStatus = execContextFSM.toProducing(execContextForTest.id, execContextService);
        assertEquals(EnumsApi.TaskProducingStatus.OK, producingStatus);

        execContextForTest = Objects.requireNonNull(execContextCache.findById(this.execContextForTest.id));
        assertNotNull(execContextForTest);
        assertEquals(EnumsApi.ExecContextState.PRODUCING.code, execContextForTest.getState());

        List<Object[]> tasks01 = taskCollector.getTasks(result.execContext);
        assertTrue(tasks01.isEmpty());

        long mills = System.currentTimeMillis();
        SourceCodeApiData.TaskProducingResultComplex taskResult = sourceCodeService.produceAllTasks(false, sourceCode, execContextForTest);
        log.info("Number of tasks was counted for " + (System.currentTimeMillis() - mills )+" ms.");

        assertEquals(EnumsApi.TaskProducingStatus.OK, taskResult.taskProducingStatus);
        int numberOfTasks = taskResult.numberOfTasks;

        ExecContext execContext = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));
        List<Object[]> tasks02 = taskCollector.getTasks(execContext);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        taskResult = sourceCodeService.produceAllTasks(true, sourceCode, execContextForTest);
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));
        assertEquals(EnumsApi.TaskProducingStatus.OK, taskResult.taskProducingStatus);
        assertEquals(EnumsApi.ExecContextState.PRODUCED.code, execContextForTest.getState());

        experiment = Objects.requireNonNull(experimentCache.findById(experiment.getId()));

        List<Object[]> tasks = taskCollector.getTasks(execContextForTest);

        assertNotNull(taskResult);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
        assertEquals(numberOfTasks, tasks.size());

        taskResult = sourceCodeService.produceAllTasks(false, sourceCode, execContextForTest);
        List<Object[]> tasks03 = taskCollector.getTasks(execContextForTest);
        assertFalse(tasks03.isEmpty());
        assertEquals(numberOfTasks, tasks.size());

        // todo 2020-03-11 because this test is just about calculating the number of tasks without processing any of them,
        //  we don't need additional calculation
/*
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
*/

    }

}
