package aiai.ai.launchpad;

import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.StationsRepository;
import aiai.ai.launchpad.task.TaskService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!launchpad")
public class NullLaunchpadService implements LaunchpadService {
    @Override
    public StationsRepository getStationsRepository() {
        return null;
    }

    @Override
    public ExperimentService getExperimentService() {
        return null;
    }

    @Override
    public TaskService getTaskService() {
        return null;
    }

    @Override
    public FlowService getFlowService() {
        return null;
    }

    @Override
    public ArtifactCleanerAtLaunchpad getArtifactCleanerAtLaunchpad() {
        return null;
    }
}
