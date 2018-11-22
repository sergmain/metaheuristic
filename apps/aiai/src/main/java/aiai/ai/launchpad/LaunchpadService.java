package aiai.ai.launchpad;

import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.StationsRepository;
import aiai.ai.launchpad.task.TaskService;

public interface LaunchpadService {
    StationsRepository getStationsRepository();
    ExperimentService getExperimentService();
    TaskService getTaskService();
    FlowService getFlowService();

    ArtifactCleanerAtLaunchpad getArtifactCleanerAtLaunchpad();
}
