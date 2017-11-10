package aiai.ai.comm;

import aiai.ai.launchpad.station.Station;
import aiai.ai.launchpad.station.StationsRepository;
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

    private ObjectMapper mapper;

    public CommandProcessor(StationsRepository stationsRepository) {
        this.stationsRepository = stationsRepository;
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
            case RequestDatasets:
                break;
            case AssignStationId:
                return getNewStationId(command);
        }

        return new Protocol.Nop();
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

            ExchangeData responses = new ExchangeData();
            for (Command command : data.commands) {
                command.setSysParams(sysParams);
                responses.addCommand(process(command));
            }

            //noinspection UnnecessaryLocalVariable
            String responseAsJson = mapper.writeValueAsString(responses);
            return responseAsJson;

        } catch (IOException e) {
            e.printStackTrace();
            return e.toString();
        }
    }
}
