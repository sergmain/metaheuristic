/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
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

package aiai.ai.launchpad.server;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.comm.Command;
import aiai.ai.comm.CommandProcessor;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import aiai.ai.exceptions.BinaryDataNotFoundException;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Station;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.StationsRepository;
import aiai.ai.resource.AssetFile;
import aiai.ai.resource.ResourceUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Profile("launchpad")
@Slf4j
public class ServerService {

    private static final ExchangeData EXCHANGE_DATA_NOP = new ExchangeData(Protocol.NOP);

    private final Globals globals;
    private final BinaryDataService binaryDataService;
    private final CommandProcessor commandProcessor;
    private final StationsRepository stationsRepository;
    private final CommandSetter commandSetter;

    public HttpEntity<AbstractResource> deliverResource(String typeAsStr, String code) {
        Enums.BinaryDataType binaryDataType = Enums.BinaryDataType.valueOf(typeAsStr.toUpperCase());
        return deliverResource(binaryDataType, code);
    }

    public HttpEntity<AbstractResource> deliverResource(Enums.BinaryDataType binaryDataType, String code) {
        return deliverResource(binaryDataType, code, null);
    }
    public HttpEntity<AbstractResource> deliverResource(Enums.BinaryDataType binaryDataType, String code, HttpHeaders httpHeaders) {
        AssetFile assetFile;
        switch(binaryDataType) {
            case SNIPPET:
                assetFile = ResourceUtils.prepareSnippetFile(globals.launchpadResourcesDir, code, null);
                break;
            case DATA:
            case TEST:
                assetFile = ResourceUtils.prepareDataFile(globals.launchpadResourcesDir, code, null);
                break;
            case UNKNOWN:
            default:
                throw new IllegalStateException("Unknown type of data: " + binaryDataType);
        }

        if (assetFile==null) {
            String es = "#442.12 resource with code "+code+" wasn't found";
            log.error(es);
            throw new BinaryDataNotFoundException(es);
        }
        try {
            binaryDataService.storeToFile(code, assetFile.file);
        } catch (BinaryDataNotFoundException e) {
            log.error("#442.16 Error store data to temp file, data doesn't exist in db, code " + code+", file: " + assetFile.file.getPath());
            throw e;
        }
        return new HttpEntity<>(new FileSystemResource(assetFile.file.toPath()), getHeader(httpHeaders, assetFile.file.length()));
    }

    private static HttpHeaders getHeader(HttpHeaders httpHeaders, long length) {
        HttpHeaders header = httpHeaders != null ? httpHeaders : new HttpHeaders();
        header.setContentLength(length);
        header.setCacheControl("max-age=0");
        header.setExpires(0);
        header.setPragma("no-cache");

        return header;
    }

    @Service
    @Profile("launchpad")
    public static class CommandSetter {
        private final FlowInstanceRepository flowInstanceRepository;

        public CommandSetter(FlowInstanceRepository flowInstanceRepository) {
            this.flowInstanceRepository = flowInstanceRepository;
        }

        @Transactional(readOnly = true)
        public void setCommandInTransaction(ExchangeData resultData) {
            try (Stream<FlowInstance> stream = flowInstanceRepository.findAllAsStream() ) {
                resultData.setCommand(new Protocol.FlowInstanceStatus(
                        stream.map(ServerService::to).collect(Collectors.toList())));
            }
        }
    }

    public ServerService(Globals globals, BinaryDataService binaryDataService, CommandProcessor commandProcessor, StationsRepository stationsRepository, CommandSetter commandSetter) {
        this.globals = globals;
        this.binaryDataService = binaryDataService;
        this.commandProcessor = commandProcessor;
        this.stationsRepository = stationsRepository;
        this.commandSetter = commandSetter;
    }

    public ExchangeData processRequest(ExchangeData data, String remoteAddress) {
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
        commandSetter.setCommandInTransaction(resultData);

        List<Command> commands = data.getCommands();
        for (Command command : commands) {
            if (data.getStationId()!=null && command instanceof Protocol.RequestStationId) {
                continue;
            }
            resultData.setCommands(commandProcessor.process(command));
        }

        return resultData.getCommands().isEmpty() ? EXCHANGE_DATA_NOP : resultData;
    }

    private static Protocol.FlowInstanceStatus.SimpleStatus to(FlowInstance flowInstance) {
        return new Protocol.FlowInstanceStatus.SimpleStatus(flowInstance.getId(), Enums.FlowInstanceExecState.toState(flowInstance.getExecState()));
    }

}
