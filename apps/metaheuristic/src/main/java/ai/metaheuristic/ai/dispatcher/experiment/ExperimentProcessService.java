/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.holders.IntHolder;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentProcessService {

    private static final String FEATURE_POOL_CODE_TYPE = "feature";

    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;
    private final VariableService variableService;
    private final ExecContextCache execContextCache;

    public SourceCodeService.ProduceTaskResult produceTasks(
            boolean isPersist, SourceCodeParamsYaml sourceCodeParams, Long execContextId,
            SourceCodeParamsYaml.Process process, SourceCodeService.ResourcePools pools, List<Long> parentTaskIds) {

        Map<String, List<String>> collectedInputs = pools.collectedInputs;
        Map<String, SourceCodeParamsYaml.Variable> inputStorageUrls = pools.inputStorageUrls;

        Long experimentId = experimentRepository.findIdByCode(process.code);
        Experiment e;
        if (isPersist) {
            e = experimentRepository.findByIdForUpdate(experimentId);
        }
        else {
            e = experimentCache.findById(experimentId);
        }

        SourceCodeService.ProduceTaskResult result = new SourceCodeService.ProduceTaskResult();
        if (e==null) {
            result.status = EnumsApi.SourceCodeProducingStatus.EXPERIMENT_NOT_FOUND_BY_CODE_ERROR;
            return result;
        }

        if (!isPersist) {
            e = e.clone();
        }
        e.setExecContextId(execContextId);
        if (isPersist) {
            e = experimentCache.save(e);
        }

        Meta meta = process.getMeta(FEATURE_POOL_CODE_TYPE);

        List<String> features;
        if (meta==null) {
            ExecContextImpl execContext = execContextCache.findById(execContextId);
            if (execContext==null) {
                result.status = EnumsApi.SourceCodeProducingStatus.EXEC_CONTEXT_NOT_FOUND_ERROR;
                return result;
            }
            ExecContextParamsYaml resourceParams = execContext.getExecContextParamsYaml();
            List<String> list = resourceParams.execContextYaml.getVariables().get(FEATURE_POOL_CODE_TYPE);
            if (CollectionUtils.isEmpty(list)) {
                result.status = EnumsApi.SourceCodeProducingStatus.META_WASNT_CONFIGURED_FOR_EXPERIMENT_ERROR;
                return result;
            }
            features = new ArrayList<>();
            for (String poolCode : list) {
                List<String> newResources = collectedInputs.get(poolCode);
                if (newResources==null) {
                    log.warn("#714.010 Can't find input resource for poolCode {}", poolCode);
                    result.status = EnumsApi.SourceCodeProducingStatus.INPUT_POOL_CODE_FROM_META_DOESNT_EXIST_ERROR;
                    return result;
                }
                features.addAll(newResources);
            }
        }
        else {
            if (!collectedInputs.containsKey(meta.getValue())) {
                List<SimpleVariableAndStorageUrl> initialInputResourceCodes = variableService.getIdInVariables(
                        Collections.singletonList(meta.getValue()), execContextId
                );

                SourceCodeService.ResourcePools metaPools = new SourceCodeService.ResourcePools(initialInputResourceCodes);
                if (metaPools.status != EnumsApi.SourceCodeProducingStatus.OK) {
                    log.warn("#714.020 (metaPools.status != EnumsApi.SourceCodeProducingStatus.OK), metaPools.status {}", metaPools.status);
                    result.status = EnumsApi.SourceCodeProducingStatus.INPUT_POOL_CODE_FROM_META_DOESNT_EXIST_ERROR;
                    return result;
                }
                pools.merge(metaPools);
            }

            features = collectedInputs.get(meta.getValue());
            if (features==null) {
                log.warn("#714.030 Can't find input resource for meta.value {}", meta.getValue());
                result.status = EnumsApi.SourceCodeProducingStatus.INPUT_POOL_CODE_FROM_META_DOESNT_EXIST_ERROR;
                return result;
            }
        }

        long mills = System.currentTimeMillis();
        IntHolder intHolder = new IntHolder();
        experimentService.produceFeaturePermutations(isPersist, e, features, intHolder);
        log.info("produceFeaturePermutations() was done for " + (System.currentTimeMillis() - mills) + " ms.");

        Monitoring.log("##051", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        EnumsApi.SourceCodeProducingStatus status = experimentService.produceTasks(
                isPersist, sourceCodeParams, execContextId, process, e, collectedInputs, inputStorageUrls, intHolder, parentTaskIds);

        log.info("experimentService.produceTasks() was done for " + (System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##071", Enums.Monitor.MEMORY);
        if (status!= EnumsApi.SourceCodeProducingStatus.OK) {
            log.error("Tasks weren't produced successfully.");
        }

        result.status = status;
        result.numberOfTasks = intHolder.value;
        return result;
    }
}
