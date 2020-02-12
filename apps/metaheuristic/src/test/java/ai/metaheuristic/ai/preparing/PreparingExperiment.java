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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.Experiment;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.variable.VariableService;
import ai.metaheuristic.ai.launchpad.variable.GlobalVariableService;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentCache;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentRepository;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetDataService;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYamlUtils;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.launchpad.ExecContext;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ai.metaheuristic.api.data.experiment.ExperimentParamsYaml.HyperParam;
import static org.junit.Assert.assertTrue;

@Slf4j
public abstract class PreparingExperiment {

    public static final String TEST_FIT_SNIPPET = "test.fit.snippet:1.0";
    public static final String TEST_PREDICT_SNIPPET = "test.predict.snippet:1.0";
    public static final String TEST_FITTING_SNIPPET = "test.fitting.snippet:1.0";
    static final String TEST_EXPERIMENT_CODE_01 = "test-experiment-code-01";

    @Autowired
    protected Globals globals;

    @Autowired
    protected ExperimentService experimentService;

    @Autowired
    protected ExperimentRepository experimentRepository;

    @Autowired
    protected StationCache stationCache;

    @Autowired
    protected SnippetCache snippetCache;

    @Autowired
    protected SnippetRepository snippetRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected ExperimentCache experimentCache;

    @Autowired
    private VariableService variableService;

    @Autowired
    public GlobalVariableService globalVariableService;

    @Autowired
    private SnippetDataService snippetDataService;

    public Station station = null;
    public String stationIdAsStr;

    public ExecContext execContext = null;
    public Experiment experiment = null;
    public boolean isCorrectInit = true;

    public Snippet fitSnippet = null;
    public Snippet predictSnippet = null;

    @Before
    public void beforePreparingExperiment() {
        assertTrue(globals.isUnitTesting);

        try {
            long mills;

            Long experimentId = experimentRepository.findIdByCode(TEST_EXPERIMENT_CODE_01);
            Experiment e = experimentId!=null ? experimentCache.findById(experimentId) : null;
            if (e!=null) {
                experimentCache.delete(e);
            }

            // Prepare station
            station = new Station();
            mills = System.currentTimeMillis();
            log.info("Start stationsRepository.saveAndFlush()");

            EnvYaml envYaml = new EnvYaml();
            envYaml.getEnvs().put("python-3", "C:\\Anaconda3\\envs\\python-36\\python.exe" );
            envYaml.getEnvs().put("env-snippet-01:1.1", "python.exe" );
            envYaml.getEnvs().put("env-snippet-02:1.1", "python.exe" );
            envYaml.getEnvs().put("env-snippet-03:1.1", "python.exe" );
            envYaml.getEnvs().put("env-snippet-04:1.1", "python.exe" );
            envYaml.getEnvs().put("env-snippet-05:1.1", "python.exe" );
            StationStatusYaml ss = new StationStatusYaml(new ArrayList<>(), envYaml,
                    new GitSourcingService.GitStatusInfo(Enums.GitStatus.not_found), "",
                    ""+ UUID.randomUUID().toString(), System.currentTimeMillis(),
                    "[unknown]", "[unknown]", null, false,
                    TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion(), EnumsApi.OS.unknown);
            station.setStatus(StationStatusYamlUtils.BASE_YAML_UTILS.toString(ss));

            station.setDescription("Test station. Must be deleted automatically");
            stationCache.save(station);
            log.info("stationsRepository.save() was finished for {}", System.currentTimeMillis() - mills);
            stationIdAsStr =  Long.toString(station.getId());

            // Prepare snippets
            mills = System.currentTimeMillis();
            log.info("Start findByCode.save()");
            fitSnippet = snippetRepository.findByCodeForUpdate(TEST_FIT_SNIPPET);
            log.info("findByCode() was finished for {}", System.currentTimeMillis() - mills);

            byte[] bytes = "some program code".getBytes();
            if (fitSnippet == null) {
                fitSnippet = new Snippet();
                SnippetConfigYaml sc = new SnippetConfigYaml();
                sc.code = TEST_FIT_SNIPPET;
                sc.env = "python-3";
                sc.type = CommonConsts.FIT_TYPE;
                sc.file = "fit-filename.txt";
                sc.checksum = "sha2";
                sc.info.length = bytes.length;

                fitSnippet.setCode(TEST_FIT_SNIPPET);
                fitSnippet.setType(CommonConsts.FIT_TYPE);
                fitSnippet.params = SnippetConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

                mills = System.currentTimeMillis();
                log.info("Start snippetRepository.save() #1");
                snippetCache.save(fitSnippet);
                log.info("snippetRepository.save() #1 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #1");
                snippetDataService.save(new ByteArrayInputStream(bytes), bytes.length, fitSnippet.getCode());
                log.info("binaryDataService.save() #1 was finished for {}", System.currentTimeMillis() - mills);
            }

            predictSnippet = snippetRepository.findByCodeForUpdate(TEST_PREDICT_SNIPPET);
            if (predictSnippet == null) {
                predictSnippet = new Snippet();
                SnippetConfigYaml sc = new SnippetConfigYaml();
                sc.code = TEST_PREDICT_SNIPPET;
                sc.type = CommonConsts.PREDICT_TYPE;
                sc.env = "python-3";
                sc.file = "predict-filename.txt";
                sc.info.length = bytes.length;
                sc.checksum = "sha2";

                predictSnippet.setCode(TEST_PREDICT_SNIPPET);
                predictSnippet.setType(CommonConsts.PREDICT_TYPE);
                predictSnippet.params = SnippetConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

                mills = System.currentTimeMillis();
                log.info("Start snippetRepository.save() #2");
                snippetCache.save(predictSnippet);
                log.info("stationsRepository.save() #2 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #2");
                snippetDataService.save(new ByteArrayInputStream(bytes), bytes.length, predictSnippet.getCode());
                log.info("binaryDataService.save() #2 was finished for {}", System.currentTimeMillis() - mills);
            }


            mills = System.currentTimeMillis();

            // Prepare experiment
            experiment = new Experiment();
            experiment.setCode(TEST_EXPERIMENT_CODE_01);

            ExperimentParamsYaml epy = new ExperimentParamsYaml();
            epy.experimentYaml.setCode(TEST_EXPERIMENT_CODE_01);
            epy.experimentYaml.setName("Test experiment.");
            epy.experimentYaml.setDescription("Test experiment. Must be deleted automatically.");
            epy.experimentYaml.setSeed(42);
            epy.processing.setAllTaskProduced(false);

            // set hyper params for experiment
            HyperParam ehp1 = new HyperParam();
            ehp1.setKey("RNN");
            ehp1.setValues("[LSTM, GRU, SimpleRNN]");

            HyperParam ehp2 = new HyperParam();
            ehp2.setKey("batches");
            ehp2.setValues("[20, 40]");

            HyperParam ehp3 = new HyperParam();
            ehp3.setKey("aaa");
            ehp3.setValues("[7, 13]");

            epy.experimentYaml.setHyperParams(List.of(ehp1, ehp2, ehp3));

            // set snippets for experiment
            epy.experimentYaml.fitSnippet = fitSnippet.getCode();
            epy.experimentYaml.predictSnippet = predictSnippet.getCode();

            experiment.updateParams(epy);

            mills = System.currentTimeMillis();
            log.info("Start experimentRepository.save()");
            experimentRepository.save(experiment);
            log.info("experimentRepository.save() was finished for {}", System.currentTimeMillis() - mills);

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
                experimentRepository.deleteById(experiment.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (execContext !=null) {
            try {
                taskRepository.deleteByWorkbookId(execContext.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (station != null) {
            try {
                stationCache.deleteById(station.getId());
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
                snippetDataService.deleteBySnippetCode(predictSnippet.getCode());
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
                snippetDataService.deleteBySnippetCode(fitSnippet.getCode());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Was finished correctly");
        log.info("after() was finished for {}", System.currentTimeMillis() - mills);
    }
}
