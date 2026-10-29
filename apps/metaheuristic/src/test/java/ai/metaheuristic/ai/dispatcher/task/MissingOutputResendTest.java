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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Dispatcher's half of output recovery: a Processor's {@code checkForMissingOutputResources} is answered by
 * {@link TaskService#variableReceivingChecker} with the outputs the Dispatcher still lacks ({@code ResendTaskOutputs}),
 * and only then does the Processor re-upload them and report {@code ResendTaskOutputResourceResult}.
 *
 * <p>The state that needs it, as MH_TASK held it for 15 Tasks after the Dispatcher shutdown of 2026-10-29 15:30: the
 * function's result received ({@code IS_RESULT_RECEIVED = 1}), the Task still IN_PROGRESS on a core, its outputs not inited
 * on the Dispatcher. The Task is handed out for real to a core whose id is not the Processor's id; every assertion is on
 * what {@link TaskService#variableReceivingChecker} returns for that Processor.
 *
 * @author Sergio Lissner
 * Date: 10/29/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class MissingOutputResendTest extends FeatureMethods {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private TaskService taskService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextFSM execContextFSM;
    @Autowired private JdbcTemplate jdbcTemplate;

    private record Assigned(Long processorId, Long coreId, Long taskId, Set<Long> outputIds) {}

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    /**
     * IN_PROGRESS, the result received, the outputs not on the Dispatcher - the Task is listed for a resend of every output.
     */
    @Test
    public void test_resultReceived_outputsMissing_isListedForResend() {
        final String code = SharedItEnv.uniqueCode("MISSING_OUTPUT");
        final Assigned a = assignExternalTaskToACoreOtherThanTheProcessorId(code);

        // the observed state: the function's result reached the Dispatcher, the outputs did not
        final int updated = jdbcTemplate.update("update MH_TASK set IS_RESULT_RECEIVED = 1, VERSION = VERSION + 1 where ID = ?", a.taskId);
        assertEquals(1, updated, code + ": Task #" + a.taskId + " must be updated in MH_TASK");

        final Set<Long> listed = listedOutputIds(a);
        System.out.println(code + " Task #" + a.taskId + " on core #" + a.coreId + " (Processor #" + a.processorId + "), outputs " +
                a.outputIds + ", listed for resend: " + listed);

        // the result received, the Task IN_PROGRESS on core #coreId, its outputs not on the Dispatcher - every output is listed
        // for a resend, whatever the core's id is
        assertEquals(a.outputIds, listed, code + ": the outputs of Task #" + a.taskId + " on core #" + a.coreId + " must be listed for resend");
    }

    /**
     * A result just stored through the production path ({@link ExecContextFSM#storeExecResultWithTx}, under the Task's lock as
     * {@code ExecContextTopLevelService} calls it): its outputs are still on their way, so they are NOT requested; once they
     * are overdue (the scheduling time a minute back) the same Task is listed.
     */
    @Test
    public void test_freshResult_isNotListed_untilItsOutputsAreOverdue() {
        final String code = SharedItEnv.uniqueCode("FRESH_RESULT");
        final Assigned a = assignExternalTaskToACoreOtherThanTheProcessorId(code);

        final ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result =
                new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(a.taskId, okExecResult());
        TaskSyncService.getWithSyncVoid(a.taskId, () -> execContextFSM.storeExecResultWithTx(result));

        final TaskImpl stored = Objects.requireNonNull(taskRepository.findByIdReadOnly(a.taskId));
        assertEquals(1, stored.resultReceived, code + ": the result of Task #" + a.taskId + " must be stored");
        assertEquals(EnumsApi.TaskExecState.IN_PROGRESS.value, stored.execState,
                code + ": Task #" + a.taskId + " must stay IN_PROGRESS - its outputs are not uploaded");
        assertTrue(System.currentTimeMillis() - stored.resultResourceScheduledOn < 15_000,
                code + ": the outputs must be due from the time the result was stored, was " + stored.resultResourceScheduledOn);

        final Set<Long> fresh = listedOutputIds(a);
        jdbcTemplate.update("update MH_TASK set RESULT_RESOURCE_SCHEDULED_ON = ?, VERSION = VERSION + 1 where ID = ?",
                System.currentTimeMillis() - 60_000, a.taskId);
        final Set<Long> overdue = listedOutputIds(a);
        System.out.println(code + " Task #" + a.taskId + " on core #" + a.coreId + ", outputs " + a.outputIds +
                ", listed when fresh: " + fresh + ", when overdue: " + overdue);

        assertEquals(Set.of(), fresh, code + ": the outputs of a result just stored must not be requested yet");
        assertEquals(a.outputIds, overdue, code + ": overdue outputs of Task #" + a.taskId + " must be listed for resend");
    }

    private static String okExecResult() {
        return FunctionExecUtils.toString(new FunctionApiData.FunctionExec(
                new FunctionApiData.SystemExecResult("output-of-a-function", true, 0, "Everything is Ok."), null, null, null));
    }

    private Set<Long> listedOutputIds(Assigned a) {
        final DispatcherCommParamsYaml.ResendTaskOutputs outputs = taskService.variableReceivingChecker(a.processorId);
        return outputs.resends.stream().filter(r -> r.taskId.equals(a.taskId)).map(r -> r.variableId).collect(Collectors.toSet());
    }

    private Assigned assignExternalTaskToACoreOtherThanTheProcessorId(String code) {
        step_0_0_produce_tasks_and_start();
        final Long processorId = preparingCodeData.processor.getId();
        final PreparingData.ProcessorIdAndCoreIds cores = preparingSourceCodeService.step_1_0_init_session_id(processorId);
        preparingSourceCodeService.step_1_1_register_function_statuses(cores, preparingSourceCodeData, preparingCodeData);
        final Long execContextId = getExecContextForTest().id;

        final Long coreId = !cores.coreId2.equals(processorId) ? cores.coreId2 : cores.coreId1;
        assertNotEquals(processorId, coreId, code + ": the core must not share its id with the Processor");

        final AtomicReference<DispatcherCommParamsYaml.AssignedTask> ref = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(Objects.requireNonNull(execContextCache.findById(execContextId, true)));
                    final DispatcherCommParamsYaml.AssignedTask t = taskProviderTopLevelService.findTask(coreId, false);
                    ref.set(t);
                    return t != null;
                });
        final Long taskId = ref.get().taskId;

        final TaskImpl task = Objects.requireNonNull(taskRepository.findByIdReadOnly(taskId));
        assertEquals(EnumsApi.TaskExecState.IN_PROGRESS.value, task.execState, code + ": Task #" + taskId + " must be IN_PROGRESS after the hand-out");
        assertEquals(coreId, task.coreId, code + ": Task #" + taskId + " must be assigned to core #" + coreId);
        assertEquals(0, task.resultReceived, code + ": Task #" + taskId + " must not have a result yet");

        final Set<Long> outputIds = task.getTaskParamsYaml().task.outputs.stream().map(o -> o.id).collect(Collectors.toSet());
        assertFalse(outputIds.isEmpty(), code + ": Task #" + taskId + " must have outputs");
        return new Assigned(processorId, coreId, taskId, outputIds);
    }
}
