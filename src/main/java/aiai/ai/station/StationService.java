/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.station;

import aiai.ai.beans.StationMetadata;
import aiai.ai.repositories.StationMetadataRepository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Service
public class StationService {

    private StationMetadataRepository stationMetadataRepository;

    public StationService(StationMetadataRepository stationMetadataRepository) {
        this.stationMetadataRepository = stationMetadataRepository;
    }

    public String getStationId() {
        return getMeta(StationConsts.STATION_ID);
    }

    public String getEnv() {
        return getMeta(StationConsts.ENV);
    }

    @PostConstruct
    public void postInit() {
        
    }

    private static final Object syncObj = new Object();
    public void storeStationId(String id){
        synchronized (syncObj) {
            StationMetadata meta = stationMetadataRepository.findByKey(StationConsts.STATION_ID).orElse(null);
            if (meta != null) {
                return;
            }
            StationMetadata m = new StationMetadata();
            m.setKey(StationConsts.STATION_ID);
            m.setValue(id);
            stationMetadataRepository.save(m);
        }
    }

    public void changeStationId(String id){
        synchronized (syncObj) {
            StationMetadata metadata = stationMetadataRepository.findByKey(StationConsts.STATION_ID).orElse(null);
            if (metadata != null) {
                metadata.setValue(id);
                stationMetadataRepository.save(metadata);
                return;
            }
            StationMetadata m = new StationMetadata();
            m.setKey(StationConsts.STATION_ID);
            m.setValue(id);
            stationMetadataRepository.save(m);
        }
    }



    private String getMeta(String key) {
        return stationMetadataRepository.findByKey(key).map(StationMetadata::getValue).orElse(null);
    }


}
