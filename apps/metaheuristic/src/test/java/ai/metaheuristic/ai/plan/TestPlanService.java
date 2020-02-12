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

import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookFSM;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookGraphTopLevelService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookSchedulerService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.launchpad.Task;
import org.apache.commons.lang3.NotImplementedException;
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

    @Autowired
    public WorkbookSchedulerService workbookSchedulerService;

    @Autowired
    public WorkbookFSM workbookFSM;

    @Autowired
    public WorkbookGraphTopLevelService workbookGraphTopLevelService;

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
        SourceCodeParamsYaml sourceCodeParamsYaml = PlanParamsYamlUtils.BASE_YAML_UTILS.to(getPlanYamlAsString());

        SourceCodeApiData.TaskProducingResultComplex result = produceTasksForTest();
        List<Object[]> tasks = taskCollector.getTasks(workbook);

        assertNotNull(result);
        assertNotNull(result.workbook);
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

        long numberFromGraph = workbookService.getCountUnfinishedTasks(workbook);
        assertEquals(actualTaskNumber, numberFromGraph);

        // ======================

        LaunchpadCommParamsYaml.AssignedTask simpleTask0 =
                workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

        assertNull(simpleTask0);

        workbookFSM.toStarted(workbook);
        workbook = workbookCache.findById(workbook.getId());

        assertEquals(EnumsApi.WorkbookExecState.STARTED.code, workbook.getExecState());
        {
            LaunchpadCommParamsYaml.AssignedTask simpleTask =
                    workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            assertNotNull(simpleTask);
            assertNotNull(simpleTask.getTaskId());
            Task task = taskRepository.findById(simpleTask.getTaskId()).orElse(null);
            assertNotNull(task);

            LaunchpadCommParamsYaml.AssignedTask simpleTask2 =
                    workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());
            assertNull(simpleTask2);

            storeExecResult(simpleTask);
            workbookSchedulerService.updateWorkbookStatuses(true);
        }
        {
            LaunchpadCommParamsYaml.AssignedTask simpleTask20 =
                    workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            assertNotNull(simpleTask20);
            assertNotNull(simpleTask20.getTaskId());
            Task task3 = taskRepository.findById(simpleTask20.getTaskId()).orElse(null);
            assertNotNull(task3);

            LaunchpadCommParamsYaml.AssignedTask simpleTask21 =
                    workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());
            assertNull(simpleTask21);

            storeExecResult(simpleTask20);
            workbookSchedulerService.updateWorkbookStatuses(true);
        }
        {
            LaunchpadCommParamsYaml.AssignedTask simpleTask30 =
                    workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            assertNotNull(simpleTask30);
            assertNotNull(simpleTask30.getTaskId());
            Task task30 = taskRepository.findById(simpleTask30.getTaskId()).orElse(null);
            assertNotNull(task30);

            LaunchpadCommParamsYaml.AssignedTask simpleTask31 =
                    workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            assertNull(simpleTask31);

            storeExecResult(simpleTask30);
            workbookSchedulerService.updateWorkbookStatuses(true);
        }
        {
            LaunchpadCommParamsYaml.AssignedTask simpleTask32 =
                    workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            assertNotNull(simpleTask32);
            assertNotNull(simpleTask32.getTaskId());
            Task task32 = taskRepository.findById(simpleTask32.getTaskId()).orElse(null);
            assertNotNull(task32);
            storeExecResult(simpleTask32);
            workbookSchedulerService.updateWorkbookStatuses(true);
        }
        int j;
        long prevValue = workbookService.getCountUnfinishedTasks(workbook);
        for ( j = 0; j < 1000; j++) {
            if (j%20==0) {
                System.out.println("j = " + j);
            }
            LaunchpadCommParamsYaml.AssignedTask loopSimpleTask =
                    workbookService.getTaskAndAssignToStation(station.getId(), false, workbook.getId());

            assertNotNull(loopSimpleTask);
            assertNotNull(loopSimpleTask.getTaskId());
            Task loopTask = taskRepository.findById(loopSimpleTask.getTaskId()).orElse(null);
            assertNotNull(loopTask);
            storeExecResult(loopSimpleTask);
            workbookSchedulerService.updateWorkbookStatus( workbook.id, true);
            workbook = workbookCache.findById(workbook.id);

            final long count = workbookService.getCountUnfinishedTasks(workbook);
            assertNotEquals(count, prevValue);
            prevValue = count;
            if (count==0) {
                break;
            }
        }
        assertEquals(0, prevValue);
    }

    public void storeExecResult(LaunchpadCommParamsYaml.AssignedTask simpleTask) {
        StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
        r.setTaskId(simpleTask.getTaskId());
        r.setMl(null);
        r.setResult(getOKExecResult());

        taskPersistencer.storeExecResult(r, t -> {
            if (t!=null) {
                workbookGraphTopLevelService.updateTaskExecStateByWorkbookId(t.getWorkbookId(), t.getId(), t.getExecState());
            }
        });
        taskPersistencer.setResultReceived(simpleTask.getTaskId(), true);
    }

    private String getOKExecResult() {
        SnippetApiData.SnippetExec snippetExec = new SnippetApiData.SnippetExec(
                new SnippetApiData.SnippetExecResult("output-of-a-snippet",true, 0, "Everything is Ok."),
                null, null, null);

        return SnippetExecUtils.toString(snippetExec);
    }
}
