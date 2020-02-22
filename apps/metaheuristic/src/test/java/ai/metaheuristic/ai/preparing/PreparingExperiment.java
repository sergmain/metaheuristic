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
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.beans.Station;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentCache;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.station.StationCache;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
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

    public static final String TEST_FIT_FUNCTION = "test.fit.function:1.0";
    public static final String TEST_PREDICT_FUNCTION = "test.predict.function:1.0";
    public static final String TEST_FITTING_FUNCTION = "test.fitting.function:1.0";
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
    protected FunctionCache functionCache;

    @Autowired
    protected FunctionRepository functionRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected ExperimentCache experimentCache;

    @Autowired
    private VariableService variableService;

    @Autowired
    public GlobalVariableService globalVariableService;

    @Autowired
    private FunctionDataService functionDataService;

    public Station station = null;
    public String stationIdAsStr;

    public ExecContext execContext = null;
    public Experiment experiment = null;
    public boolean isCorrectInit = true;

    public Function fitFunction = null;
    public Function predictFunction = null;

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
            envYaml.getEnvs().put("env-function-01:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-02:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-03:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-04:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-05:1.1", "python.exe" );
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

            // Prepare functions
            mills = System.currentTimeMillis();
            log.info("Start findByCode.save()");
            fitFunction = functionRepository.findByCodeForUpdate(TEST_FIT_FUNCTION);
            log.info("findByCode() was finished for {}", System.currentTimeMillis() - mills);

            byte[] bytes = "some program code".getBytes();
            if (fitFunction == null) {
                fitFunction = new Function();
                FunctionConfigYaml sc = new FunctionConfigYaml();
                sc.code = TEST_FIT_FUNCTION;
                sc.env = "python-3";
                sc.type = CommonConsts.FIT_TYPE;
                sc.file = "fit-filename.txt";
                sc.checksum = "sha2";
                sc.info.length = bytes.length;

                fitFunction.setCode(TEST_FIT_FUNCTION);
                fitFunction.setType(CommonConsts.FIT_TYPE);
                fitFunction.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

                mills = System.currentTimeMillis();
                log.info("Start functionRepository.save() #1");
                functionCache.save(fitFunction);
                log.info("functionRepository.save() #1 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #1");
                functionDataService.save(new ByteArrayInputStream(bytes), bytes.length, fitFunction.getCode());
                log.info("binaryDataService.save() #1 was finished for {}", System.currentTimeMillis() - mills);
            }

            predictFunction = functionRepository.findByCodeForUpdate(TEST_PREDICT_FUNCTION);
            if (predictFunction == null) {
                predictFunction = new Function();
                FunctionConfigYaml sc = new FunctionConfigYaml();
                sc.code = TEST_PREDICT_FUNCTION;
                sc.type = CommonConsts.PREDICT_TYPE;
                sc.env = "python-3";
                sc.file = "predict-filename.txt";
                sc.info.length = bytes.length;
                sc.checksum = "sha2";

                predictFunction.setCode(TEST_PREDICT_FUNCTION);
                predictFunction.setType(CommonConsts.PREDICT_TYPE);
                predictFunction.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

                mills = System.currentTimeMillis();
                log.info("Start functionRepository.save() #2");
                functionCache.save(predictFunction);
                log.info("stationsRepository.save() #2 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #2");
                functionDataService.save(new ByteArrayInputStream(bytes), bytes.length, predictFunction.getCode());
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

            // set functions for experiment
            epy.experimentYaml.fitFunction = fitFunction.getCode();
            epy.experimentYaml.predictFunction = predictFunction.getCode();

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
                taskRepository.deleteByExecContextId(execContext.getId());
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
        if (predictFunction != null) {
            try {
                functionCache.delete(predictFunction.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                functionDataService.deleteByFunctionCode(predictFunction.getCode());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (fitFunction != null) {
            try {
                functionCache.delete(fitFunction.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                functionDataService.deleteByFunctionCode(fitFunction.getCode());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Was finished correctly");
        log.info("after() was finished for {}", System.currentTimeMillis() - mills);
    }
}
