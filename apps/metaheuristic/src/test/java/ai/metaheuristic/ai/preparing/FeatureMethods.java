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
package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.ExecContextImpl;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookFSM;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.launchpad.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@Slf4j
public abstract class FeatureMethods extends PreparingPlan {

    @Autowired
    protected Globals globals;

    @Autowired
    protected ExperimentService experimentService;

    @Autowired
    protected ExperimentRepository experimentRepository;

    @Autowired
    protected SnippetCache snippetCache;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected TaskService taskService;

    @Autowired
    public WorkbookService workbookService;

    @Autowired
    public WorkbookFSM workbookFSM;

    public boolean isCorrectInit = true;

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    public void toStarted() {
        workbookFSM.toStarted(workbook);
        workbook = workbookCache.findById(workbook.getId());
        assertEquals(EnumsApi.ExecContextState.STARTED.code, workbook.getExecState());
    }

    protected void produceTasks() {
        EnumsApi.SourceCodeValidateStatus status = sourceCodeService.validate(plan);
        assertEquals(EnumsApi.SourceCodeValidateStatus.OK, status);

        SourceCodeApiData.TaskProducingResultComplex result = workbookService.createWorkbook(plan.getId(), workbookYaml);
        workbook = (ExecContextImpl)result.execContext;
        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, result.sourceCodeProducingStatus);
        assertNotNull(workbook);
        assertEquals(EnumsApi.ExecContextState.NONE.code, workbook.getExecState());


        EnumsApi.SourceCodeProducingStatus producingStatus = workbookService.toProducing(workbook.id);
        workbook = workbookCache.findById(workbook.id);
        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, producingStatus);
        assertEquals(EnumsApi.ExecContextState.PRODUCING.code, workbook.getExecState());

        List<Object[]> tasks01 = taskCollector.getTasks(result.execContext);
        assertTrue(tasks01.isEmpty());

        long mills;

        List<Object[]> tasks02 = taskCollector.getTasks(result.execContext);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        result = sourceCodeService.produceAllTasks(true, plan, workbook);
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        workbook = (ExecContextImpl)result.execContext;
        assertEquals(EnumsApi.SourceCodeProducingStatus.OK, result.sourceCodeProducingStatus);
        assertEquals(EnumsApi.ExecContextState.PRODUCED.code, workbook.getExecState());

        experiment = experimentCache.findById(experiment.getId());
        assertNotNull(experiment.getWorkbookId());
    }

    protected LaunchpadCommParamsYaml.AssignedTask getTaskAndAssignToStation_mustBeNewTask() {
        long mills;

        mills = System.currentTimeMillis();
        log.info("Start experimentService.getTaskAndAssignToStation()");
        LaunchpadCommParamsYaml.AssignedTask task = workbookService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getWorkbookId());
        log.info("experimentService.getTaskAndAssignToStation() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(task);
        return task;
    }

    protected void finishCurrentWithError(int expectedSeqs) {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByStationIdAndResultReceivedIsFalse(station.getId());
        if (expectedSeqs!=0) {
            assertEquals(expectedSeqs, tasks.size());
        }
        for (Task task : tasks) {
            SnippetApiData.SnippetExecResult snippetExecResult = new SnippetApiData.SnippetExecResult("output-of-a-snippet",false, -1, "This is sample console output");
            SnippetApiData.SnippetExec snippetExec = new SnippetApiData.SnippetExec(snippetExecResult, null, null, null);
            String yaml = SnippetExecUtils.toString(snippetExec);

            StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult sser =
                    new StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml, null);
            // TODO 2019-11-17 left it here for info. delete when code will be merged in master
            // new StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml, MetricsUtils.toString(MetricsUtils.EMPTY_METRICS));
            results.add(sser);
        }

        workbookService.storeAllConsoleResults(results);
    }

    protected void finishCurrentWithOk(int expectedTasks) {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByStationIdAndResultReceivedIsFalse(station.getId());
        if (expectedTasks!=0) {
            assertEquals(expectedTasks, tasks.size());
        }
        for (Task task : tasks) {
            SnippetApiData.SnippetExec snippetExec = new SnippetApiData.SnippetExec();
            snippetExec.setExec( new SnippetApiData.SnippetExecResult("output-of-a-snippet", true, 0, "This is sample console output. fit"));
            String yaml = SnippetExecUtils.toString(snippetExec);

            StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult ster =
                    new StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult(task.getId(), yaml, null);
            results.add(ster);
        }

        workbookService.storeAllConsoleResults(results);
    }


}
