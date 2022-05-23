/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskLastProcessingHelper;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.dispatcher.task.*;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestSourceCodeService extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private ExecContextService execContextService;
    @Autowired private GlobalVariableRepository globalVariableRepository;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextTaskStateTopLevelService execContextTaskStateTopLevelService;
    @Autowired private ExecContextGraphTopLevelService execContextGraphTopLevelService;
    @Autowired private TaskFinishingTopLevelService taskFinishingTopLevelService;
    @Autowired private ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;
    @Autowired private TaskVariableTopLevelService taskVariableTopLevelService;
    @Autowired private ExecContextSchedulerService execContextSchedulerService;
    @Autowired private VariableService variableService;
    @Autowired private VariableRepository variableRepository;
    @Autowired private ExecContextFSM execContextFSM;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private ExecContextTaskAssigningTopLevelService execContextTaskAssigningTopLevelService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @AfterEach
    public void afterTestSourceCodeService() {
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
    public void testCreateTasks() {

        System.out.println("start produceTasksForTest()");
        preparingSourceCodeService.produceTasksForTest(getSourceCodeYamlAsString(), preparingSourceCodeData);

        List<Object[]> tasks = taskRepositoryForTest.findByExecContextId(getExecContextForTest().getId());

        assertNotNull(getExecContextForTest());
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        System.out.println("start verifyGraphIntegrity()");
        verifyGraphIntegrity();

        // ======================

        System.out.println("start taskProviderService.findTask()");
        DispatcherCommParamsYaml.AssignedTask simpleTask0 =
                taskProviderTopLevelService.findTask(getProcessor().getId(), false);

        assertNull(simpleTask0);

        ExecContextSyncService.getWithSync(getExecContextForTest().id, () -> {

            System.out.println("start txSupportForTestingService.toStarted()");
            txSupportForTestingService.toStarted(getExecContextForTest().id);
            setExecContextForTest(Objects.requireNonNull(execContextService.findById(getExecContextForTest().getId())));

            SimpleGlobalVariable gv = globalVariableRepository.findIdByName("global-test-variable");
            assertNotNull(gv);

            assertEquals(EnumsApi.ExecContextState.STARTED.code, getExecContextForTest().getState());
            return null;
        });
        System.out.println("start execContextStatusService.resetStatus()");
        execContextStatusService.resetStatus();

        System.out.println("start step_1_0_init_session_id()");
        PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());

        System.out.println("start step_1_1_register_function_statuses()");
        preparingSourceCodeService.step_1_1_register_function_statuses(processorIdAndCoreIds, preparingSourceCodeData, preparingCodeData);

        System.out.println("start findInternalTaskForRegisteringInQueue()");
        preparingSourceCodeService.findInternalTaskForRegisteringInQueue(getExecContextForTest().id);

        System.out.println("start findTaskForRegisteringInQueueAndWait() #1");
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest().id);

        System.out.println("start step_AssembledRaw()");
        step_AssembledRaw(processorIdAndCoreIds);

        System.out.println("start findTaskForRegisteringInQueueAndWait() #2");
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest().id);

        System.out.println("start step_DatasetProcessing()");
        step_DatasetProcessing(processorIdAndCoreIds);

        System.out.println("start findTaskForRegisteringInQueueAndWait() #3");
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest().id);

        //   processCode: feature-processing-1, function code: function-03:1.1
        System.out.println("start step_CommonProcessing(feature-output-1)");
        step_CommonProcessing(processorIdAndCoreIds, "feature-output-1");

        System.out.println("start findTaskForRegisteringInQueueAndWait() #4");
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest().id);

        //   processCode: feature-processing-2, function code: function-04:1.1
        System.out.println("start step_CommonProcessing(feature-output-2)");
        step_CommonProcessing(processorIdAndCoreIds, "feature-output-2");

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));
        // mh.permute-values-of-variables
        preparingSourceCodeService.findInternalTaskForRegisteringInQueue(getExecContextForTest().id);

        // mh.permute-variables
//        preparingSourceCodeService.findInternalTaskForRegisteringInQueue(getExecContextForTest().id);

        final List<Long> taskIds = getUnfinishedTaskVertices(getExecContextForTest());
        assertEquals(4, taskIds.size());

        TaskHolder finishTask = new TaskHolder(), permuteTask = new TaskHolder(), aggregateTask = new TaskHolder();

        for (Long taskId : taskIds) {
            TaskImpl tempTask = taskRepository.findById(taskId).orElse(null);
            assertNotNull(tempTask);
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(tempTask.params);
            assertTrue(List.of(Consts.MH_FINISH_FUNCTION, Consts.MH_PERMUTE_VARIABLES_FUNCTION, Consts.MH_AGGREGATE_FUNCTION,
                    "test.fit.function:1.0", "test.predict.function:1.0")
                    .contains(tpy.task.function.code));

            switch (tpy.task.function.code) {
                case Consts.MH_PERMUTE_VARIABLES_FUNCTION:
                    permuteTask.task = tempTask;
                    break;
                case Consts.MH_AGGREGATE_FUNCTION:
                    aggregateTask.task = tempTask;
                    break;
                case Consts.MH_FINISH_FUNCTION:
                    finishTask.task = tempTask;
                    break;
                case "test.fit.function:1.0":
                case "test.predict.function:1.0":
                    break;
                default:
                    throw new IllegalStateException("unknown code: " + tpy.task.function.code);
            }
        }
        assertNotNull(permuteTask.task);
        assertNotNull(aggregateTask.task);
        assertNotNull(finishTask.task);

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(permuteTask.task.params);
        assertFalse(tpy.task.metas.isEmpty());

        DispatcherCommParamsYaml.AssignedTask task40 = taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        // null because current task is 'internal' and will be processed in async way
        assertNull(task40);

        System.out.println("start findTaskForRegisteringInQueue() #5");

        // mh.permute-variables
        preparingSourceCodeService.findInternalTaskForRegisteringInQueue(getExecContextForTest().id);

        TaskQueue.TaskGroup taskGroup =
                ExecContextGraphSyncService.getWithSync(getExecContextForTest().execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId, ()->
                                execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(
                                        getExecContextForTest().id, getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId)));

        ExecContextSyncService.getWithSync(getExecContextForTest().id, () -> {
            setExecContextForTest(Objects.requireNonNull(execContextService.findById(getExecContextForTest().id)));

            TaskImpl tempTask = taskRepository.findById(permuteTask.task.id).orElse(null);
            assertNotNull(tempTask);

            EnumsApi.TaskExecState taskExecState = EnumsApi.TaskExecState.from(tempTask.execState);
            FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(tempTask.functionExecResults);
            assertNotNull(functionExec);
            assertEquals(EnumsApi.TaskExecState.OK, taskExecState,
                    "Current status: " + taskExecState + ", exitCode: " + functionExec.exec.exitCode + ", console: " + functionExec.exec.console);

            verifyGraphIntegrity();
            taskIds.clear();
            taskIds.addAll(getUnfinishedTaskVertices(getExecContextForTest()));

            assertEquals(14, taskIds.size());

            Set<ExecContextData.TaskVertex> descendants = execContextGraphTopLevelService.findDescendants(getExecContextForTest().execContextGraphId, permuteTask.task.id);
            // there are:
            // 3 'test.fit.function:1.0' tasks,
            // 3 'test.predict.function:1.0' tasks
            // 1 'mh.aggregate-internal-context' task
            // and 1 'mh.finish' task
            assertEquals(8, descendants.size());

            descendants = execContextGraphTopLevelService.findDirectDescendants(getExecContextForTest().execContextGraphId, permuteTask.task.id);
            // there are:
            // 3 'test.fit.function:1.0' tasks,
            // 1 'mh.aggregate-internal-context' task
            // and 1 'mh.finish' task
            assertEquals(3 + 1 + 1, descendants.size());
            return null;
        });

        // process and complete fit/predict tasks
        for (int i = 0; i < 12; i++) {
            step_FitAndPredict(processorIdAndCoreIds);
        }

        verifyGraphIntegrity();
        taskIds.clear();
        taskIds.addAll(getUnfinishedTaskVertices(getExecContextForTest()));
        // 1 'mh.aggregate-internal-context'  task,
        // and 1 'mh.finish' task
        assertEquals(2, taskIds.size());

        execContextTaskAssigningTopLevelService.findUnassignedTasksAndRegisterInQueue(getExecContextForTest().id);
        DispatcherCommParamsYaml.AssignedTask t =
                taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        // null because current task is 'internal' and will be processed in async way
        assertNull(t);
        waitForFinishing(aggregateTask.task.id, 40);
        taskGroup =
                ExecContextGraphSyncService.getWithSync(getExecContextForTest().execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId, ()->
                                execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(
                                        getExecContextForTest().id, getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId)));

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () -> {
            setExecContextForTest(Objects.requireNonNull(execContextService.findById(getExecContextForTest().id)));
            verifyGraphIntegrity();
            taskIds.clear();
            taskIds.addAll(getUnfinishedTaskVertices(getExecContextForTest()));
            assertEquals(1, taskIds.size());
        });

        execContextTaskAssigningTopLevelService.findUnassignedTasksAndRegisterInQueue(getExecContextForTest().id);
        t = taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        // null because current task is 'internal' and will be processed in async way
        assertNull(t);
        waitForFinishing(finishTask.task.id, 40);
        taskGroup =
                ExecContextGraphSyncService.getWithSync(getExecContextForTest().execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId, ()->
                                execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(
                                        getExecContextForTest().id, getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId)));

        finalAssertions(22);
    }

    private void step_CommonProcessing(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds, String outputVariable) {
        DispatcherCommParamsYaml.AssignedTask simpleTask32 =
                taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);

        assertNotNull(simpleTask32);
        assertNotNull(simpleTask32.getTaskId());
        TaskImpl task32 = taskRepository.findById(simpleTask32.getTaskId()).orElse(null);
        assertNotNull(task32);

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(simpleTask32.params);
        storeOutputVariable(outputVariable, "feature-processing-result", taskParamsYaml.task.processCode);
        storeExecResult(simpleTask32);

        finishTask(task32);
    }

    private void finishTask(TaskImpl task32) {
        taskFinishingTopLevelService.checkTaskCanBeFinished(task32.id);

        processScheduledTasks();

        TaskQueue.TaskGroup taskGroup =
                ExecContextGraphSyncService.getWithSync(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId, () ->
                                execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(
                                        getExecContextForTest().id, getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId)));
        processScheduledTasks();
    }

    private void processScheduledTasks() {
        execContextTaskStateTopLevelService.processUpdateTaskExecStatesInGraph();
        execContextVariableStateTopLevelService.processFlushing();
    }

    private void step_FitAndPredict(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest().id);

        DispatcherCommParamsYaml.AssignedTask simpleTask32 =
                taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);

        assertNotNull(simpleTask32);
        assertNotNull(simpleTask32.getTaskId());
        Task task32 = taskRepository.findById(simpleTask32.getTaskId()).orElse(null);
        assertNotNull(task32);

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(simpleTask32.params);
        assertNotNull(taskParamsYaml.task.processCode);
        assertNotNull(taskParamsYaml.task.inputs);
        assertNotNull(taskParamsYaml.task.outputs);

        boolean fitTask = "fit-dataset".equals(taskParamsYaml.task.processCode);

        assertEquals(fitTask ? 6 : 3, taskParamsYaml.task.inputs.size());
        assertEquals(fitTask ? 1 : 2, taskParamsYaml.task.outputs.size());

        if (fitTask) {
            txSupportForTestingService.storeOutputVariableWithTaskContextId(getExecContextForTest().id,
                    "model", "model-data-result-"+taskParamsYaml.task.taskContextId, taskParamsYaml.task.taskContextId);
        }
        else {
            txSupportForTestingService.storeOutputVariableWithTaskContextId(getExecContextForTest().id,
                    "metrics", "metrics-output-result-"+taskParamsYaml.task.taskContextId, taskParamsYaml.task.taskContextId);
            txSupportForTestingService.storeOutputVariableWithTaskContextId(getExecContextForTest().id,
                    "predicted", "predicted-output-result-"+taskParamsYaml.task.taskContextId, taskParamsYaml.task.taskContextId);
        }

        storeExecResult(simpleTask32);

        for (TaskParamsYaml.OutputVariable output : taskParamsYaml.task.outputs) {
            Enums.UploadVariableStatus status = TaskSyncService.getWithSyncNullable(simpleTask32.taskId,
                    () -> taskVariableTopLevelService.updateStatusOfVariable(simpleTask32.taskId, output.id).status);
            assertEquals(Enums.UploadVariableStatus.OK, status);
        }
        taskFinishingTopLevelService.checkTaskCanBeFinished(simpleTask32.taskId);
        execContextSchedulerService.updateExecContextStatuses();
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

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(simpleTask20.params);
        assertNotNull(taskParamsYaml.task.processCode);
        assertNotNull(taskParamsYaml.task.inputs);
        assertNotNull(taskParamsYaml.task.outputs);
        assertEquals(1, taskParamsYaml.task.inputs.size());
        assertEquals(1, taskParamsYaml.task.outputs.size());

        storeOutputVariable("dataset-processing-output", "dataset-processing-output-result", taskParamsYaml.task.processCode);
        storeExecResult(simpleTask20);

        finishTask(task3);
    }

    private void storeOutputVariable(String variableName, String variableData, String processCode) {

        SimpleVariable v = variableService.getVariableAsSimple(
                variableName, processCode, getExecContextForTest());

        assertNotNull(v);
        assertFalse(v.inited);

        Variable variable = variableRepository.findById(v.id).orElse(null);
        assertNotNull(variable);

        byte[] bytes = variableData.getBytes();
        variableService.updateWithTx(new ByteArrayInputStream(bytes), bytes.length, variable.id);



        v = variableService.getVariableAsSimple(v.variable, processCode, getExecContextForTest());
        assertNotNull(v);
        assertTrue(v.inited);


    }

    private void step_AssembledRaw(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
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

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(simpleTask.params);
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

        storeOutputVariable("assembled-raw-output", "assembled-raw-output-result", taskParamsYaml.task.processCode);
        storeExecResult(simpleTask);

        finishTask(task);
    }

    @SneakyThrows
    private void waitForFinishing(Long id, int secs) {
        TaskLastProcessingHelper.resetLastTask();
        long mills = System.currentTimeMillis();
        boolean processed = false;
        System.out.println("Start waiting for processing of task #"+ id);
        int period = secs * 1000;
        while(true) {
            if (!(System.currentTimeMillis() - mills < period)) break;
            TimeUnit.SECONDS.sleep(1);
            processed = TaskLastProcessingHelper.taskProcessed(id);
            if (processed) {
                break;
            }
        }
        assertTrue(processed, "After "+secs+" seconds task #"+id+" still isn't processed ");
        System.out.println(S.f("Task #%d was processed for %d", id, System.currentTimeMillis() - mills));

        mills = System.currentTimeMillis();
        boolean finished = false;
        System.out.println("Start waiting for finishing of task #"+ id);
        period = secs * 1000;
        while(true) {
            if (!(System.currentTimeMillis() - mills < period)) break;
            TimeUnit.SECONDS.sleep(1);
            TaskImpl task = taskRepository.findById(id).orElse(null);
            if (task==null) {
                throw new IllegalStateException("(task==null)");
            }

            finished = EnumsApi.TaskExecState.isFinishedState(task.execState);
            if (finished) {
                break;
            }
        }
        assertTrue(finished, "After "+secs+" seconds task #"+id+" still isn't finished ");
        System.out.println(S.f("Task #%d was finished for %d milliseconds", id, System.currentTimeMillis() - mills));

    }

    private void storeExecResult(DispatcherCommParamsYaml.AssignedTask simpleTask) {

        processScheduledTasks();

        ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
        r.setTaskId(simpleTask.getTaskId());
        r.setResult(getOKExecResult());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () -> execContextFSM.storeExecResultWithTx(r));

        TaskImpl task = taskRepository.findById(simpleTask.taskId).orElse(null);
        assertNotNull(task);

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
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
