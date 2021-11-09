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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
@DirtiesContext
@AutoConfigureCache
public class TestCountOfTasks extends PreparingExperiment {

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testCountNumberOfTasks() {
        createExperiment();
        log.info("Start TestCountOfTasks.testCountNumberOfTasks()");
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(getSourceCodeYamlAsString());

        assertFalse(sourceCodeParamsYaml.source.processes.isEmpty());

        SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(sourceCode);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status.status, status.error);

        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(sourceCode, company.getUniqueId());
        execContextForTest = result.execContext;
        assertFalse(result.isErrorMessages());
        assertNotNull(execContextForTest);
        assertEquals(EnumsApi.ExecContextState.NONE.code, execContextForTest.getState());


        EnumsApi.TaskProducingStatus producingStatus = ExecContextSyncService.getWithSync(execContextForTest.id,
                () -> txSupportForTestingService.toProducing(execContextForTest.id));

        assertEquals(EnumsApi.TaskProducingStatus.OK, producingStatus);

        execContextForTest = Objects.requireNonNull(execContextService.findById(this.execContextForTest.id));
        assertNotNull(execContextForTest);
        assertEquals(EnumsApi.ExecContextState.PRODUCING.code, execContextForTest.getState());

        List<Object[]> tasks01 = taskRepositoryForTest.findByExecContextId(execContextForTest.id);
        assertTrue(tasks01.isEmpty());

        long mills = System.currentTimeMillis();

        ExecContextSyncService.getWithSync(execContextForTest.id, () -> {
            ExecContextParamsYaml execContextParamsYaml = result.execContext.getExecContextParamsYaml();
            ExecContextGraphSyncService.getWithSync(execContextForTest.execContextGraphId, ()->
                    ExecContextTaskStateSyncService.getWithSync(execContextForTest.execContextTaskStateId, ()-> {
                        txSupportForTestingService.produceAndStartAllTasks(sourceCode, result.execContext.id, execContextParamsYaml);
                        return null;
                    }));
            return null;
        });

        log.info("Number of tasks was counted for " + (System.currentTimeMillis() - mills )+" ms.");

        ExecContextImpl execContext = Objects.requireNonNull(execContextService.findById(execContextForTest.id));
        List<Object[]> tasks02 = taskRepositoryForTest.findByExecContextId(execContext.id);
        assertEquals(7, tasks02.size());

        mills = System.currentTimeMillis();
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));
        assertEquals(EnumsApi.ExecContextState.STARTED.code, execContextForTest.getState());

        experiment = Objects.requireNonNull(experimentCache.findById(experiment.getId()));

        List<Object[]> tasks = taskRepositoryForTest.findByExecContextId(execContextForTest.id);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
//        assertEquals(numberOfTasks, tasks.size());

//        taskResult = sourceCodeService.produceAndStartAllTasks(false, sourceCode, execContextForTest);
//        List<Object[]> tasks03 = taskCollector.getTasks(execContextForTest);
//        assertFalse(tasks03.isEmpty());
//        assertEquals(numberOfTasks, tasks.size());

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
