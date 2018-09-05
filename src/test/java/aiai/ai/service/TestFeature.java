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
import aiai.ai.beans.*;
import aiai.ai.comm.CommandProcessor;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.experiment.SimpleSequenceExecResult;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.repositories.*;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestFeature {

    public static final String TEST_FIT_SNIPPET = "test.fit.snippet";
    public static final String SNIPPET_VERSION_1_0 = "1.0";
    public static final String TEST_PREDICT_SNIPPET = "test.predict.snippet";
    public static final int EXPECTED_FEATURE_PERMUTATIONS_NUMBER = 7;
    @Autowired
    public Globals globals;

    @Autowired
    public ExperimentService experimentService;

    @Autowired
    public ExperimentRepository experimentRepository;

    @Autowired
    public ExperimentFeatureRepository experimentFeatureRepository;

    @Autowired
    public DatasetRepository datasetRepository;

    @Autowired
    public StationsRepository stationsRepository;

    @Autowired
    public SnippetRepository snippetRepository;

    @Autowired
    private ExperimentSequenceRepository experimentSequenceRepository;

    @PostConstruct
    public void preapre_1() {
        globals.isUnitTesting = true;
    }

    private Station station=null;
    private Dataset dataset=null;
    private Experiment experiment=null;
    private Snippet fitSnippet=null;
    private Snippet predictSnippet=null;

    private boolean isCorrectInit = true;

    @Before
    public void before() {
        try {
            // Prepare station
            station = new Station();
            station.setEnv("envs:\n" +
                    "  python-3: E:\\Anaconda3\\envs\\python-36\\python.exe\n" +
                    "");
            station.setDescription("Test station. Must be deleted automatically");
            stationsRepository.save(station);


            // Prepare snippets
            fitSnippet = snippetRepository.findByNameAndSnippetVersion(TEST_FIT_SNIPPET, SNIPPET_VERSION_1_0);
            if (fitSnippet==null) {
                fitSnippet = new Snippet();
                fitSnippet.setName(TEST_FIT_SNIPPET);
                fitSnippet.setSnippetVersion(SNIPPET_VERSION_1_0);
                fitSnippet.setEnv("python-3");
                fitSnippet.setType(SnippetType.fit.toString());
                fitSnippet.setChecksum("sha2");
                fitSnippet.setCode("some program code");
                fitSnippet.setFilename("fit-filename.txt");
                snippetRepository.save(fitSnippet);
            }

            predictSnippet = snippetRepository.findByNameAndSnippetVersion(TEST_PREDICT_SNIPPET, SNIPPET_VERSION_1_0);
            if (predictSnippet==null) {
                predictSnippet = new Snippet();
                predictSnippet.setName(TEST_PREDICT_SNIPPET);
                predictSnippet.setSnippetVersion(SNIPPET_VERSION_1_0);
                predictSnippet.setEnv("python-3");
                predictSnippet.setType(SnippetType.predict.toString());
                predictSnippet.setChecksum("sha2");
                predictSnippet.setCode("some program code");
                predictSnippet.setFilename("predict-filename.txt");
                snippetRepository.save(predictSnippet);
            }

            // Prepare dataset
            dataset = new Dataset();
            dataset.setName("Test dataset");
            dataset.setDescription("Test dataset. must be deleted automatically");
            dataset.setLocked(true);
            dataset.setEditable(false);

            DatasetGroup dg1 = new DatasetGroup();
            dg1.setGroupNumber(1);
            dg1.setCommand("Test cmd #1. Must be deleted automatically");
            dg1.setFeature(true);
            dg1.setFeatureStatus(DatasetGroup.FEATURE_STATUS.OK.value);
            dg1.setDataset(dataset);

            DatasetGroup dg2 = new DatasetGroup();
            dg2.setGroupNumber(2);
            dg2.setCommand("Test cmd #2. Must be deleted automatically");
            dg2.setFeature(true);
            dg2.setFeatureStatus(DatasetGroup.FEATURE_STATUS.OK.value);
            dg2.setDataset(dataset);

            DatasetGroup dg3 = new DatasetGroup();
            dg3.setGroupNumber(3);
            dg3.setCommand("Test cmd #3. Must be deleted automatically");
            dg3.setFeature(true);
            dg3.setFeatureStatus(DatasetGroup.FEATURE_STATUS.OK.value);
            dg3.setDataset(dataset);

            dataset.setDatasetGroups(Arrays.asList(dg1, dg2, dg3));

            datasetRepository.save(dataset);

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

            // set snippets for experiment
            ExperimentSnippet es1 = new ExperimentSnippet();
            es1.setExperiment(experiment);
            es1.setOrder(1);
            es1.setType(SnippetType.fit.toString());
            es1.setSnippetCode(fitSnippet.getSnippetCode());

            ExperimentSnippet es2 = new ExperimentSnippet();
            es2.setExperiment(experiment);
            es2.setOrder(2);
            es2.setType(SnippetType.predict.toString());
            es2.setSnippetCode(predictSnippet.getSnippetCode());

            experiment.setSnippets(Arrays.asList(es1, es2));

            experimentRepository.save(experiment);

            // produce artifacts - features, sequences,...
            experimentService.produceFeaturePermutations(dataset, experiment);

            List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(experiment.getId());
            assertNotNull(features);
            assertEquals(EXPECTED_FEATURE_PERMUTATIONS_NUMBER, features.size());
            for (ExperimentFeature feature : features) {
                assertFalse(feature.isFinished);
            }

            // produce sequences
            experimentService.produceSequences(experiment);

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

        if (experiment!=null) {
            experimentSequenceRepository.deleteByExperimentId(experiment.getId());
            experimentFeatureRepository.deleteByExperimentId(experiment.getId());
            experimentRepository.deleteById(experiment.getId());
        }
        if (dataset!=null) {
            datasetRepository.deleteById(dataset.getId());
        }
        if (station!=null) {
            stationsRepository.deleteById(station.getId());
        }
        if (predictSnippet!=null) {
            snippetRepository.deleteById(predictSnippet.getId());
        }
        if (fitSnippet!=null) {
            snippetRepository.deleteById(fitSnippet.getId());
        }

        System.out.println("Was finished correctly");
    }

    @Test
    public void testFeatureComplition() {
        assertTrue(isCorrectInit);

        ExperimentService.SequencesAndAssignToStationResult sequences = experimentService.getSequencesAndAssignToStation(station.getId(), CommandProcessor.MAX_SEQUENSE_POOL_SIZE);
        assertNotNull(sequences);
        assertNotNull(sequences.getFeature());
        assertNotNull(sequences.getSimpleSequences());
        assertEquals(CommandProcessor.MAX_SEQUENSE_POOL_SIZE, sequences.getSimpleSequences().size());
        assertTrue(sequences.getFeature().isInProgress);


        // this station already got sequences, so don't provide any new
        sequences = experimentService.getSequencesAndAssignToStation(station.getId(), CommandProcessor.MAX_SEQUENSE_POOL_SIZE);
        assertNotNull(sequences);
        // sequences is empty cos we still didn't finish those sequences
        assertTrue(sequences.getSimpleSequences().isEmpty());

        finishCurrent();

        ExperimentService.SequencesAndAssignToStationResult sequences1 = experimentService.getSequencesAndAssignToStation(station.getId(), CommandProcessor.MAX_SEQUENSE_POOL_SIZE);
        assertNotNull(sequences1);
        assertNotNull(sequences1.getFeature());
        assertNotNull(sequences1.getSimpleSequences());
        assertEquals(2, sequences1.getSimpleSequences().size());
        assertTrue(sequences1.getFeature().isInProgress);

        finishCurrent();

        ExperimentService.SequencesAndAssignToStationResult sequences2 = experimentService.getSequencesAndAssignToStation(station.getId(), CommandProcessor.MAX_SEQUENSE_POOL_SIZE);
/*
        assertNotNull(sequences2);
        assertNotNull(sequences2.getFeature());
        assertNotNull(sequences2.getSimpleSequences());
        assertEquals(2, sequences2.getSimpleSequences().size());
        assertTrue(sequences2.getFeature().isInProgress);
*/

        System.out.println();


    }

    private void finishCurrent() {
        // lets report about sequences that all finished with error (errorCode!=0)
        List<SimpleSequenceExecResult> results = new ArrayList<>();
        List<ExperimentSequence> experimentSequences = experimentSequenceRepository.findByStationIdAndIsCompletedIsFalse(station.getId());
        for (ExperimentSequence experimentSequence : experimentSequences) {
            ProcessService.Result result = new ProcessService.Result(false, -1, "This is sample console output");
            SnippetExec snippetExec = new SnippetExec();
            snippetExec.getExecs().put(1, result);
            String yaml = SnippetExecUtils.toString(snippetExec);

            SimpleSequenceExecResult sser = new SimpleSequenceExecResult(experimentSequence.getId(), yaml);
            results.add(sser);
        }

        experimentService.storeAllResults(results);
    }


}
