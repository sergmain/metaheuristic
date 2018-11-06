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
import aiai.ai.core.ArtifactStatus;
import aiai.ai.core.ExecProcessService;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.experiment.dataset.DatasetCache;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.experiment.SimpleTaskExecResult;
import aiai.ai.launchpad.experiment.feature.FeatureExecStatus;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.apps.commons.yaml.snippet.SnippetType;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@Slf4j
public abstract class TestFeature {

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
    protected DatasetRepository datasetRepository;

    @Autowired
    protected StationsRepository stationsRepository;

/*
    @Autowired
    protected SnippetRepository snippetRepository;

*/
    @Autowired
    protected SnippetCache snippetCache;

    @Autowired
    protected ExperimentSnippetRepository experimentSnippetRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    private DatasetCache datasetCache;

    @Autowired
    private BinaryDataService binaryDataService;

    Station station = null;
    Experiment experiment = null;
    boolean isCorrectInit = true;

    private Dataset dataset = null;
    private Snippet fitSnippet = null;
    private Snippet predictSnippet = null;

    @PostConstruct
    public void preapre_1() {
    }

    @Before
    public void before() {
        try {
            long mills;

            // Prepare station
            station = new Station();
            mills = System.currentTimeMillis();
            log.info("Start stationsRepository.save()");
            station.setEnv("envs:\n" +
                    "  python-3: C:\\Anaconda3\\envs\\python-36\\python.exe\n" +
                    "");
            station.setDescription("Test station. Must be deleted automatically");
            stationsRepository.save(station);
            log.info("stationsRepository.save() was finished for {}", System.currentTimeMillis() - mills);


            // Prepare snippets
            mills = System.currentTimeMillis();
            log.info("Start findByNameAndSnippetVersion.save()");
            fitSnippet = snippetCache.findByNameAndSnippetVersion(TEST_FIT_SNIPPET, SNIPPET_VERSION_1_0);
            log.info("findByNameAndSnippetVersion() was finished for {}", System.currentTimeMillis() - mills);

            byte[] bytes = "some program code".getBytes();
            if (fitSnippet == null) {
                fitSnippet = new Snippet();
                fitSnippet.setName(TEST_FIT_SNIPPET);
                fitSnippet.setSnippetVersion(SNIPPET_VERSION_1_0);
                fitSnippet.setEnv("python-3");
                fitSnippet.setType(SnippetType.fit.toString());
                fitSnippet.setChecksum("sha2");
                fitSnippet.length = bytes.length;
                fitSnippet.setFilename("fit-filename.txt");

                mills = System.currentTimeMillis();
                log.info("Start snippetRepository.save() #1");
                snippetCache.save(fitSnippet);
                log.info("snippetRepository.save() #1 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #1");
                binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length, fitSnippet.getId(), Enums.BinaryDataType.SNIPPET);
                log.info("binaryDataService.save() #1 was finished for {}", System.currentTimeMillis() - mills);
            }

            predictSnippet = snippetCache.findByNameAndSnippetVersion(TEST_PREDICT_SNIPPET, SNIPPET_VERSION_1_0);
            if (predictSnippet == null) {
                predictSnippet = new Snippet();
                predictSnippet.setName(TEST_PREDICT_SNIPPET);
                predictSnippet.setSnippetVersion(SNIPPET_VERSION_1_0);
                predictSnippet.setEnv("python-3");
                predictSnippet.setType(SnippetType.predict.toString());
                predictSnippet.setChecksum("sha2");
                predictSnippet.length = bytes.length;
                predictSnippet.setFilename("predict-filename.txt");

                mills = System.currentTimeMillis();
                log.info("Start snippetRepository.save() #2");
                snippetCache.save(predictSnippet);
                log.info("stationsRepository.save() #2 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #2");
                binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length, predictSnippet.getId(), Enums.BinaryDataType.SNIPPET);
                log.info("binaryDataService.save() #2 was finished for {}", System.currentTimeMillis() - mills);
            }

            // Prepare dataset
            dataset = new Dataset();
            dataset.setName("Test dataset");
            dataset.setDescription("Test dataset. must be deleted automatically");
            dataset.setLocked(true);
            dataset.setEditable(false);

            Feature dg1 = new Feature();
            dg1.setFeatureOrder(1);
            dg1.setDescription("Test cmd #1. Must be deleted automatically");
            dg1.setFeatureStatus(ArtifactStatus.OK.value);
            dg1.setDataset(dataset);

            Feature dg2 = new Feature();
            dg2.setFeatureOrder(2);
            dg2.setDescription("Test cmd #2. Must be deleted automatically");
            dg2.setFeatureStatus(ArtifactStatus.OK.value);
            dg2.setDataset(dataset);

            Feature dg3 = new Feature();
            dg3.setFeatureOrder(3);
            dg3.setDescription("Test cmd #3. Must be deleted automatically");
            dg3.setFeatureStatus(ArtifactStatus.OK.value);
            dg3.setDataset(dataset);

            dataset.setFeatures(Arrays.asList(dg1, dg2, dg3));

            mills = System.currentTimeMillis();
            log.info("Start datasetCache.save()");
            datasetCache.save(dataset);
            log.info("datasetCache.save() was finished for {}", System.currentTimeMillis() - mills);

            // Prepare experiment
            experiment = new Experiment();
            String epoch = "10";
            experiment.setEpoch(epoch);
            ExperimentUtils.NumberOfVariants numberOfVariants = ExperimentUtils.getNumberOfVariants(experiment.getEpoch());
            if (!numberOfVariants.status) {
                throw new IllegalStateException("Something wrong with ExperimentUtils.getNumberOfVariants()");
            }
            experiment.setEpochVariant(numberOfVariants.getCount());
            experiment.setName("Test experiment.");
            experiment.setDescription("Test experiment. Must be deleted automatically.");
            experiment.setSeed(42);
            experiment.setExecState(Enums.TaskExecState.STARTED.code);
            experiment.setLaunched(true);
            experiment.setLaunchedOn(System.currentTimeMillis());
            experiment.setAllSequenceProduced(false);
            experiment.setDatasetId(dataset.getId());

            // set hyper params for experiment
            ExperimentHyperParams ehp1 = new ExperimentHyperParams();
            ehp1.setKey("RNN");
            ehp1.setValues("[LSTM, GRU, SimpleRNN]");
            ehp1.setExperiment(experiment);

            ExperimentHyperParams ehp2 = new ExperimentHyperParams();
            ehp2.setKey("batches");
            ehp2.setValues("[20, 40]");
            ehp2.setExperiment(experiment);

            ExperimentHyperParams ehp3 = new ExperimentHyperParams();
            ehp3.setKey("aaa");
            ehp3.setValues("[7, 13]");
            ehp3.setExperiment(experiment);

            experiment.setHyperParams(Arrays.asList(ehp1, ehp2, ehp3));

            mills = System.currentTimeMillis();
            log.info("Start experimentRepository.save()");
            experimentRepository.save(experiment);
            log.info("experimentRepository.save() was finished for {}", System.currentTimeMillis() - mills);

            // set snippets for experiment
            ExperimentSnippet es1 = new ExperimentSnippet();
            es1.setRefId(experiment.getId());
            es1.setTaskType(Enums.TaskType.Experiment.code);
            es1.setType(SnippetType.fit.toString());
            es1.setSnippetCode(fitSnippet.getSnippetCode());

            ExperimentSnippet es2 = new ExperimentSnippet();
            es2.setRefId(experiment.getId());
            es2.setTaskType(Enums.TaskType.Experiment.code);
            es2.setType(SnippetType.predict.toString());
            es2.setSnippetCode(predictSnippet.getSnippetCode());

            mills = System.currentTimeMillis();
            log.info("Start taskSnippetRepository.saveAll()");
            experimentSnippetRepository.saveAll(Arrays.asList(es1, es2));
            log.info("taskSnippetRepository.saveAll() was finished for {}", System.currentTimeMillis() - mills);

            // produce artifacts - features, sequences,...
            mills = System.currentTimeMillis();
            log.info("Start experimentService.produceFeaturePermutations()");
            experimentService.produceFeaturePermutations(dataset, experiment);
            log.info("experimentService.produceFeaturePermutations() was finished for {}", System.currentTimeMillis() - mills);

            mills = System.currentTimeMillis();
            log.info("Start experimentFeatureRepository.findByExperimentId()");
            List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(experiment.getId());
            log.info("experimentFeatureRepository.findByExperimentId() was finished for {}", System.currentTimeMillis() - mills);

            assertNotNull(features);
            assertEquals(EXPECTED_FEATURE_PERMUTATIONS_NUMBER, features.size());
            for (ExperimentFeature feature : features) {
                assertFalse(feature.isFinished);
            }

            mills = System.currentTimeMillis();
            log.info("Start experimentService.produceTasks()");
            // produce sequences
            List<String> codes = new ArrayList<>();
            experimentService.produceTasks(experiment, codes);
            log.info("experimentService.produceTasks() was finished for {}", System.currentTimeMillis() - mills);

            // some global final check
            assertEquals(EXPECTED_FEATURE_PERMUTATIONS_NUMBER, experimentFeatureRepository.findByExperimentId(experiment.getId()).size());

            System.out.println("Was inited correctly");
        }
        catch (Throwable th) {
            th.printStackTrace();
            isCorrectInit = false;
        }
    }

    @After
    public void after() {

        long mills = System.currentTimeMillis();
        log.info("Start after()");
        if (experiment != null) {
            taskRepository.deleteByExperimentId(experiment.getId());
            experimentFeatureRepository.deleteByExperimentId(experiment.getId());
            experimentRepository.deleteById(experiment.getId());
        }
        if (dataset != null) {
            datasetCache.delete(dataset.getId());
        }
        if (station != null) {
            stationsRepository.deleteById(station.getId());
        }
        if (predictSnippet != null) {
            snippetCache.delete(predictSnippet.getId());
        }
        if (fitSnippet != null) {
            snippetCache.delete(fitSnippet.getId());
        }

        System.out.println("Was finished correctly");
        log.info("after() was finished for {}", System.currentTimeMillis() - mills);
    }

    protected void checkForCorrectFinishing_withEmpty(ExperimentFeature sequences1Feature) {
        assertEquals(sequences1Feature.experimentId, experiment.getId());
        ExperimentService.SequencesAndAssignToStationResult sequences2 = experimentService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getId());
        assertNotNull(sequences2);
        assertTrue(sequences2.getSimpleTasks().isEmpty());
        assertNull(sequences2.getFeature());


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
        ExperimentService.SequencesAndAssignToStationResult sequences = experimentService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getId());
        log.info("experimentService.getTaskAndAssignToStation() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(sequences);
        assertNotNull(sequences.getFeature());
        assertNotNull(sequences.getSimpleTasks());
        assertEquals(1, sequences.getSimpleTasks().size());
        assertTrue(sequences.getFeature().isInProgress);

        mills = System.currentTimeMillis();
        log.info("Start experimentFeatureRepository.findById()");
        ExperimentFeature feature = experimentFeatureRepository.findById(sequences.getFeature().getId()).orElse(null);
        log.info("experimentFeatureRepository.findById() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(feature);
        assertFalse(feature.isFinished);
        assertTrue(feature.isInProgress);
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

        experimentService.storeAllResults(results);
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

        experimentService.storeAllResults(results);
    }


}
