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

    private final StationsRepository stationsRepository;
    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final BinaryDataService binaryDataService;

    public ExperimentProcessService(StationsRepository stationsRepository, ExperimentService experimentService, ExperimentRepository experimentRepository, BinaryDataService binaryDataService) {
        this.stationsRepository = stationsRepository;
        this.experimentService = experimentService;
        this.experimentRepository = experimentRepository;
        this.binaryDataService = binaryDataService;
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
        experimentService.produceTasks(e, inputResourceCodes);

        result.status = Enums.FlowProducingStatus.OK;
        return result;
    }

}

