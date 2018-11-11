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
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.apps.commons.yaml.snippet.SnippetType;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

@Slf4j
public abstract class PreparingExperiment {

    private static final String TEST_FIT_SNIPPET = "test.fit.snippet";
    private static final String SNIPPET_VERSION_1_0 = "1.0";
    private static final String TEST_PREDICT_SNIPPET = "test.predict.snippet";

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
    private ExperimentCache experimentCache;

    @Autowired
    private BinaryDataService binaryDataService;

    public Station station = null;
    public FlowInstance flowInstance = null;
    public Experiment experiment = null;
    public boolean isCorrectInit = true;

    public Snippet fitSnippet = null;
    public Snippet predictSnippet = null;

    @Before
    public void before() {
        try {
            long mills;

            Experiment e = experimentCache.findByCode("test-experiment-code-01");
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
                binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length, Enums.BinaryDataType.SNIPPET, fitSnippet.getSnippetCode(), fitSnippet.getSnippetCode());
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
                binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length, Enums.BinaryDataType.SNIPPET, predictSnippet.getSnippetCode(), predictSnippet.getSnippetCode());
                log.info("binaryDataService.save() #2 was finished for {}", System.currentTimeMillis() - mills);
            }


            mills = System.currentTimeMillis();

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
            experiment.setCode("test-experiment-code-01");
            experiment.setSeed(42);
            experiment.setExecState(Enums.TaskExecState.STARTED.code);
            experiment.setLaunched(true);
            experiment.setLaunchedOn(System.currentTimeMillis());
            experiment.setAllSequenceProduced(false);
            experiment.setFlowInstanceId(flowInstance.getId());

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
            experimentFeatureRepository.deleteByExperimentId(experiment.getId());
            experimentRepository.deleteById(experiment.getId());
        }
        if (flowInstance!=null) {
            taskRepository.deleteByFlowInstanceId(flowInstance.getId());
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
}
