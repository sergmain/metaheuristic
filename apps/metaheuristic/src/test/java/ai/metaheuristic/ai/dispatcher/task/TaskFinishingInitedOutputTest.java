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
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A Task whose result was received and whose output Variable is inited on the Dispatcher - stored through the
 * Dispatcher's own {@link VariableTxService#storeVariable}, the first half of the upload endpoint - but whose
 * Task-params {@code outputs[].uploaded} flag was never set, because {@code updateStatusOfVariable} did not run.
 *
 * <p>That is the state the Processor leaves behind when {@code /variable-status} answers "already inited": it marks
 * the output delivered locally and never calls the upload endpoint again, so nothing on the Dispatcher sets the flag.
 * Task #7460 of ExecContext #26 stayed IN_PROGRESS with {@code resultReceived=1} in exactly this shape.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TaskFinishingInitedOutputTest extends FeatureMethods {

    @Autowired private TaskFinishingTopLevelService taskFinishingTopLevelService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private VariableTxService variableTxService;
    @Autowired private VariableRepository variableRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_resultReceivedAndOutputVariableInited_butUploadedFlagNotSet() {
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

        // the Processor reports its result - resultReceived becomes 1
        storeConsoleResultAsOk(processorIdAndCoreIds);

        final TaskImpl task = taskRepository.findByIdReadOnly(taskId);
        assertNotNull(task, "PRE: Task #" + taskId + " must exist");
        final List<TaskParamsYaml.OutputVariable> outputs = task.getTaskParamsYaml().task.outputs.stream()
            .filter(o->o.sourcing==EnumsApi.DataSourcing.dispatcher)
            .toList();
        assertFalse(outputs.isEmpty(), "PRE: Task #" + taskId + " must have at least one dispatcher-sourced output");

        // the output lands on the Dispatcher through its own store - the upload endpoint's first half -
        // and updateStatusOfVariable is never called, as when the Processor was told "already inited"
        final byte[] bytes = "output of task".getBytes(StandardCharsets.UTF_8);
        for (TaskParamsYaml.OutputVariable output : outputs) {
            VariableSyncService.getWithSyncVoid(output.id,
                () -> variableTxService.storeVariable(new ByteArrayInputStream(bytes), bytes.length, task.execContextId, taskId, output.id));
        }

        for (TaskParamsYaml.OutputVariable output : outputs) {
            final Variable v = variableRepository.findByIdAsSimple(output.id);
            assertNotNull(v, "PRE: Variable #" + output.id + " must exist");
            assertTrue(v.inited, "PRE: Variable #" + output.id + " must be inited on the Dispatcher");
        }
        final TaskImpl taskBefore = taskRepository.findByIdReadOnly(taskId);
        assertNotNull(taskBefore);
        assertEquals(1, taskBefore.resultReceived, "PRE: Task #" + taskId + ".resultReceived");
        assertTrue(taskBefore.getTaskParamsYaml().task.outputs.stream()
                .filter(o->o.sourcing==EnumsApi.DataSourcing.dispatcher)
                .noneMatch(o->o.uploaded),
            "PRE: Task #" + taskId + " must have no output flagged as uploaded");

        taskFinishingTopLevelService.checkTaskCanBeFinished(taskId);

        final TaskImpl taskAfter = taskRepository.findByIdReadOnly(taskId);
        assertNotNull(taskAfter);
        // Green-1: current behaviour - the Task is not finished
        // Red: desired behaviour - an output whose Variable is inited on the Dispatcher counts as uploaded, so the Task finishes as OK
        assertEquals(EnumsApi.TaskExecState.OK.value, taskAfter.execState,
            "Task #" + taskId + ".execState after checkTaskCanBeFinished()");
        assertEquals(1, taskAfter.completed, "Task #" + taskId + ".completed after checkTaskCanBeFinished()");
    }
}
