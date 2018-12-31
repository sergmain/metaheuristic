/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.service;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ExecProcessService;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.ExperimentFeature;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.experiment.feature.FeatureExecStatus;
import aiai.ai.launchpad.experiment.task.SimpleTaskExecResult;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.preparing.PreparingExperiment;
import aiai.ai.preparing.PreparingFlow;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.snippet_exec.SnippetExec;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@Slf4j
public abstract class FeatureMethods extends PreparingFlow {

    private static final String TEST_FIT_SNIPPET = "test.fit.snippet";
    private static final String SNIPPET_VERSION_1_0 = "1.0";
    private static final String TEST_PREDICT_SNIPPET = "test.predict.snippet";
    private static final int EXPECTED_FEATURE_PERMUTATIONS_NUMBER = 7;

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

    @Autowired
    private BinaryDataService binaryDataService;

    boolean isCorrectInit = true;

    @Override
    public String getFlowParamsAsYaml() {
        flowYaml = new FlowYaml();
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.inputType = "raw-part-data";
            p.snippetCodes = Collections.singletonList("snippet-01:1.1");
            p.collectResources = false;
            p.outputType = "assembled-raw";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippetCodes = Collections.singletonList("snippet-02:1.1");
            p.collectResources = true;
            p.outputType = "dataset-processing";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "feature processing";
            p.code = "feature-processing";

            p.snippetCodes = Arrays.asList("snippet-03:1.1", "snippet-04:1.1", "snippet-05:1.1");
            p.parallelExec = true;
            p.collectResources = false;
            p.outputType = "feature";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = PreparingExperiment.TEST_EXPERIMENT_CODE_01;

            p.metas.addAll(
                    Arrays.asList(
                            new Process.Meta("assembled-raw", "assembled-raw", null),
                            new Process.Meta("dataset", "dataset-processing", null),
                            new Process.Meta("feature", "feature", null)
                    )
            );

            flowYaml.processes.add(p);
        }

        String yaml = flowYamlUtils.toString(flowYaml);
        System.out.println(yaml);
        return yaml;
    }

    protected void produceTasks() {
        Enums.FlowValidateStatus status = flowService.validate(flow);
        assertEquals(Enums.FlowValidateStatus.OK, status);

        FlowService.TaskProducingResult result = flowService.createFlowInstance(flow, PreparingFlow.INPUT_POOL_CODE);
        flowInstance = result.flowInstance;
        assertEquals(Enums.FlowProducingStatus.OK, result.flowProducingStatus);
        assertNotNull(flowInstance);
        assertEquals(Enums.FlowInstanceExecState.NONE.code, flowInstance.execState);


        Enums.FlowProducingStatus producingStatus = flowService.toProducing(flowInstance);
        assertEquals(Enums.FlowProducingStatus.OK, producingStatus);
        assertEquals(Enums.FlowInstanceExecState.PRODUCING.code, flowInstance.execState);

        List<Object[]> tasks01 = taskCollector.getTasks(result.flowInstance);
        assertTrue(tasks01.isEmpty());

        long mills;

        List<Object[]> tasks02 = taskCollector.getTasks(result.flowInstance);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        result = flowService.produceAllTasks(true, flow, flowInstance);
        log.info("All tasks were produced for " + (System.currentTimeMillis() - mills )+" ms.");

        flowInstance = result.flowInstance;
        assertEquals(Enums.FlowProducingStatus.OK, result.flowProducingStatus);
        assertEquals(Enums.FlowInstanceExecState.PRODUCED.code, flowInstance.execState);

        experiment = experimentCache.findById(experiment.getId());
        assertNotNull(experiment.getFlowInstanceId());
    }

    protected void checkForCorrectFinishing_withEmpty(ExperimentFeature sequences1Feature) {
        assertEquals(sequences1Feature.experimentId, experiment.getId());
        TaskService.TasksAndAssignToStationResult sequences2 = taskService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getFlowInstanceId());
        assertNotNull(sequences2);
        assertNotNull(sequences2.getSimpleTask());

        ExperimentFeature feature = experimentFeatureRepository.findById(sequences1Feature.getId()).orElse(null);
        assertNotNull(feature);
        assertEquals(FeatureExecStatus.error.code, feature.execStatus);
    }

    protected Protocol.AssignedTask.Task getTaskAndAssignToStation_mustBeNewTask() {
        long mills;

        mills = System.currentTimeMillis();
        log.info("Start experimentService.getTaskAndAssignToStation()");
        TaskService.TasksAndAssignToStationResult sequences = taskService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getFlowInstanceId());
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
            ExecProcessService.Result result = new ExecProcessService.Result(false, -1, "This is sample console output");
            SnippetExec snippetExec = new SnippetExec();
            snippetExec.setExec(result);
            String yaml = SnippetExecUtils.toString(snippetExec);

            SimpleTaskExecResult sser = new SimpleTaskExecResult(task.getId(), yaml, MetricsUtils.toString(MetricsUtils.EMPTY_METRICS));
            results.add(sser);
        }

        taskService.storeAllConsoleResults(results);
    }

    protected void finishCurrentWithOk(int expectedSeqs) {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByStationIdAndResultReceivedIsFalse(station.getId());
        if (expectedSeqs!=0) {
            assertEquals(expectedSeqs, tasks.size());
        }
        for (Task task : tasks) {
            SnippetExec snippetExec = new SnippetExec();
            snippetExec.setExec( new ExecProcessService.Result(true, 0, "This is sample console output. fit"));
            String yaml = SnippetExecUtils.toString(snippetExec);

            SimpleTaskExecResult sser = new SimpleTaskExecResult(task.getId(), yaml, MetricsUtils.toString(MetricsUtils.EMPTY_METRICS));
            results.add(sser);
        }

        taskService.storeAllConsoleResults(results);
    }


}
