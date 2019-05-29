/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.launchpad.server;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.comm.Command;
import ai.metaheuristic.ai.comm.CommandProcessor;
import ai.metaheuristic.ai.comm.ExchangeData;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Profile("launchpad")
@Slf4j
public class ServerService {

    private static final ExchangeData EXCHANGE_DATA_NOP = new ExchangeData(Protocol.NOP);

    public static final long SESSION_TTL = TimeUnit.MINUTES.toMillis(30);

    private final Globals globals;
    private final BinaryDataService binaryDataService;
    private final CommandProcessor commandProcessor;
    private final StationsRepository stationsRepository;
    private final CommandSetter commandSetter;

    public HttpEntity<AbstractResource> deliverResource(String typeAsStr, String code, String chunkSize, int chunkNum) {
        EnumsApi.BinaryDataType binaryDataType = EnumsApi.BinaryDataType.valueOf(typeAsStr.toUpperCase());
        return deliverResource(binaryDataType, code, chunkSize, chunkNum);
    }

    public HttpEntity<AbstractResource> deliverResource(EnumsApi.BinaryDataType binaryDataType, String code, String chunkSize, int chunkNum) {
        return deliverResource(binaryDataType, code, null, chunkSize, chunkNum);
    }

    public HttpEntity<AbstractResource> deliverResource(EnumsApi.BinaryDataType binaryDataType, String code, HttpHeaders httpHeaders, String chunkSize, int chunkNum) {
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
        File f;
        if (chunkSize==null || chunkSize.isBlank()) {
            f = assetFile.file;
        }
        else {
            f = new File(DirUtils.createTempDir("chunked-file-"), "file-part.bin");
            final long size = Long.parseLong(chunkSize);
            final long offset = size * chunkNum;
            if (offset >= assetFile.file.length()) {
                return null;
            }
            final long realSize = assetFile.file.length() < offset + size ? assetFile.file.length() - offset : size;
            copyChunk(assetFile.file, f, offset, realSize);
            long len = f.length();
        }
        return new HttpEntity<>(new FileSystemResource(f.toPath()), getHeader(httpHeaders, f.length()));
    }

    public static void copyChunk(File sourceFile, File destFile, long offset, long size) {

        try (final FileInputStream fis = new FileInputStream(sourceFile);
             final FileOutputStream fos = new FileOutputStream(destFile);
             FileChannel source = fis.getChannel();
             FileChannel destination = fos.getChannel()
        ){
            destination.transferFrom(source, offset, size);
        }
        catch (Throwable th) {
            ExceptionUtils.wrapAndThrow(th);
        }
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
        private final WorkbookRepository workbookRepository;

        public CommandSetter(WorkbookRepository workbookRepository) {
            this.workbookRepository = workbookRepository;
        }

        // TODO 2019-05-28 Transaction is read-only but method is setCommandInTransaction
        // need to investigate and fix
        @Transactional(readOnly = true)
        public void setCommandInTransaction(ExchangeData resultData) {
            try (Stream<Workbook> stream = workbookRepository.findAllAsStream() ) {
                resultData.setCommand(new Protocol.WorkbookStatus(
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
        Command[] cmds = checkStationId(data, remoteAddress);
        if (cmds!=null) {
            return new ExchangeData(cmds);
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
        addLaunchpadInfo(resultData);

        return resultData;
    }

    private void addLaunchpadInfo(ExchangeData data) {
        LaunchpadConfig lc = new LaunchpadConfig();
        lc.chunkSize = globals.chunkSize;
        data.setLaunchpadConfig(lc);
    }

    private Command[] checkStationId(ExchangeData data, String remoteAddress) {
        if (StringUtils.isBlank(data.getStationId())) {
            return commandProcessor.process(new Protocol.RequestStationId());
        }
        final Station station = stationsRepository.findById(Long.parseLong(data.getStationId())).orElse(null);
        if (station == null) {
            return reassignStationId(remoteAddress, "Id was reassigned from " + data.getStationId());
        }
        StationStatus ss;
        try {
            ss = StationStatusUtils.to(station.status);
        } catch (Throwable e) {
            log.error("Error parsing current status of station:\n{}", station.status);
            log.error("Error ", e);
            // skip any command from this station
            return Protocol.NOP_ARRAY;
        }
        if (StringUtils.isBlank(data.getSessionId())) {
            // the same station but with different and expired sessionId
            // so we can continue to use this stationId with new sessionId
            return assignNewSessionId(station, ss);
        }
        if (!ss.sessionId.equals(data.getSessionId())) {
            if ((System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL) {
                // the same station but with different and expired sessionId
                // so we can continue to use this stationId with new sessionId
                // we won't use station's sessionIf to be sure that sessionId has valid format
                return assignNewSessionId(station, ss);
            } else {
                // different stations with the same stationId
                // there is other active station with valid sessionId
                return reassignStationId(remoteAddress, "Id was reassigned from " + data.getStationId());
            }
        }
        else {
            if ((System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL) {
                // the same station, with the same sessionId
                // so we need just to refresh sessionId
                ss.sessionCreatedOn = System.currentTimeMillis();
                station.status = StationStatusUtils.toString(ss);
                stationsRepository.save(station);
                // the same stationId but new sessionId
                return new Command[]{new Protocol.ReAssignStationId(station.getId(), ss.sessionId)};
            } else {
                // the same stationId, the same sessionId, session isn't expired
                return null;
            }
        }
    }

    private Command[] assignNewSessionId(Station station, StationStatus ss) {
        ss.sessionId = UUID.randomUUID().toString() + '-' + UUID.randomUUID().toString();
        ss.sessionCreatedOn = System.currentTimeMillis();
        station.status = StationStatusUtils.toString(ss);
        stationsRepository.save(station);
        // the same stationId but new sessionId
        return new Command[]{new Protocol.ReAssignStationId(station.getId(), ss.sessionId)};
    }

    public Command[] reassignStationId(String remoteAddress, String description) {
        Station s = new Station();
        s.setIp(remoteAddress);
        s.setDescription(description);

        String sessionId = UUID.randomUUID().toString()+'-'+UUID.randomUUID().toString();
        StationStatus ss = new StationStatus(null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown), "",
                sessionId, System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, false);

        s.status = StationStatusUtils.toString(ss);
        stationsRepository.save(s);
        return new Command[]{new Protocol.ReAssignStationId(s.getId(), sessionId)};
    }

    private static Protocol.WorkbookStatus.SimpleStatus to(Workbook workbook) {
        return new Protocol.WorkbookStatus.SimpleStatus(workbook.getId(), EnumsApi.WorkbookExecState.toState(workbook.getExecState()));
    }

}
