/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.preparing;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.beans.ExperimentFeature;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import ai.metaheuristic.api.v1.launchpad.Task;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.experiment.task.SimpleTaskExecResult;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.yaml.input_resource_param.InputResourceParamUtils;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
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
    protected ExperimentFeatureRepository experimentFeatureRepository;

    @Autowired
    protected StationsRepository stationsRepository;

    @Autowired
    protected SnippetCache snippetCache;

    @Autowired
    protected ExperimentSnippetRepository experimentSnippetRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected TaskService taskService;

    public boolean isCorrectInit = true;

    @Override
    public String getPlanParamsAsYaml() {
        return getPlanParamsAsYaml_Simple();
    }

    public void toStarted() {
        workbook = planService.toStarted(workbook);
    }

    protected void produceTasks() {
        EnumsApi.PlanValidateStatus status = planService.validate(plan);
        assertEquals(EnumsApi.PlanValidateStatus.OK, status);

        PlanApiData.TaskProducingResultComplex result = planService.createWorkbook(plan.getId(), InputResourceParamUtils.toString(inputResourceParam));
        workbook = result.workbook;
        assertEquals(EnumsApi.PlanProducingStatus.OK, result.planProducingStatus);
        assertNotNull(workbook);
        assertEquals(EnumsApi.WorkbookExecState.NONE.code, workbook.getExecState());


        EnumsApi.PlanProducingStatus producingStatus = planService.toProducing(workbook);
        assertEquals(EnumsApi.PlanProducingStatus.OK, producingStatus);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCING.code, workbook.getExecState());

        List<Object[]> tasks01 = taskCollector.getTasks(result.workbook);
        assertTrue(tasks01.isEmpty());

        long mills;

        List<Object[]> tasks02 = taskCollector.getTasks(result.workbook);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        result = planService.produceAllTasks(true, plan, workbook);
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        workbook = result.workbook;
        assertEquals(EnumsApi.PlanProducingStatus.OK, result.planProducingStatus);
        assertEquals(EnumsApi.WorkbookExecState.PRODUCED.code, workbook.getExecState());

        experiment = experimentCache.findById(experiment.getId());
        assertNotNull(experiment.getWorkbookId());
    }

    protected void checkForCorrectFinishing_withEmpty(ExperimentFeature sequences1Feature) {
        assertEquals(sequences1Feature.experimentId, experiment.getId());
        TaskService.TasksAndAssignToStationResult sequences2 = taskService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getWorkbookId());
        assertNotNull(sequences2);
        assertNotNull(sequences2.getSimpleTask());

        ExperimentFeature feature = experimentFeatureRepository.findById(sequences1Feature.getId()).orElse(null);
        assertNotNull(feature);
        assertEquals(Enums.FeatureExecStatus.error.code, feature.execStatus);
    }

    protected Protocol.AssignedTask.Task getTaskAndAssignToStation_mustBeNewTask() {
        long mills;

        mills = System.currentTimeMillis();
        log.info("Start experimentService.getTaskAndAssignToStation()");
        TaskService.TasksAndAssignToStationResult sequences = taskService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getWorkbookId());
        log.info("experimentService.getTaskAndAssignToStation() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(sequences);
        assertNotNull(sequences.getSimpleTask());
        return sequences.getSimpleTask();
    }

    protected void finishCurrentWithError(int expectedSeqs) {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByStationIdAndResultReceivedIsFalse(station.getId());
        if (expectedSeqs!=0) {
            assertEquals(expectedSeqs, tasks.size());
        }
        for (Task task : tasks) {
            SnippetApiData.SnippetExecResult snippetExecResult = new SnippetApiData.SnippetExecResult(false, -1, "This is sample console output");
            SnippetApiData.SnippetExec snippetExec = new SnippetApiData.SnippetExec(snippetExecResult, null, null);
            String yaml = SnippetExecUtils.toString(snippetExec);

            SimpleTaskExecResult sser = new SimpleTaskExecResult(task.getId(), yaml, MetricsUtils.toString(MetricsUtils.EMPTY_METRICS));
            results.add(sser);
        }

        taskService.storeAllConsoleResults(results);
    }

    protected void finishCurrentWithOk(int expectedTasks) {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByStationIdAndResultReceivedIsFalse(station.getId());
        if (expectedTasks!=0) {
            assertEquals(expectedTasks, tasks.size());
        }
        for (Task task : tasks) {
            SnippetApiData.SnippetExec snippetExec = new SnippetApiData.SnippetExec();
            snippetExec.setExec( new SnippetApiData.SnippetExecResult(true, 0, "This is sample console output. fit"));
            String yaml = SnippetExecUtils.toString(snippetExec);

            SimpleTaskExecResult ster = new SimpleTaskExecResult(task.getId(), yaml, MetricsUtils.toString(MetricsUtils.EMPTY_METRICS));
            results.add(ster);
        }

        taskService.storeAllConsoleResults(results);
    }


}
