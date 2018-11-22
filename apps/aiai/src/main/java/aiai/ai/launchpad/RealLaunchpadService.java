package aiai.ai.launchpad;

import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.StationsRepository;
import aiai.ai.launchpad.task.TaskService;
import lombok.Getter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Getter
@Profile("launchpad")
public class RealLaunchpadService implements LaunchpadService{
    private final StationsRepository stationsRepository;
    private final ExperimentService experimentService;
    private final TaskService taskService;
    private final FlowService flowService;
    private final ArtifactCleanerAtLaunchpad artifactCleanerAtLaunchpad;

    public RealLaunchpadService(StationsRepository stationsRepository, ExperimentService experimentService, TaskService taskService, FlowService flowService, ArtifactCleanerAtLaunchpad artifactCleanerAtLaunchpad) {
        this.stationsRepository = stationsRepository;
        this.experimentService = experimentService;
        this.taskService = taskService;
        this.flowService = flowService;
        this.artifactCleanerAtLaunchpad = artifactCleanerAtLaunchpad;
    }
}

