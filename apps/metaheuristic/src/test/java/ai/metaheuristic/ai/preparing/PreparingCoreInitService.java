/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * @author Serge
 * Date: 1/6/2022
 * Time: 2:57 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class PreparingCoreInitService {

    private final ExperimentRepository experimentRepository;
    private final FunctionService functionService;
    private final FunctionRepository functionRepository;
    private final ExperimentCache experimentCache;
    private final ProcessorTopLevelService processorTopLevelService;
    private final ProcessorTransactionService processorTransactionService;
    private final TxSupportForTestingService txSupportForTestingService;

    public PreparingData.PreparingCodeData beforePreparingCore() {
        PreparingData.PreparingCodeData data = new PreparingData.PreparingCodeData();

        long mills;

        Long experimentId = experimentRepository.findIdByCode(PreparingConsts.TEST_EXPERIMENT_CODE_01);
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
        ProcessorStatusYaml ss = new ProcessorStatusYaml(Map.of(), envYaml,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.not_found), "",
                ""+ UUID.randomUUID(), System.currentTimeMillis(),
                Consts.UNKNOWN_INFO, Consts.UNKNOWN_INFO, null, false,
                TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion(), EnumsApi.OS.unknown, Consts.UNKNOWN_INFO, null);
        final String description = "Test processor. Must be deleted automatically";

        mills = System.currentTimeMillis();
        log.info("Start processorRepository.saveAndFlush()");


        // Prepare processor
        data.processor = processorTransactionService.createProcessor(description, null, ss);
        log.info("processorRepository.save() was finished for {} milliseconds", System.currentTimeMillis() - mills);
        data.processorIdAsStr =  Long.toString(data.processor.getId());

        // Prepare functions
        mills = System.currentTimeMillis();
        byte[] bytes = "some program code".getBytes();

        log.info("Start findByCode.save()");
        Function function = functionRepository.findByCode(PreparingConsts.TEST_FIT_FUNCTION);
        log.info("findByCode() was finished for {} milliseconds", System.currentTimeMillis() - mills);
        if (function == null) {

            FunctionConfigYaml sc = new FunctionConfigYaml();
            sc.code = PreparingConsts.TEST_FIT_FUNCTION;
            sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
            sc.env = "python-3";
            sc.type = CommonConsts.FIT_TYPE;
            sc.file = "fit-filename.txt";

            mills = System.currentTimeMillis();
            log.info("Start functionRepository.save() #1");
            function = functionService.persistFunction(sc, new ByteArrayInputStream(bytes), bytes.length);
            log.info("functionRepository.save() #1 was finished for {} milliseconds", System.currentTimeMillis() - mills);
        }
        data.fitFunction = function;

        Function predictFunction = functionRepository.findByCode(PreparingConsts.TEST_PREDICT_FUNCTION);
        if (predictFunction == null) {
            predictFunction = new Function();
            FunctionConfigYaml sc = new FunctionConfigYaml();
            sc.code = PreparingConsts.TEST_PREDICT_FUNCTION;
            sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
            sc.type = CommonConsts.PREDICT_TYPE;
            sc.env = "python-3";
            sc.file = "predict-filename.txt";

            predictFunction.setCode(PreparingConsts.TEST_PREDICT_FUNCTION);
            predictFunction.setType(CommonConsts.PREDICT_TYPE);
            predictFunction.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

            mills = System.currentTimeMillis();
            log.info("Start functionRepository.save() #2");
            functionService.persistFunction(sc, new ByteArrayInputStream(bytes), bytes.length);
            log.info("processorRepository.save() #2 was finished for {} milliseconds", System.currentTimeMillis() - mills);

        }
        data.predictFunction = predictFunction;


        mills = System.currentTimeMillis();

        System.out.println("Was inited correctly");

        return data;
    }

    public void afterPreparingCore(PreparingData.PreparingCodeData preparingCodeData) {
        long mills = System.currentTimeMillis();
        log.info("Start afterPreparingCore()");
        if (preparingCodeData.processor != null) {
            try {
                processorTopLevelService.deleteProcessorById(preparingCodeData.processor.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (preparingCodeData.predictFunction != null) {
            try {
                txSupportForTestingService.deleteFunctionById(preparingCodeData.predictFunction.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (preparingCodeData.fitFunction != null) {
            try {
                txSupportForTestingService.deleteFunctionById(preparingCodeData.fitFunction.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        TaskWithInternalContextEventService.shutdown();
        System.out.println("afterPreparingCore() Was finished correctly");
        log.info("after() was finished for {} milliseconds", System.currentTimeMillis() - mills);


    }

}
