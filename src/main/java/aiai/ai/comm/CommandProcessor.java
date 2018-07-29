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
import aiai.ai.beans.Station;
import aiai.ai.repositories.StationsRepository;
import aiai.ai.station.StationService;
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
    private StationService stationService;
    private InviteService inviteService;

    private ObjectMapper mapper;

    public CommandProcessor(StationsRepository stationsRepository, StationService stationService, InviteService inviteService) {
        this.stationsRepository = stationsRepository;
        this.stationService = stationService;
        this.inviteService = inviteService;
        this.mapper = new ObjectMapper();
        this.mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public Command process(Command command) {
        switch (command.getType()) {
            case Nop:
                break;
            case ReportStation:
                break;
            case RequestDefinitions:
                break;
            case RequestStationId:
                return getNewStationId((Protocol.RequestStationId)command);
            case AssignedStationId:
                return storeStationId((Protocol.AssignedStationId)command);
            case ReAssignStationId:
                return reAssignStationId((Protocol.ReAssignStationId)command);
            case RegisterInvite:
                return processInvite((Protocol.RegisterInvite) command);
            case RegisterInviteResult:
                break;
            case RequestExperiment:
                break;
        }
        return new Protocol.Nop();
    }

    private Command storeStationId(Protocol.AssignedStationId command) {
        System.out.println("New station Id: " + command.getStationId());
        stationService.storeStationId(command.getStationId());
        return new Protocol.Nop();
    }

    private Command reAssignStationId(Protocol.ReAssignStationId command) {
        System.out.println("New station Id: " + command.getStationId());
        stationService.changeStationId(command.getStationId());
        return new Protocol.Nop();
    }

    private Command processInvite(Protocol.RegisterInvite command) {
        Protocol.RegisterInviteResult result = new Protocol.RegisterInviteResult();
        result.setInviteResult(inviteService.processInvite(command.getInvite()));
        return result;
    }

    private Command getNewStationId(Protocol.RequestStationId command) {
        final Station st = new Station();
        stationsRepository.save(st);

        return new Protocol.AssignedStationId(Long.toString(st.getId()));
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

    public ExchangeData processExchangeData(ExchangeData data) {
        return processExchangeData(null, data);
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
