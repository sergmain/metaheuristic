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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("unused")
@ExtendWith(SpringExtension.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TestEvaluationOfConditionVariables extends TestBaseEvaluation {

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
