/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.internal_function.evaluation;
import ai.metaheuristic.api.EnumsApi;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.spi.MhSpi;
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("unused")
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
class TestEvaluationOfConditionVariables extends TestBaseEvaluation {

    @SneakyThrows
        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/test-evaluation/test-evaluation-of-condition-1.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void testEvaluation() {

        System.out.println("start produceTasksForTest()");
        preparingSourceCodeService.produceTasksForTest(resolveSourceCode(getSourceCodeAndLang()), preparingSourceCodeData);

        System.out.println("start execContextStatusService.resetStatus()");
        execContextStatusService.resetStatus();

        // V3 async model: drive the whole pipeline to completion order-independently rather than
        // single-stepping a guessed task id. The old lockstep (initVariableEvents -> findRegister ->
        // waitUntilTaskFinished, three fixed rounds) assumed a cold scheduler; under a warm scheduler
        // (a prior test in the shared V3 context) the async wiring finishes an earlier task before the
        // test picks it, so the round's "first unfinished" id is the NEXT task and the test deadlocks
        // waiting on a task it never enqueues. See RgPipelineTestExecutionService.runPipelineToCompletion.
        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id, 40);

        // mh.evaluation's output variable must hold "false" (condition expression result).
        TaskImpl evaluationTask = findTaskByFunctionCode(getExecContextForTest().id, "mh.evaluation");
        assertNotNull(evaluationTask);
        String value = variableTxService.getVariableDataAsString(evaluationTask.getTaskParamsYaml().task.outputs.get(0).id);
        assertEquals("false", value);

        finalAssertions(3);
    }

    private TaskImpl findTaskByFunctionCode(Long execContextId, String functionCode) {
        for (TaskImpl task : taskRepositoryForTest.findByExecContextIdAsList(execContextId)) {
            if (functionCode.equals(task.getTaskParamsYaml().task.function.code)) {
                return task;
            }
        }
        return null;
    }


}
