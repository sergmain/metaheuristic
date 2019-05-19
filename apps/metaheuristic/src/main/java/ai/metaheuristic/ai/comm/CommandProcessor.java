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

package ai.metaheuristic.ai.comm;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.launchpad.LaunchpadService;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.station.StationServicesHolder;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * User: Serg
 * Date: 12.08.2017
 * Time: 19:48
 */
@Service
@Slf4j
public class CommandProcessor {

    private final LaunchpadService launchpadService;
    private final StationServicesHolder stationServicesHolder;

    public CommandProcessor(LaunchpadService launchpadService, StationServicesHolder stationServicesHolder) {
        this.launchpadService = launchpadService;
        this.stationServicesHolder = stationServicesHolder;
    }

    public Command[] process(Command command) {
        switch (command.getType()) {
            case Nop:
                break;
            case ReportStation:
                //noinspection
                break;
            case RequestStationId:
                // processing on launchpad side
                return getNewStationId((Protocol.RequestStationId) command);
            case AssignedStationId:
                // processing on station side
                return storeStationId((Protocol.AssignedStationId) command);
            case ReAssignStationId:
                // processing on station side
                return reAssignStationId((Protocol.ReAssignStationId) command);

            case RequestTask:
                // processing on launchpad side
                return processRequestTask((Protocol.RequestTask) command);
            case CheckForMissingOutputResources:
                // processing on launchpad side
                return checkForMissingOutputResources((Protocol.CheckForMissingOutputResources) command);
            case ResendTaskOutputResourceResult:
                // processing on launchpad side
                return processResendTaskOutputResourceResult((Protocol.ResendTaskOutputResourceResult) command);
            case AssignedTask:
                // processing on station side
                return processAssignedTask((Protocol.AssignedTask) command);
            case ResendTaskOutputResource:
                // processing on station side
                return resendTaskOutputResource((Protocol.ResendTaskOutputResource) command);
            case ReportStationStatus:
                // processing on launchpad side
                return processReportStationStatus((Protocol.ReportStationStatus) command);
            case ReportTaskProcessingResult:
                // processing on launchpad side
                return processReportTaskProcessingResult((Protocol.ReportTaskProcessingResult) command);
            case ReportResultDelivering:
                // processing on station side
                return processReportResultDelivering((Protocol.ReportResultDelivering) command);
            case WorkbookStatus:
                // processing on station side
                return processWorkbookStatus((Protocol.WorkbookStatus) command);
            case StationTaskStatus:
                // processing on launchpad side
                return processStationTaskStatus((Protocol.StationTaskStatus) command);
            default:
                System.out.println("There is new command which isn't processed: " + command.getType());
        }
        return Protocol.NOP_ARRAY;
    }

    private Command[] checkForMissingOutputResources(Protocol.CheckForMissingOutputResources command) {
        final long stationId = Long.parseLong(command.getStationId());
        List<Long> ids = launchpadService.getTaskService().resourceReceivingChecker(stationId);
        return new Command[]{new Protocol.ResendTaskOutputResource(ids)};
    }

    private Command[] resendTaskOutputResource(Protocol.ResendTaskOutputResource command) {
        if (command.taskIds==null) {
            return Protocol.NOP_ARRAY;
        }
        List<Protocol.ResendTaskOutputResourceResult.SimpleStatus> statuses = new ArrayList<>();
        for (Long taskId : command.taskIds) {
            Enums.ResendTaskOutputResourceStatus status = stationServicesHolder.getStationService().resendTaskOutputResource(command.launchpadUrl, taskId);
            statuses.add( new Protocol.ResendTaskOutputResourceResult.SimpleStatus(taskId, status));
        }
        return new Command[]{new Protocol.ResendTaskOutputResourceResult(statuses)};
    }

    private Command[] processResendTaskOutputResourceResult(Protocol.ResendTaskOutputResourceResult command) {
        for (Protocol.ResendTaskOutputResourceResult.SimpleStatus status : command.statuses) {
            launchpadService.getTaskService().processResendTaskOutputResourceResult(command.getStationId(), status.status, status.taskId);
        }
        return Protocol.NOP_ARRAY;
    }

    private Command[] processStationTaskStatus(Protocol.StationTaskStatus command) {
        launchpadService.getTaskService().reconcileStationTasks(command.stationId, command.statuses!=null ? command.statuses : new ArrayList<>());
        return Protocol.NOP_ARRAY;
    }

    private Command[] processWorkbookStatus(Protocol.WorkbookStatus command) {
        if (command.launchpadUrl==null) {
            throw new IllegalStateException("command.launchpadUrl is null");
        }
        stationServicesHolder.getTaskProcessor().processWorkbookStatus(command.launchpadUrl, command.statuses);
        return Protocol.NOP_ARRAY;
    }

    private Command[] processReportResultDelivering(Protocol.ReportResultDelivering command) {
        stationServicesHolder.getStationService().markAsDelivered(command.launchpadUrl, command.getIds());
        return Protocol.NOP_ARRAY;
    }

    private Command[] processReportTaskProcessingResult(Protocol.ReportTaskProcessingResult command) {
        if (command.getResults().isEmpty()) {
            return Protocol.NOP_ARRAY;
        }
        final Protocol.ReportResultDelivering cmd1 = new Protocol.ReportResultDelivering(
                launchpadService.getTaskService().storeAllConsoleResults(command.getResults())
        );
        // we can't return immediately task because we have to receive some params from station,
        // like: does snippet have to be signed or not

        return new Command[]{cmd1};
    }

    private Command[] processReportStationStatus(Protocol.ReportStationStatus command) {
        checkStationId(command);
        final long stationId = Long.parseLong(command.getStationId());
        Station station = launchpadService.getStationsRepository().findById(stationId).orElse(null);
        if (station==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("Station wasn't found for stationId: " + stationId );
        }
        final String stationStatus = StationStatusUtils.toString(command.status);
        if (!stationStatus.equals(station.status)) {
            station.status = stationStatus;
            station.setUpdatedOn(System.currentTimeMillis());
            launchpadService.getStationsRepository().save(station);
        }
        return Protocol.NOP_ARRAY;
    }

    private Command[] processAssignedTask(Protocol.AssignedTask command) {
        stationServicesHolder.getStationService().assignTasks(command.launchpadUrl, command.tasks);
        return Protocol.NOP_ARRAY;
    }

    private Command[] processRequestTask(Protocol.RequestTask command) {
        checkStationId(command);
        Protocol.AssignedTask r = assignTaskToStation(command.getStationId(), command.isAcceptOnlySigned());
        if (r.tasks!=null && !r.tasks.isEmpty()) {
            for (Protocol.AssignedTask.Task task : r.tasks) {
                log.info("Assign task #{} to station #{}",task.getTaskId(), command.getStationId() );
            }
        }
        return Protocol.asArray(r);
    }

    private synchronized Protocol.AssignedTask assignTaskToStation(String stationId, boolean isAcceptOnlySigned) {
        Protocol.AssignedTask r = new Protocol.AssignedTask();
        TaskService.TasksAndAssignToStationResult result =
            launchpadService.getTaskService().getTaskAndAssignToStation(
                    Long.parseLong(stationId), isAcceptOnlySigned, null);

        if (result.getSimpleTask()!=null) {
            r.tasks = Collections.singletonList(result.getSimpleTask());
        }
        return r;
    }

    private void checkStationId(Command command) {
        if (command.getStationId()==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("stationId is null");
        }
    }

    // processing on station side
    private Command[] storeStationId(Protocol.AssignedStationId command) {
        log.info("storeStationId() new station Id: {}", command.getAssignedStationId());
        stationServicesHolder.getMetadataService().setStationIdAndSessionId(
                command.launchpadUrl, command.getAssignedStationId(), command.getAssignedSessionId());
        return Protocol.NOP_ARRAY;
    }

    // processing on station side
    private Command[] reAssignStationId(Protocol.ReAssignStationId command) {
        log.info("reAssignStationId() station Id: {}", command.getReAssignedStationId());
        stationServicesHolder.getMetadataService().setStationIdAndSessionId(
                command.launchpadUrl, command.getReAssignedStationId(), command.sessionId);
        return Protocol.NOP_ARRAY;
    }

    private Command[] getNewStationId(@SuppressWarnings("unused") Protocol.RequestStationId command) {
        final Station st = new Station();
        // TODO 2019.05.19 need to decide do we need better solution or it's ok
        String sessionId = UUID.randomUUID().toString()+'-'+UUID.randomUUID().toString();
        StationStatus ss = new StationStatus(null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown), "", sessionId, System.currentTimeMillis());
        st.status = StationStatusUtils.toString(ss);
        launchpadService.getStationsRepository().save(st);

        // TODO 2019.05.19 why we send stationId as a String?
        return Protocol.asArray(new Protocol.AssignedStationId(Long.toString(st.getId()), sessionId));
    }

    public ExchangeData processExchangeData(ExchangeData data) {
        ExchangeData responses = new ExchangeData();
        for (Command command : data.getCommands()) {
            if (command.getType()== Command.Type.Nop) {
                continue;
            }
            command.launchpadUrl = data.launchpadUrl;
            responses.setCommands(process(command));
        }
        return responses;
    }
}
