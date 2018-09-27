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

import aiai.ai.Globals;
import aiai.ai.yaml.station.StationExperimentSequence;
import aiai.ai.comm.*;
import aiai.ai.launchpad.experiment.SimpleSequenceExecResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */
@Service
@Slf4j
public class LaunchpadRequester {

    private final Globals globals;

    private final RestTemplate restTemplate;

    private final CommandProcessor commandProcessor;
    private final StationExperimentService stationExperimentService;
    private final StationService stationService;

    @Autowired
    public LaunchpadRequester(Globals globals, CommandProcessor commandProcessor, StationExperimentService stationExperimentService, StationService stationService) {
        this.globals = globals;
        this.commandProcessor = commandProcessor;
        this.stationExperimentService = stationExperimentService;
        this.stationService = stationService;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void postConstruct() {
        if (!globals.isStationEnabled) {
            return;
        }
        String env = stationService.getEnv();
        addCommand(new Protocol.ReportStationEnv(env));
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

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        ExchangeData data = new ExchangeData();
        String stationId = stationService.getStationId();
        if (stationId==null) {
            data.setCommand(new Protocol.RequestStationId());
        }
        data.setStationId(stationId);

        needNewExperimentSequence(data, stationId);
        reportSequenceProcessingResult(data);

        List<Command> cmds;
        synchronized (commands) {
            cmds = new ArrayList<>(commands);
            commands.clear();
        }
        data.setCommands(cmds);
        if (stationId!=null) {
            // always report about current active sequences, if we have actual stationId
            data.setCommand(stationExperimentService.produceStationSequenceStatus());
        }

        // !!! always use data.setCommand() for correct initializing stationId !!!

        // we have to pull new tasks from server constantly
        try {
            HttpEntity<ExchangeData> request = new HttpEntity<>(data, headers);
            ResponseEntity<ExchangeData> response = restTemplate.exchange(globals.serverRestUrl, HttpMethod.POST, request, ExchangeData.class);
            ExchangeData result = response.getBody();

            addCommands(commandProcessor.processExchangeData(result).getCommands());
            log.debug("fixedDelay(), {}", result);
        } catch (RestClientException e) {
            System.out.println("Error accessing url: " + globals.serverRestUrl);
            e.printStackTrace();
        }
    }

    private void reportSequenceProcessingResult(ExchangeData data) {
        final List<StationExperimentSequence> list = stationExperimentService.getForReporting();
        if (list.isEmpty()) {
            return;
        }
        final Protocol.ReportSequenceProcessingResult command = new Protocol.ReportSequenceProcessingResult();
        for (StationExperimentSequence seq : list) {
            command.getResults().add(new SimpleSequenceExecResult(seq.getExperimentSequenceId(), seq.getSnippetExecResults(), seq.getMetrics()));
            seq.setReported(true);
            seq.setReportedOn(System.currentTimeMillis());
        }
        stationExperimentService.saveReported(list);
        data.setCommand(command);
    }

    private void needNewExperimentSequence(ExchangeData data, String stationId) {
        final boolean b = stationExperimentService.isNeedNewExperimentSequence(stationId);
        if (b) {
            data.setCommand(new Protocol.RequestExperimentSequence());
        }
    }
}


