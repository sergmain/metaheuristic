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

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.Station;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import aiai.ai.launchpad.repositories.StationsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 19:19
 */
@RestController
@Slf4j
@Profile("launchpad")
public class ServerController {

    private static final ExchangeData EXCHANGE_DATA_NOP = new ExchangeData(Protocol.NOP);

    private final Globals globals;
    private final CommandProcessor commandProcessor;
    private final StationsRepository stationsRepository;
    private final ExperimentRepository experimentRepository;

    public ServerController(Globals globals, CommandProcessor commandProcessor, StationsRepository stationsRepository, ExperimentRepository experimentRepository) {
        this.globals = globals;
        this.commandProcessor = commandProcessor;
        this.stationsRepository = stationsRepository;
        this.experimentRepository = experimentRepository;
    }

    @PostMapping("/rest-anon/srv")
    public ExchangeData processRequestAnon(HttpServletResponse response, @RequestBody ExchangeData data, HttpServletRequest request) throws IOException {
        log.debug("processRequestAnon(), globals.isSecureRestUrl: {}, data: {}", globals.isSecureRestUrl, data);
        if (globals.isSecureRestUrl) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return processRequest(data, request);
    }

    @PostMapping("/rest-auth/srv")
    public ExchangeData processRequestAuth(@RequestBody ExchangeData data, HttpServletRequest request) {
        log.debug("processRequestAnon(), globals.isSecureRestUrl: {}, data: {}", globals.isSecureRestUrl, data);
        return processRequest(data, request);
    }

    private ExchangeData processRequest(@RequestBody ExchangeData data, HttpServletRequest request) {
        if (StringUtils.isBlank(data.getStationId())) {
            return new ExchangeData(commandProcessor.process(new Protocol.RequestStationId()));
        }
        if (stationsRepository.findById(Long.parseLong(data.getStationId())).orElse(null)==null) {
            Station s = new Station();
            s.setIp(request.getRemoteAddr());
            s.setDescription("Id was reassigned from "+data.getStationId());
            stationsRepository.save(s);
            return new ExchangeData(new Protocol.ReAssignStationId(s.getId()));
        }

        ExchangeData resultData = new ExchangeData();
        resultData.setCommand(new Protocol.ExperimentStatus(experimentRepository.findAll().stream().map(ServerController::to).collect(Collectors.toList())));

        List<Command> commands = data.getCommands();
        for (Command command : commands) {
            if (data.getStationId()!=null && command instanceof Protocol.RequestStationId) {
                continue;
            }
            resultData.setCommands(commandProcessor.process(command));
        }

        return resultData.getCommands().isEmpty() ? EXCHANGE_DATA_NOP : resultData;
    }

    /**
     * This endpoint is only for testing security. Do not delete
     * @return String
     */
    @GetMapping("/rest-anon/test")
    public String getMessage_1() {
        return "Ok";
    }

    /**
     * This endpoint is only for testing security. Do not delete
     * @return String
     */
    @GetMapping("/rest-auth/test")
    public String getMessage_2() {
        return "Ok";
    }

    private static Protocol.ExperimentStatus.SimpleStatus to(Experiment experiment) {
        return new Protocol.ExperimentStatus.SimpleStatus(experiment.getId(), Enums.ExperimentExecState.toState(experiment.getExecState()));
    }
}
