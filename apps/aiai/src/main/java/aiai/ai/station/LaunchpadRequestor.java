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

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.Monitoring;
import aiai.ai.comm.Command;
import aiai.ai.comm.CommandProcessor;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.experiment.task.SimpleTaskExecResult;
import aiai.ai.yaml.station.StationTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        if (launchpad==null) {
            throw new IllegalStateException("Can'r find launchpad config for url "+ launchpadUrl);
        }
        final String restUrl = launchpadUrl + (launchpad.launchpadLookup.isSecureRestUrl ? Consts.REST_AUTH_URL : Consts.REST_ANON_URL );
        serverRestUrl = restUrl + Consts.SERVER_REST_URL;

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

    /**
     * this scheduler is being run at the station side
     *
     * long fixedDelay()
     * Execute the annotated method with a fixed period in milliseconds between the end of the last invocation and the start of the next.
     */
    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        if (launchpad.periods.isCurrentTimeInactive()) {
            log.info("LaunchpadRequestor for url {} is inactive", launchpadUrl);
            return;
        }

        Monitoring.log("##010", Enums.Monitor.MEMORY);
        ExchangeData data = new ExchangeData();
        String stationId = metadataService.getStationId(launchpadUrl);
        if (stationId==null) {
            data.setCommand(new Protocol.RequestStationId());
        }
        data.setStationId(stationId);

        if (stationId!=null) {
            // always report about current active sequences, if we have actual stationId
            data.setCommand(stationTaskService.produceStationTaskStatus(launchpadUrl));
            data.setCommand(stationService.produceReportStationStatus(launchpad.periods));
            if (currentExecState.isInit(launchpadUrl)) {
                Monitoring.log("##011", Enums.Monitor.MEMORY);
                final boolean b = stationTaskService.isNeedNewTask(launchpadUrl, stationId);
                Monitoring.log("##012", Enums.Monitor.MEMORY);
                if (b) {
                    data.setCommand(new Protocol.RequestTask(launchpad.launchpadLookup.isAcceptOnlySignedSnippets));
                }
            }
            if (System.currentTimeMillis() - lastRequestForMissingResources > 15_000) {
                data.setCommand(new Protocol.CheckForMissingOutputResources());
                lastRequestForMissingResources = System.currentTimeMillis();
            }
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

        // !!! always use data.setCommand() for correct initializing stationId !!!

        // we have to pull new tasks from server constantly
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (launchpad.launchpadLookup.isSecureRestUrl) {
                String auth = launchpad.launchpadLookup.restUsername+'='+launchpad.launchpadLookup.restToken + ':' + launchpad.launchpadLookup.restPassword;
                byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.US_ASCII));
                String authHeader = "Basic " + new String(encodedAuth);
                headers.set("Authorization", authHeader);
            }

            HttpEntity<ExchangeData> request = new HttpEntity<>(data, headers);
            Monitoring.log("##015", Enums.Monitor.MEMORY);
            ResponseEntity<ExchangeData> response = restTemplate.exchange(serverRestUrl, HttpMethod.POST, request, ExchangeData.class);
            Monitoring.log("##016", Enums.Monitor.MEMORY);
            ExchangeData result = response.getBody();
            if (result==null) {
                log.warn("Launchpad returned null as a result");
                return;
            }
            result.launchpadUrl = launchpadUrl;

            Monitoring.log("##017", Enums.Monitor.MEMORY);
            addCommands(commandProcessor.processExchangeData(result).getCommands());
            Monitoring.log("##018", Enums.Monitor.MEMORY);
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode()== HttpStatus.UNAUTHORIZED) {
                log.error("Error 401 accessing url {}, isSecureRestUrl: {}", serverRestUrl, launchpad.launchpadLookup.isSecureRestUrl);
            }
            else if (e.getStatusCode()== HttpStatus.FORBIDDEN) {
                log.error("Error 403 accessing url {}, isSecureRestUrl: {}", serverRestUrl, launchpad.launchpadLookup.isSecureRestUrl);
            }
            else {
                throw e;
            }
        }
        catch (RestClientException e) {
            log.error("Error accessing url: {}, error: {}", serverRestUrl, e.getMessage());
            if (e.getMessage()==null || !e.getMessage().contains("503")) {
                log.error("Stacktrace", e);
            }
        }
    }

    private void reportTaskProcessingResult(ExchangeData data) {
        final List<StationTask> list = stationTaskService.getForReporting(launchpadUrl);
        if (list.isEmpty()) {
            return;
        }
        final Protocol.ReportTaskProcessingResult command = new Protocol.ReportTaskProcessingResult();
        for (StationTask task : list) {
            command.getResults().add(new SimpleTaskExecResult(task.getTaskId(), task.getSnippetExecResult(), task.getMetrics()));
            stationTaskService.setReportedOn(launchpadUrl, task.taskId);
        }
        data.setCommand(command);
    }

}


