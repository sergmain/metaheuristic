/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.station.StationTopLevelService;
import ai.metaheuristic.ai.launchpad.task.TaskService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final TaskService taskService;
    private final StationTopLevelService stationTopLevelService;
    private final WorkbookService workbookService;
    private final SnippetRepository snippetRepository;
    private final SnippetCache snippetCache;

    private static final long SNIPPET_INFOS_TIMEOUT_REFRESH = TimeUnit.SECONDS.toMillis(5);
    private List<LaunchpadCommParamsYaml.Snippets.Info> snippetInfosCache = new ArrayList<>();
    private long mills = System.currentTimeMillis();

    public void process(StationCommParamsYaml scpy, LaunchpadCommParamsYaml lcpy) {
        lcpy.resendTaskOutputResource = checkForMissingOutputResources(scpy);
        processStationTaskStatus(scpy);
        processResendTaskOutputResourceResult(scpy);
        processStationTaskStatus(scpy);
        lcpy.reportResultDelivering = processReportTaskProcessingResult(scpy);
        processReportStationStatus(scpy);
        lcpy.assignedTask = processRequestTask(scpy);
        lcpy.assignedStationId = getNewStationId(scpy.requestStationId);
        lcpy.snippets.infos.addAll( getSnippetInfos() );
    }

    private synchronized List<LaunchpadCommParamsYaml.Snippets.Info> getSnippetInfos() {
        if (System.currentTimeMillis() - mills > SNIPPET_INFOS_TIMEOUT_REFRESH) {
            mills = System.currentTimeMillis();
            final List<Long> allIds = snippetRepository.findAllIds();
            snippetInfosCache = allIds.stream()
                    .map(snippetCache::findById)
                    .map(s->new LaunchpadCommParamsYaml.Snippets.Info(s.code, s.getSnippetConfig(false).sourcing))
                    .collect(Collectors.toList());
        }
        return snippetInfosCache;
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
        stationTopLevelService.storeStationStatuses(request.stationCommContext.stationId, request.reportStationStatus, request.snippetDownloadStatus);
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
        StationStatusYaml ss = new StationStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown),
                "", sessionId, System.currentTimeMillis(), "", "", null, false,
                1, EnumsApi.OS.unknown);

        st.status = StationStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
        stationCache.save(st);

        // TODO 2019.05.19 why do we send stationId as a String?
        return new LaunchpadCommParamsYaml.AssignedStationId(Long.toString(st.getId()), sessionId);
    }
}
