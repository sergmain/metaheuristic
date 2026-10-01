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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskVariableTopLevelService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A reset Task must not keep {@code outputs[].uploaded=true}: the reset sets every output Variable back to not inited,
 * so a stale flag would let the Task count its outputs as delivered - and finish - on its next result, before the new
 * upload arrives.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextTaskResettingUploadedFlagTest extends FeatureMethods {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private TaskVariableTopLevelService taskVariableTopLevelService;
    @Autowired private ExecContextTaskResettingService execContextTaskResettingService;
    @Autowired private TaskRepository taskRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    private static List<TaskParamsYaml.OutputVariable> dispatcherOutputs(TaskImpl task) {
        return task.getTaskParamsYaml().task.outputs.stream()
            .filter(o->o.sourcing==EnumsApi.DataSourcing.dispatcher)
            .toList();
    }

    @Test
    public void test_resetTask_clearsUploadedFlagOfOutputs() {
        step_0_0_produce_tasks_and_start();

        final PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());
        preparingSourceCodeService.step_1_1_register_function_statuses(processorIdAndCoreIds, preparingSourceCodeData, preparingCodeData);

        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());

        final AtomicReference<DispatcherCommParamsYaml.AssignedTask> tRef = new AtomicReference<>();
        await()
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(500))
            .until(()-> {
                final DispatcherCommParamsYaml.AssignedTask t = taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
                tRef.set(t);
                return t!=null;
            });
        final Long taskId = tRef.get().taskId;

        storeConsoleResultAsOk(processorIdAndCoreIds);

        final TaskImpl task = taskRepository.findByIdReadOnly(taskId);
        assertNotNull(task, "PRE: Task #" + taskId + " must exist");
        final List<TaskParamsYaml.OutputVariable> outputs = dispatcherOutputs(task);
        assertFalse(outputs.isEmpty(), "PRE: Task #" + taskId + " must have at least one dispatcher-sourced output");

        // the normal upload path flags every output as uploaded
        TaskSyncService.getWithSyncVoid(taskId, () -> {
            for (TaskParamsYaml.OutputVariable output : outputs) {
                assertEquals(Enums.UploadVariableStatus.OK, taskVariableTopLevelService.updateStatusOfVariable(taskId, output.id).status,
                    "PRE: updateStatusOfVariable(task #" + taskId + ", variable #" + output.id + ")");
            }
        });
        final TaskImpl taskBefore = taskRepository.findByIdReadOnly(taskId);
        assertNotNull(taskBefore);
        assertTrue(dispatcherOutputs(taskBefore).stream().allMatch(o->o.uploaded),
            "PRE: every output of Task #" + taskId + " must be flagged as uploaded before the reset");

        final Long execContextId = taskBefore.execContextId;
        ExecContextSyncService.getWithSyncVoid(execContextId, () -> execContextTaskResettingService.resetTaskWithTx(execContextId, taskId));

        final TaskImpl taskAfter = taskRepository.findByIdReadOnly(taskId);
        assertNotNull(taskAfter);
        assertEquals(0, taskAfter.resultReceived, "Task #" + taskId + ".resultReceived after resetTask()");
        for (TaskParamsYaml.OutputVariable o : dispatcherOutputs(taskAfter)) {
            // Green-1: current behaviour - the reset keeps the stale flag
            // Red: desired behaviour - the reset clears the flag together with the output Variable
            assertFalse(o.uploaded, "Task #" + taskId + ", output variable #" + o.id + ".uploaded after resetTask()");
        }
    }
}
