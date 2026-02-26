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
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that boolean conditions work in all three forms:
 * 1. bare variable reference: condition: varName
 * 2. equality comparison: condition: varName==true
 * 3. ternary (regression): condition: 'varName ? true : false'
 */
@SuppressWarnings("unused")
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class TestEvaluationOfConditionBoolean extends TestBaseEvaluation {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    @SneakyThrows
    @Override
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString("/source_code/yaml/test-evaluation/test-evaluation-of-condition-boolean-1.yaml", StandardCharsets.UTF_8);
    }

    @Test
    public void testBooleanConditions() {

        System.out.println("start produceTasksForTest()");
        preparingSourceCodeService.produceTasksForTest(getSourceCodeYamlAsString(), preparingSourceCodeData);

        System.out.println("start execContextStatusService.resetStatus()");
        execContextStatusService.resetStatus();

        Long taskId;

        // Step 1: mh.string-as-variable for boolTrue
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);

        // Step 2: mh.string-as-variable for boolFalse
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);

        // Step 3: mh.nop-bare-true (condition: boolTrue) — should execute (not skip)
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);
        TaskImpl taskBareTrue = taskRepositoryForTest.findById(taskId).orElseThrow();
        assertEquals(EnumsApi.TaskExecState.OK.value, taskBareTrue.execState,
                "Task with 'condition: boolTrue' should have executed successfully");

        // Step 4: mh.nop-bare-false (condition: boolFalse) — should be SKIPPED
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);
        TaskImpl taskBareFalse = taskRepositoryForTest.findById(taskId).orElseThrow();
        assertEquals(EnumsApi.TaskExecState.SKIPPED.value, taskBareFalse.execState,
                "Task with 'condition: boolFalse' should have been skipped");

        // Step 5: mh.nop-eq-true (condition: boolTrue==true) — should execute (not skip)
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);
        TaskImpl taskEqTrue = taskRepositoryForTest.findById(taskId).orElseThrow();
        assertEquals(EnumsApi.TaskExecState.OK.value, taskEqTrue.execState,
                "Task with 'condition: boolTrue==true' should have executed successfully");

        // Step 6: mh.nop-eq-false (condition: boolFalse==true) — should be SKIPPED
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);
        TaskImpl taskEqFalse = taskRepositoryForTest.findById(taskId).orElseThrow();
        assertEquals(EnumsApi.TaskExecState.SKIPPED.value, taskEqFalse.execState,
                "Task with 'condition: boolFalse==true' should have been skipped");

        // Step 7: mh.nop-ternary-true (condition: 'boolTrue ? true : false') — regression, should execute
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);
        TaskImpl taskTernaryTrue = taskRepositoryForTest.findById(taskId).orElseThrow();
        assertEquals(EnumsApi.TaskExecState.OK.value, taskTernaryTrue.execState,
                "Task with ternary condition 'boolTrue ? true : false' should have executed successfully");

        // mh.finish
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);

        finalAssertions(8); // 2 init + 5 condition + 1 mh.finish
    }
}
