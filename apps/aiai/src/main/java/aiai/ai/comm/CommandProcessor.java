/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.comm;

import aiai.ai.launchpad.LaunchpadService;
import aiai.ai.launchpad.beans.Station;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.station.StationService;
import aiai.ai.station.TaskProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/**
 * User: Serg
 * Date: 12.08.2017
 * Time: 19:48
 */
@Service
@Slf4j
public class CommandProcessor {

    private final LaunchpadService launchpadService;

    private final StationService stationService;
    private final TaskProcessor taskProcessor;

    // TODO not implemented yet
//    private final InviteService inviteService;

    public CommandProcessor(StationService stationService, TaskProcessor taskProcessor, LaunchpadService launchpadService) {
        this.launchpadService = launchpadService;
        this.stationService = stationService;
        this.taskProcessor = taskProcessor;
    }

    public Command[] process(Command command) {
        switch (command.getType()) {
            case Nop:
                break;
            case ReportStation:
                break;
            case RequestStationId:
                return getNewStationId((Protocol.RequestStationId) command);
            case AssignedStationId:
                return storeStationId((Protocol.AssignedStationId) command);
            case ReAssignStationId:
                return reAssignStationId((Protocol.ReAssignStationId) command);
            case RequestTask:
                // processing on launchpad side
                return processRequestTask((Protocol.RequestTask) command);
            case AssignedTask:
                // processing on station side
                return processAssignedTask((Protocol.AssignedTask) command);
            case ReportStationStatus:
                return processReportStationStatus((Protocol.ReportStationStatus) command);
            case ReportTaskProcessingResult:
                // processing on launchpad side
                return processReportTaskProcessingResult((Protocol.ReportTaskProcessingResult) command);
            case ReportResultDelivering:
                return processReportResultDelivering((Protocol.ReportResultDelivering) command);
            case ExperimentStatus:
                // processing on station side
                return processExperimentStatus((Protocol.ExperimentStatus) command);
            case StationTaskStatus:
                // processing on launchpad side
                return processStationTaskStatus((Protocol.StationTaskStatus) command);
            default:
                System.out.println("There is new command which isn't processed: " + command.getType());
        }
        return Protocol.NOP_ARRAY;
    }

    private Command[] processStationTaskStatus(Protocol.StationTaskStatus command) {
        launchpadService.getExperimentService().reconcileStationTasks(command.stationId, command.statuses!=null ? command.statuses : new ArrayList<>());
        return Protocol.NOP_ARRAY;
    }

    private Command[] processExperimentStatus(Protocol.ExperimentStatus command) {
        taskProcessor.processExperimentStatus(command.statuses);
        return Protocol.NOP_ARRAY;
    }

    private Command[] processReportResultDelivering(Protocol.ReportResultDelivering command) {
        stationService.markAsDelivered(command.getIds());
        return Protocol.NOP_ARRAY;
    }

    private Command[] processReportTaskProcessingResult(Protocol.ReportTaskProcessingResult command) {
        if (command.getResults().isEmpty()) {
            return Protocol.NOP_ARRAY;
        }
        final Protocol.ReportResultDelivering cmd1 = new Protocol.ReportResultDelivering(
                launchpadService.getTaskService().storeAllResults(command.getResults())
        );
        // we can't return immediately task because we have to receive some params from station,
        // like has snippet to be signed or not

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
        if (!Objects.equals(station.getEnv(), command.getEnv()) || !Objects.equals(station.getActiveTime(), command.getActiveTime())) {
            station.setEnv(command.env);
            station.setActiveTime(command.activeTime);
            launchpadService.getStationsRepository().save(station);
        }
        return Protocol.NOP_ARRAY;
    }

    private Command[] processAssignedTask(Protocol.AssignedTask command) {
        stationService.assignTasks(command.tasks);
        return Protocol.NOP_ARRAY;
    }

    private Command[] processRequestTask(Protocol.RequestTask command) {
        checkStationId(command);
        Protocol.AssignedTask r = assignTaskToStation(command.getStationId(), command.isAcceptOnlySigned());
        return Protocol.asArray(r);
    }

    private synchronized Protocol.AssignedTask assignTaskToStation(String stationId, boolean isAcceptOnlySigned) {
        Protocol.AssignedTask r = new Protocol.AssignedTask();
        TaskService.TasksAndAssignToStationResult result =
            launchpadService.getTaskService()
                .getTaskAndAssignToStation(Long.parseLong(stationId), isAcceptOnlySigned, null);

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

    private Command[] storeStationId(Protocol.AssignedStationId command) {
        System.out.println("New station Id: " + command.getStationId());
        stationService.setStationId(command.getAssignedStationId());
        return Protocol.NOP_ARRAY;
    }

    private Command[] reAssignStationId(Protocol.ReAssignStationId command) {
        System.out.println("New station Id: " + command.getReAssignedStationId());
        stationService.setStationId(command.getReAssignedStationId());
        return Protocol.NOP_ARRAY;
    }

    @SuppressWarnings("unused")
    private Command[] getNewStationId(Protocol.RequestStationId command) {
        final Station st = new Station();
        launchpadService.getStationsRepository().save(st);

        return Protocol.asArray(new Protocol.AssignedStationId(Long.toString(st.getId())));
    }

    public ExchangeData processExchangeData(ExchangeData data) {
        ExchangeData responses = new ExchangeData();
        for (Command command : data.getCommands()) {
            if (command.getType()== Command.Type.Nop) {
                continue;
            }
            responses.setCommands(process(command));
        }
        return responses;
    }
}
