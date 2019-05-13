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

package aiai.ai.launchpad.experiment;

import aiai.ai.Enums;
import aiai.ai.Monitoring;
import metaheuristic.api.v1.data_storage.DataStorageParams;
import metaheuristic.api.v1.EnumsApi;
import metaheuristic.api.v1.launchpad.Plan;
import metaheuristic.api.v1.launchpad.Process;
import aiai.ai.launchpad.beans.Experiment;
import metaheuristic.api.v1.launchpad.Workbook;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import aiai.ai.launchpad.plan.PlanService;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import aiai.ai.utils.CollectionUtils;
import aiai.ai.utils.holders.IntHolder;
import metaheuristic.api.v1.data.InputResourceParam;
import aiai.ai.yaml.input_resource_param.InputResourceParamUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("launchpad")
public class ExperimentProcessService {

    private static final String FEATURE_POOL_CODE_TYPE = "feature";
    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;
    private final BinaryDataService binaryDataService;

    public ExperimentProcessService(ExperimentService experimentService, ExperimentRepository experimentRepository, ExperimentCache experimentCache, BinaryDataService binaryDataService) {
        this.experimentService = experimentService;
        this.experimentRepository = experimentRepository;
        this.experimentCache = experimentCache;
        this.binaryDataService = binaryDataService;
    }

    public PlanService.ProduceTaskResult produceTasks(
            boolean isPersist, Plan plan, Workbook workbook,
            Process process, PlanService.ResourcePools pools) {

        Map<String, List<String>> collectedInputs = pools.collectedInputs;
        Map<String, DataStorageParams> inputStorageUrls = pools.inputStorageUrls;

        Experiment e = experimentRepository.findByCode(process.code);

        // real copy of experiment
        e = experimentCache.findById(e.getId());
        PlanService.ProduceTaskResult result = new PlanService.ProduceTaskResult();
        if (e==null) {
            result.status = EnumsApi.PlanProducingStatus.EXPERIMENT_NOT_FOUND_BY_CODE_ERROR;
            return result;
        }

        e.setWorkbookId(workbook.getId());
        if (isPersist) {
            e = experimentCache.save(e);
        }

        Process.Meta meta = process.getMeta(FEATURE_POOL_CODE_TYPE);

        List<String> features;
        if (meta==null) {
            InputResourceParam resourceParams = InputResourceParamUtils.to(workbook.getInputResourceParam());
            List<String> list = resourceParams.getPoolCodes().get(FEATURE_POOL_CODE_TYPE);
            if (CollectionUtils.isEmpty(list)) {
                result.status = EnumsApi.PlanProducingStatus.META_WASNT_CONFIGURED_FOR_EXPERIMENT_ERROR;
                return result;
            }
            features = new ArrayList<>();
            for (String poolCode : list) {
                List<String> newResources = collectedInputs.get(poolCode);
                if (newResources==null) {
                    result.status = EnumsApi.PlanProducingStatus.INPUT_POOL_CODE_FROM_META_DOESNT_EXIST_ERROR;
                    return result;
                }
                features.addAll(newResources);
            }
        }
        else {
            if (!collectedInputs.containsKey(meta.getValue())) {
                List<SimpleCodeAndStorageUrl> initialInputResourceCodes = binaryDataService.getResourceCodesInPool(
                        Collections.singletonList(meta.getValue()), workbook.getId()
                );

                PlanService.ResourcePools metaPools = new PlanService.ResourcePools(initialInputResourceCodes);
                if (metaPools.status != EnumsApi.PlanProducingStatus.OK) {
                    result.status = EnumsApi.PlanProducingStatus.INPUT_POOL_CODE_FROM_META_DOESNT_EXIST_ERROR;
                    return result;
                }
                pools.merge(metaPools);
            }

            features = collectedInputs.get(meta.getValue());
            if (features==null) {
                result.status = EnumsApi.PlanProducingStatus.INPUT_POOL_CODE_FROM_META_DOESNT_EXIST_ERROR;
                return result;
            }
        }

        long mills = System.currentTimeMillis();
        IntHolder intHolder = new IntHolder();
        experimentService.produceFeaturePermutations(isPersist, e.getId(), features, intHolder);
        int numberOfFeatures = intHolder.value;
        log.info("produceFeaturePermutations() was done for " + (System.currentTimeMillis() - mills) + " ms.");

        Monitoring.log("##051", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        EnumsApi.PlanProducingStatus status = experimentService.produceTasks(
                isPersist, plan, workbook, process, e, collectedInputs, inputStorageUrls, intHolder);

        log.info("experimentService.produceTasks() was done for " + (System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##071", Enums.Monitor.MEMORY);
        if (status!= EnumsApi.PlanProducingStatus.OK) {
            log.error("Tasks weren't produced successfully.");
        }

        result.status = status;
        result.numberOfTasks = numberOfFeatures * intHolder.value;
        return result;
    }
}

