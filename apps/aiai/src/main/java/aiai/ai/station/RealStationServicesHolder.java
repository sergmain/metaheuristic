package aiai.ai.station;

import lombok.Getter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Getter
@Profile("station")
public class RealStationServicesHolder implements StationServicesHolder {
    private final StationService stationService;
    private final TaskProcessor taskProcessor;
    private final MetadataService metadataService;

    public RealStationServicesHolder(StationService stationService, TaskProcessor taskProcessor, MetadataService metadataService) {
        this.stationService = stationService;
        this.taskProcessor = taskProcessor;
        this.metadataService = metadataService;
    }
}
