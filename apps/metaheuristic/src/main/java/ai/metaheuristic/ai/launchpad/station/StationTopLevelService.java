/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.launchpad.station;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.data.StationData;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StaleObjectStateException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Profile("launchpad")
@Service
public class StationTopLevelService {

    private final Globals globals;
    private final StationsRepository stationsRepository;

    private static final long STATION_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

    public StationTopLevelService(Globals globals, StationsRepository stationsRepository) {
        this.globals = globals;
        this.stationsRepository = stationsRepository;
    }

    public StationData.StationsResult getStations(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.stationRowsLimit, pageable);
        StationData.StationsResult result = new StationData.StationsResult();
        Slice<Station> items = stationsRepository.findAllByOrderByUpdatedOnDescId(pageable);
        List<StationData.StationStatus> ss = new ArrayList<>(pageable.getPageSize()+1);
        for (Station item : items) {
            StationStatus status = StationStatusUtils.to(item.status);

            ss.add(new StationData.StationStatus(
                    item, System.currentTimeMillis() - item.updatedOn < STATION_TIMEOUT, item.updatedOn,
                    (StringUtils.isNotBlank(status.ip) ? status.ip : "[unknown]"),
                    (StringUtils.isNotBlank(status.host) ? status.host : "[unknown]")
            ));
        }
        result.items =  new SliceImpl<>(ss, pageable, items.hasNext());
        return result;
    }

    public StationData.StationResult getStation(Long id) {
        //noinspection UnnecessaryLocalVariable
        StationData.StationResult r = new StationData.StationResult(stationsRepository.findById(id).orElse(null));
        return r;
    }

    public StationData.StationResult saveStation(Station station) {
        Station s = stationsRepository.findById(station.getId()).orElse(null);
        if (s==null) {
            return new StationData.StationResult("#807.05 station wasn't found, stationId: " + station.getId());
        }
        s.description = station.description;
        //noinspection UnnecessaryLocalVariable
        StationData.StationResult r = new StationData.StationResult(stationsRepository.save(s));
        return r;
    }

    public OperationStatusRest deleteStationById(Long id) {
        Station station = stationsRepository.findById(id).orElse(null);
        if (station == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#807.15 Station wasn't found, stationId: " + id);
        }
        stationsRepository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public void storeStationStatus(Protocol.ReportStationStatus command) {
        final Long stationId = Long.valueOf(command.getStationId());
        final Station station = stationsRepository.findById(stationId).orElse(null);
        if (station==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("Station wasn't found for stationId: " + stationId );
        }
        final String stationStatus = StationStatusUtils.toString(command.status);
        if (!stationStatus.equals(station.status)) {
            station.status = stationStatus;
            station.setUpdatedOn(System.currentTimeMillis());
            try {
                stationsRepository.save(station);
            } catch (StaleObjectStateException e) {
                log.warn("#807.105 StaleObjectStateException was encountered\n" +
                        "new station:\n{}\n" +
                        "db station\n{}", station, stationsRepository.findById(stationId));
                // we dont do anything about this error because station will report again in short time
            }
        }
    }


}
