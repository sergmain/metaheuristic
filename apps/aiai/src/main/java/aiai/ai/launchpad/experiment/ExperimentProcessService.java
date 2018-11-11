package aiai.ai.launchpad.experiment;

import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.repositories.StationsRepository;
import lombok.Getter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
public class ExperimentProcessService {

    private final StationsRepository stationsRepository;
    private final ExperimentService experimentService;

    public ExperimentProcessService(StationsRepository stationsRepository, ExperimentService experimentService) {
        this.stationsRepository = stationsRepository;
        this.experimentService = experimentService;
    }

    public void produceTasks(Flow flow, FlowInstance flowInstance, Process process, int idx, String inputResourcePoolCode, String outputResourcePoolCode) {
        Experiment experiment = new Experiment();
        experiment.setFlowInstanceId(flowInstance.getId());

    }
}

