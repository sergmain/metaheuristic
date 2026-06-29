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

@SuppressWarnings("unused")
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TestEvaluationVariables extends TestBaseEvaluation {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @SneakyThrows
        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/test-evaluation/test-evaluation-1.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void testEvaluation() {

        System.out.println("start produceTasksForTest()");
        preparingSourceCodeService.produceTasksForTest(resolveSourceCode(getSourceCodeAndLang()), preparingSourceCodeData);

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
        assertEquals("var1", value);

        // mh.finish
        taskId = initVariableEvents();
        preparingSourceCodeService.findRegisterInternalTaskInQueue(getExecContextForTest().id);
        preparingSourceCodeService.waitUntilTaskFinished(taskId);

        finalAssertions(3);
    }

}
