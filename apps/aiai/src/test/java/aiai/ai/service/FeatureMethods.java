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

import aiai.ai.Globals;
import aiai.ai.core.ExecProcessService;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.experiment.SimpleTaskExecResult;
import aiai.ai.launchpad.experiment.feature.FeatureExecStatus;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.preparing.PreparingExperiment;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.ai.yaml.metrics.MetricsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@Slf4j
public abstract class FeatureMethods extends PreparingExperiment {

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

    Station station = null;
    Experiment experiment = null;
    boolean isCorrectInit = true;

    protected void checkForCorrectFinishing_withEmpty(ExperimentFeature sequences1Feature) {
        assertEquals(sequences1Feature.experimentId, experiment.getId());
        ExperimentService.TasksAndAssignToStationResult sequences2 = experimentService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getId());
        assertNotNull(sequences2);
        assertNotNull(sequences2.getSimpleTask());

        ExperimentFeature feature = experimentFeatureRepository.findById(sequences1Feature.getId()).orElse(null);
        assertNotNull(feature);
        assertTrue(feature.isFinished);
        assertFalse(feature.isInProgress);
        assertEquals(FeatureExecStatus.error.code, feature.execStatus);
    }

    protected void checkCurrentState_with10sequences() {
        long mills;

        mills = System.currentTimeMillis();
        log.info("Start experimentService.getTaskAndAssignToStation()");
        ExperimentService.TasksAndAssignToStationResult sequences = experimentService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getId());
        log.info("experimentService.getTaskAndAssignToStation() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(sequences);
        assertNotNull(sequences.getSimpleTask());
        assertNotNull(sequences.getSimpleTask());

        mills = System.currentTimeMillis();
        log.info("Start experimentFeatureRepository.findById()");

        if (true) throw new IllegalStateException("Not implemented yet");
/*
        ExperimentFeature feature =
                experimentFeatureRepository.findById(sequences.getFeature().getId()).orElse(null);
        log.info("experimentFeatureRepository.findById() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(feature);
        assertFalse(feature.isFinished);
        assertTrue(feature.isInProgress);
*/
    }

    protected void finishCurrentWithError(int expectedSeqs) {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByStationIdAndIsCompletedIsFalse(station.getId());
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

        taskService.storeAllResults(results);
    }

    protected void finishCurrentWithOk(int expectedSeqs) {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<SimpleTaskExecResult> results = new ArrayList<>();
        List<Task> tasks = taskRepository.findByStationIdAndIsCompletedIsFalse(station.getId());
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

        taskService.storeAllResults(results);
    }


}
