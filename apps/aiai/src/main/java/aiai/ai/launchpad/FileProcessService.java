package aiai.ai.launchpad;

import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.repositories.StationsRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!launchpad")
public class FileProcessService implements ProcessService {
    @Override
    public void produceTasks(Process process) {
    }
}
