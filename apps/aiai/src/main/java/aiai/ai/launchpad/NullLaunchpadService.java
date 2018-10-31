package aiai.ai.launchpad;

import aiai.ai.launchpad.dataset.DatasetService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.repositories.StationsRepository;
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
    public DatasetService getDatasetService() {
        return null;
    }
}
