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
import aiai.ai.comm.Command;
import aiai.ai.comm.CommandProcessor;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */
@Service
@EnableScheduling
public class LaunchpadRequester {

    private String targetUrl;

    private final Globals globals;

    private final RestTemplate restTemplate;

    private final CommandProcessor commandProcessor;
    private final StationExperimentService stationExperimentService;
    private StationService stationService;

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
        if (globals.isStationEnabled) {
            targetUrl = globals.launchpadUrl + "/rest-anon/srv";
        }
    }

    private final List<Command> commands = new ArrayList<>();

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
    @Scheduled(fixedDelayString = "#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.station.request-launchpad.timeout'), 3, 20, 10)*1000 }")
    public void fixedDelayTaskComplex() {
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

        final boolean b = stationExperimentService.isNeedNewExperimentSequence(stationId);
        if (b) {
            data.setCommand(new Protocol.RequestExperimentSequence());
        }

        List<Command> cmds;
        synchronized (commands) {
            cmds = new ArrayList<>(commands);
            commands.clear();
        }
        data.setCommands(cmds);

        // we have to pull new tasks from server constantly
        try {
            HttpEntity<ExchangeData> request = new HttpEntity<>(data, headers);
            ResponseEntity<ExchangeData> response = restTemplate.exchange(targetUrl, HttpMethod.POST, request, ExchangeData.class);
            ExchangeData result = response.getBody();

            addCommands(commandProcessor.processExchangeData(result).getCommands());

            System.out.println(new Date() + " This runs in a fixed delay (Complex), result: " + result);
        } catch (RestClientException e) {
            System.out.println("Error accessing url: " + targetUrl);
            e.printStackTrace();
        }
    }
}


