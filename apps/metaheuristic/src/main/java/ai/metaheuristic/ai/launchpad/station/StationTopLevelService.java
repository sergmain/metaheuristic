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
import ai.metaheuristic.ai.launchpad.data.StationData;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class StationTopLevelService {

    private final Globals globals;
    private final StationsRepository stationsRepository;
    private final StationCache stationCache;
    private final WorkbookService workbookService;
    private final TaskRepository taskRepository;

    private static final long STATION_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

    public static String createNewSessionId() {
        return UUID.randomUUID().toString() + '-' + UUID.randomUUID().toString();
    }

    public StationData.StationsResult getStations(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.stationRowsLimit, pageable);
        StationData.StationsResult result = new StationData.StationsResult();
        Slice<Long> ids = stationsRepository.findAllByOrderByUpdatedOnDescId(pageable);
        List<StationData.StationStatus> ss = new ArrayList<>(pageable.getPageSize()+1);
        for (Long stationId : ids) {
            Station station = stationCache.findById(stationId);
            if (station==null) {
                continue;
            }
            StationStatus status = StationStatusUtils.to(station.status);

            ss.add(new StationData.StationStatus(
                    station, System.currentTimeMillis() - station.updatedOn < STATION_TIMEOUT,
                    status.taskParamsVersion < TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion(),
                    station.updatedOn,
                    (StringUtils.isNotBlank(status.ip) ? status.ip : "[unknown]"),
                    (StringUtils.isNotBlank(status.host) ? status.host : "[unknown]")
            ));
        }
        result.items =  new SliceImpl<>(ss, pageable, ids.hasNext());
        return result;
    }

    public StationData.StationResult getStation(Long id) {
        //noinspection UnnecessaryLocalVariable
        StationData.StationResult r = new StationData.StationResult(stationCache.findById(id));
        return r;
    }

    public StationData.StationResult saveStation(Station station) {
        Station s = stationsRepository.findByIdForUpdate(station.getId());
        if (s==null) {
            return new StationData.StationResult("#807.05 station wasn't found, stationId: " + station.getId());
        }
        s.description = station.description;
        //noinspection UnnecessaryLocalVariable
        StationData.StationResult r = new StationData.StationResult(stationCache.save(s));
        return r;
    }

    public OperationStatusRest deleteStationById(Long id) {
        Station station = stationCache.findById(id);
        if (station == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#807.15 Station wasn't found, stationId: " + id);
        }
        stationCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private static final ConcurrentHashMap<Long, Object> syncMap = new ConcurrentHashMap<>(50, 0.75f, 10);

    public void storeStationStatus(Protocol.ReportStationStatus command) {
        final Long stationId = Long.valueOf(command.getStationId());
        final Object obj = syncMap.computeIfAbsent(stationId, o -> new Object());
        log.debug("Before entering in sync block, storeStationStatus()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                final Station station = stationsRepository.findByIdForUpdate(stationId);
                if (station == null) {
                    // we throw ISE cos all checks have to be made early
                    throw new IllegalStateException("Station wasn't found for stationId: " + stationId);
                }
                StationStatus ss = StationStatusUtils.to(station.status);
                if (isStationStatusDifferent(command.status, ss)) {
                    ss.env = command.status.env;
                    ss.gitStatusInfo = command.status.gitStatusInfo;
                    ss.schedule = command.status.schedule;

                    // Do not include updating of sessionId
                    // ss.sessionId = command.status.sessionId;

                    // Do not include updating of sessionCreatedOn!
                    // ss.sessionCreatedOn = command.status.sessionCreatedOn;

                    ss.ip = command.status.ip;
                    ss.host = command.status.host;
                    ss.errors = command.status.errors;
                    ss.logDownloadable = command.status.logDownloadable;
                    ss.taskParamsVersion = command.status.taskParamsVersion;

                    station.status = StationStatusUtils.toString(ss);
                    station.updatedOn = System.currentTimeMillis();
                    try {
                        log.debug("Save new station status, station: {}", station);
                        stationCache.save(station);
                    } catch (ObjectOptimisticLockingFailureException e) {
                        log.warn("#807.105 ObjectOptimisticLockingFailureException was encountered\n" +
                                "new station:\n{}\n" +
                                "db station\n{}", station, stationsRepository.findById(stationId).orElse(null));

                        stationCache.clearCache();
                    }
                }
                else {
                    log.info("Station status is equal to stored in db");
                }
            }
            finally {
                syncMap.remove(stationId);
            }
        }
        log.debug("After leaving sync block");
    }

    public static boolean isStationStatusDifferent(StationStatus status, StationStatus ss) {
        return
        !Objects.equals(ss.env, status.env) ||
        !Objects.equals(ss.gitStatusInfo, status.gitStatusInfo) ||
        !Objects.equals(ss.schedule, status.schedule) ||
        !Objects.equals(ss.ip, status.ip) ||
        !Objects.equals(ss.host, status.host) ||
        !Objects.equals(ss.errors, status.errors) ||
        ss.logDownloadable!=status.logDownloadable ||
        ss.taskParamsVersion!=status.taskParamsVersion;
    }

    // TODO Need to re-write this method
    public void reconcileStationTasks(String stationIdAsStr, List<Protocol.StationTaskStatus.SimpleStatus> statuses) {
        final long stationId = Long.parseLong(stationIdAsStr);
        List<Object[]> tasks = taskRepository.findAllByStationIdAndResultReceivedIsFalseAndCompletedIsFalse(stationId);
        for (Object[] obj : tasks) {
            long taskId = ((Number)obj[0]).longValue();
            Long assignedOn = obj[1]!=null ? ((Number)obj[1]).longValue() : null;

            boolean isFound = false;
            for (Protocol.StationTaskStatus.SimpleStatus status : statuses) {
                if (status.taskId ==taskId) {
                    isFound = true;
                }
            }

            boolean isExpired = assignedOn!=null && (System.currentTimeMillis() - assignedOn > 90_000);
            if (!isFound && isExpired) {
                log.info("De-assign task #{} from station #{}", taskId, stationIdAsStr);
                log.info("\tstatuses: {}", statuses.stream().map( o -> Long.toString(o.taskId)).collect(Collectors.toList()));
                log.info("\ttasks: {}", tasks.stream().map( o -> ""+o[0] + ',' + o[1]).collect(Collectors.toList()));
                log.info("\tisFound: {}, is expired: {}", isFound, isExpired);
                OperationStatusRest result = workbookService.resetTask(taskId);
                if (result.status== EnumsApi.OperationStatus.ERROR) {
                    log.error("#179.10 Resetting of task #{} was failed. See log for more info.", taskId);
                }
            }
        }
    }


}
