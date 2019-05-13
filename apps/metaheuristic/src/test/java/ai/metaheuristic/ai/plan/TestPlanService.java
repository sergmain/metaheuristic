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
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import ai.metaheuristic.api.v1.launchpad.Process;
import ai.metaheuristic.api.v1.launchpad.Task;
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

    @Override
    public String getPlanParamsAsYaml() {
        return getPlanParamsAsYaml_Simple();
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
        for (Process process : planYaml.processes) {
            if (process.type== EnumsApi.ProcessType.EXPERIMENT) {
                continue;
            }
            taskNumber += process.snippetCodes.size();
        }

        assertEquals( 1+1+3+ 2*12*7, taskNumber +  experiment.getNumberOfTask());

        // ======================

        TaskService.TasksAndAssignToStationResult assignToStation0 =
                taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

        Protocol.AssignedTask.Task simpleTask0 = assignToStation0.getSimpleTask();
        assertNull(simpleTask0);

        workbook = planService.toStarted(workbook);

        TaskService.TasksAndAssignToStationResult assignToStation =
                taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

        Protocol.AssignedTask.Task simpleTask = assignToStation.getSimpleTask();

//        if (true)  throw new NotImplementedException("Not yet");

        assertNotNull(simpleTask);
        assertNotNull(simpleTask.getTaskId());
        Task task = taskRepository.findById(simpleTask.getTaskId()). orElse(null);
        assertNotNull(task);
        assertEquals(1, task.getOrder());

        TaskService.TasksAndAssignToStationResult assignToStation2 =
                taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());
        assertNull(assignToStation2.getSimpleTask());

        SimpleTaskExecResult r = new SimpleTaskExecResult();
        r.setTaskId(simpleTask.getTaskId());
        r.setMetrics(null);
        r.setResult(getOKExecResult());
        taskPersistencer.storeExecResult(r);
        taskPersistencer.setResultReceived(simpleTask.getTaskId(), true);
        planService.markOrderAsProcessed();

        TaskService.TasksAndAssignToStationResult assignToStation3 =
                taskService.getTaskAndAssignToStation(
                        station.getId(), false, workbook.getId());

        Protocol.AssignedTask.Task simpleTask3 = assignToStation3.getSimpleTask();

        assertNotNull(simpleTask3);
        assertNotNull(simpleTask3.getTaskId());
        Task task3 = taskRepository.findById(simpleTask3.getTaskId()). orElse(null);
        assertNotNull(task3);
        assertEquals(2, task3.getOrder());

        TaskService.TasksAndAssignToStationResult assignToStation4 =
                taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());
        assertNull(assignToStation4.getSimpleTask());

        taskPersistencer.setResultReceived(simpleTask3.getTaskId(), true);
        planService.markOrderAsProcessed();

        TaskService.TasksAndAssignToStationResult assignToStation51 =
                taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

        Protocol.AssignedTask.Task simpleTask51 = assignToStation51.getSimpleTask();
        assertNotNull(simpleTask51);
        assertNotNull(simpleTask51.getTaskId());
        Task task51 = taskRepository.findById(simpleTask51.getTaskId()). orElse(null);
        assertNotNull(task51);
        assertEquals(3, task51.getOrder());

        TaskService.TasksAndAssignToStationResult assignToStation52 =
                taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

        Protocol.AssignedTask.Task simpleTask52 = assignToStation52.getSimpleTask();
        assertNull(simpleTask52);

        taskPersistencer.setResultReceived(simpleTask51.getTaskId(), true);

        TaskService.TasksAndAssignToStationResult assignToStation53 =
                taskService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

        Protocol.AssignedTask.Task simpleTask53 = assignToStation53.getSimpleTask();
        assertNotNull(simpleTask53);
        assertNotNull(simpleTask53.getTaskId());
        Task task53 = taskRepository.findById(simpleTask53.getTaskId()).orElse(null);
        assertNotNull(task53);
        assertEquals(3, task53.getOrder());

        int i=0;
    }

    private String getOKExecResult() {
        SnippetApiData.SnippetExec snippetExec = new SnippetApiData.SnippetExec(
                new SnippetApiData.SnippetExecResult(true, 0, "Everything is Ok."), null, null);

        return SnippetExecUtils.toString(snippetExec);
    }
}
