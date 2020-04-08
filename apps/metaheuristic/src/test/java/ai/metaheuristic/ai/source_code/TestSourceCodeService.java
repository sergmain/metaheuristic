/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
public class TestSourceCodeService extends PreparingSourceCode {

    @Autowired
    public TaskService taskService;
    @Autowired
    public TaskPersistencer taskPersistencer;
    @Autowired
    public TaskCollector taskCollector;

    @Autowired
    public ExecContextService execContextService;

    @Autowired
    public ExecContextSchedulerService execContextSchedulerService;

    @Autowired
    public ExecContextFSM execContextFSM;

    @Autowired
    public GlobalVariableRepository globalVariableRepository;

    @Autowired
    public VariableRepository variableRepository;

    @Autowired
    public ExecContextGraphTopLevelService execContextGraphTopLevelService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @AfterEach
    public void afterTestPlanService() {
        System.out.println("Finished TestSourceCodeService.afterTestPlanService()");
        variableRepository.deleteByExecContextId(execContextForTest.id);
    }

    @SneakyThrows
    @Test
    public void testCreateTasks() {
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(getSourceCodeYamlAsString());

        SourceCodeApiData.TaskProducingResultComplex result = produceTasksForTest();
        List<Object[]> tasks = taskCollector.getTasks(execContextForTest);

        assertNotNull(result);
        assertNotNull(execContextForTest);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        verifyGraphIntegrity();

        // ======================

        // the calling of this method will produce a warning "#705.180 ExecContext wasn't started." which is correct behaviour
        DispatcherCommParamsYaml.AssignedTask simpleTask0 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());

        assertNull(simpleTask0);

        execContextFSM.toStarted(execContextForTest);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.getId()));

        GlobalVariable gv = globalVariableRepository.findIdByName("test-variable");
        assertNotNull(gv);

        assertEquals(EnumsApi.ExecContextState.STARTED.code, execContextForTest.getState());
        step_AssembledRaw();
        step_DatasetProcessing();

        //   processCode: feature-processing-1, function code: function-03:1.1
        step_CommonProcessing();

        //   processCode: feature-processing-2, function code: function-04:1.1
        step_CommonProcessing();

        List<ExecContextData.TaskVertex> taskVertices = execContextService.getUnfinishedTaskVertices(execContextForTest.id);
        assertEquals(3, taskVertices.size());
        TaskImpl finishTask = null, permuteTask = null, aggregateTask = null;

        for (ExecContextData.TaskVertex taskVertex : taskVertices) {
            TaskImpl tempTask = taskRepository.findById(taskVertex.taskId).orElse(null);
            assertNotNull(tempTask);
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(tempTask.params);
            assertTrue(List.of(Consts.MH_FINISH_FUNCTION, Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_FUNCTION, Consts.MH_AGGREGATE_INTERNAL_CONTEXT_FUNCTION,
                    "test.fit.function:1.0", "test.predict.function:1.0")
                    .contains(tpy.task.function.code));

            switch(tpy.task.function.code) {
                case Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_FUNCTION:
                    permuteTask = tempTask;
                    break;
                case Consts.MH_AGGREGATE_INTERNAL_CONTEXT_FUNCTION:
                    aggregateTask = tempTask;
                    break;
                case Consts.MH_FINISH_FUNCTION:
                    finishTask = tempTask;
                    break;
//                case "test.fit.function:1.0":
//                case "test.predict.function:1.0":
//                    break;
                default:
                    throw new IllegalStateException("unknown code: " + tpy.task.function.code );
            }
        }
        assertNotNull(permuteTask);
        assertNotNull(aggregateTask);
        assertNotNull(finishTask);

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(permuteTask.params);
        assertFalse(tpy.task.metas.isEmpty());

        DispatcherCommParamsYaml.AssignedTask task40 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());
        // null because current task is 'internal' and will be processed in async way
        assertNull(task40);

        waitForFinishing(permuteTask.id, 20);

        TaskImpl tempTask = taskRepository.findById(permuteTask.id).orElse(null);
        assertNotNull(tempTask);

        EnumsApi.TaskExecState taskExecState = EnumsApi.TaskExecState.from(tempTask.execState);
        FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(tempTask.functionExecResults);
        assertEquals(EnumsApi.TaskExecState.OK, taskExecState,
                "Current status: " + taskExecState + ", exitCode: " + functionExec.exec.exitCode+", console: " + functionExec.exec.console);

        verifyGraphIntegrity();
        taskVertices = execContextService.getUnfinishedTaskVertices(execContextForTest.id);

        // there are 3 'test.fit.function:1.0' tasks,
        // 3 'test.predict.function:1.0',
        // 1 'mh.aggregate-internal-context'  task,
        // and 1 'mh.finish' task
        assertEquals(8, taskVertices.size());

        // process and complete fit/predict tasks
        for (int i = 0; i < 6; i++) {
            step_FitAndPredict();
        }

        verifyGraphIntegrity();
        taskVertices = execContextService.getUnfinishedTaskVertices(execContextForTest.id);
        // 1 'mh.aggregate-internal-context'  task,
        // and 1 'mh.finish' task
        assertEquals(2, taskVertices.size());

        {
            DispatcherCommParamsYaml.AssignedTask t =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());
            // null because current task is 'internal' and will be processed in async way
            assertNull(t);
            waitForFinishing(aggregateTask.id, 20);
        }
        taskVertices = execContextService.getUnfinishedTaskVertices(execContextForTest.id);
        assertEquals(1, taskVertices.size());
        {
            DispatcherCommParamsYaml.AssignedTask t =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());
            // null because current task is 'internal' and will be processed in async way
            assertNull(t);
            waitForFinishing(finishTask.id, 20);
        }
        taskVertices = execContextService.getUnfinishedTaskVertices(execContextForTest.id);
        assertEquals(0, taskVertices.size());

        ExecContext execContext = execContextCache.findById(execContextForTest.id);
        assertNotNull(execContext);
        assertEquals(EnumsApi.ExecContextState.FINISHED, EnumsApi.ExecContextState.toState(execContext.getState()));

        execContext = execContextRepository.findById(execContextForTest.id).orElse(null);
        assertNotNull(execContext);
        assertEquals(EnumsApi.ExecContextState.FINISHED, EnumsApi.ExecContextState.toState(execContext.getState()));
    }

    public void step_CommonProcessing() {
        DispatcherCommParamsYaml.AssignedTask simpleTask32 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());

        assertNotNull(simpleTask32);
        assertNotNull(simpleTask32.getTaskId());
        Task task32 = taskRepository.findById(simpleTask32.getTaskId()).orElse(null);
        assertNotNull(task32);

        DispatcherCommParamsYaml.AssignedTask simpleTask31 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());

        assertNull(simpleTask31);

        storeExecResult(simpleTask32);
        execContextSchedulerService.updateExecContextStatuses(true);
    }

    public void step_FitAndPredict() {
        DispatcherCommParamsYaml.AssignedTask simpleTask32 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());

        assertNotNull(simpleTask32);
        assertNotNull(simpleTask32.getTaskId());
        Task task32 = taskRepository.findById(simpleTask32.getTaskId()).orElse(null);
        assertNotNull(task32);

        DispatcherCommParamsYaml.AssignedTask simpleTask31 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());

        assertNull(simpleTask31);

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(simpleTask32.params);
        assertNotNull(taskParamsYaml.task.processCode);
        assertNotNull(taskParamsYaml.task.inputs);
        assertNotNull(taskParamsYaml.task.outputs);

        boolean fitTask = "fit-dataset".equals(taskParamsYaml.task.processCode);

        assertEquals(2, taskParamsYaml.task.inputs.size());
        assertEquals(fitTask ? 1 : 2, taskParamsYaml.task.outputs.size());

        if (fitTask) {
            storeOutputVariableWithTaskContextId(
                    "model", "model-data-result-"+taskParamsYaml.task.taskContextId, taskParamsYaml.task.taskContextId, taskParamsYaml.task.processCode);
        }
        else {
            storeOutputVariableWithTaskContextId(
                    "metrics", "metrics-output-result-"+taskParamsYaml.task.taskContextId, taskParamsYaml.task.taskContextId, taskParamsYaml.task.processCode);
            storeOutputVariableWithTaskContextId(
                    "predicted", "predicted-output-result-"+taskParamsYaml.task.taskContextId, taskParamsYaml.task.taskContextId, taskParamsYaml.task.processCode);
        }

        storeExecResult(simpleTask32);
        execContextSchedulerService.updateExecContextStatuses(true);
    }

    public void step_DatasetProcessing() {
        DispatcherCommParamsYaml.AssignedTask simpleTask20 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());
        // function code is function-02:1.1
        assertNotNull(simpleTask20);
        assertNotNull(simpleTask20.getTaskId());
        Task task3 = taskRepository.findById(simpleTask20.getTaskId()).orElse(null);
        assertNotNull(task3);

        // the calling of this method will produce warning "#705.160 can't assign any new task to the processor" which is correct behaviour
        DispatcherCommParamsYaml.AssignedTask simpleTask21 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());
        assertNull(simpleTask21);

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(simpleTask20.params);
        assertNotNull(taskParamsYaml.task.processCode);
        assertNotNull(taskParamsYaml.task.inputs);
        assertNotNull(taskParamsYaml.task.outputs);
        assertEquals(1, taskParamsYaml.task.inputs.size());
        assertEquals(1, taskParamsYaml.task.outputs.size());

        storeOutputVariable("dataset-processing-output", "dataset-processing-output-result", taskParamsYaml.task.processCode);
        storeExecResult(simpleTask20);

        execContextSchedulerService.updateExecContextStatuses(true);
    }

    private void storeOutputVariable(String variableName, String variableData, String processCode) {

        SimpleVariableAndStorageUrl v = variableService.getVariableAsSimple(
                variableName, processCode, execContextForTest);

        assertNotNull(v);
        assertFalse(v.inited);

        Variable variable = variableService.findById(v.id).orElse(null);
        assertNotNull(variable);

        byte[] bytes = variableData.getBytes();
        variableService.update(new ByteArrayInputStream(bytes), bytes.length, variable);



        v = variableService.getVariableAsSimple(v.variable, processCode, execContextForTest);
        assertNotNull(v);
        assertTrue(v.inited);


    }

    private void storeOutputVariableWithTaskContextId(String variableName, String variableData, String taskContextId, String processCode) {

        SimpleVariableAndStorageUrl v = variableRepository.findByNameAndTaskContextIdAndExecContextId(variableName, taskContextId, execContextForTest.id);

        assertNotNull(v);
        assertFalse(v.inited);

        Variable variable = variableService.findById(v.id).orElse(null);
        assertNotNull(variable);

        byte[] bytes = variableData.getBytes();
        variableService.update(new ByteArrayInputStream(bytes), bytes.length, variable);

        v = variableRepository.findByNameAndTaskContextIdAndExecContextId(variableName, taskContextId, execContextForTest.id);
        assertNotNull(v);
        assertTrue(v.inited);


    }

    public void step_AssembledRaw() {
        DispatcherCommParamsYaml.AssignedTask simpleTask =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());
        // function code is function-01:1.1
        assertNotNull(simpleTask);
        assertNotNull(simpleTask.getTaskId());
        Task task = taskRepository.findById(simpleTask.getTaskId()).orElse(null);
        assertNotNull(task);

        // the calling of this method will produce warning "#705.160 can't assign any new task to the processor" which is correct behaviour
        DispatcherCommParamsYaml.AssignedTask simpleTask2 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForTest.getId());
        assertNull(simpleTask2);

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(simpleTask.params);
        assertNotNull(taskParamsYaml.task.processCode);
        assertNotNull(taskParamsYaml.task.inputs);
        assertNotNull(taskParamsYaml.task.outputs);
        assertEquals(1, taskParamsYaml.task.inputs.size());
        assertEquals(1, taskParamsYaml.task.outputs.size());

        TaskParamsYaml.InputVariable inputVariable = taskParamsYaml.task.inputs.get(0);
        assertEquals("test-variable", inputVariable.name);
        assertEquals(EnumsApi.VariableContext.global, inputVariable.context);
        assertEquals(testGlobalVariable.id.toString(), inputVariable.id);

        TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
        assertEquals("assembled-raw-output", outputVariable.name);
        assertEquals(EnumsApi.VariableContext.local, outputVariable.context);
        assertNotNull(outputVariable.id);

        storeOutputVariable("assembled-raw-output", "assembled-raw-output-result", taskParamsYaml.task.processCode);
        storeExecResult(simpleTask);

        execContextSchedulerService.updateExecContextStatuses(true);
    }

    public void waitForFinishing(Long id, int secs) throws InterruptedException {
        long mills = System.currentTimeMillis();
        boolean finished = false;
        System.out.println("Start waiting for finishing of task #"+ id);
        int period = secs * 1000;
        while(true) {
            if (!(System.currentTimeMillis() - mills < period)) break;
            TimeUnit.SECONDS.sleep(1);
            finished = TaskWithInternalContextEventService.taskFinished(id);
            if (finished) {
                break;
            }
        }
        assertTrue(finished, "After 60 seconds permuteTask still isn't finished ");
    }

    private void verifyGraphIntegrity() {

        List<TaskImpl> tasks = taskRepository.findByExecContextIdAsList(execContextForTest.id);
        List<ExecContextData.TaskVertex> taskVertices = execContextService.findAllVertices(execContextForTest.id);
        assertEquals(tasks.size(), taskVertices.size());

        for (ExecContextData.TaskVertex taskVertex : taskVertices) {
            Task t = tasks.stream().filter(o->o.id.equals(taskVertex.taskId)).findAny().orElse(null);
            assertNotNull(t, "task with id #"+ taskVertex.taskId+"wasn't found");
            assertEquals(t.getExecState(), taskVertex.execState.value, "task has a different states in db and graph, " +
                    "db: " + EnumsApi.TaskExecState.from(t.getExecState())+", graph: " + taskVertex.execState);
        }
    }

    public void storeExecResult(DispatcherCommParamsYaml.AssignedTask simpleTask) {
        verifyGraphIntegrity();

        ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
        r.setTaskId(simpleTask.getTaskId());
        r.setResult(getOKExecResult());

        taskPersistencer.storeExecResult(r, t -> {
            if (t!=null) {
                execContextGraphTopLevelService.updateTaskExecStateByExecContextId(t.getExecContextId(), t.getId(), t.getExecState());
            }
        });
        taskPersistencer.setResultReceived(simpleTask.getTaskId(), true);

        verifyGraphIntegrity();
    }

    private String getOKExecResult() {
        FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec(
                new FunctionApiData.SystemExecResult("output-of-a-function",true, 0, "Everything is Ok."),
                null, null, null);

        return FunctionExecUtils.toString(functionExec);
    }
}
