/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.api.EnumsApi;
import lombok.SneakyThrows;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * mh.evaluation, pure assignment {@code resultVariable1 = variable1} where {@code variable1} is NULLIFIED.
 *
 * <p>Setting a Variable as null and copying a Variable's content are two different write paths in MH
 * ({@code VariableTxService.setVariableAsNull} vs {@code updateWithTx}), so the evaluator must check the input's
 * {@code nullified} flag first to pick the path. The source produces the nullified input through mh.evaluation's own
 * null path ({@code variable1 = null}), then copies it.
 *
 * <p>CT Green-1: characterizes today's behaviour - the copy reads the nullified input
 * ({@code VariableTxService.storeToFileWithTx} throws 01.171.750), the task lands in ERROR_WITH_RECOVERY, and the
 * pipeline never completes (the runner's wait for the task times out).
 * <br>CT Red / Green-3: flipped to the desired behaviour - null in, null out: the copy takes the SET-AS-NULL path
 * for a nullified input, the task finishes OK, its output is initialized and nullified, and the pipeline completes.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class EvaluationNullifiedVariableCopyTest extends TestBaseEvaluation {

    @Autowired private VariableRepository variableRepository;

    @SneakyThrows
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/test-evaluation/test-evaluation-null-copy-1.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_copyOfANullifiedVariable() {
        preparingSourceCodeService.produceTasksForTest(resolveSourceCode(getSourceCodeAndLang()), preparingSourceCodeData);
        execContextStatusService.resetStatus();

        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id, 40);

        final TaskImpl setNull = findTaskByProcessCode(getExecContextForTest().id, "mh.evaluation-set-null");
        final Variable variable1 = variableRepository.findByIdAsSimple(setNull.getTaskParamsYaml().task.outputs.getFirst().id);
        assertNotNull(variable1);
        assertTrue(variable1.nullified, "FIXTURE: 'variable1 = null' must leave variable1 nullified");

        final TaskImpl copy = findTaskByProcessCode(getExecContextForTest().id, "mh.evaluation-copy");
        assertEquals(EnumsApi.TaskExecState.OK.value, copy.execState,
                "PHASE #1: copying a nullified variable must succeed; execState=" + copy.execState
                        + ", results: " + copy.functionExecResults);
        final Variable result = variableRepository.findByIdAsSimple(copy.getTaskParamsYaml().task.outputs.getFirst().id);
        assertNotNull(result);
        assertTrue(result.inited, "PHASE #2: resultVariable1 must be initialized");
        assertTrue(result.nullified, "PHASE #2: null in -> null out: resultVariable1 must be nullified");
    }

    private TaskImpl findTaskByProcessCode(Long execContextId, String processCode) {
        for (TaskImpl task : taskRepositoryForTest.findByExecContextIdAsList(execContextId)) {
            if (processCode.equals(task.getTaskParamsYaml().task.processCode)) {
                return task;
            }
        }
        fail("no task for process " + processCode);
        return null;
    }
}
