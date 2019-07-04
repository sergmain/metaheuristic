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

package ai.metaheuristic.ai.plan;

import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.launchpad.experiment.task.SimpleTaskExecResult;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.launchpad.process.Process;
import ai.metaheuristic.api.launchpad.Task;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestPlanService extends PreparingPlan {

    @Autowired
    public TaskService taskService;
    @Autowired
    public TaskPersistencer taskPersistencer;
    @Autowired
    public TaskCollector taskCollector;

    @Autowired
    public WorkbookService workbookService;

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    @After
    public void afterTestPlanService() {
        if (workbook!=null) {
            try {
                taskRepository.deleteByWorkbookId(workbook.getId());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Finished TestPlanService.afterTestPlanService()");
    }

    @Test
    public void testCreateTasks() {
        PlanApiData.TaskProducingResultComplex result = produceTasksForTest();
        List<Object[]> tasks = taskCollector.getTasks(workbook);

        assertNotNull(result);
        assertNotNull(result.workbook);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        int taskNumber = 0;
        for (Process process : planParamsYaml.planYaml.processes) {
            if (process.type == EnumsApi.ProcessType.EXPERIMENT) {
                continue;
            }
            taskNumber += process.snippets.size();
        }
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        assertEquals(1 + 1 + 3 + 2 * 12 * 7, taskNumber + epy.processing.getNumberOfTask());

        // ======================

        TaskService.TasksAndAssignToStationResult assignToStation0 =
                taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

        Protocol.AssignedTask.Task simpleTask0 = assignToStation0.getSimpleTask();
        assertNull(simpleTask0);

        workbook = planService.toStarted(workbook);
        {
            TaskService.TasksAndAssignToStationResult assignToStation =
                    taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            Protocol.AssignedTask.Task simpleTask = assignToStation.getSimpleTask();

            assertNotNull(simpleTask);
            assertNotNull(simpleTask.getTaskId());
            Task task = taskRepository.findById(simpleTask.getTaskId()).orElse(null);
            assertNotNull(task);
            assertEquals(1, task.getOrder());

            TaskService.TasksAndAssignToStationResult assignToStation2 =
                    taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());
            assertNull(assignToStation2.getSimpleTask());

            storeExecResult(simpleTask);
            workbookService.markOrderAsProcessed();
        }
        {
            TaskService.TasksAndAssignToStationResult assignToStation20 =
                    taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            Protocol.AssignedTask.Task simpleTask20 = assignToStation20.getSimpleTask();

            assertNotNull(simpleTask20);
            assertNotNull(simpleTask20.getTaskId());
            Task task3 = taskRepository.findById(simpleTask20.getTaskId()).orElse(null);
            assertNotNull(task3);
            assertEquals(2, task3.getOrder());

            TaskService.TasksAndAssignToStationResult assignToStation21 =
                    taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());
            assertNull(assignToStation21.getSimpleTask());

            storeExecResult(simpleTask20);
            workbookService.markOrderAsProcessed();
        }
        {
            TaskService.TasksAndAssignToStationResult assignToStation30 =
                    taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            Protocol.AssignedTask.Task simpleTask30 = assignToStation30.getSimpleTask();
            assertNotNull(simpleTask30);
            assertNotNull(simpleTask30.getTaskId());
            Task task30 = taskRepository.findById(simpleTask30.getTaskId()).orElse(null);
            assertNotNull(task30);
            assertEquals(3, task30.getOrder());

            TaskService.TasksAndAssignToStationResult assignToStation31 =
                    taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            Protocol.AssignedTask.Task simpleTask31 = assignToStation31.getSimpleTask();
            assertNull(simpleTask31);

            storeExecResult(simpleTask30);
            workbookService.markOrderAsProcessed();
        }
        {
            TaskService.TasksAndAssignToStationResult assignToStation32 =
                    taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            Protocol.AssignedTask.Task simpleTask32 = assignToStation32.getSimpleTask();
            assertNotNull(simpleTask32);
            assertNotNull(simpleTask32.getTaskId());
            Task task32 = taskRepository.findById(simpleTask32.getTaskId()).orElse(null);
            assertNotNull(task32);
            assertEquals(3, task32.getOrder());
            storeExecResult(simpleTask32);
            workbookService.markOrderAsProcessed();
        }
        int j;
        for ( j = 0; j < 1000; j++) {

            System.out.println("j = " + j);
            TaskService.TasksAndAssignToStationResult loopAssignToStation =
                    taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            Protocol.AssignedTask.Task loopSimpleTask = loopAssignToStation.getSimpleTask();
            assertNotNull(loopSimpleTask);
            assertNotNull(loopSimpleTask.getTaskId());
            Task loopTask = taskRepository.findById(loopSimpleTask.getTaskId()).orElse(null);
            assertNotNull(loopTask);
            taskPersistencer.setResultReceived(loopSimpleTask.getTaskId(), true);
            workbookService.markOrderAsProcessed();
            if (loopTask.getOrder()==4) {
                break;
            }
        }
        assertNotEquals(1000, j);

        TaskService.TasksAndAssignToStationResult assignToStation40 =
                taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

        Protocol.AssignedTask.Task simpleTask40 = assignToStation40.getSimpleTask();
        assertNotNull(simpleTask40);
        assertNotNull(simpleTask40.getTaskId());
        Task task40 = taskRepository.findById(simpleTask40.getTaskId()).orElse(null);
        assertNotNull(task40);
        assertEquals(4, task40.getOrder());
        taskPersistencer.setResultReceived(simpleTask40.getTaskId(), true);

        int i=0;
    }

    public void storeExecResult(Protocol.AssignedTask.Task simpleTask) {
        SimpleTaskExecResult r = new SimpleTaskExecResult();
        r.setTaskId(simpleTask.getTaskId());
        r.setMetrics(null);
        r.setResult(getOKExecResult());
        taskPersistencer.storeExecResult(r);
        taskPersistencer.setResultReceived(simpleTask.getTaskId(), true);
    }

    private String getOKExecResult() {
        SnippetApiData.SnippetExec snippetExec = new SnippetApiData.SnippetExec(
                new SnippetApiData.SnippetExecResult("output-of-a-snippet",true, 0, "Everything is Ok."),
                null, null, null);

        return SnippetExecUtils.toString(snippetExec);
    }
}
