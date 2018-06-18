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

import aiai.ai.invite.InviteService;
import aiai.ai.launchpad.station.Station;
import aiai.ai.repositories.StationsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * User: Serg
 * Date: 12.08.2017
 * Time: 19:48
 */
@Service
public class CommandProcessor {

    private static final String EMPTY_JSON = "[{}]";

    private StationsRepository stationsRepository;
    private InviteService inviteService;

    private ObjectMapper mapper;

    public CommandProcessor(StationsRepository stationsRepository, InviteService inviteService) {
        this.stationsRepository = stationsRepository;
        this.inviteService = inviteService;
        this.mapper = new ObjectMapper();
        this.mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public Command process(Command command) {
        switch (command.getType()) {
            case Nop:
                break;
            case Ok:
                break;
            case ReportStation:
                break;
            case RequestDefinitions:
                break;
            case AssignStationId:
                return getNewStationId(command);
            case RegisterInvite:
                return processInvite((Protocol.RegisterInvite) command);
            case RegisterInviteResult:
                break;
            case RequestExperiment:
                break;
        }

        return new Protocol.Nop();
    }

    private Command processInvite(Protocol.RegisterInvite command) {
        Protocol.RegisterInviteResult result = new Protocol.RegisterInviteResult();
        result.setInviteResult(inviteService.processInvite(command.getInvite()));
        return result;
    }

    private Command getNewStationId(Command command) {
        Command response = new Protocol.AssignStationId();

        final Station st = new Station();
        st.setDescription(command.getParams().get(CommConsts.DESC));
        st.setIp(command.getSysParams().get(CommConsts.IP));
        stationsRepository.save(st);

        response.getResponse().put(CommConsts.STATION_ID, Long.toString(st.getId()));
        return response;
    }

    public String processAll(String json, Map<String, String> sysParams) {
        try {
            ExchangeData data = mapper.readValue(json, ExchangeData.class);
            System.out.println(data);

            ExchangeData exchangeData = processExchangeData(sysParams, data);

            //noinspection UnnecessaryLocalVariable
            String responseAsJson = mapper.writeValueAsString(exchangeData);
            return responseAsJson;


        } catch (IOException e) {
            e.printStackTrace();
            return "error parsing the request json";
        }
    }

    public ExchangeData processExchangeData(Map<String, String> sysParams, ExchangeData data) {
        ExchangeData responses = new ExchangeData();
        for (Command command : data.getCommands()) {
            command.setSysParams(sysParams);
            responses.setCommand(process(command));
        }
        return responses;
    }
}
