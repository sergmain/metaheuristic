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
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import aiai.ai.utils.holders.IntHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("launchpad")
public class ExperimentProcessService {

    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;

    public ExperimentProcessService(ExperimentService experimentService, ExperimentRepository experimentRepository, ExperimentCache experimentCache) {
        this.experimentService = experimentService;
        this.experimentRepository = experimentRepository;
        this.experimentCache = experimentCache;
    }

    public FlowService.ProduceTaskResult produceTasks(
            boolean isPersist, Flow flow, FlowInstance flowInstance,
            Process process, Map<String, List<String>> collectedInputs, Map<String, String> inputStorageUrls) {
        Experiment e = experimentRepository.findByCode(process.code);

        // real copy of experiment
        e = experimentCache.findById(e.getId());
        FlowService.ProduceTaskResult result = new FlowService.ProduceTaskResult();
        if (e==null) {
            result.status = Enums.FlowProducingStatus.EXPERIMENT_NOT_FOUND_BY_CODE_ERROR;
            return result;
        }

        e.setFlowInstanceId(flowInstance.getId());
        if (isPersist) {
            e = experimentCache.save(e);
        }

        Process.Meta meta = process.getMeta("feature");
        if (meta==null) {
            result.status = Enums.FlowProducingStatus.META_WASNT_CONFIGURED_FOR_EXPERIMENT_ERROR;
            return result;
        }

        List<String> features = collectedInputs.get(meta.getValue());
        long mills = System.currentTimeMillis();
        IntHolder intHolder = new IntHolder();
        experimentService.produceFeaturePermutations(isPersist, e.getId(), features, intHolder);
        int numberOfFeatures = intHolder.value;
        log.info("produceFeaturePermutations() was done for " + (System.currentTimeMillis() - mills) + " ms.");

        Monitoring.log("##051", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        Enums.FlowProducingStatus status = experimentService.produceTasks(
                isPersist, flow, flowInstance, process, e, collectedInputs, inputStorageUrls, intHolder);

        log.info("experimentService.produceTasks() was done for " + (System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##071", Enums.Monitor.MEMORY);
        if (status!= Enums.FlowProducingStatus.OK) {
            log.error("Tasks weren't produced successfully.");
        }

        result.status = status;
        result.numberOfTasks = numberOfFeatures * intHolder.value;
        return result;
    }
}

