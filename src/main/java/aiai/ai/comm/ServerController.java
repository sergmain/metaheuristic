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

import aiai.ai.beans.Station;
import aiai.ai.repositories.StationsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 19:19
 */
@RestController
@Slf4j
public class ServerController {

    private static final ExchangeData EXCHANGE_DATA_NOP = new ExchangeData(Protocol.NOP);
    private CommandProcessor commandProcessor;
    private StationsRepository stationsRepository;

    public ServerController(CommandProcessor commandProcessor, StationsRepository stationsRepository) {
        this.commandProcessor = commandProcessor;
        this.stationsRepository = stationsRepository;
    }

    @PostMapping("/rest-anon/srv")
    public ExchangeData postDatasets(@RequestBody ExchangeData data, HttpServletRequest request) {
        log.debug("postDatasets(),  {}", data);
        if (StringUtils.isBlank(data.getStationId())) {
            return new ExchangeData(commandProcessor.process(new Protocol.RequestStationId()));
        }
        if (stationsRepository.findById(Long.parseLong(data.getStationId())).orElse(null)==null) {
            Station s = new Station();
            s.setIp(request.getRemoteAddr());
            s.setDescription("Id was ressigned from "+data.getStationId());
            stationsRepository.save(s);
            return new ExchangeData(new Protocol.ReAssignStationId(s.getId()));
        }

        if (data.isNothingTodo()) {
            return EXCHANGE_DATA_NOP;
        }

        List<Command> commands = data.getCommands();
        ExchangeData resultData = new ExchangeData();
        for (Command command : commands) {
            if (data.getStationId()!=null && command instanceof Protocol.RequestStationId) {
                continue;
            }
            resultData.setCommand(commandProcessor.process(command));
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


}
