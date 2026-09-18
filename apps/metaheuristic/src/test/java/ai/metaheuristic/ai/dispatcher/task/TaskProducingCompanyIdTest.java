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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization of {@code TaskParamsYaml.companyId} on the Tasks the dispatcher produces.
 *
 * <p>The field is documented as the company that owns the Task's ExecContext, and it is what the
 * Processor's {@code TaskSecretPlan.plan} reads before anything else: {@code companyId == 0L} short-
 * circuits to NO_SECRET_NEEDED. A Task that reaches a Processor with 0 therefore never receives the
 * vault key its Function declares in {@code api.keyCode} - the Function is launched with no
 * {@code secretPort} / {@code checkCode} at all, and nothing reports why.
 *
 * <p>The Tasks come from the real producing path - {@code step_0_0_produce_tasks_and_start()} drives
 * {@code TaskProducingService.produceTaskForProcess} -> {@code createTaskHelper} - and are read back
 * from the repository, so the assertion is on the params exactly as stored for the Processor.
 *
 * @author Sergio Lissner
 * Date: 9/17/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TaskProducingCompanyIdTest extends PreparingSourceCode {

    @Autowired private TaskRepository taskRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_producedTasksCarryTheCompanyOfTheirExecContext() {
        step_0_0_produce_tasks_and_start();

        final ExecContextImpl execContext = getExecContextForTest();
        assertNotNull(execContext.companyId);

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(execContext.id);
        assertFalse(tasks.isEmpty(), "the default SourceCode must produce Tasks");

        // the vault matters only for a Function that runs on a Processor - make sure one is here, so the
        // assertion below cannot pass on internal Tasks alone
        assertTrue(tasks.stream().anyMatch(t -> t.getTaskParamsYaml().task.context == EnumsApi.FunctionExecContext.external),
                "the default SourceCode must produce at least one external-Function Task");

        for (TaskImpl task : tasks) {
            final TaskParamsYaml tpy = task.getTaskParamsYaml();
            assertEquals(execContext.companyId.longValue(), tpy.companyId,
                    "task #" + task.id + " (" + tpy.task.processCode + ") must carry the company of its ExecContext #" + execContext.id);
        }
    }
}
