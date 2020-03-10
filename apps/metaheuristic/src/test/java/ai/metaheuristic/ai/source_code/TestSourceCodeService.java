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

import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
public class TestSourceCodeService extends PreparingPlan {

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
    public ExecContextGraphTopLevelService execContextGraphTopLevelService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @After
    public void afterTestPlanService() {
        if (execContextForFeature !=null) {
            try {
                taskRepository.deleteByExecContextId(execContextForFeature.getId());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Finished TestSourceCodeService.afterTestPlanService()");
    }

    @Test
    public void testCreateTasks() {
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(getSourceCodeYamlAsString());

        SourceCodeApiData.TaskProducingResultComplex result = produceTasksForTest();
        List<Object[]> tasks = taskCollector.getTasks(execContextForFeature);

        assertNotNull(result);
        assertNotNull(result.execContext);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        int taskNumber = 0;
        for (SourceCodeParamsYaml.Process process : sourceCodeParamsYaml.source.processes) {
            if (process.subProcesses!=null) {
                if (true) {
                    throw new NotImplementedException("Need to calc number of tasks for parallel case");
                }
            }
            taskNumber++;
        }
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        final int actualTaskNumber = taskNumber + epy.processing.getNumberOfTask();
        assertEquals(1 + 1 + 3 + 2 * 12 * 7, actualTaskNumber);

        long numberFromGraph = execContextService.getCountUnfinishedTasks(execContextForFeature);
        assertEquals(actualTaskNumber, numberFromGraph);

        // ======================

        DispatcherCommParamsYaml.AssignedTask simpleTask0 =
                execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForFeature.getId());

        assertNull(simpleTask0);

        execContextFSM.toStarted(execContextForFeature);
        execContextForFeature = Objects.requireNonNull(execContextCache.findById(execContextForFeature.getId()));

        assertEquals(EnumsApi.ExecContextState.STARTED.code, execContextForFeature.getState());
        {
            DispatcherCommParamsYaml.AssignedTask simpleTask =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForFeature.getId());

            assertNotNull(simpleTask);
            assertNotNull(simpleTask.getTaskId());
            Task task = taskRepository.findById(simpleTask.getTaskId()).orElse(null);
            assertNotNull(task);

            DispatcherCommParamsYaml.AssignedTask simpleTask2 =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForFeature.getId());
            assertNull(simpleTask2);

            storeExecResult(simpleTask);
            execContextSchedulerService.updateExecContextStatuses(true);
        }
        {
            DispatcherCommParamsYaml.AssignedTask simpleTask20 =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForFeature.getId());

            assertNotNull(simpleTask20);
            assertNotNull(simpleTask20.getTaskId());
            Task task3 = taskRepository.findById(simpleTask20.getTaskId()).orElse(null);
            assertNotNull(task3);

            DispatcherCommParamsYaml.AssignedTask simpleTask21 =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForFeature.getId());
            assertNull(simpleTask21);

            storeExecResult(simpleTask20);
            execContextSchedulerService.updateExecContextStatuses(true);
        }
        {
            DispatcherCommParamsYaml.AssignedTask simpleTask30 =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForFeature.getId());

            assertNotNull(simpleTask30);
            assertNotNull(simpleTask30.getTaskId());
            Task task30 = taskRepository.findById(simpleTask30.getTaskId()).orElse(null);
            assertNotNull(task30);

            DispatcherCommParamsYaml.AssignedTask simpleTask31 =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForFeature.getId());

            assertNull(simpleTask31);

            storeExecResult(simpleTask30);
            execContextSchedulerService.updateExecContextStatuses(true);
        }
        {
            DispatcherCommParamsYaml.AssignedTask simpleTask32 =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForFeature.getId());

            assertNotNull(simpleTask32);
            assertNotNull(simpleTask32.getTaskId());
            Task task32 = taskRepository.findById(simpleTask32.getTaskId()).orElse(null);
            assertNotNull(task32);
            storeExecResult(simpleTask32);
            execContextSchedulerService.updateExecContextStatuses(true);
        }
        int j;
        long prevValue = execContextService.getCountUnfinishedTasks(execContextForFeature);
        for ( j = 0; j < 1000; j++) {
            if (j%20==0) {
                System.out.println("j = " + j);
            }
            DispatcherCommParamsYaml.AssignedTask loopSimpleTask =
                    execContextService.getTaskAndAssignToProcessor(processor.getId(), false, execContextForFeature.getId());

            assertNotNull(loopSimpleTask);
            assertNotNull(loopSimpleTask.getTaskId());
            Task loopTask = taskRepository.findById(loopSimpleTask.getTaskId()).orElse(null);
            assertNotNull(loopTask);
            storeExecResult(loopSimpleTask);
            execContextSchedulerService.updateExecContextStatus( execContextForFeature.id, true);
            execContextForFeature = Objects.requireNonNull(execContextCache.findById(execContextForFeature.id));

            final long count = execContextService.getCountUnfinishedTasks(execContextForFeature);
            assertNotEquals(count, prevValue);
            prevValue = count;
            if (count==0) {
                break;
            }
        }
        assertEquals(0, prevValue);
    }

    public void storeExecResult(DispatcherCommParamsYaml.AssignedTask simpleTask) {
        ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
        r.setTaskId(simpleTask.getTaskId());
        r.setResult(getOKExecResult());

        taskPersistencer.storeExecResult(r, t -> {
            if (t!=null) {
                execContextGraphTopLevelService.updateTaskExecStateByExecContextId(t.getExecContextId(), t.getId(), t.getExecState());
            }
        });
        taskPersistencer.setResultReceived(simpleTask.getTaskId(), true);
    }

    private String getOKExecResult() {
        FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec(
                new FunctionApiData.SystemExecResult("output-of-a-function",true, 0, "Everything is Ok."),
                null, null, null);

        return FunctionExecUtils.toString(functionExec);
    }
}
