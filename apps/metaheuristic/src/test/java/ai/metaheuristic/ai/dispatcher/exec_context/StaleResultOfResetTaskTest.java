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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A Processor reports the result of a Task the Dispatcher has already reset: after a restart the Processor delivers the
 * result of a Task again when its outputs weren't uploaded (ProcessorTaskService.recoveryAtLoad), and the Dispatcher may
 * have reset that Task meanwhile - 12 Tasks were reset at 2026-10-29 15:42:52 to NONE with no core and no result. Such a
 * result belongs to a run that is over; stored, it stays on the Task through its next assignment and the next run's own
 * result is then ignored ({@code storeExecResultWithTx} returns early when the result is already received).
 *
 * <p>The Task is handed out for real, reset in MH_TASK to the state those 12 were left in, and reported through the
 * production entry {@link ExecContextTopLevelService#storeAllConsoleResults}; every assertion is on MH_TASK and on what
 * the entry acknowledges.
 *
 * @author Sergio Lissner
 * Date: 10/29/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class StaleResultOfResetTaskTest extends FeatureMethods {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private ExecContextTopLevelService execContextTopLevelService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_resultOfAResetTask_isNotStored() {
        final String code = SharedItEnv.uniqueCode("STALE_RESULT");
        final Long taskId = assignExternalTask(code);

        // the state the Dispatcher left the 12 Tasks in at 2026-10-29 15:42:52
        final int updated = jdbcTemplate.update(
                "update MH_TASK set EXEC_STATE = ?, CORE_ID = null, IS_RESULT_RECEIVED = 0, VERSION = VERSION + 1 where ID = ?",
                EnumsApi.TaskExecState.NONE.value, taskId);
        assertEquals(1, updated, code + ": Task #" + taskId + " must be reset in MH_TASK");

        final List<Long> acknowledged = execContextTopLevelService.storeAllConsoleResults(List.of(
                new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(taskId, okExecResult())));

        final TaskImpl after = Objects.requireNonNull(taskRepository.findByIdReadOnly(taskId));
        System.out.println(code + " Task #" + taskId + " after the report: state " + EnumsApi.TaskExecState.from(after.execState) +
                ", resultReceived " + after.resultReceived + ", acknowledged " + acknowledged);

        assertEquals(List.of(taskId), acknowledged, code + ": the report of Task #" + taskId + " is acknowledged either way");
        assertEquals(EnumsApi.TaskExecState.NONE.value, after.execState, code + ": Task #" + taskId + " stays NONE");
        // the result of a run that is over is not stored on the reset Task - its next run's result must not be ignored
        assertEquals(0, after.resultReceived, code + ": the stale result must not be stored on Task #" + taskId);
    }

    private static String okExecResult() {
        return FunctionExecUtils.toString(new FunctionApiData.FunctionExec(
                new FunctionApiData.SystemExecResult("output-of-a-function", true, 0, "Everything is Ok."), null, null, null));
    }

    private Long assignExternalTask(String code) {
        step_0_0_produce_tasks_and_start();
        final Long processorId = preparingCodeData.processor.getId();
        final PreparingData.ProcessorIdAndCoreIds cores = preparingSourceCodeService.step_1_0_init_session_id(processorId);
        preparingSourceCodeService.step_1_1_register_function_statuses(cores, preparingSourceCodeData, preparingCodeData);
        final Long execContextId = getExecContextForTest().id;

        final AtomicReference<DispatcherCommParamsYaml.AssignedTask> ref = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(Objects.requireNonNull(execContextCache.findById(execContextId, true)));
                    final DispatcherCommParamsYaml.AssignedTask t = taskProviderTopLevelService.findTask(cores.coreId1, false);
                    ref.set(t);
                    return t != null;
                });
        final Long taskId = ref.get().taskId;
        final TaskImpl task = Objects.requireNonNull(taskRepository.findByIdReadOnly(taskId));
        assertEquals(EnumsApi.TaskExecState.IN_PROGRESS.value, task.execState, code + ": Task #" + taskId + " must be IN_PROGRESS after the hand-out");
        return taskId;
    }
}
