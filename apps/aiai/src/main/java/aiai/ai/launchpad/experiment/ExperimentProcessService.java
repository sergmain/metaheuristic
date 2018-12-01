package aiai.ai.launchpad.experiment;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ExperimentProcessService {

    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;

    public ExperimentProcessService(ExperimentService experimentService, ExperimentRepository experimentRepository, ExperimentCache experimentCache) {
        this.experimentService = experimentService;
        this.experimentRepository = experimentRepository;
        this.experimentCache = experimentCache;
    }

    public FlowService.ProduceTaskResult produceTasks(Flow flow, FlowInstance flowInstance, Process process, Map<String, List<String>> collectedInputs) {
        Experiment e = experimentCache.findByCode(process.code);
        FlowService.ProduceTaskResult result = new FlowService.ProduceTaskResult();
        if (e==null) {
            result.status = Enums.FlowProducingStatus.EXPERIMENT_NOT_FOUND_BY_CODE_ERROR;
            return result;
        }

        e.setFlowInstanceId(flowInstance.getId());
        e = experimentCache.save(e);

        Process.Meta meta = process.getMeta("feature");
        if (meta==null) {
            result.status = Enums.FlowProducingStatus.META_WASNT_CONFIGURED_FOR_EXPERIMENT_ERROR;
            return result;
        }

        List<String> features = collectedInputs.get(meta.getValue());
        experimentService.produceFeaturePermutations(e, features);
        boolean status = experimentService.produceTasks(flowInstance, process, e, collectedInputs);
        if (!status) {
            log.error("Tasks weren't produced successfully.");
        }

        result.status = status ? Enums.FlowProducingStatus.OK : Enums.FlowProducingStatus.PRODUCING_OF_EXPERIMENT_ERROR;
        return result;
    }
}

