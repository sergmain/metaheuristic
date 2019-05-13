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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.*;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentCache;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.repositories.*;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.SnippetApiData;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import aiai.apps.commons.CommonConsts;
import aiai.apps.commons.yaml.snippet.SnippetConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

@Slf4j
public abstract class PreparingExperiment {

    private static final String TEST_FIT_SNIPPET = "test.fit.snippet:1.0";
    private static final String TEST_PREDICT_SNIPPET = "test.predict.snippet:1.0";
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

    public Workbook workbook = null;
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

            EnvYaml envYaml = new EnvYaml();
            envYaml.getEnvs().put("python-3", "C:\\Anaconda3\\envs\\python-36\\python.exe" );
            envYaml.getEnvs().put("env-snippet-01:1.1", "python.exe" );
            envYaml.getEnvs().put("env-snippet-02:1.1", "python.exe" );
            envYaml.getEnvs().put("env-snippet-03:1.1", "python.exe" );
            envYaml.getEnvs().put("env-snippet-04:1.1", "python.exe" );
            envYaml.getEnvs().put("env-snippet-05:1.1", "python.exe" );
            StationStatus ss = new StationStatus(envYaml, new GitSourcingService.GitStatusInfo(Enums.GitStatus.not_found), "");
            station.setStatus(StationStatusUtils.toString(ss));

            station.setDescription("Test station. Must be deleted automatically");
            stationsRepository.save(station);
            log.info("stationsRepository.save() was finished for {}", System.currentTimeMillis() - mills);
            stationIdAsStr =  Long.toString(station.getId());

            // Prepare snippets
            mills = System.currentTimeMillis();
            log.info("Start findByCode.save()");
            fitSnippet = snippetRepository.findByCode(TEST_FIT_SNIPPET);
            log.info("findByCode() was finished for {}", System.currentTimeMillis() - mills);

            byte[] bytes = "some program code".getBytes();
            if (fitSnippet == null) {
                fitSnippet = new Snippet();
                SnippetApiData.SnippetConfig sc = new SnippetApiData.SnippetConfig();
                sc.code = TEST_FIT_SNIPPET;
                sc.env = "python-3";
                sc.type = CommonConsts.FIT_TYPE;
                sc.file = "fit-filename.txt";
                sc.checksum = "sha2";
                sc.info.length = bytes.length;

                fitSnippet.setCode(TEST_FIT_SNIPPET);
                fitSnippet.setType(CommonConsts.FIT_TYPE);
                fitSnippet.params = SnippetConfigUtils.toString(sc);

                mills = System.currentTimeMillis();
                log.info("Start snippetRepository.save() #1");
                snippetCache.save(fitSnippet);
                log.info("snippetRepository.save() #1 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #1");
                binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length, EnumsApi.BinaryDataType.SNIPPET, fitSnippet.getCode(), fitSnippet.getCode(),
                        false, null, null);
                log.info("binaryDataService.save() #1 was finished for {}", System.currentTimeMillis() - mills);
            }

            predictSnippet = snippetRepository.findByCode(TEST_PREDICT_SNIPPET);
            if (predictSnippet == null) {
                predictSnippet = new Snippet();
                SnippetApiData.SnippetConfig sc = new SnippetApiData.SnippetConfig();
                sc.code = TEST_PREDICT_SNIPPET;
                sc.type = CommonConsts.PREDICT_TYPE;
                sc.env = "python-3";
                sc.file = "predict-filename.txt";
                sc.info.length = bytes.length;
                sc.checksum = "sha2";

                predictSnippet.setCode(TEST_PREDICT_SNIPPET);
                predictSnippet.setType(CommonConsts.PREDICT_TYPE);
                predictSnippet.params = SnippetConfigUtils.toString(sc);

                mills = System.currentTimeMillis();
                log.info("Start snippetRepository.save() #2");
                snippetCache.save(predictSnippet);
                log.info("stationsRepository.save() #2 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #2");
                binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length, EnumsApi.BinaryDataType.SNIPPET, predictSnippet.getCode(), predictSnippet.getCode(),
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

            experiment.setHyperParams(List.of(ehp1, ehp2, ehp3));

            mills = System.currentTimeMillis();
            log.info("Start experimentRepository.save()");
            experimentRepository.save(experiment);
            log.info("experimentRepository.save() was finished for {}", System.currentTimeMillis() - mills);

            // set snippets for experiment
            ExperimentSnippet es1 = new ExperimentSnippet();
            es1.setExperimentId(experiment.getId());
            es1.setType(CommonConsts.FIT_TYPE);
            es1.setSnippetCode(fitSnippet.getCode());

            ExperimentSnippet es2 = new ExperimentSnippet();
            es2.setExperimentId(experiment.getId());
            es2.setType(CommonConsts.PREDICT_TYPE);
            es2.setSnippetCode(predictSnippet.getCode());

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
        if (workbook!=null) {
            try {
                taskRepository.deleteByWorkbookId(workbook.getId());
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
                binaryDataService.deleteByCodeAndDataType(predictSnippet.getCode(), EnumsApi.BinaryDataType.SNIPPET);
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
                binaryDataService.deleteByCodeAndDataType(fitSnippet.getCode(), EnumsApi.BinaryDataType.SNIPPET);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Was finished correctly");
        log.info("after() was finished for {}", System.currentTimeMillis() - mills);
    }
}
