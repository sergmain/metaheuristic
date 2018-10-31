package aiai.ai.launchpad;

import aiai.ai.launchpad.dataset.DatasetService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.repositories.StationsRepository;

public interface LaunchpadService {
    StationsRepository getStationsRepository();
    ExperimentService getExperimentService();
    DatasetService getDatasetService();
}
