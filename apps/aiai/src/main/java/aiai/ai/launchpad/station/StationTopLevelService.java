/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.station;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Station;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.data.StationData;
import aiai.ai.launchpad.repositories.StationsRepository;
import aiai.ai.utils.ControllerUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("launchpad")
@Service
public class StationTopLevelService {

    private final Globals globals;
    private final StationsRepository repository;

    public StationTopLevelService(Globals globals, StationsRepository repository) {
        this.globals = globals;
        this.repository = repository;
    }

    public StationData.StationsResultRest getStations(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.stationRowsLimit, pageable);
        StationData.StationsResultRest result = new StationData.StationsResultRest();
        result.items = repository.findAll(pageable);
        return result;
    }

    public StationData.StationResultRest getStation(Long id) {
        //noinspection UnnecessaryLocalVariable
        StationData.StationResultRest r = new StationData.StationResultRest(repository.findById(id).orElse(null));
        return r;
    }

    public StationData.StationResultRest saveStation(Station station) {
        //noinspection UnnecessaryLocalVariable
        StationData.StationResultRest r = new StationData.StationResultRest(repository.save(station));
        return r;
    }

    public OperationStatusRest deleteStationById(Long id) {
        Station station = repository.findById(id).orElse(null);
        if (station == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "#076.042 Station wasn't found, stationId: " + id);
        }
        repository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

}
