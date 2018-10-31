package aiai.ai.launchpad;

import aiai.ai.launchpad.dataset.DatasetService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.repositories.StationsRepository;
import lombok.Getter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Getter
@Profile("launchpad")
public class RealLaunchpadService implements LaunchpadService{
    private final StationsRepository stationsRepository;
    private final ExperimentService experimentService;
    private final DatasetService datasetService;

    public RealLaunchpadService(StationsRepository stationsRepository, ExperimentService experimentService, DatasetService datasetService) {
        this.stationsRepository = stationsRepository;
        this.experimentService = experimentService;
        this.datasetService = datasetService;
    }
}

