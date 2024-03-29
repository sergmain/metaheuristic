/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.complex;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.event.events.ResetTasksWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskResettingTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.task.*;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
//@ActiveProfiles({"dispatcher", "mysql"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class TestExecutionWithRecoveryFromError extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextTaskStateService execContextTaskStateTopLevelService;
    @Autowired private TaskFinishingTopLevelService taskFinishingTopLevelService;
    @Autowired private TaskFinishingTxService taskFinishingTxService;
    @Autowired private ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;
    @Autowired private TaskVariableTopLevelService taskVariableTopLevelService;
    @Autowired private VariableTxService variableService;
    @Autowired private VariableRepository variableRepository;
    @Autowired private ExecContextFSM execContextFSM;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private ExecContextTaskResettingTopLevelService execContextTaskResettingTopLevelService;

    @Override
    @SneakyThrows
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString("/source_code/yaml/source-code-for-testing-error-recovery.yaml", StandardCharsets.UTF_8);
    }

    @AfterEach
    public void afterTestExecutionWithRecoveryFromError() {
        System.out.println("Finished TestSourceCodeService.afterTestSourceCodeService()");
        if (getExecContextForTest() !=null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Data
    @NoArgsConstructor
    public static class TaskHolder {
        public TaskImpl task;
    }

    @SneakyThrows
    @Test
    public void test() {

        System.out.println("start step_0_0_produce_tasks_and_start()");
        step_0_0_produce_tasks_and_start();

        System.out.println("start step_1_0_init_session_id()");
        PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());

        System.out.println("start step_1_1_register_function_statuses()");
        preparingSourceCodeService.step_1_1_register_function_statuses(processorIdAndCoreIds, preparingSourceCodeData, preparingCodeData);

        //System.out.println("start findTaskForRegisteringInQueue()");
        //preparingSourceCodeService.findTaskForRegisteringInQueue(getExecContextForTest().id);

        System.out.println("start findTaskForRegisteringInQueueAndWait() #1");
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());

        System.out.println("start step_AssembledRaw()");
        step_AssembledRaw(processorIdAndCoreIds, true);
        Thread.sleep(5_000);
        step_AssembledRaw(processorIdAndCoreIds, false);

        System.out.println("start findTaskForRegisteringInQueueAndWait() #2");
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());
        System.out.println("start step_DatasetProcessing()");
        step_DatasetProcessing(processorIdAndCoreIds);

        finalAssertions(4);
    }

    private void finishTask(TaskImpl task32) {
        taskFinishingTopLevelService.checkTaskCanBeFinished(task32.id);

        processScheduledTasks();

        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId,
            () -> execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(getExecContextForTest().id, getExecContextForTest().execContextTaskStateId));
        processScheduledTasks();
    }

    private void processScheduledTasks() {
        execContextTaskStateTopLevelService.processUpdateTaskExecStatesInGraph();
        execContextVariableStateTopLevelService.processFlushing();
    }

    private void step_DatasetProcessing(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
        DispatcherCommParamsYaml.AssignedTask simpleTask20 =
                taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        // function code is function-02:1.1
        assertNotNull(simpleTask20);
        assertNotNull(simpleTask20.getTaskId());
        TaskImpl task3 = taskRepository.findById(simpleTask20.getTaskId()).orElse(null);
        assertNotNull(task3);

        DispatcherCommParamsYaml.AssignedTask simpleTask21 =
                taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        assertNotNull(simpleTask21);
        assertEquals(simpleTask20.getTaskId(), simpleTask21.getTaskId());

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.UTILS.to(simpleTask20.params);
        assertNotNull(taskParamsYaml.task.processCode);
        assertNotNull(taskParamsYaml.task.inputs);
        assertNotNull(taskParamsYaml.task.outputs);
        assertEquals(1, taskParamsYaml.task.inputs.size());
        assertEquals(1, taskParamsYaml.task.outputs.size());

        storeOutputVariable(simpleTask20.taskId, "dataset-processing-output", "dataset-processing-output-result", taskParamsYaml.task.processCode);
        storeExecResult(simpleTask20);

        finishTask(task3);
    }

    private void storeOutputVariable(Long taskId, String variableName, String variableData, String processCode) {

        Variable v = variableService.getVariable(
                variableName, processCode, getExecContextForTest());

        assertNotNull(v);
        assertFalse(v.inited);

        Variable variable = variableRepository.findById(v.id).orElse(null);
        assertNotNull(variable);

        byte[] bytes = variableData.getBytes();
        VariableSyncService.getWithSyncVoidForCreation(variable.id, ()->variableService.updateWithTx(taskId, new ByteArrayInputStream(bytes), bytes.length, variable.id));



        v = variableService.getVariable(v.name, processCode, getExecContextForTest());
        assertNotNull(v);
        assertTrue(v.inited);


    }

    private void step_AssembledRaw(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds, boolean error) {
        DispatcherCommParamsYaml.AssignedTask simpleTask =
                taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        // function code is function-01:1.1
        assertNotNull(simpleTask);
        assertNotNull(simpleTask.getTaskId());
        TaskImpl task = taskRepository.findById(simpleTask.getTaskId()).orElse(null);
        assertNotNull(task);

        DispatcherCommParamsYaml.AssignedTask simpleTask2 =
                taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);

        assertNotNull(simpleTask2);
        assertEquals(simpleTask.getTaskId(), simpleTask2.getTaskId());

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.UTILS.to(simpleTask.params);
        assertNotNull(taskParamsYaml.task.processCode);
        assertNotNull(taskParamsYaml.task.inputs);
        assertNotNull(taskParamsYaml.task.outputs);
        assertEquals(1, taskParamsYaml.task.inputs.size());
        assertEquals(1, taskParamsYaml.task.outputs.size());
        assertNotNull(taskParamsYaml.task.inline);
        assertTrue(taskParamsYaml.task.inline.containsKey("mh.hyper-params"));
/*
      mh.hyper-params:
        seed: '42'
        batches: '[40, 60]'
        time_steps: '7'
        RNN: LSTM
*/
        assertTrue(taskParamsYaml.task.inline.get("mh.hyper-params").containsKey("seed"));
        assertEquals("42", taskParamsYaml.task.inline.get("mh.hyper-params").get("seed"));
        assertTrue(taskParamsYaml.task.inline.get("mh.hyper-params").containsKey("batches"));
        assertEquals("[40, 60]", taskParamsYaml.task.inline.get("mh.hyper-params").get("batches"));
        assertTrue(taskParamsYaml.task.inline.get("mh.hyper-params").containsKey("time_steps"));
        assertEquals("7", taskParamsYaml.task.inline.get("mh.hyper-params").get("time_steps"));
        assertTrue(taskParamsYaml.task.inline.get("mh.hyper-params").containsKey("RNN"));
        assertEquals("LSTM", taskParamsYaml.task.inline.get("mh.hyper-params").get("RNN"));

        TaskParamsYaml.InputVariable inputVariable = taskParamsYaml.task.inputs.get(0);
        assertEquals("global-test-variable", inputVariable.name);
        assertEquals(EnumsApi.VariableContext.global, inputVariable.context);
        assertEquals(getTestGlobalVariable().id, inputVariable.id);

        TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
        assertEquals("assembled-raw-output", outputVariable.name);
        assertEquals(EnumsApi.VariableContext.local, outputVariable.context);
        assertNotNull(outputVariable.id);

        if (error) {
            TaskSyncService.getWithSyncVoid(simpleTask.taskId,
                    () -> taskFinishingTxService.finishWithErrorWithTx(simpleTask.taskId, "1st cycle of error"));

            TaskImpl task1 = taskRepository.findById(simpleTask.taskId).orElse(null);
            assertNotNull(task1);
            assertEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value, task1.execState);

            execContextTaskResettingTopLevelService.resetTasksWithErrorForRecovery(new ResetTasksWithErrorEvent(task1.execContextId));

            TaskImpl task2 = taskRepository.findById(simpleTask.taskId).orElse(null);
            assertNotNull(task2);
            assertEquals(EnumsApi.TaskExecState.NONE.value, task2.execState);
        }
        else {
            storeOutputVariable(simpleTask.taskId, "assembled-raw-output", "assembled-raw-output-result", taskParamsYaml.task.processCode);
            storeExecResult(simpleTask);
            finishTask(task);
        }
    }

    private void storeExecResult(DispatcherCommParamsYaml.AssignedTask simpleTask) {

        processScheduledTasks();

        ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
        r.setTaskId(simpleTask.getTaskId());
        r.setResult(getOKExecResult());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () -> execContextFSM.storeExecResultWithTx(r));

        TaskImpl task = taskRepository.findById(simpleTask.taskId).orElse(null);
        assertNotNull(task);

        TaskParamsYaml tpy = task.getTaskParamsYaml();
        for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
            Enums.UploadVariableStatus status = TaskSyncService.getWithSyncNullable(task.id,
                    () -> taskVariableTopLevelService.updateStatusOfVariable(task.id, output.id).status);
            assertEquals(Enums.UploadVariableStatus.OK, status);
        }

        processScheduledTasks();
    }

    private static String getOKExecResult() {
        FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec(
                new FunctionApiData.SystemExecResult("output-of-a-function",true, 0, "Everything is Ok."),
                null, null, null);

        return FunctionExecUtils.toString(functionExec);
    }

}
