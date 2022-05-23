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

package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateCache;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.Task;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("WeakerAccess")
@Slf4j
public abstract class PreparingSourceCode extends PreparingCore {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private Globals globals;
    @Autowired private ExecContextTaskStateCache execContextTaskStateCache;
    @Autowired private ExecContextGraphTopLevelService execContextGraphTopLevelService;
    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private ExecContextService execContextService;
    @Autowired private ExecContextRepository execContextRepository;

    public SourceCodeImpl getSourceCode() {
        return preparingSourceCodeData.sourceCode;
    }

    public Function getF1() {
        return preparingSourceCodeData.f1;
    }

    public Function getF2() {
        return preparingSourceCodeData.f2;
    }

    public Function getF3() {
        return preparingSourceCodeData.f3;
    }

    public Function getF4() {
        return preparingSourceCodeData.f4;
    }

    public Function getF5() {
        return preparingSourceCodeData.f5;
    }

    public ExecContextImpl getExecContextForTest() {
        return preparingSourceCodeData.execContextForTest;
    }

    public void setExecContextForTest(ExecContextImpl execContextForTest) {
        preparingSourceCodeData.execContextForTest = execContextForTest;
    }

    public ExecContextParamsYaml getExecContextYaml() {
        return preparingSourceCodeData.execContextYaml;
    }

    public Company getCompany() {
        return preparingSourceCodeData.company;
    }

    public GlobalVariable getTestGlobalVariable() {
        return preparingSourceCodeData.testGlobalVariable;
    }

    public PreparingData.PreparingSourceCodeData preparingSourceCodeData;

    public abstract String getSourceCodeYamlAsString();

    @SneakyThrows
    public static String getSourceCodeV1() {
        return IOUtils.resourceToString("/source_code/yaml/default-source-code-for-testing.yaml", StandardCharsets.UTF_8);
    }

    public static String getSourceParamsYamlAsString_Simple() {
        return getSourceCodeV1();
    }

    @BeforeEach
    public void beforePreparingSourceCode() {
        assertTrue(globals.testing);
        assertNotSame(globals.dispatcher.asset.mode, EnumsApi.DispatcherAssetMode.replicated);

        String params = getSourceCodeYamlAsString();
        preparingSourceCodeData = preparingSourceCodeInitService.beforePreparingSourceCode(params);
    }

    @AfterEach
    public void afterPreparingSourceCode() {
        if (preparingSourceCodeData!=null) {
            preparingSourceCodeInitService.afterPreparingSourceCode(preparingSourceCodeData);
        }
    }

    public void finalAssertions(int expectedNumberOfTasks) {
        ExecContextSyncService.getWithSync(getExecContextForTest().id, () -> {
            verifyGraphIntegrity();
            List<Long> taskIds = getUnfinishedTaskVertices(getExecContextForTest());
            assertEquals(0, taskIds.size());

            ExecContext execContext = execContextService.findById(getExecContextForTest().id);
            assertNotNull(execContext);
            assertEquals(EnumsApi.ExecContextState.FINISHED, EnumsApi.ExecContextState.toState(execContext.getState()));

            execContext = execContextRepository.findById(getExecContextForTest().id).orElse(null);
            assertNotNull(execContext);
            assertEquals(EnumsApi.ExecContextState.FINISHED, EnumsApi.ExecContextState.toState(execContext.getState()));

            List<TaskImpl> tasks = taskRepositoryForTest.findByExecContextIdAsList(getExecContextForTest().id);
            assertEquals(expectedNumberOfTasks, tasks.size());
            for (TaskImpl task : tasks) {
                assertTrue(task.isCompleted());
                assertEquals(EnumsApi.TaskExecState.OK.value, task.execState);
            }
            return null;
        });
    }

    public void verifyGraphIntegrity() {
        List<TaskImpl> tasks = taskRepositoryForTest.findByExecContextIdAsList(getExecContextForTest().id);

        setExecContextForTest(Objects.requireNonNull(execContextService.findById(this.getExecContextForTest().id)));
        List<ExecContextData.TaskVertex> taskVertices = execContextGraphTopLevelService.findAll(getExecContextForTest().execContextGraphId);
        assertEquals(tasks.size(), taskVertices.size());

        for (ExecContextData.TaskVertex taskVertex : taskVertices) {
            Task t = tasks.stream().filter(o->o.id.equals(taskVertex.taskId)).findFirst().orElse(null);
            assertNotNull(t, "task with id #"+ taskVertex.taskId+" wasn't found");
            final EnumsApi.TaskExecState taskExecState = EnumsApi.TaskExecState.from(t.getExecState());
            final EnumsApi.TaskExecState graphTaskState = preparingSourceCodeService.findTaskState(getExecContextForTest(), taskVertex.taskId);
            assertEquals(taskExecState, graphTaskState, "task has a different states in db and graph, " +
                    "db: " + taskExecState +", graph: " + graphTaskState);
        }
    }

    public List<Long> getUnfinishedTaskVertices(ExecContextImpl execContext) {
        if (execContext.execContextTaskStateId==null) {
            return List.of();
        }
        ExecContextTaskState ects = execContextTaskStateCache.findById(execContext.execContextTaskStateId);
        if (ects==null) {
            return List.of();
        }
        return ExecContextTaskStateService.getUnfinishedTaskVertices(ects);
    }


}
