package aiai.ai.launchpad.server;

import aiai.ai.Enums;
import aiai.ai.comm.Command;
import aiai.ai.comm.CommandProcessor;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.Station;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import aiai.ai.launchpad.repositories.StationsRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServerService {

    private static final ExchangeData EXCHANGE_DATA_NOP = new ExchangeData(Protocol.NOP);

    private final CommandProcessor commandProcessor;
    private final StationsRepository stationsRepository;
    private final ExperimentRepository experimentRepository;
    private final BinaryDataService binaryDataService;

    public static final UploadResult OK_UPLOAD_RESULT = new UploadResult(true, null);

    public void storeUploadedData(File resFile, Enums.BinaryDataType type, String code) throws IOException {
        try (InputStream is = new FileInputStream(resFile)) {
            binaryDataService.save(is, resFile.length(), type, code, code, false, null);
        }
    }

    @Data
    @AllArgsConstructor
    public static class UploadResult {
        public boolean isOk;
        public String error;
    }

    public ServerService(CommandProcessor commandProcessor, StationsRepository stationsRepository, ExperimentRepository experimentRepository, BinaryDataService binaryDataService) {
        this.commandProcessor = commandProcessor;
        this.stationsRepository = stationsRepository;
        this.experimentRepository = experimentRepository;
        this.binaryDataService = binaryDataService;
    }

    ExchangeData processRequest(@RequestBody ExchangeData data, String remoteAddress) {
        if (StringUtils.isBlank(data.getStationId())) {
            return new ExchangeData(commandProcessor.process(new Protocol.RequestStationId()));
        }
        if (stationsRepository.findById(Long.parseLong(data.getStationId())).orElse(null)==null) {
            Station s = new Station();
            s.setIp(remoteAddress);
            s.setDescription("Id was reassigned from "+data.getStationId());
            stationsRepository.save(s);
            return new ExchangeData(new Protocol.ReAssignStationId(s.getId()));
        }

        ExchangeData resultData = new ExchangeData();
        resultData.setCommand(new Protocol.ExperimentStatus(experimentRepository.findAll().stream().map(ServerService::to).collect(Collectors.toList())));

        List<Command> commands = data.getCommands();
        for (Command command : commands) {
            if (data.getStationId()!=null && command instanceof Protocol.RequestStationId) {
                continue;
            }
            resultData.setCommands(commandProcessor.process(command));
        }

        return resultData.getCommands().isEmpty() ? EXCHANGE_DATA_NOP : resultData;
    }

    private static Protocol.ExperimentStatus.SimpleStatus to(Experiment experiment) {
        return new Protocol.ExperimentStatus.SimpleStatus(experiment.getId(), Enums.ExperimentExecState.toState(experiment.getExecState()));
    }
}
