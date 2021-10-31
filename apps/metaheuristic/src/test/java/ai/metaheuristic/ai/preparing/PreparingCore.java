/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentCache;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
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
import java.util.UUID;

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
    protected FunctionService functionService;

    @Autowired
    protected FunctionRepository functionRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected ExperimentCache experimentCache;

    @Autowired
    public VariableService variableService;

    @Autowired
    public GlobalVariableService globalVariableService;

    @Autowired
    private FunctionDataService functionDataService;

    @Autowired
    public ExecContextFSM execContextFSM;

    @Autowired
    private ProcessorTopLevelService processorTopLevelService;

    @Autowired
    private ProcessorTransactionService processorTransactionService;

    @Autowired
    private TxSupportForTestingService txSupportForTestingService;

    public Processor processor = null;
    public String processorIdAsStr;

    public boolean isCorrectInit = true;

    public Function fitFunction = null;
    public Function predictFunction = null;

    @BeforeEach
    public void beforePreparingCore() {
        assertTrue(globals.testing);

        try {
            long mills;

            Long experimentId = experimentRepository.findIdByCode(TEST_EXPERIMENT_CODE_01);
            Experiment e = experimentId!=null ? experimentCache.findById(experimentId) : null;
            if (e!=null) {
                experimentCache.delete(e);
            }

            ProcessorStatusYaml.Env envYaml = new ProcessorStatusYaml.Env();
            envYaml.quotas.disabled = true;

            envYaml.getEnvs().put("python-3", "C:\\Anaconda3\\envs\\python-36\\python.exe" );
            envYaml.getEnvs().put("env-function-01:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-02:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-03:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-04:1.1", "python.exe" );
            envYaml.getEnvs().put("env-function-05:1.1", "python.exe" );
            ProcessorStatusYaml ss = new ProcessorStatusYaml(new ArrayList<>(), envYaml,
                    new GitSourcingService.GitStatusInfo(Enums.GitStatus.not_found), "",
                    ""+ UUID.randomUUID(), System.currentTimeMillis(),
                    Consts.UNKNOWN_INFO, Consts.UNKNOWN_INFO, null, false,
                    TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion(), EnumsApi.OS.unknown, Consts.UNKNOWN_INFO, null, null);
            final String description = "Test processor. Must be deleted automatically";

            mills = System.currentTimeMillis();
            log.info("Start processorRepository.saveAndFlush()");


            // Prepare processor
            processor = processorTransactionService.createProcessor(description, null, ss);
            log.info("processorRepository.save() was finished for {} milliseconds", System.currentTimeMillis() - mills);
            processorIdAsStr =  Long.toString(processor.getId());

            // Prepare functions
            mills = System.currentTimeMillis();
            byte[] bytes = "some program code".getBytes();

            log.info("Start findByCode.save()");
            Function function = functionRepository.findByCode(TEST_FIT_FUNCTION);
            log.info("findByCode() was finished for {} milliseconds", System.currentTimeMillis() - mills);
            if (function == null) {

                FunctionConfigYaml sc = new FunctionConfigYaml();
                sc.code = TEST_FIT_FUNCTION;
                sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
                sc.env = "python-3";
                sc.type = CommonConsts.FIT_TYPE;
                sc.file = "fit-filename.txt";

                mills = System.currentTimeMillis();
                log.info("Start functionRepository.save() #1");
                function = functionService.persistFunction(sc, new ByteArrayInputStream(bytes), bytes.length);
                log.info("functionRepository.save() #1 was finished for {} milliseconds", System.currentTimeMillis() - mills);
            }
            fitFunction = function;

            Function predictFunction = functionRepository.findByCode(TEST_PREDICT_FUNCTION);
            if (predictFunction == null) {
                predictFunction = new Function();
                FunctionConfigYaml sc = new FunctionConfigYaml();
                sc.code = TEST_PREDICT_FUNCTION;
                sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
                sc.type = CommonConsts.PREDICT_TYPE;
                sc.env = "python-3";
                sc.file = "predict-filename.txt";

                predictFunction.setCode(TEST_PREDICT_FUNCTION);
                predictFunction.setType(CommonConsts.PREDICT_TYPE);
                predictFunction.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

                mills = System.currentTimeMillis();
                log.info("Start functionRepository.save() #2");
                functionService.persistFunction(sc, new ByteArrayInputStream(bytes), bytes.length);
                log.info("processorRepository.save() #2 was finished for {} milliseconds", System.currentTimeMillis() - mills);

            }
            this.predictFunction = predictFunction;


            mills = System.currentTimeMillis();

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
        if (processor != null) {
            try {
                processorTopLevelService.deleteProcessorById(processor.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (predictFunction != null) {
            try {
                txSupportForTestingService.deleteFunctionById(predictFunction.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (fitFunction != null) {
            try {
                txSupportForTestingService.deleteFunctionById(fitFunction.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        System.out.println("Was finished correctly");
        log.info("after() was finished for {} milliseconds", System.currentTimeMillis() - mills);
    }
}
