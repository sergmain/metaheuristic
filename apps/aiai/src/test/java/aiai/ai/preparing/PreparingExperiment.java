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
package aiai.ai.preparing;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.experiment.ExperimentCache;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetCache;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

@Slf4j
public abstract class PreparingExperiment {

    private static final String TEST_FIT_SNIPPET = "test.fit.snippet";
    private static final String SNIPPET_VERSION_1_0 = "1.0";
    private static final String TEST_PREDICT_SNIPPET = "test.predict.snippet";
    public static final String TEST_EXPERIMENT_CODE_01 = "test-experiment-code-01";

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
    protected SnippetRepository snippetRepository;

    @Autowired
    protected ExperimentSnippetRepository experimentSnippetRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected ExperimentCache experimentCache;

    @Autowired
    private BinaryDataService binaryDataService;

    public Station station = null;
    public String stationIdAsStr;

    public FlowInstance flowInstance = null;
    public Experiment experiment = null;
    public boolean isCorrectInit = true;

    public Snippet fitSnippet = null;
    public Snippet predictSnippet = null;

    @Before
    public void beforePreparingExperiment() {
        assertTrue(globals.isUnitTesting);

        try {
            long mills;

            Experiment e = experimentRepository.findByCode(TEST_EXPERIMENT_CODE_01);
            if (e!=null) {
                experimentCache.delete(e);
            }

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
            stationIdAsStr =  Long.toString(station.getId());

            // Prepare snippets
            mills = System.currentTimeMillis();
            log.info("Start findByNameAndSnippetVersion.save()");
            fitSnippet = snippetRepository.findByNameAndSnippetVersion(TEST_FIT_SNIPPET, SNIPPET_VERSION_1_0);
            log.info("findByNameAndSnippetVersion() was finished for {}", System.currentTimeMillis() - mills);

            byte[] bytes = "some program code".getBytes();
            if (fitSnippet == null) {
                fitSnippet = new Snippet();
                fitSnippet.setName(TEST_FIT_SNIPPET);
                fitSnippet.setSnippetVersion(SNIPPET_VERSION_1_0);
                fitSnippet.setEnv("python-3");
                fitSnippet.setType("fit");
                fitSnippet.setChecksum("sha2");
                fitSnippet.length = bytes.length;
                fitSnippet.setFilename("fit-filename.txt");

                mills = System.currentTimeMillis();
                log.info("Start snippetRepository.save() #1");
                snippetCache.save(fitSnippet);
                log.info("snippetRepository.save() #1 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #1");
                binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length, Enums.BinaryDataType.SNIPPET, fitSnippet.getSnippetCode(), fitSnippet.getSnippetCode(),
                        false, null, null);
                log.info("binaryDataService.save() #1 was finished for {}", System.currentTimeMillis() - mills);
            }

            predictSnippet = snippetRepository.findByNameAndSnippetVersion(TEST_PREDICT_SNIPPET, SNIPPET_VERSION_1_0);
            if (predictSnippet == null) {
                predictSnippet = new Snippet();
                predictSnippet.setName(TEST_PREDICT_SNIPPET);
                predictSnippet.setSnippetVersion(SNIPPET_VERSION_1_0);
                predictSnippet.setEnv("python-3");
                predictSnippet.setType("predict");
                predictSnippet.setChecksum("sha2");
                predictSnippet.length = bytes.length;
                predictSnippet.setFilename("predict-filename.txt");

                mills = System.currentTimeMillis();
                log.info("Start snippetRepository.save() #2");
                snippetCache.save(predictSnippet);
                log.info("stationsRepository.save() #2 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #2");
                binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length, Enums.BinaryDataType.SNIPPET, predictSnippet.getSnippetCode(), predictSnippet.getSnippetCode(),
                        false, null,
                        null);
                log.info("binaryDataService.save() #2 was finished for {}", System.currentTimeMillis() - mills);
            }


            mills = System.currentTimeMillis();

            // Prepare experiment
            experiment = new Experiment();
            experiment.setName("Test experiment.");
            experiment.setDescription("Test experiment. Must be deleted automatically.");
            experiment.setCode(TEST_EXPERIMENT_CODE_01);
            experiment.setSeed(42);
            experiment.setAllTaskProduced(false);

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
            es1.setExperimentId(experiment.getId());
            es1.setType("fit");
            es1.setSnippetCode(fitSnippet.getSnippetCode());

            ExperimentSnippet es2 = new ExperimentSnippet();
            es2.setExperimentId(experiment.getId());
            es2.setType("predict");
            es2.setSnippetCode(predictSnippet.getSnippetCode());

            mills = System.currentTimeMillis();
            log.info("Start taskSnippetRepository.saveAll()");
            experimentSnippetRepository.saveAll(Arrays.asList(es1, es2));
            log.info("taskSnippetRepository.saveAll() was finished for {}", System.currentTimeMillis() - mills);

            System.out.println("Was inited correctly");
        }
        catch (Throwable th) {
            th.printStackTrace();
            isCorrectInit = false;
        }
    }

    @After
    public void afterPreparingExperiment() {
        long mills = System.currentTimeMillis();
        log.info("Start after()");
        if (experiment != null && experiment.getId() != null) {
            try {
                experimentFeatureRepository.deleteByExperimentId(experiment.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                experimentRepository.deleteById(experiment.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                experimentSnippetRepository.deleteByExperimentId(experiment.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (flowInstance!=null) {
            try {
                taskRepository.deleteByFlowInstanceId(flowInstance.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (station != null) {
            try {
                stationsRepository.deleteById(station.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (predictSnippet != null) {
            try {
                snippetCache.delete(predictSnippet.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                binaryDataService.deleteByCodeAndDataType(predictSnippet.getSnippetCode(), Enums.BinaryDataType.SNIPPET);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (fitSnippet != null) {
            try {
                snippetCache.delete(fitSnippet.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                binaryDataService.deleteByCodeAndDataType(fitSnippet.getSnippetCode(), Enums.BinaryDataType.SNIPPET);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Was finished correctly");
        log.info("after() was finished for {}", System.currentTimeMillis() - mills);
    }
}
