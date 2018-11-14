package aiai.ai.launchpad.experiment;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import aiai.ai.launchpad.repositories.StationsRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExperimentProcessService {

    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;

    public ExperimentProcessService(ExperimentService experimentService, ExperimentRepository experimentRepository) {
        this.experimentService = experimentService;
        this.experimentRepository = experimentRepository;
    }

    public FlowService.ProduceTaskResult produceTasks(Flow flow, FlowInstance flowInstance, Process process, int idx, List<String> inputResourceCodes) {
        Experiment e = experimentRepository.findByCode(process.code);
        FlowService.ProduceTaskResult result = new FlowService.ProduceTaskResult();
        if (e==null) {
            result.status = Enums.FlowProducingStatus.EXPERIMENT_NOT_FOUND_BY_CODE_ERROR;
            return result;
        }

        e.setFlowInstanceId(flowInstance.getId());

        experimentService.produceFeaturePermutations(e, inputResourceCodes);
        experimentService.produceTasks(flowInstance, idx, e);

        result.status = Enums.FlowProducingStatus.OK;
        return result;
    }
}

