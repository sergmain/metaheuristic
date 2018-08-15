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

import aiai.ai.station.StationExperimentService;
import aiai.ai.station.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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

    @Value("${aiai.station.launchpad.url}")
    private String launchpadUrl;
    private String targetUrl;


    private final RestTemplate restTemplate;

    private final CommandProcessor commandProcessor;
    private final StationExperimentService stationExperimentService;
    private StationService stationService;

    @Autowired
    public LaunchpadRequester(CommandProcessor commandProcessor, StationExperimentService stationExperimentService, StationService stationService) {
        this.commandProcessor = commandProcessor;
        this.stationExperimentService = stationExperimentService;
        this.stationService = stationService;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void postConstruct() {
        targetUrl = launchpadUrl + "/rest-anon/srv";
    }

    private final List<Command> commands = new ArrayList<>();

    public void addCommand(Command command) {
        synchronized (commands) {
            commands.add(command);
        }
    }

    public void addCommands(List<Command> cmds) {
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
    @Scheduled(fixedDelayString = "#{ new Integer(environment.getProperty('aiai.station.request.launchpad.timeout')) > 10 ? new Integer(environment.getProperty('aiai.station.request.launchpad.timeout'))*1000 : 10000 }")
    public void fixedDelayTaskComplex() {

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
        if (data.isNothingTodo()) {
            return;
        }

        HttpEntity<ExchangeData> request = new HttpEntity<>(data, headers);
        ResponseEntity<ExchangeData> response = restTemplate.exchange(targetUrl, HttpMethod.POST, request, ExchangeData.class);
        ExchangeData result = response.getBody();

        addCommands(commandProcessor.processExchangeData(result).getCommands());

        System.out.println(new Date() + " This runs in a fixed delay (Complex), result: " + result);
    }
}


