/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.ProcessorCore;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentCache;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorSyncService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTxService;
import ai.metaheuristic.ai.dispatcher.processor_core.ProcessorCoreTxService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxTestingTopLevelService;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static ai.metaheuristic.ai.functions.FunctionRepositoryDispatcherService.registerReadyFunctionCodesOnProcessor;

/**
 * @author Serge
 * Date: 1/6/2022
 * Time: 2:57 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class PreparingCoreInitService {

    private final ExperimentRepository experimentRepository;
    private final TxTestingTopLevelService txTestingTopLevelService;
    private final ExperimentCache experimentCache;
    private final ProcessorTopLevelService processorTopLevelService;
    private final ProcessorTxService processorTxService;
    private final TxSupportForTestingService txSupportForTestingService;
    private final ProcessorCoreTxService processorCoreService;
    private final TaskWithInternalContextEventService taskWithInternalContextEventService;

    @SneakyThrows
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
        envYaml.getEnvs().put("java-17", "/path/to/java-17" );
        envYaml.getEnvs().put("env-function-01:1.1", "python.exe" );
        envYaml.getEnvs().put("env-function-02:1.1", "python.exe" );
        envYaml.getEnvs().put("env-function-03:1.1", "python.exe" );
        envYaml.getEnvs().put("env-function-04:1.1", "python.exe" );
        envYaml.getEnvs().put("env-function-05:1.1", "python.exe" );

        ProcessorStatusYaml ss = new ProcessorStatusYaml(envYaml,
                new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.not_found), "",
                ""+ UUID.randomUUID(), System.currentTimeMillis(),
                Consts.UNKNOWN_INFO, Consts.UNKNOWN_INFO, null, false,
                TaskParamsYamlUtils.UTILS.getDefault().getVersion(), EnumsApi.OS.unknown, Consts.UNKNOWN_INFO, null, null);
        final String description = "Test processor. Must be deleted automatically";
        final String descriptionCore = "Test processor core. Must be deleted automatically";

        mills = System.currentTimeMillis();
        log.info("Start processorRepository.saveAndFlush()");


        // Prepare processor
        data.processor = processorTxService.createProcessor(description, null, ss);
        log.info("processorRepository.save() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        registerReadyFunctionCodesOnProcessor(PreparingConsts.TEST_FIT_FUNCTION, data.processor.id, true);
        registerReadyFunctionCodesOnProcessor(PreparingConsts.TEST_PREDICT_FUNCTION, data.processor.id, true);

        registerReadyFunctionCodesOnProcessor("function-01:1.1", data.processor.id, true);
        registerReadyFunctionCodesOnProcessor("function-02:1.1", data.processor.id, true);
        registerReadyFunctionCodesOnProcessor("function-03:1.1", data.processor.id, true);
        registerReadyFunctionCodesOnProcessor("function-04:1.1", data.processor.id, true);
        registerReadyFunctionCodesOnProcessor("function-05:1.1", data.processor.id, true);


        // Prepare processor's cores
        CoreStatusYaml csy1 = new CoreStatusYaml("/home/core-1", null, null);
        data.core1 = processorTxService.createProcessorCore(descriptionCore, csy1, data.processor.id);

        CoreStatusYaml csy2 = new CoreStatusYaml("/home/core-2", null, null);
        data.core2 = processorTxService.createProcessorCore(descriptionCore, csy2, data.processor.id);

        // Prepare functions
        data.fitFunction = txTestingTopLevelService.getOrCreateFunction(PreparingConsts.TEST_FIT_FUNCTION, CommonConsts.FIT_TYPE, "fit-filename.txt");
        data.predictFunction = txTestingTopLevelService.getOrCreateFunction(PreparingConsts.TEST_PREDICT_FUNCTION, CommonConsts.PREDICT_TYPE, "predict-filename.txt");

        System.out.println("Was inited correctly");

        return data;
    }

    public void afterPreparingCore(@Nullable PreparingData.PreparingCodeData preparingCodeData) {
        long mills = System.currentTimeMillis();
        log.info("Start afterPreparingCore()");
        if (preparingCodeData!=null) {
            if (preparingCodeData.core1 != null) {
                try {
                    processorTopLevelService.deleteProcessorCoreById(preparingCodeData.core1.getId());
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
            if (preparingCodeData.core2 != null) {
                try {
                    processorTopLevelService.deleteProcessorCoreById(preparingCodeData.core2.getId());
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
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

            deleteProcessorCore(preparingCodeData.core1);
            deleteProcessorCore(preparingCodeData.core2);
            deleteProcessor(preparingCodeData.processor);
        }

        taskWithInternalContextEventService.clearQueue();
        System.out.println("afterPreparingCore() Was finished correctly");
        log.info("after() was finished for {} milliseconds", System.currentTimeMillis() - mills);


    }
    private void deleteProcessorCore(@Nullable ProcessorCore s) {
        if (s!=null) {
            try {
                ProcessorSyncService.getWithSyncVoid(s.processorId, ()->processorCoreService.deleteProcessorCoreById(s.id));
            } catch (Throwable th) {
                log.error("Error", th);
            }
        }
    }

    private void deleteProcessor(@Nullable Processor s) {
        if (s!=null) {
            try {
                processorTopLevelService.deleteProcessorById(s.id);
            } catch (Throwable th) {
                log.error("Error", th);
            }
        }
    }


}
