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

package ai.metaheuristic.ai.launchpad;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.station.StationTopLevelService;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookSchedulerService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 8/29/2019
 * Time: 8:03 PM
 */
@Slf4j
@Service
@Profile("launchpad")
@RequiredArgsConstructor
public class LaunchpadCommandProcessor {

    private final StationCache stationCache;
    private final ExperimentService experimentService;
    private final TaskService taskService;
    private final PlanService planService;
    private final ArtifactCleanerAtLaunchpad artifactCleanerAtLaunchpad;
    private final StationTopLevelService stationTopLevelService;
    private final WorkbookService workbookService;
    private final WorkbookSchedulerService workbookSchedulerService;

    public void process(StationCommParamsYaml scpy, LaunchpadCommParamsYaml lcpy) {
        lcpy.resendTaskOutputResource = checkForMissingOutputResources(scpy);
        checkForMissingOutputResources(scpy);
        processStationTaskStatus(scpy);
        processStationTaskStatus(scpy);
        processResendTaskOutputResourceResult(scpy);
        processStationTaskStatus(scpy);
        lcpy.reportResultDelivering = processReportTaskProcessingResult(scpy);
        processReportStationStatus(scpy);
        lcpy.assignedTask = processRequestTask(scpy);
        lcpy.assignedStationId = getNewStationId(scpy.requestStationId);
    }

    // processing on launchpad side
    public LaunchpadCommParamsYaml.ResendTaskOutputResource checkForMissingOutputResources(StationCommParamsYaml request) {
        if (request.checkForMissingOutputResources==null) {
            return null;
        }
        final long stationId = Long.parseLong(request.stationCommContext.stationId);
        List<Long> ids = taskService.resourceReceivingChecker(stationId);
        return new LaunchpadCommParamsYaml.ResendTaskOutputResource(ids);
    }

    // processing on launchpad side
    public void processResendTaskOutputResourceResult(StationCommParamsYaml request) {
        if (request.resendTaskOutputResourceResult==null) {
            return;
        }
        for (StationCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus status : request.resendTaskOutputResourceResult.statuses) {
            taskService.processResendTaskOutputResourceResult(request.stationCommContext.stationId, status.status, status.taskId);
        }
    }

    // processing on launchpad side
    public void processStationTaskStatus(StationCommParamsYaml request) {
        if (request.reportStationTaskStatus==null || request.reportStationTaskStatus.statuses==null) {
            return;
        }
        stationTopLevelService.reconcileStationTasks(request.stationCommContext.stationId, request.reportStationTaskStatus.statuses);
    }

    // processing on launchpad side
    public LaunchpadCommParamsYaml.ReportResultDelivering processReportTaskProcessingResult(StationCommParamsYaml request) {
        if (request.reportTaskProcessingResult==null || request.reportTaskProcessingResult.results==null) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final LaunchpadCommParamsYaml.ReportResultDelivering cmd1 = new LaunchpadCommParamsYaml.ReportResultDelivering(
                workbookService.storeAllConsoleResults(request.reportTaskProcessingResult.results)
        );
        return cmd1;
    }

    // processing on launchpad side
    public void processReportStationStatus(StationCommParamsYaml request) {
        if (request.reportStationStatus==null) {
            return;
        }
        checkStationId(request);
        stationTopLevelService.storeStationStatus(request.stationCommContext.stationId, request.reportStationStatus);
    }

    // processing on launchpad side
    public LaunchpadCommParamsYaml.AssignedTask processRequestTask(StationCommParamsYaml request) {
        if (request.requestTask==null) {
            return null;
        }
        checkStationId(request);
        LaunchpadCommParamsYaml.AssignedTask assignedTask =
                workbookService.getTaskAndAssignToStation(Long.parseLong(request.stationCommContext.stationId), request.requestTask.isAcceptOnlySigned(), null);

        if (assignedTask!=null) {
            log.info("Assign task #{} to station #{}", assignedTask.getTaskId(), request.stationCommContext.stationId );
        }
        return assignedTask;
    }

    public void checkStationId(StationCommParamsYaml request) {
        if (request.stationCommContext==null  || request.stationCommContext.stationId==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("stationId is null");
        }
    }

    // processing on launchpad side
    public LaunchpadCommParamsYaml.AssignedStationId getNewStationId(StationCommParamsYaml.RequestStationId request) {
        if (request==null) {
            return null;
        }
        String sessionId = StationTopLevelService.createNewSessionId();
        final Station st = new Station();
        StationStatus ss = new StationStatus(null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown),
                "", sessionId, System.currentTimeMillis(), "", "", null, false,
                1, EnumsApi.OS.unknown);

        st.status = StationStatusUtils.toString(ss);
        stationCache.save(st);

        // TODO 2019.05.19 why do we send stationId as a String?
        return new LaunchpadCommParamsYaml.AssignedStationId(Long.toString(st.getId()), sessionId);
    }
}
