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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.preparing.MhInternalTaskPipelineRunner;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.EnumsApi;
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that boolean conditions work in all three forms:
 * 1. bare variable reference: condition: varName
 * 2. equality comparison: condition: varName==true
 * 3. ternary (regression): condition: 'varName ? true : false'
 * <p>
 * Each condition-test process is wrapped in an outer mh.nop with a single
 * sub-process. A SKIP on the inner sub-process is isolated to that wrapper's
 * subtree and does not cascade to the next top-level process. Production uses
 * the .mhsc SourceCode language which produces a different (correct) graph
 * topology directly; this fan-out structure is a YAML-fixture concern.
 */
@SuppressWarnings("unused")
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TestEvaluationOfConditionBoolean extends TestBaseEvaluation {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;

    @SneakyThrows
        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/test-evaluation/test-evaluation-of-condition-boolean-1.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void testBooleanConditions() {

        System.out.println("start produceTasksForTest()");
        preparingSourceCodeService.produceTasksForTest(resolveSourceCode(getSourceCodeAndLang()), preparingSourceCodeData);

        System.out.println("start execContextStatusService.resetStatus()");
        execContextStatusService.resetStatus();

        // Drive the whole pipeline to completion. The scaffold handles async
        // event drainage, task assignment, and condition-gated SKIP propagation
        // for us; we only need to inspect the final task states.
        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id, 30);

        List<TaskImpl> tasks = taskRepositoryForTest.findByExecContextIdAsList(getExecContextForTest().id);
        Map<String, TaskImpl> byProcessCode = tasks.stream()
                .collect(Collectors.toMap(
                        t -> t.getTaskParamsYaml().task.processCode,
                        Function.identity()));

        // Init tasks — must have run successfully so boolTrue/boolFalse exist
        assertExecState(byProcessCode, "mh.string-as-variable-bool-true", EnumsApi.TaskExecState.OK);
        assertExecState(byProcessCode, "mh.string-as-variable-bool-false", EnumsApi.TaskExecState.OK);

        // Wrapper mh.nop parents — always run (no condition on them)
        assertExecState(byProcessCode, "mh.nop-wrap-bare-true", EnumsApi.TaskExecState.OK);
        assertExecState(byProcessCode, "mh.nop-wrap-bare-false", EnumsApi.TaskExecState.OK);
        assertExecState(byProcessCode, "mh.nop-wrap-eq-true", EnumsApi.TaskExecState.OK);
        assertExecState(byProcessCode, "mh.nop-wrap-eq-false", EnumsApi.TaskExecState.OK);
        assertExecState(byProcessCode, "mh.nop-wrap-ternary-true", EnumsApi.TaskExecState.OK);

        // Condition-test tasks — the actual coverage. Production conditions
        // are evaluated independently for each, with no cross-sibling cascade
        // because each lives under its own wrapper subtree.
        assertExecState(byProcessCode, "mh.nop-bare-true", EnumsApi.TaskExecState.OK);
        assertExecState(byProcessCode, "mh.nop-bare-false", EnumsApi.TaskExecState.SKIPPED);
        assertExecState(byProcessCode, "mh.nop-eq-true", EnumsApi.TaskExecState.OK);
        assertExecState(byProcessCode, "mh.nop-eq-false", EnumsApi.TaskExecState.SKIPPED);
        assertExecState(byProcessCode, "mh.nop-ternary-true", EnumsApi.TaskExecState.OK);

        // mh.finish — must complete
        assertExecState(byProcessCode, "mh.finish", EnumsApi.TaskExecState.OK);

        // Total: 2 init + 5 wrappers + 5 conditions + 1 mh.finish = 13 tasks
        assertEquals(13, tasks.size(), "Unexpected task count");
    }

    private static void assertExecState(Map<String, TaskImpl> byProcessCode, String processCode, EnumsApi.TaskExecState expected) {
        TaskImpl task = byProcessCode.get(processCode);
        assertNotNull(task, "Task with processCode '" + processCode + "' wasn't found");
        assertEquals(expected.value, task.execState,
                "Task '" + processCode + "' expected " + expected + " but was " + EnumsApi.TaskExecState.from(task.execState));
    }
}
