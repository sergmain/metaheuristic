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

package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.comm.Command;
import ai.metaheuristic.ai.comm.CommandProcessor;
import ai.metaheuristic.ai.comm.ExchangeData;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.launchpad.experiment.task.SimpleTaskExecResult;
import ai.metaheuristic.ai.yaml.station_task.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */

@Slf4j
public class LaunchpadRequestor {

    private final Globals globals;

    private final RestTemplate restTemplate;

    private final CommandProcessor commandProcessor;
    private final StationTaskService stationTaskService;
    private final StationService stationService;
    private final MetadataService metadataService;
    private final CurrentExecState currentExecState;
    private final LaunchpadLookupExtendedService launchpadLookupExtendedService;

    private final LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad;
    private final String launchpadUrl;
    private final String serverRestUrl;

    public LaunchpadRequestor(String launchpadUrl, Globals globals, CommandProcessor commandProcessor, StationTaskService stationTaskService, StationService stationService, MetadataService metadataService, CurrentExecState currentExecState, LaunchpadLookupExtendedService launchpadLookupExtendedService) {
        this.launchpadUrl = launchpadUrl;
        this.globals = globals;
        this.commandProcessor = commandProcessor;
        this.stationTaskService = stationTaskService;
        this.stationService = stationService;
        this.metadataService = metadataService;
        this.currentExecState = currentExecState;
        this.launchpadLookupExtendedService = launchpadLookupExtendedService;
        this.restTemplate = new RestTemplate();
        this.launchpad = this.launchpadLookupExtendedService.lookupExtendedMap.get(launchpadUrl);
        if (launchpad == null) {
            throw new IllegalStateException("#775.01 Can'r find launchpad config for url " + launchpadUrl);
        }
        serverRestUrl = launchpadUrl + Consts.REST_V1_URL + Consts.SERVER_REST_URL;

    }

    private final List<Command> commands = new ArrayList<>();

    private void addCommand(Command cmd) {
        synchronized (commands) {
            commands.add(cmd);
        }
    }

    private void addCommands(List<Command> cmds) {
        synchronized (commands) {
            commands.addAll(cmds);
        }
    }

    private long lastRequestForMissingResources = 0;

    public void proceedWithRequest() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        if (launchpad.schedule.isCurrentTimeInactive()) {
            log.info("LaunchpadRequestor for url {} is inactive", launchpadUrl);
            return;
        }
        try {
            Monitoring.log("##010", Enums.Monitor.MEMORY);
            ExchangeData data = new ExchangeData();
            final String stationId = metadataService.getStationId(launchpadUrl);
            final String sessionId = metadataService.getSessionId(launchpadUrl);
            data.initRequestToLaunchpad(stationId, sessionId);

            // !!! always use data.setCommand() for correct initializing of stationId !!!

            if (stationId == null || sessionId==null) {
                data.setCommand(new Protocol.RequestStationId());
            }
            else {

                // always report about current active sequences, if we have actual stationId
                final Protocol.StationTaskStatus stationTaskStatus = stationTaskService.produceStationTaskStatus(launchpadUrl);
                data.setCommand(stationTaskStatus);
                data.setCommand(stationService.produceReportStationStatus(launchpadUrl, launchpad.schedule));

                // we have to pull new tasks from server constantly
                if (currentExecState.isInited(launchpadUrl)) {
                    Monitoring.log("##011", Enums.Monitor.MEMORY);
                    final boolean b = stationTaskService.isNeedNewTask(launchpadUrl, stationId);
                    Monitoring.log("##012", Enums.Monitor.MEMORY);
                    if (b) {
                        data.setCommand(new Protocol.RequestTask(launchpad.launchpadLookup.acceptOnlySignedSnippets));
                    }
                }
                if (System.currentTimeMillis() - lastRequestForMissingResources > 15_000) {
                    data.setCommand(new Protocol.CheckForMissingOutputResources());
                    lastRequestForMissingResources = System.currentTimeMillis();
                }

                Monitoring.log("##013", Enums.Monitor.MEMORY);
                reportTaskProcessingResult(data);
                Monitoring.log("##014", Enums.Monitor.MEMORY);

                List<Command> cmds;
                synchronized (commands) {
                    cmds = new ArrayList<>(commands);
                    commands.clear();
                }
                data.setCommands(cmds);
            }

            final String url = serverRestUrl + '/' + UUID.randomUUID().toString().substring(0, 8) + '-' + stationId;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (launchpad.launchpadLookup.securityEnabled) {
                    String auth = launchpad.launchpadLookup.restUsername + '=' + launchpad.launchpadLookup.restToken + ':' + launchpad.launchpadLookup.restPassword;
                    byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.US_ASCII));
                    String authHeader = "Basic " + new String(encodedAuth);
                    headers.set("Authorization", authHeader);
                }

                HttpEntity<ExchangeData> request = new HttpEntity<>(data, headers);
                Monitoring.log("##015", Enums.Monitor.MEMORY);

                log.debug("Start to request a launchpad at {}", url);
                ResponseEntity<ExchangeData> response = restTemplate.exchange(url, HttpMethod.POST, request, ExchangeData.class);
                Monitoring.log("##016", Enums.Monitor.MEMORY);
                ExchangeData result = response.getBody();
                log.debug("ExchangeData from launchpad: {}", data);
                if (result == null) {
                    log.warn("#775.050 Launchpad returned null as a result");
                    return;
                }
                if (!result.isSuccess()) {
                    log.error("#775.055 Something wrong at the launchpad side. Check the launchpad's logs for more info.");
                    return;
                }
                result.launchpadUrl = launchpadUrl;
                storeLaunchpadConfig(result);

                Monitoring.log("##017", Enums.Monitor.MEMORY);
                addCommands(commandProcessor.processExchangeData(result).getCommands());
                Monitoring.log("##018", Enums.Monitor.MEMORY);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    log.error("#775.11 Error 401 accessing url {}, securityEnabled: {}", serverRestUrl, launchpad.launchpadLookup.securityEnabled);
                } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                    log.error("#775.16 Error 403 accessing url {}, securityEnabled: {}", serverRestUrl, launchpad.launchpadLookup.securityEnabled);
                } else {
                    throw e;
                }
            } catch (ResourceAccessException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SocketException) {
                    log.error("#775.22 Connection error: url: {}, err: {}", url, cause.toString());
                } else {
                    log.error("#775.27 Error, url: " + url, e);
                }
            } catch (RestClientException e) {
                log.error("#775.31 Error accessing url: {}, error: {}", url, e.getMessage());
                if (e.getMessage() == null || !e.getMessage().contains("503")) {
                    log.error("#775.35 Stacktrace", e);
                }
            }
        } catch (Throwable e) {
            log.error("#775.41 Error in fixedDelay(), url: "+serverRestUrl+", error: {}", e);
        }
    }

    private void reportTaskProcessingResult(ExchangeData data) {
        final List<StationTask> list = stationTaskService.getForReporting(launchpadUrl);
        if (list.isEmpty()) {
            return;
        }
        final Protocol.ReportTaskProcessingResult command = new Protocol.ReportTaskProcessingResult();
        for (StationTask task : list) {
            if (task.isDelivered() || task.isReported()) {
                continue;
            }
            command.getResults().add(new SimpleTaskExecResult(task.getTaskId(),
                    task.getSnippetExecResult(),
                    task.getMetrics()));
            stationTaskService.setReportedOn(launchpadUrl, task.taskId);
        }
        data.setCommand(command);
    }

    private void storeLaunchpadConfig(ExchangeData data) {
        if (data==null || data.getLaunchpadConfig()==null) {
            return;
        }
        LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad =
                launchpadLookupExtendedService.lookupExtendedMap.get(data.launchpadUrl);

        if (launchpad==null) {
            return;
        }
        launchpad.config = data.getLaunchpadConfig();
    }
}


