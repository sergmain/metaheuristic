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
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentCache;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ai.metaheuristic.api.data.experiment.ExperimentParamsYaml.HyperParam;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public abstract class PreparingCore {

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
    protected ProcessorCache processorCache;

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

    public Processor processor = null;
    public String processorIdAsStr;

    public Experiment experiment = null;
    public boolean isCorrectInit = true;

    public Function fitFunction = null;
    public Function predictFunction = null;

    @BeforeEach
    public void beforePreparingCore() {
        assertTrue(globals.isUnitTesting);

        try {
            long mills;

            Long experimentId = experimentRepository.findIdByCode(TEST_EXPERIMENT_CODE_01);
            Experiment e = experimentId!=null ? experimentCache.findById(experimentId) : null;
            if (e!=null) {
                experimentCache.delete(e);
            }

            // Prepare processor
            Processor p = new Processor();
            mills = System.currentTimeMillis();
            log.info("Start processorRepository.saveAndFlush()");

            EnvYaml envYaml = new EnvYaml();
            envYaml.getEnvs().put("python-3", "C:\\Anaconda3\\envs\\python-36\\python.exe" );
            envYaml.getEnvs().put("env-function-01:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-02:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-03:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-04:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-05:1.1", "python.exe" );
            ProcessorStatusYaml ss = new ProcessorStatusYaml(new ArrayList<>(), envYaml,
                    new GitSourcingService.GitStatusInfo(Enums.GitStatus.not_found), "",
                    ""+ UUID.randomUUID().toString(), System.currentTimeMillis(),
                    "[unknown]", "[unknown]", null, false,
                    TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion(), EnumsApi.OS.unknown);
            p.setStatus(ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss));

            p.setDescription("Test processor. Must be deleted automatically");
            processor = processorCache.save(p);
            log.info("processorRepository.save() was finished for {}", System.currentTimeMillis() - mills);
            processorIdAsStr =  Long.toString(p.getId());

            // Prepare functions
            mills = System.currentTimeMillis();
            byte[] bytes = "some program code".getBytes();

            log.info("Start findByCode.save()");
            Function function = functionRepository.findByCodeForUpdate(TEST_FIT_FUNCTION);
            log.info("findByCode() was finished for {}", System.currentTimeMillis() - mills);
            if (function == null) {
                function = new Function();
                FunctionConfigYaml sc = new FunctionConfigYaml();
                sc.code = TEST_FIT_FUNCTION;
                sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
                sc.env = "python-3";
                sc.type = CommonConsts.FIT_TYPE;
                sc.file = "fit-filename.txt";
                sc.checksum = "sha2";
                sc.info = new FunctionConfigYaml.FunctionInfo(false, bytes.length);

                function.setCode(TEST_FIT_FUNCTION);
                function.setType(CommonConsts.FIT_TYPE);
                function.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

                mills = System.currentTimeMillis();
                log.info("Start functionRepository.save() #1");
                functionCache.save(function);
                log.info("functionRepository.save() #1 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #1");
                functionDataService.save(new ByteArrayInputStream(bytes), bytes.length, function.getCode());
                log.info("binaryDataService.save() #1 was finished for {}", System.currentTimeMillis() - mills);
            }
            fitFunction = function;

            Function predictFunction = functionRepository.findByCodeForUpdate(TEST_PREDICT_FUNCTION);
            if (predictFunction == null) {
                predictFunction = new Function();
                FunctionConfigYaml sc = new FunctionConfigYaml();
                sc.code = TEST_PREDICT_FUNCTION;
                sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
                sc.type = CommonConsts.PREDICT_TYPE;
                sc.env = "python-3";
                sc.file = "predict-filename.txt";
                sc.info = new FunctionConfigYaml.FunctionInfo(false, bytes.length);
                sc.checksum = "sha2";

                predictFunction.setCode(TEST_PREDICT_FUNCTION);
                predictFunction.setType(CommonConsts.PREDICT_TYPE);
                predictFunction.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

                mills = System.currentTimeMillis();
                log.info("Start functionRepository.save() #2");
                functionCache.save(predictFunction);
                log.info("processorRepository.save() #2 was finished for {}", System.currentTimeMillis() - mills);

                mills = System.currentTimeMillis();
                log.info("Start binaryDataService.save() #2");
                functionDataService.save(new ByteArrayInputStream(bytes), bytes.length, predictFunction.getCode());
                log.info("binaryDataService.save() #2 was finished for {}", System.currentTimeMillis() - mills);
            }
            this.predictFunction = predictFunction;


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

            epy.experimentYaml.hyperParams.addAll(List.of(ehp1, ehp2, ehp3));

            // set functions for experiment
            epy.experimentYaml.fitFunction = fitFunction.getCode();
            epy.experimentYaml.predictFunction = this.predictFunction.getCode();

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
        assertTrue(isCorrectInit);
    }

    @AfterEach
    public void afterPreparingCore() {
        long mills = System.currentTimeMillis();
        log.info("Start after()");
        if (experiment != null) {
            try {
                experimentRepository.deleteById(experiment.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (processor != null) {
            try {
                processorCache.deleteById(processor.getId());
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
