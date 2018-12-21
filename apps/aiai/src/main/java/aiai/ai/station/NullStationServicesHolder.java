package aiai.ai.station;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!station")
public class NullStationServicesHolder implements StationServicesHolder {

    @Override
    public StationService getStationService() {
        return null;
    }

    @Override
    public TaskProcessor getTaskProcessor() {
        return null;
    }

    @Override
    public MetadataService getMetadataService() {
        return null;
    }
}
