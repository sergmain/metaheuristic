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
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("unused")
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
class TestEvaluationOfConditionVariables extends TestBaseEvaluation {

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
        return IOUtils.resourceToString("/source_code/yaml/test-evaluation/test-evaluation-of-condition-1.yaml", StandardCharsets.UTF_8);
    }

    @Test
    public void testEvaluation() {

        System.out.println("start produceTasksForTest()");
        preparingSourceCodeService.produceTasksForTest(getSourceCodeYamlAsString(), preparingSourceCodeData);

        // ======================

        System.out.println("start execContextStatusService.resetStatus()");
        execContextStatusService.resetStatus();

        Long taskId;

        // mh.string-as-variable
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);

        // mh.evaluation
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);

        TaskImpl task = taskRepositoryForTest.findById(taskId).orElseThrow();
        String value = variableTxService.getVariableDataAsString(task.getTaskParamsYaml().task.outputs.get(0).id);
        assertEquals("false", value);

        // mh.finish
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);

        finalAssertions(3);
    }


}
