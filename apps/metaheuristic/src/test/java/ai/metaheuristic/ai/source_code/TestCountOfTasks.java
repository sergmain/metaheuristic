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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestCountOfTasks extends PreparingExperiment {

    @Autowired private SourceCodeValidationService sourceCodeValidationService;
    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextService execContextService;
    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private ExperimentCache experimentCache;

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

        SourceCodeApiData.SourceCodeValidationResult status = sourceCodeValidationService.checkConsistencyOfSourceCode(getSourceCode());
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status.status, status.error);

        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), getCompany().getUniqueId());
        setExecContextForTest(result.execContext);
        assertFalse(result.isErrorMessages());
        assertNotNull(getExecContextForTest());
        assertEquals(EnumsApi.ExecContextState.NONE.code, getExecContextForTest().getState());


        EnumsApi.TaskProducingStatus producingStatus = ExecContextSyncService.getWithSync(getExecContextForTest().id,
                () -> txSupportForTestingService.toProducing(getExecContextForTest().id));

        assertEquals(EnumsApi.TaskProducingStatus.OK, producingStatus);

        setExecContextForTest(Objects.requireNonNull(execContextService.findById(this.getExecContextForTest().id)));
        assertNotNull(getExecContextForTest());
        assertEquals(EnumsApi.ExecContextState.PRODUCING.code, getExecContextForTest().getState());

        List<Object[]> tasks01 = taskRepositoryForTest.findByExecContextId(getExecContextForTest().id);
        assertTrue(tasks01.isEmpty());

        long mills = System.currentTimeMillis();

        ExecContextSyncService.getWithSync(getExecContextForTest().id, () -> {
            ExecContextParamsYaml execContextParamsYaml = result.execContext.getExecContextParamsYaml();
            ExecContextGraphSyncService.getWithSync(getExecContextForTest().execContextGraphId, ()->
                    ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId, ()-> {
                        txSupportForTestingService.produceAndStartAllTasks(getSourceCode(), result.execContext.id, execContextParamsYaml);
                        return null;
                    }));
            return null;
        });

        log.info("Number of tasks was counted for " + (System.currentTimeMillis() - mills )+" ms.");

        ExecContextImpl execContext = Objects.requireNonNull(execContextService.findById(getExecContextForTest().id));
        List<Object[]> tasks02 = taskRepositoryForTest.findByExecContextId(execContext.id);
        assertEquals(8, tasks02.size());

        mills = System.currentTimeMillis();
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        setExecContextForTest(Objects.requireNonNull(execContextService.findById(getExecContextForTest().id)));
        assertEquals(EnumsApi.ExecContextState.STARTED.code, getExecContextForTest().getState());

        setExperiment(Objects.requireNonNull(experimentCache.findById(getExperiment().getId())));

        List<Object[]> tasks = taskRepositoryForTest.findByExecContextId(getExecContextForTest().id);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());


    }

}
